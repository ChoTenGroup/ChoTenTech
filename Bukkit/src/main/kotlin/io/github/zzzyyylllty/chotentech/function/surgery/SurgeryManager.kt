package io.github.zzzyyylllty.chotentech.function.surgery

import io.github.zzzyyylllty.chotentech.data.BodyPart
import io.github.zzzyyylllty.chotentech.data.BodyParts
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.SurgeryConfig
import io.github.zzzyyylllty.chotentech.data.SurgeryTreatment
import io.github.zzzyyylllty.chotentech.data.ToughnessRestore
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.playFor
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.chotentech.function.HealingManager
import io.github.zzzyyylllty.chotentech.function.HealingPhase
import io.github.zzzyyylllty.chotentech.function.PostReason
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.nms.getItemTag
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * 手术：唯一能够**恢复损毁部位韧性**的治疗手段。
 *
 * 与医疗包（[io.github.zzzyyylllty.chotentech.function.HealingManager]）的三点关键差异：
 * 1. **一次只处理一个部位**——Dialog 里选中即锁定，中途不换部位。
 * 2. `per` 阶段开始后按配置**禁止取消 / 移动 / 跳跃**，用来做「不可打断的长手术」。
 * 3. 可以作用于 `DESTROYED` 部位——`BodyPart.heal` 会拒绝损毁部位，
 *    所以这里直接写 `toughness`，由手术自身承担「把部位从 0 拉起来」的语义。
 *
 * 耐久与消耗按「**每轮有效治疗**」结算：`durabilitycost` + `cost.food/san/water` 各一次，
 * `removedebuff` 里的条目额外单独扣耐久（与医疗包一致，不占用本轮额度）。
 */
object SurgeryManager {

    data class SurgeryState(
        val config: SurgeryConfig,
        val item: ItemStack,
        val slot: Int,
        val profile: PlayerData,
        val partId: String,
        /** 已恢复的韧性总计，仅用于展示。 */
        var totalRestored: Double = 0.0,
        var rounds: Int = 0,
        var phase: HealingPhase = HealingPhase.PRE,
        var phaseTicks: Int = 0,
        var perInterval: Int = 0,
        var postReason: PostReason = PostReason.COMPLETE
    )

    private val states = mutableMapOf<UUID, SurgeryState>()
    private var task: PlatformExecutor.PlatformTask? = null

    fun isOperating(uuid: UUID): Boolean = states[uuid]?.phase?.let { it != HealingPhase.POST } ?: false
    fun isInSurgeryOrPost(uuid: UUID): Boolean = uuid in states
    fun getState(uuid: UUID): SurgeryState? = states[uuid]

    /** `per` 阶段且配置为不可取消时，任何取消请求都应被吞掉。 */
    fun isLocked(uuid: UUID): Boolean {
        val state = states[uuid] ?: return false
        return state.phase == HealingPhase.HEALING && !state.config.cancellable
    }

    // ==================== 耐久 ====================

    private fun durability(item: ItemStack): Double {
        val raw = item.getItemTag()["sertraline_data"]?.asCompound()?.get("durability") ?: return 0.0
        val value = raw.asDouble() ?: raw.asString()?.toDoubleOrNull() ?: 0.0
        // 与 HealingManager 一致：整数耐久走 asInt，避免手术费被读成小数误差。
        return if (value == floor(value)) raw.asInt().toDouble() else value
    }

    private fun setDurability(item: ItemStack, value: Double, player: Player) {
        if (value <= 0.000001) {
            item.amount = 0
            return
        }
        val tag = item.getItemTag()
        tag["sertraline_data"]?.asCompound()?.put("durability", value)
        tag.saveTo(item)
        item.rebuildDisplaySafely(player)
    }

    /** 手术刀不一定带 Sertraline 展示组件，缺失时跳过重绘而不是崩溃。 */
    private fun ItemStack.rebuildDisplaySafely(player: Player) {
        try {
            rebuildDisplay(player)
        } catch (_: Throwable) {
        }
    }

    // ==================== 入口 ====================

    /** 右键手术刀：弹出部位选择。一次只能选一个部位。 */
    fun open(player: Player, config: SurgeryConfig, item: ItemStack) {
        if (isInSurgeryOrPost(player.uniqueId) || BleedoutManager.isDowned(player.uniqueId) ||
            HealingManager.isInHealingOrPost(player.uniqueId)) return
        val data = player.getData() ?: return
        if (!config.operable) {
            player.sendActionBar("<#E08A98>该手术配置没有可执行的恢复内容（toughness / removedebuff 均为空）".toComponent())
            return
        }
        val body = data.bodyHealth
        BodyHealthManager.syncMaxima(player, body)
        val slot = player.inventory.heldItemSlot
        val snapshot = item.clone()

        fun button(part: BodyPart): ActionButton {
            val effects = part.effects.keys.joinToString("、")
            val state = when {
                part.destroyed -> "损毁"
                part.percentage < 1 -> "受伤"
                else -> "正常"
            }
            val label = "${BodyParts.names[part.id]} ${num(part.toughness)}/${num(part.maxToughness)} $state" +
                (if (effects.isNotEmpty()) "（$effects）" else "")
            return ActionButton.builder(label.toComponent()).width(220).action(DialogAction.customClick({ _, audience ->
                if (audience == player) submit {
                    if (!player.isOnline || player.getData() !== data || player.inventory.heldItemSlot != slot ||
                        player.inventory.itemInMainHand != snapshot || BleedoutManager.isDowned(player.uniqueId)) return@submit
                    begin(player, config, player.inventory.itemInMainHand, part.id)
                }
            }, ClickCallback.Options.builder().uses(1).build())).build()
        }

        val buttons = body.parts.values.map { button(it) }
        val lines = listOf(
            DialogBody.plainMessage("一次手术只能处理一个部位。".toComponent()),
            DialogBody.plainMessage(
                ("每轮恢复 ${describe(config)}，消耗耐久 ${num(config.durabilityCost)}" +
                    "、饱食 ${num(config.costFood)}、精神 ${num(config.costSan)}、水分 ${num(config.costWater)}。")
                    .toComponent()
            ),
            DialogBody.plainMessage(
                ("准备 ${num(config.preTime)}s 后开始，每 ${num(config.perTime)}s 结算一次" +
                    (if (!config.cancellable) "，开始后不可取消" else "") +
                    (if (!config.moveable) "、不可移动" else "") +
                    (if (!config.jumpable) "、不可跳跃" else "") + "。").toComponent()
            )
        )
        val dialogBase = DialogBase.builder("选择手术部位".toComponent())
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(lines)
            .build()
        player.showDialog(Dialog.create { factory -> factory.empty().apply {
            base(dialogBase)
            type(DialogType.multiAction(buttons, null, 1))
        } })
    }

    private fun describe(config: SurgeryConfig): String = when (val t = config.toughness) {
        is ToughnessRestore.Flat -> "${num(t.value)} 点韧性"
        is ToughnessRestore.Percent -> "最大韧性的 ${num(t.percent)}%"
    }

    private fun begin(player: Player, config: SurgeryConfig, item: ItemStack, partId: String) {
        if (isInSurgeryOrPost(player.uniqueId) || BleedoutManager.isDowned(player.uniqueId) ||
            HealingManager.isInHealingOrPost(player.uniqueId)) return
        val data = player.getData() ?: return
        val part = data.bodyHealth.parts[partId] ?: return
        // 手术的卖点是「把损毁部位拉起来」，所以这里**不**拒绝 DESTROYED。
        val canRemove = part.effects.keys.any { it in config.removeDebuff }
        val canRestore = part.percentage < 1 &&
            config.toughness.amountFor(part.maxToughness) > 0
        if (!canRemove && !canRestore) {
            player.sendActionBar("<#EFDF8F>该部位没有手术可处理的内容".toComponent())
            return
        }
        if (durability(item) <= 0) {
            player.sendActionBar("<#E08A98>手术刀耐久不足".toComponent())
            return
        }
        val slot = player.inventory.heldItemSlot
        val useItem = item.clone().apply { amount = 1 }
        // 与医疗包相同的拆分逻辑：先把多余数量塞回背包，避免整堆共享耐久。
        if (item.amount > 1) {
            val remaining = item.clone().apply { amount = item.amount - 1 }
            for (index in 0 until player.inventory.storageContents.size) {
                if (index == slot || remaining.amount <= 0) continue
                val existing = player.inventory.getItem(index) ?: continue
                if (!existing.isSimilar(remaining)) continue
                val added = min(remaining.amount, (existing.maxStackSize - existing.amount).coerceAtLeast(0))
                if (added > 0) {
                    existing.amount += added
                    remaining.amount -= added
                    player.inventory.setItem(index, existing)
                }
            }
            for (index in 0 until player.inventory.storageContents.size) {
                if (index == slot || remaining.amount <= 0) continue
                if (player.inventory.getItem(index)?.type?.isAir?.not() == true) continue
                val placed = remaining.clone().apply { amount = min(remaining.amount, maxStackSize) }
                player.inventory.setItem(index, placed)
                remaining.amount -= placed.amount
            }
            if (remaining.amount > 0) player.world.dropItemNaturally(player.location, remaining)
        }
        player.inventory.setItem(slot, useItem)
        states[player.uniqueId] = SurgeryState(
            config = config, item = useItem.clone(), slot = slot, profile = data, partId = partId,
            phaseTicks = ticks(config.preTime)
        )
        config.soundPre.playFor(player)
        player.sendActionBar(
            "<#84D1CB>准备手术：<#F2F5FA>${BodyParts.names[partId]} <#A9B4C3>${num(config.preTime)}s 后开始".toComponent()
        )
    }

    // ==================== 取消与收尾 ====================

    /** 外部请求取消；`per` 阶段不可取消时会被忽略。 */
    fun cancel(player: Player, reason: PostReason) {
        val state = states[player.uniqueId] ?: return
        if (state.phase == HealingPhase.POST) return
        if (state.phase == HealingPhase.HEALING && !state.config.cancellable) return
        post(player, state, reason)
    }

    /** 无障碍取消：物品丢失、下线、死亡等必须终止的情况走这里。 */
    fun forceStop(player: Player, reason: PostReason) {
        states[player.uniqueId]?.takeIf { it.phase != HealingPhase.POST }?.let { post(player, it, reason) }
    }

    private fun post(player: Player, state: SurgeryState, reason: PostReason) {
        state.phase = HealingPhase.POST
        state.postReason = reason
        state.phaseTicks = ticks(state.config.postTime)
        when (reason) {
            PostReason.COMPLETE -> state.config.soundComplete.playFor(player)
            PostReason.CANCELLED -> state.config.soundCancel.playFor(player)
            PostReason.ITEM_LOST -> state.config.soundExhaust.playFor(player)
        }
    }

    @Awake(LifeCycle.ACTIVE)
    fun startSurgeryTick() {
        task = submit(period = 5) { tick() }
    }

    @Awake(LifeCycle.DISABLE)
    fun stopSurgeryTick() {
        task?.cancel()
        states.clear()
    }

    private fun ticks(seconds: Double): Int = (seconds * 4).toInt().coerceAtLeast(0)

    private fun tick() {
        for ((uuid, state) in states.toMap()) {
            val player = Bukkit.getPlayer(uuid)
            if (player == null || player.isDead || player.getData() !== state.profile) {
                states.remove(uuid)
                continue
            }
            if (state.phase == HealingPhase.POST) {
                if (--state.phaseTicks <= 0) {
                    states.remove(uuid)
                    state.config.soundEnd.playFor(player)
                    player.sendActionBar("<#8FD4A6>手术结束".toComponent())
                }
                continue
            }
            if (BleedoutManager.isDowned(uuid)) {
                forceStop(player, PostReason.CANCELLED)
                continue
            }
            val held = player.inventory.itemInMainHand
            if (player.inventory.heldItemSlot != state.slot || held != state.item || held.amount <= 0) {
                forceStop(player, PostReason.ITEM_LOST)
                continue
            }
            if (state.phase == HealingPhase.PRE) {
                if (--state.phaseTicks > 0) {
                    continue
                }
                state.phase = HealingPhase.HEALING
                state.config.soundStart.playFor(player)
                player.sendActionBar("<#84D1CB>手术开始".toComponent())
            }
            if (--state.perInterval > 0) continue
            state.perInterval = ticks(state.config.perTime).coerceAtLeast(1)
            treat(player, state, held)
            state.item.itemMeta = held.itemMeta
            state.item.amount = held.amount
        }
    }

    // ==================== 结算 ====================

    private fun treat(player: Player, state: SurgeryState, held: ItemStack) {
        val config = state.config
        val body = state.profile.bodyHealth
        val part = body.parts[state.partId] ?: run { forceStop(player, PostReason.CANCELLED); return }
        val available = durability(held)
        if (available <= 0) {
            forceStop(player, PostReason.ITEM_LOST)
            return
        }

        // 结算全部交给纯逻辑，运行时只负责消耗与播报。
        val result = SurgeryTreatment.apply(
            body = body,
            partId = state.partId,
            durability = available,
            durabilityCost = config.durabilityCost,
            toughness = config.toughness,
            removeDebuff = config.removeDebuff
        )

        if (result.stop == SurgeryTreatment.Stop.UNAFFORDABLE_DEBUFF) {
            val need = config.removeDebuff.filterKeys { it !in result.removed }.values.sum()
            player.sendActionBar(
                "<#E08A98>移除伤势需要 ${num(need)} 耐久，当前仅有 ${num(result.durability)}，手术中止".toComponent()
            )
            forceStop(player, PostReason.ITEM_LOST)
            BodyHealthManager.syncPenalties(player, body)
            return
        }

        if (!result.worked) {
            player.sendActionBar("<#8FD4A6>${BodyParts.names[part.id]} 已无可手术内容".toComponent())
            forceStop(player, PostReason.COMPLETE)
            return
        }

        // 每轮有效治疗统一扣一次耐久与消耗。
        state.profile.customHunger = (state.profile.customHunger - config.costFood).coerceAtLeast(0.0)
        state.profile.sanity = (state.profile.sanity - config.costSan).coerceAtLeast(0.0)
        if (config.costWater > 0) {
            body.water = (body.water - config.costWater).coerceAtLeast(0.0)
            // 与饮水一致：水分回正即重置脱水计时；这里只是别让手术把计时拖着走。
            if (body.water > 0) body.dehydrationTicks = 0
        }
        state.totalRestored += result.restored
        state.rounds++
        setDurability(held, result.durability, player)
        BodyHealthManager.syncPenalties(player, body)
        config.soundHeal.playFor(player)

        player.sendActionBar(
            ("<#84D1CB>手术中 <#A9B4C3>${BodyParts.names[part.id]} " +
                "<#F2F5FA>${num(part.toughness)}<#6E7A8C>/${num(part.maxToughness)} " +
                "<#A9B4C3>[${part.state}] 耐久 ${num(result.durability)}").toComponent()
        )

        if (result.finished) {
            forceStop(player, if (result.stop == SurgeryTreatment.Stop.NO_DURABILITY) PostReason.ITEM_LOST
            else PostReason.COMPLETE)
        }
    }

    // ==================== 阶段限制 ====================

    /** `moveable: false` → `per` 阶段锁定水平位移，但仍允许抬头/低头。 */
    @SubscribeEvent(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val state = states[event.player.uniqueId] ?: return
        if (state.phase != HealingPhase.HEALING || state.config.moveable) return
        if (event.from.x == event.to.x && event.from.y == event.to.y && event.from.z == event.to.z) return
        event.to = event.from
    }

    /** `jumpable: false` → `per` 阶段拦截跳跃。 */
    @SubscribeEvent(ignoreCancelled = true)
    fun onJump(event: PlayerJumpEvent) {
        val state = states[event.player.uniqueId] ?: return
        if (state.phase != HealingPhase.HEALING || state.config.jumpable) return
        event.isCancelled = true
    }

    /** 不可取消的手术在 `per` 阶段免疫打断：受击不再中止手术。 */
    @SubscribeEvent(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        val state = states[player.uniqueId] ?: return
        if (state.phase != HealingPhase.HEALING) return
        if (state.config.cancellable) forceStop(player, PostReason.CANCELLED)
    }

    private fun num(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded) && abs(rounded) < 1e15) rounded.toLong().toString()
        else rounded.toString()
    }
}

package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.surgery.SurgeryManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.nms.getItemTag
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor

// Keep the existing listener-facing phase API.
enum class HealingPhase { PRE, HEALING, POST }
enum class PostReason { COMPLETE, CANCELLED, ITEM_LOST }

object HealingManager {
    data class HealingState(
        val config: HealConfig,
        val item: ItemStack,
        val slot: Int,
        val profile: PlayerData,
        val partId: String?,
        val healHealth: Boolean,
        var totalHealed: Double = 0.0,
        var phase: HealingPhase = HealingPhase.PRE,
        var phaseTicks: Int = 0,
        var healInterval: Int = 0,
        var postReason: PostReason = PostReason.COMPLETE
    )
    private val states = mutableMapOf<UUID, HealingState>()
    private var task: PlatformExecutor.PlatformTask? = null
    fun isHealing(uuid: UUID) = states[uuid]?.phase?.let { it != HealingPhase.POST } ?: false
    fun isInHealingOrPost(uuid: UUID) = uuid in states
    fun getState(uuid: UUID) = states[uuid]

    private fun durability(item: ItemStack): Double {
        val raw = item.getItemTag()["sertraline_data"]?.asCompound()?.get("durability") ?: return 0.0
        val value = raw.asDouble() ?: raw.asString()?.toDoubleOrNull() ?: 0.0
        // NBT ints are read back as 3 decimals; healing must spend and keep fractional durability.
        return if (value == floor(value)) raw.asInt().toDouble() else value
    }
    private fun setDurability(item: ItemStack, value: Double, player: Player) {
        if (value <= 0.000001) { item.amount = 0; return }
        val tag = item.getItemTag()
        tag["sertraline_data"]?.asCompound()?.put("durability", value)
        tag.saveTo(item)
        item.rebuildDisplay(player)
    }

    fun enter(player: Player, config: HealConfig, item: ItemStack) {
        if (isInHealingOrPost(player.uniqueId) || BleedoutManager.isDowned(player.uniqueId) ||
            SurgeryManager.isInSurgeryOrPost(player.uniqueId)) return
        val data = player.getData() ?: return
        val body = data.bodyHealth
        BodyHealthManager.syncMaxima(player, body)
        val slot = player.inventory.heldItemSlot
        val snapshot = item.clone()
        fun button(label: String, part: String?, hp: Boolean, quick: Boolean = false): ActionButton =
            ActionButton.builder(label.toComponent()).width(220).action(DialogAction.customClick({ _, audience ->
                if (audience == player) submit {
                    if (!player.isOnline || player.getData() !== data || player.inventory.heldItemSlot != slot ||
                        player.inventory.itemInMainHand != snapshot || BleedoutManager.isDowned(player.uniqueId)) return@submit
                    val selected = if (quick) body.quickTreatmentPart()?.id ?: body.parts.values.firstOrNull {
                        !it.destroyed && it.effects.keys.any { type -> type in config.removeDebuff }
                    }?.id else part
                    begin(player, config, player.inventory.itemInMainHand, selected, hp)
                }
            }, ClickCallback.Options.builder().uses(1).build())).build()
        val buttons = mutableListOf<ActionButton>()
        for (part in body.parts.values) {
            val effects = part.effects.keys.joinToString("、")
            val state = if (part.destroyed) "损毁，无法治疗" else effects.ifEmpty { "正常" }
            val label = "${BodyParts.names[part.id]} ${num(part.toughness)}/${num(part.maxToughness)} $state"
            // ①②：移除伤势、恢复韧性。
            buttons += button(label, part.id, false)
            // ③：同一次治疗里，该部位韧性回满后用当轮剩余额度继续恢复血量。
            if (config.hpLeast >= 0 && !part.destroyed) {
                buttons += button("$label ＋血量", part.id, true)
            }
        }
        buttons += button("快速治疗受损部位", null, false, true)
        if (config.hpLeast >= 0) {
            buttons += button("快速治疗，随后恢复血量（韧性>${num(config.hpLeast)}%）", null, true, true)
            // 单独的「仅恢复血量」按钮：不治韧性，只走第三步。门槛不满足时按下会说明原因。
            buttons += button("仅恢复血量（需全身韧性>${num(config.hpLeast)}%）", null, true)
        }
        player.showDialog(Dialog.create { factory -> factory.empty().apply {
            base(DialogBase.builder("选择医疗部位".toComponent()).pause(false).canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(listOf(
                    DialogBody.plainMessage("结算顺序：① 移除所选部位伤势 → ② 恢复该部位韧性 → ③ 恢复血量。".toComponent()),
                    DialogBody.plainMessage("带「＋血量」的按钮在第 ② 步回满后，用当轮剩余额度执行第 ③ 步。".toComponent()),
                    DialogBody.plainMessage("「仅恢复血量」跳过 ①②，且每轮都重新检查全身韧性需严格大于 ${num(config.hpLeast)}%。".toComponent()),
                )).build())
            type(DialogType.multiAction(buttons, null, 2))
        } })
    }

    private fun num(value: Double): String =
        if (value == Math.floor(value) && abs(value) < 1e15) value.toLong().toString() else "%.1f".format(value)

    private fun begin(player: Player, config: HealConfig, item: ItemStack, partId: String?, hp: Boolean) {
        if (isInHealingOrPost(player.uniqueId) || BleedoutManager.isDowned(player.uniqueId) ||
            SurgeryManager.isInSurgeryOrPost(player.uniqueId)) return
        val data = player.getData() ?: return
        val part = partId?.let { data.bodyHealth.parts[it] }
        if (part?.destroyed == true) {
            player.sendActionBar("<#E08A98>损毁肢体不能接受常规治疗".toComponent()); return
        }
        val canRemove = part?.effects?.keys?.any { it in config.removeDebuff } == true
        val canRepair = part != null && part.percentage < 1 && config.maxHeal > 0 && config.healSpeed > 0
        val canHp = hp && config.hpLeast >= 0 && data.bodyHealth.toughnessPercentage > config.hpLeast &&
            player.health < (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
        if (!canRemove && !canRepair && !canHp) {
            // 门槛/血量已满这类拒绝很常见，直接说清楚，别让人以为是物品坏了。
            val current = data.bodyHealth.toughnessPercentage
            val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            val message = when {
                hp && config.hpLeast < 0 -> "该医疗包不能恢复血量（hpleast: -1）"
                hp && current <= config.hpLeast ->
                    "全身韧性 ${num(current)}% 未超过 ${num(config.hpLeast)}%，暂不能恢复血量"
                hp && player.health >= maxHp -> "血量已满"
                else -> "该部位没有可治疗项目"
            }
            player.sendActionBar("<#EFDF8F>$message".toComponent())
            return
        }
        if (durability(item) <= 0) return
        val slot = player.inventory.heldItemSlot
        val useItem = item.clone().apply { amount = 1 }
        // Exclude the selected slot while returning the rest; addItem would merge it back
        // into the unit being used and make the whole stack share durability again.
        if (item.amount > 1) {
            val remaining = item.clone().apply { amount = item.amount - 1 }
            for (index in 0 until player.inventory.storageContents.size) {
                if (index == slot || remaining.amount <= 0) continue
                val existing = player.inventory.getItem(index) ?: continue
                if (!existing.isSimilar(remaining)) continue
                val added = minOf(remaining.amount, (existing.maxStackSize - existing.amount).coerceAtLeast(0))
                existing.amount += added
                remaining.amount -= added
                player.inventory.setItem(index, existing)
            }
            for (index in 0 until player.inventory.storageContents.size) {
                if (index == slot || remaining.amount <= 0) continue
                if (player.inventory.getItem(index)?.type?.isAir == false) continue
                val placed = remaining.clone().apply { amount = minOf(remaining.amount, maxStackSize) }
                player.inventory.setItem(index, placed)
                remaining.amount -= placed.amount
            }
            if (remaining.amount > 0) player.world.dropItemNaturally(player.location, remaining)
        }
        player.inventory.setItem(slot, useItem)
        states[player.uniqueId] = HealingState(config, useItem.clone(), slot, data,
            partId, hp, phaseTicks = (config.preTime * 4).toInt())
        if (config.slow > 0) AttributeManager.addModifier(player.uniqueId, AttributeModifier(
            attributeId = "movement_speed", value = -config.slow / 100,
            type = ModifierType.PERCENT, source = ModifierSource.HEALING, slot = AttributeSlot.OTHER))
        config.soundPre.play(player)
    }

    fun cancel(player: Player, reason: PostReason) {
        states[player.uniqueId]?.takeIf { it.phase != HealingPhase.POST }?.let { post(player, it, reason) }
    }
    private fun post(player: Player, state: HealingState, reason: PostReason) {
        state.phase = HealingPhase.POST
        state.postReason = reason
        state.phaseTicks = (state.config.postTime * 4).toInt()
        when (reason) {
            PostReason.COMPLETE -> state.config.soundComplete.play(player)
            PostReason.CANCELLED -> state.config.soundCancel.play(player)
            PostReason.ITEM_LOST -> state.config.soundExhaust.play(player)
        }
    }

    @Awake(LifeCycle.ACTIVE)
    fun startHealingTick() { task = submit(period = 5) { tick() } }
    @Awake(LifeCycle.DISABLE)
    fun stopHealingTick() {
        task?.cancel()
        states.keys.toList().forEach { cleanup(it) }
    }
    private fun cleanup(uuid: UUID) {
        states.remove(uuid)
        AttributeManager.removeModifiersByAttribute(uuid, "movement_speed", ModifierSource.HEALING)
    }
    private fun tick() {
        for ((uuid, state) in states.toMap()) {
            val player = Bukkit.getPlayer(uuid)
            if (player == null || player.isDead || player.getData() !== state.profile) { cleanup(uuid); continue }
            if (state.phase == HealingPhase.POST) {
                if (--state.phaseTicks <= 0) { cleanup(uuid); state.config.soundEnd.play(player) }
                continue
            }
            if (BleedoutManager.isDowned(uuid)) { post(player, state, PostReason.CANCELLED); continue }
            val held = player.inventory.itemInMainHand
            if (player.inventory.heldItemSlot != state.slot || held != state.item || held.amount <= 0) {
                post(player, state, PostReason.ITEM_LOST); continue
            }
            if (state.phase == HealingPhase.PRE) {
                if (--state.phaseTicks > 0) continue
                state.phase = HealingPhase.HEALING
                state.config.soundStart.play(player)
            }
            if (--state.healInterval > 0) continue
            state.healInterval = (state.config.perTime * 4).toInt().coerceAtLeast(1)
            treat(player, state, held)
            state.item.itemMeta = held.itemMeta
            state.item.amount = held.amount
        }
    }

    private fun treat(player: Player, state: HealingState, held: ItemStack) {
        val config = state.config
        val body = state.profile.bodyHealth
        val part = state.partId?.let { body.parts[it] }
        if (part?.destroyed == true) { post(player, state, PostReason.CANCELLED); return }
        val available = durability(held)
        if (available <= 0) { post(player, state, PostReason.ITEM_LOST); return }
        val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        val result = BodyTreatment.apply(body, state.partId, state.healHealth, maxHp - player.health,
            available, state.totalHealed, config.maxHeal, config.healSpeed, config.healRate,
            config.hpLeast, config.removeDebuff)
        val dur = result.durability
        val changed = result.changed
        // treated 是「韧性与血量合计」的额度占用，血量已包含在内，不要再叠加 hpHealed。
        state.totalHealed += result.treated
        if (result.hpHealed > 0) player.health = minOf(maxHp, player.health + result.hpHealed)
        BodyHealthManager.syncPenalties(player, body)
        val hpEligible = state.healHealth && config.hpLeast >= 0 && body.toughnessPercentage > config.hpLeast
        if (changed) {
            setDurability(held, dur, player)
            state.profile.customHunger = (state.profile.customHunger - config.costFood).coerceAtLeast(0.0)
            state.profile.sanity = (state.profile.sanity - config.costSan).coerceAtLeast(0.0)
            config.soundHeal.play(player)
        }
        val done = result.blocked || !changed || state.totalHealed + 1e-9 >= config.maxHeal ||
            ((part == null || part.percentage >= 1) && (!hpEligible || player.health >= maxHp))
        when {
            dur <= 1e-9 -> post(player, state, PostReason.ITEM_LOST)
            done -> post(player, state, PostReason.COMPLETE)
            else -> player.sendActionBar("<#84D1CB>治疗中 <#A9B4C3>耐久 ${"%.1f".format(dur)}，已治疗 ${"%.1f".format(state.totalHealed)}/${config.maxHeal}".toComponent())
        }
    }
}

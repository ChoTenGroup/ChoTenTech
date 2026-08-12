package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.data.HealConfig
import io.github.zzzyyylllty.chotentech.data.FRACTURE
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import io.github.zzzyyylllty.sertraline.item.rebuildName
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.nms.getItemTag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

enum class HealingPhase { PRE, HEALING, POST }
enum class PostReason { COMPLETE, CANCELLED, ITEM_LOST }

object HealingManager {

    private val states = ConcurrentHashMap<UUID, HealingState>()
    private var healingTask: PlatformExecutor.PlatformTask? = null

    data class HealingState(
        val config: HealConfig,
        val item: ItemStack,
        var totalHealedHP: Double,
        var totalHealedII: Double,
        var totalHealedEI: Double,
        var hpFullWarned: Boolean,
        var iiCapWarned: Boolean,
        var eiCapWarned: Boolean,
        val speedModifierUuid: UUID?,
        var phase: HealingPhase = HealingPhase.PRE,
        var phaseTicks: Int = 0,
        var healInterval: Int = 0,
        var postReason: PostReason = PostReason.COMPLETE
    )

    fun isHealing(uuid: UUID): Boolean = states[uuid]?.let { it.phase != HealingPhase.POST } ?: false

    fun isInHealingOrPost(uuid: UUID): Boolean = states.containsKey(uuid)

    fun getState(uuid: UUID): HealingState? = states[uuid]

    // --- NBT durability helpers ---

    private fun getNbtDurability(item: ItemStack): Double {
        val tag = item.getItemTag() ?: return 0.0
        val raw = tag["sertraline_data"]?.asCompound()?.get("durability") ?: return 0.0
        return raw.asDouble() ?: raw.asString()?.toDoubleOrNull() ?: 0.0
    }

    private fun setNbtDurability(item: ItemStack, value: Double, player: Player) {
        val tag = item.getItemTag() ?: return
        tag["sertraline_data"]?.asCompound()?.put("durability", floor(value))
        tag.saveTo(item)
        item.rebuildDisplay(player)
    }

    fun enter(player: Player, config: HealConfig, item: ItemStack) {
        val uuid = player.uniqueId
        if (states.containsKey(uuid)) return  // already healing or in post
        val data = playerDataMap[uuid.toString()] ?: return

        // 检查是否有任何该医疗物品可以治疗的项目
        val canHealHP = config.maxHeal != 0.0 && config.healSpeed != 0.0
        val canHealII = config.maxIInjury != 0.0 && config.iinjurySpeed != 0.0
        val canHealEI = config.maxEInjury != 0.0 && config.einjurySpeed != 0.0

        val needsHP = canHealHP && player.health < (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
        val needsII = canHealII && data.internalInjury < data.maxInternalInjury
        val needsEI = canHealEI && data.externalInjury < data.maxExternalInjury

        if (!needsHP && !needsII && !needsEI) {
            // 玩家是否有任何伤势（无论该物品能否治疗）
            val hasAnyInjury = player.health < (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
                || data.internalInjury < data.maxInternalInjury
                || data.externalInjury < data.maxExternalInjury
            if (hasAnyInjury) {
                player.sendActionBar("<yellow>无需治疗（或当前物品无法治疗某些伤势）".toComponent())
            } else {
                player.sendActionBar("<yellow>状态已满，无需治疗".toComponent())
            }
            config.soundAlreadyFull.play(player)
            return
        }

        // 堆叠物品：拆分一个单独使用，避免共享 NBT 导致整组耐久变化
        val useItem: ItemStack = if (item.amount > 1) {
            val single = item.clone()
            single.amount = 1
            item.amount -= 1
            player.inventory.setItem(player.inventory.heldItemSlot, single)
            single
        } else {
            item
        }

        var speedModUuid: UUID? = null
        if (config.slow > 0) {
            // 先清除上次残留的治疗减速（防止 UUID 丢失导致修饰符泄漏）
            AttributeManager.removeModifiersByAttribute(uuid, "movement_speed", ModifierSource.HEALING)

            val modifier = AttributeModifier(
                attributeId = "movement_speed",
                value = -(config.slow / 100.0),
                type = ModifierType.PERCENT,
                source = ModifierSource.HEALING,
                slot = AttributeSlot.OTHER
            )
            speedModUuid = modifier.uuid
            AttributeManager.addModifier(uuid, modifier)
        }

        val state = HealingState(
            config = config,
            item = useItem,
            totalHealedHP = 0.0,
            totalHealedII = 0.0,
            totalHealedEI = 0.0,
            hpFullWarned = false,
            iiCapWarned = false,
            eiCapWarned = false,
            speedModifierUuid = speedModUuid,
            phase = HealingPhase.PRE,
            phaseTicks = maxOf((config.preTime * 4).toInt(), 0),
            healInterval = 0
        )

        states[uuid] = state
        config.soundPre.play(player)
    }

    fun cancel(player: Player, reason: PostReason) {
        val state = states[player.uniqueId] ?: return
        if (state.phase == HealingPhase.POST) return
        enterPost(state, reason, player)
    }

    private fun enterPost(state: HealingState, reason: PostReason, player: Player? = null) {
        state.phase = HealingPhase.POST
        state.phaseTicks = if (reason == PostReason.ITEM_LOST) 1 else maxOf((state.config.postTime * 4).toInt(), 0)
        state.postReason = reason
        if (player != null) {
            when (reason) {
                PostReason.COMPLETE -> state.config.soundComplete.play(player)
                PostReason.CANCELLED -> state.config.soundCancel.play(player)
                PostReason.ITEM_LOST -> state.config.soundExhaust.play(player)
            }
        }
    }

    private fun isItemValid(item: ItemStack): Boolean =
        item.amount > 0 && item.type != Material.AIR

    // --- 理疗技能：概率不消耗耐久 ---

    private fun shouldPreserveDurability(player: Player): Boolean {
        if (!player.isOnline) return false
        val level = try { PlayerDataManager(player).getLevel("physiotherapy") ?: 0L } catch (_: Exception) { 0L }
        if (level <= 0) return false
        // 每级 0.25%，满级 100 级 = 25%
        return ThreadLocalRandom.current().nextDouble() < (level * 0.0025)
    }

    // --- instant heal helpers ---

    private fun tryInstantHeal(
        item: ItemStack,
        dur: Double,
        cap: Double,
        rate: Double,
        player: Player,
        apply: (actual: Double) -> Unit
    ): Boolean {
        if (dur <= 0 || cap <= 0) return false
        val healed = minOf(cap, dur / rate)
        val actual = floor(healed * 100) / 100
        if (actual <= 0) return false
        apply(actual)
        // 理疗技能：概率不消耗耐久
        if (shouldPreserveDurability(player)) return false
        val consumed = maxOf(1.0, floor(actual * rate))
        val newDur = dur - consumed
        if (newDur <= 0) {
            item.amount -= 1
            return true
        }
        setNbtDurability(item, newDur, player)
        return false
    }

    // --- tick-based heal helper ---

    private fun tryHealTick(
        item: ItemStack,
        dur: Double,
        cumulativeCap: Double,
        rate: Double,
        player: Player,
        apply: (actual: Double) -> Unit
    ): HealResult {
        val durabilityLimit = dur / rate
        val toHeal = minOf(cumulativeCap, durabilityLimit)
        val actual = floor(toHeal * 100) / 100
        if (actual <= 0) {
            // 剩余可治疗量过小经舍入后为0，不消耗物品，由 isHealDone 判定是否完成
            return HealResult(dur, false)
        }
        apply(actual)
        // 理疗技能：概率不消耗耐久
        if (shouldPreserveDurability(player)) return HealResult(dur, false)
        val consumed = maxOf(1.0, floor(actual * rate))
        val newDur = dur - consumed
        if (newDur <= 0) {
            item.amount -= 1
            return HealResult(0.0, true)
        }
        setNbtDurability(item, newDur, player)
        return HealResult(newDur, false)
    }

    private data class HealResult(val newDur: Double, val itemConsumed: Boolean)

    private fun isAllFull(
        player: Player,
        data: io.github.zzzyyylllty.chotentech.data.PlayerData,
        config: HealConfig
    ): Boolean {
        val hpFull = if (config.maxHeal != 0.0 && config.healSpeed != 0.0) {
            val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health >= maxHP - 0.01
        } else true
        val iiFull = if (config.maxIInjury != 0.0 && config.iinjurySpeed != 0.0) {
            data.internalInjury >= data.maxInternalInjury - 0.01
        } else true
        val eiFull = if (config.maxEInjury != 0.0 && config.einjurySpeed != 0.0) {
            data.externalInjury >= data.maxExternalInjury - 0.01
        } else true
        return hpFull && iiFull && eiFull
    }

    private fun isHealDone(
        player: Player,
        data: io.github.zzzyyylllty.chotentech.data.PlayerData,
        state: HealingState
    ): Boolean {
        val config = state.config
        val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        val hpDone = if (config.maxHeal != 0.0 && config.healSpeed > 0) {
            player.health >= maxHP - 0.01 || (config.maxHeal != -1.0 && state.totalHealedHP >= config.maxHeal - 0.01)
        } else true
        val iiDone = if (config.maxIInjury != 0.0 && config.iinjurySpeed > 0) {
            data.internalInjury >= data.maxInternalInjury - 0.01 || state.totalHealedII >= config.maxIInjury - 0.01
        } else true
        val eiDone = if (config.maxEInjury != 0.0 && config.einjurySpeed > 0) {
            data.externalInjury >= data.maxExternalInjury - 0.01 || state.totalHealedEI >= config.maxEInjury - 0.01
        } else true
        return hpDone && iiDone && eiDone
    }

    @Awake(LifeCycle.ACTIVE)
    fun startHealingTick() {
        healingTask = submit(period = 5L) {
            tick()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun stopHealingTick() {
        healingTask?.cancel()
        // 清理所有残留的减速修饰符
        for (uuid in states.keys) {
            AttributeManager.removeModifiersByAttribute(uuid, "movement_speed", ModifierSource.HEALING)
        }
        states.clear()
    }

    private fun tick() {
        val toRemove = mutableListOf<UUID>()

        for ((uuid, state) in states) {
            val player = Bukkit.getPlayer(uuid)
            if (player == null || !player.isOnline) {
                toRemove.add(uuid)
                continue
            }
            if (player.isDead) {
                toRemove.add(uuid)
                continue
            }

            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            val config = state.config
            val item = state.item

            when (state.phase) {

                HealingPhase.PRE -> {
                    state.phaseTicks--
                    if (state.phaseTicks > 0) {
                        val sec = state.phaseTicks / 4 + 1
                        player.sendActionBar("<aqua>治疗准备中... <gray>${sec}秒".toComponent())
                        continue
                    }
                    // Transition to HEALING
                    state.phase = HealingPhase.HEALING
                    state.healInterval = 1  // first heal on next tick immediately

                    // Check item validity
                    if (!isItemValid(item)) {
                        enterPost(state, PostReason.ITEM_LOST, player)
                        continue
                    }

                    // Check initial durability
                    val dur = getNbtDurability(item)
                    if (dur <= 0) {
                        item.amount -= 1
                        enterPost(state, PostReason.ITEM_LOST, player)
                        continue
                    }

                    // Apply instant heals
                    var consumed = false
                    val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

                    if (config.healSpeed == -1.0 && config.maxHeal != 0.0) {
                        val missing = maxOf(maxHP - player.health, 0.0)
                        val cap = if (config.maxHeal == -1.0) missing else minOf(config.maxHeal, missing)
                        val d = getNbtDurability(item)
                        consumed = tryInstantHeal(item, d, cap, config.healRate, player) { actual ->
                            player.health = minOf(player.health + actual, maxHP)
                            state.totalHealedHP += actual
                        }
                        if (!consumed) {
                            val hpFull = maxHP - player.health <= 0
                            val capReached = config.maxHeal != -1.0 && state.totalHealedHP >= config.maxHeal
                            if (!state.hpFullWarned && (hpFull || capReached)) {
                                player.sendMessage("<yellow>${if (hpFull) "血量已满" else "已达最大治疗量"}，不再回复血量。".toComponent())
                                config.soundCapReached.play(player)
                                state.hpFullWarned = true
                            }
                        }
                    }
                    if (!consumed && config.iinjurySpeed == -1.0 && config.maxIInjury != 0.0) {
                        val missing = data.maxInternalInjury - data.internalInjury
                        val cap = minOf(config.maxIInjury, maxOf(missing, 0.0))
                        val d = getNbtDurability(item)
                        consumed = tryInstantHeal(item, d, cap, config.iinjuryRate, player) { actual ->
                            data.internalInjury = minOf(data.internalInjury + actual, data.maxInternalInjury)
                            state.totalHealedII += actual
                        }
                        if (!consumed) {
                            val iiFull = data.internalInjury >= data.maxInternalInjury
                            val capReached = state.totalHealedII >= config.maxIInjury
                            if (!state.iiCapWarned && (iiFull || capReached)) {
                                player.sendMessage("<yellow>${if (iiFull) "内伤已满" else "已达最大内伤治疗量"}，不再回复内伤。".toComponent())
                                config.soundCapReached.play(player)
                                state.iiCapWarned = true
                            }
                        }
                    }
                    if (!consumed && config.einjurySpeed == -1.0 && config.maxEInjury != 0.0) {
                        val missing = data.maxExternalInjury - data.externalInjury
                        val cap = minOf(config.maxEInjury, maxOf(missing, 0.0))
                        val d = getNbtDurability(item)
                        consumed = tryInstantHeal(item, d, cap, config.einjuryRate, player) { actual ->
                            data.externalInjury = minOf(data.externalInjury + actual, data.maxExternalInjury)
                            state.totalHealedEI += actual
                        }
                        if (!consumed) {
                            val eiFull = data.externalInjury >= data.maxExternalInjury
                            val capReached = state.totalHealedEI >= config.maxEInjury
                            if (!state.eiCapWarned && (eiFull || capReached)) {
                                player.sendMessage("<yellow>${if (eiFull) "外伤已满" else "已达最大外伤治疗量"}，不再回复外伤。".toComponent())
                                config.soundCapReached.play(player)
                                state.eiCapWarned = true
                            }
                        }
                    }

                    if (consumed) {
                        enterPost(state, PostReason.ITEM_LOST, player)
                        continue
                    }

                    if (isHealDone(player, data, state)) {
                        enterPost(state, PostReason.COMPLETE, player)
                        continue
                    }

                    // Apply cost for instant heals that just fired
                    if (state.totalHealedHP > 0 || state.totalHealedII > 0 || state.totalHealedEI > 0) {
                        if (config.costFood > 0) {
                            data.customHunger = maxOf(data.customHunger - config.costFood, 0.0)
                        }
                        if (config.costSan > 0) {
                            data.sanity = maxOf(data.sanity - config.costSan, 0.0)
                        }
                    }

                    config.soundStart.play(player)
                    player.sendActionBar("<aqua>治疗中... 抬头以退出治疗模式".toComponent())
                }

                HealingPhase.HEALING -> {
                    state.healInterval--
                    if (state.healInterval > 0) continue

                    state.healInterval = maxOf((config.perTime * 4).toInt(), 1)

                    // Check item validity
                    if (!isItemValid(item)) {
                        enterPost(state, PostReason.ITEM_LOST, player)
                        continue
                    }

                    // Check NBT durability
                    var dur = getNbtDurability(item)
                    if (dur <= 0) {
                        item.amount -= 1
                        enterPost(state, PostReason.ITEM_LOST, player)
                        continue
                    }

                    val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                    var itemConsumed = false
                    var healedSomething = false

                    // --- HP heal ---
                    if (!itemConsumed && config.healSpeed > 0 && config.maxHeal != 0.0) {
                        val hpMissing = maxOf(maxHP - player.health, 0.0)
                        val speedCap = minOf(config.healSpeed, hpMissing)
                        val cumulativeCap = if (config.maxHeal == -1.0) speedCap
                            else minOf(speedCap, maxOf(config.maxHeal - state.totalHealedHP, 0.0))
                        if (cumulativeCap > 0 && hpMissing > 0) {
                            val result = tryHealTick(item, dur, cumulativeCap, config.healRate, player) { actual ->
                                player.health = minOf(player.health + actual, maxHP)
                                state.totalHealedHP += actual
                            }
                            dur = result.newDur
                            itemConsumed = result.itemConsumed
                            healedSomething = true
                        }
                        if (!itemConsumed && !state.hpFullWarned) {
                            val hpFull = maxHP - player.health <= 0
                            val capReached = config.maxHeal != -1.0 && state.totalHealedHP >= config.maxHeal
                            if (hpFull || capReached) {
                                player.sendMessage("<yellow>${if (hpFull) "血量已满" else "已达最大治疗量"}，不再回复血量。".toComponent())
                                config.soundCapReached.play(player)
                                state.hpFullWarned = true
                            }
                        }
                    }

                    // --- Internal injury heal ---
                    if (!itemConsumed && config.iinjurySpeed > 0 && config.maxIInjury != 0.0) {
                        val iiMissing = maxOf(data.maxInternalInjury - data.internalInjury, 0.0)
                        val speedCap = minOf(config.iinjurySpeed, iiMissing)
                        val cumulativeCap = minOf(speedCap, maxOf(config.maxIInjury - state.totalHealedII, 0.0))
                        if (cumulativeCap > 0 && iiMissing > 0) {
                            val result = tryHealTick(item, dur, cumulativeCap, config.iinjuryRate, player) { actual ->
                                data.internalInjury = minOf(data.internalInjury + actual, data.maxInternalInjury)
                                state.totalHealedII += actual
                            }
                            dur = result.newDur
                            itemConsumed = result.itemConsumed
                            healedSomething = true
                        }
                        if (!itemConsumed && !state.iiCapWarned) {
                            val iiFull = data.internalInjury >= data.maxInternalInjury
                            val capReached = state.totalHealedII >= config.maxIInjury
                            if (iiFull || capReached) {
                                player.sendMessage("<yellow>${if (iiFull) "内伤已满" else "已达最大内伤治疗量"}，不再回复内伤。".toComponent())
                                config.soundCapReached.play(player)
                                state.iiCapWarned = true
                            }
                        }
                    }

                    // --- External injury heal ---
                    if (!itemConsumed && config.einjurySpeed > 0 && config.maxEInjury != 0.0) {
                        val eiMissing = maxOf(data.maxExternalInjury - data.externalInjury, 0.0)
                        val speedCap = minOf(config.einjurySpeed, eiMissing)
                        val cumulativeCap = minOf(speedCap, maxOf(config.maxEInjury - state.totalHealedEI, 0.0))
                        if (cumulativeCap > 0 && eiMissing > 0) {
                            val result = tryHealTick(item, dur, cumulativeCap, config.einjuryRate, player) { actual ->
                                data.externalInjury = minOf(data.externalInjury + actual, data.maxExternalInjury)
                                state.totalHealedEI += actual
                            }
                            itemConsumed = result.itemConsumed
                            healedSomething = true
                        }
                        if (!itemConsumed && !state.eiCapWarned) {
                            val eiFull = data.externalInjury >= data.maxExternalInjury
                            val capReached = state.totalHealedEI >= config.maxEInjury
                            if (eiFull || capReached) {
                                player.sendMessage("<yellow>${if (eiFull) "外伤已满" else "已达最大外伤治疗量"}，不再回复外伤。".toComponent())
                                config.soundCapReached.play(player)
                                state.eiCapWarned = true
                            }
                        }
                    }

                    // --- Fracture heal (每轮治疗最后部分，消除1层骨折) ---
                    if (!itemConsumed && config.fractureCost > 0) {
                        val fractureEffect = data.statusEffects.firstOrNull { it.type == FRACTURE }
                        if (fractureEffect != null) {
                            val currentDur = getNbtDurability(item)
                            if (currentDur >= config.fractureCost) {
                                val newLevel = fractureEffect.level - 1
                                if (newLevel <= 0) {
                                    data.statusEffects.removeAll { it.type == FRACTURE }
                                } else {
                                    val idx = data.statusEffects.indexOf(fractureEffect)
                                    if (idx >= 0) {
                                        data.statusEffects[idx] = fractureEffect.copy(level = newLevel)
                                    }
                                }
                                val newDur = currentDur - config.fractureCost
                                if (newDur <= 0) {
                                    item.amount -= 1
                                    itemConsumed = true
                                } else {
                                    setNbtDurability(item, newDur, player)
                                }
                                healedSomething = true
                            }
                        }
                    }

                    if (itemConsumed) {
                        enterPost(state, PostReason.ITEM_LOST, player)
                        continue
                    }

                    if (isHealDone(player, data, state)) {
                        enterPost(state, PostReason.COMPLETE, player)
                        continue
                    }

                    if (healedSomething) {
                        config.soundHeal.play(player)
                        if (config.costFood > 0) {
                            data.customHunger = maxOf(data.customHunger - config.costFood, 0.0)
                        }
                        if (config.costSan > 0) {
                            data.sanity = maxOf(data.sanity - config.costSan, 0.0)
                        }
                    }

                    val curDur = getNbtDurability(item)
                    val durStr = "${curDur.toInt()}/${config.maxDurability.toInt()}"
                    player.sendActionBar("<aqua>治疗中... 抬头以退出治疗模式 <gray>耐久: $durStr".toComponent())
                }

                HealingPhase.POST -> {
                    state.phaseTicks--
                    if (state.phaseTicks <= 0) {
                        toRemove.add(uuid)
                        continue
                    }
                    val sec = state.phaseTicks / 4 + 1
                    val msg = when (state.postReason) {
                        PostReason.COMPLETE -> "治疗完成"
                        PostReason.CANCELLED -> "治疗中断"
                        PostReason.ITEM_LOST -> "医疗物品耗尽"
                    }
                    val suffix = if (state.postReason == PostReason.COMPLETE) "低头以结束" else "自动结束中"
                    player.sendActionBar("<yellow>$msg <gray>$suffix <white>${sec}秒".toComponent())
                }
            }
        }

        for (uuid in toRemove) {
            val state = states.remove(uuid) ?: continue
            AttributeManager.removeModifiersByAttribute(uuid, "movement_speed", ModifierSource.HEALING)
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                player.sendActionBar("<green>已退出治疗模式".toComponent())
                state.config.soundEnd.play(player)
            }
        }
    }
}

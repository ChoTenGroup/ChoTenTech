package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.attribute.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.attribute.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.attribute.data.ModifierSource
import io.github.zzzyyylllty.chotentech.attribute.data.ModifierType
import io.github.zzzyyylllty.chotentech.attribute.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.data.HealConfig
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object HealingManager {

    private val states = ConcurrentHashMap<UUID, HealingState>()

    data class HealingState(
        val config: HealConfig,
        var remainingDurability: Double,
        var totalHealedHP: Double,
        var totalHealedII: Double,
        var totalHealedEI: Double,
        var hpFullWarned: Boolean,
        val speedModifierUuid: UUID?  // non-null if slow modifier was applied
    )

    fun isHealing(uuid: UUID): Boolean = states.containsKey(uuid)

    fun getState(uuid: UUID): HealingState? = states[uuid]

    fun enter(player: Player, config: HealConfig) {
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        val uuid = player.uniqueId

        // Apply speed slow modifier
        var speedModUuid: UUID? = null
        if (config.slow > 0) {
            val modifier = AttributeModifier(
                attributeId = "movement_speed",
                value = -(config.slow / 100.0),
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER,
                slot = AttributeSlot.OTHER
            )
            speedModUuid = modifier.uuid
            AttributeManager.addModifier(uuid, modifier)
        }

        val state = HealingState(
            config = config,
            remainingDurability = config.durability,
            totalHealedHP = 0.0,
            totalHealedII = 0.0,
            totalHealedEI = 0.0,
            hpFullWarned = false,
            speedModifierUuid = speedModUuid
        )

        // Handle instant speeds (-1)
        var appliedInstant = applyInstantHeals(player, data, config, state)
        if (appliedInstant) {
            // Check if all done after instant
            if (isAllFull(player, data, config)) {
                exit(player)
                return
            }
        }

        states[uuid] = state
        config.soundStart.play(player)
    }

    fun exit(player: Player) {
        val uuid = player.uniqueId
        val state = states.remove(uuid) ?: return

        state.config.soundEnd.play(player)

        // Remove speed slow modifier
        if (state.speedModifierUuid != null) {
            AttributeManager.removeModifierByUuid(uuid, state.speedModifierUuid)
        }
    }

    /** Returns true if anything was healed instantly */
    private fun applyInstantHeals(
        player: Player,
        data: io.github.zzzyyylllty.chotentech.data.PlayerData,
        config: HealConfig,
        state: HealingState
    ): Boolean {
        var applied = false
        val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

        // Instant HP heal
        if (config.healSpeed == -1.0 && config.maxHeal != 0.0) {
            val missing = maxOf(maxHP - player.health, 0.0)
            val cap = if (config.maxHeal == -1.0) missing else minOf(config.maxHeal, missing)
            if (cap > 0 && state.remainingDurability > 0) {
                val healed = minOf(cap, state.remainingDurability / config.healRate)
                val actual = floor(healed * 100) / 100
                if (actual > 0) {
                    player.health = minOf(player.health + actual, maxHP)
                    state.totalHealedHP += actual
                    state.remainingDurability -= actual * config.healRate
                    state.remainingDurability = floor(state.remainingDurability * 100) / 100
                    applied = true
                }
            }
        }

        // Instant internal injury heal
        if (config.iinjurySpeed == -1.0 && config.maxIInjury != 0.0) {
            val missing = data.effectiveMaxInternalInjury - data.internalInjury
            val cap = minOf(config.maxIInjury, maxOf(missing, 0.0))
            if (cap > 0 && state.remainingDurability > 0) {
                val healed = minOf(cap, state.remainingDurability / config.iinjuryRate)
                val actual = floor(healed * 100) / 100
                if (actual > 0) {
                    data.internalInjury = minOf(data.internalInjury + actual, data.effectiveMaxInternalInjury)
                    state.totalHealedII += actual
                    state.remainingDurability -= actual * config.iinjuryRate
                    state.remainingDurability = floor(state.remainingDurability * 100) / 100
                    applied = true
                }
            }
        }

        // Instant external injury heal
        if (config.einjurySpeed == -1.0 && config.maxEInjury != 0.0) {
            val missing = data.effectiveMaxExternalInjury - data.externalInjury
            val cap = minOf(config.maxEInjury, maxOf(missing, 0.0))
            if (cap > 0 && state.remainingDurability > 0) {
                val healed = minOf(cap, state.remainingDurability / config.einjuryRate)
                val actual = floor(healed * 100) / 100
                if (actual > 0) {
                    data.externalInjury = minOf(data.externalInjury + actual, data.effectiveMaxExternalInjury)
                    state.totalHealedEI += actual
                    state.remainingDurability -= actual * config.einjuryRate
                    state.remainingDurability = floor(state.remainingDurability * 100) / 100
                    applied = true
                }
            }
        }

        return applied
    }

    private fun isAllFull(
        player: Player,
        data: io.github.zzzyyylllty.chotentech.data.PlayerData,
        config: HealConfig
    ): Boolean {
        val hpFull = if (config.maxHeal != 0.0 && config.healSpeed != 0.0) {
            val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health >= maxHP
        } else true

        val iiFull = if (config.maxIInjury != 0.0 && config.iinjurySpeed != 0.0) {
            data.internalInjury >= data.effectiveMaxInternalInjury
        } else true

        val eiFull = if (config.maxEInjury != 0.0 && config.einjurySpeed != 0.0) {
            data.externalInjury >= data.effectiveMaxExternalInjury
        } else true

        return hpFull && iiFull && eiFull
    }

    @Awake(LifeCycle.ACTIVE)
    fun startHealingTick() {
        submit(async = true, period = 20L) {
            tick()
        }
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
            var healedSomething = false

            if (state.remainingDurability <= 0) {
                player.sendMessage("<red>医疗物品耐久已耗尽，治疗中断。".toComponent())
                toRemove.add(uuid)
                continue
            }

            val maxHP = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

            // --- HP heal (per-second speed) ---
            if (config.healSpeed > 0 && config.maxHeal != 0.0) {
                val hpMissing = maxOf(maxHP - player.health, 0.0)
                val speedCap = minOf(config.healSpeed, hpMissing)
                val cumulativeCap = if (config.maxHeal == -1.0) speedCap
                    else minOf(speedCap, maxOf(config.maxHeal - state.totalHealedHP, 0.0))
                if (cumulativeCap > 0 && hpMissing > 0) {
                    val durabilityLimit = state.remainingDurability / config.healRate
                    val toHeal = minOf(cumulativeCap, durabilityLimit)
                    val actual = floor(toHeal * 100) / 100
                    if (actual > 0) {
                        player.health = minOf(player.health + actual, maxHP)
                        state.totalHealedHP += actual
                        state.remainingDurability -= actual * config.healRate
                        state.remainingDurability = floor(state.remainingDurability * 100) / 100
                        healedSomething = true
                    }
                }
                if (hpMissing <= 0 && config.maxHeal != 0.0 && !state.hpFullWarned) {
                    player.sendMessage("<yellow>血量已满，不再回复血量。".toComponent())
                    state.hpFullWarned = true
                }
            }

            // --- Internal injury heal ---
            if (config.iinjurySpeed > 0 && config.maxIInjury != 0.0) {
                val iiMissing = maxOf(data.effectiveMaxInternalInjury - data.internalInjury, 0.0)
                val speedCap = minOf(config.iinjurySpeed, iiMissing)
                val cumulativeCap = minOf(speedCap, maxOf(config.maxIInjury - state.totalHealedII, 0.0))
                if (cumulativeCap > 0 && iiMissing > 0) {
                    val durabilityLimit = state.remainingDurability / config.iinjuryRate
                    val toHeal = minOf(cumulativeCap, durabilityLimit)
                    val actual = floor(toHeal * 100) / 100
                    if (actual > 0) {
                        data.internalInjury = minOf(data.internalInjury + actual, data.effectiveMaxInternalInjury)
                        state.totalHealedII += actual
                        state.remainingDurability -= actual * config.iinjuryRate
                        state.remainingDurability = floor(state.remainingDurability * 100) / 100
                        healedSomething = true
                    }
                }
            }

            // --- External injury heal ---
            if (config.einjurySpeed > 0 && config.maxEInjury != 0.0) {
                val eiMissing = maxOf(data.effectiveMaxExternalInjury - data.externalInjury, 0.0)
                val speedCap = minOf(config.einjurySpeed, eiMissing)
                val cumulativeCap = minOf(speedCap, maxOf(config.maxEInjury - state.totalHealedEI, 0.0))
                if (cumulativeCap > 0 && eiMissing > 0) {
                    val durabilityLimit = state.remainingDurability / config.einjuryRate
                    val toHeal = minOf(cumulativeCap, durabilityLimit)
                    val actual = floor(toHeal * 100) / 100
                    if (actual > 0) {
                        data.externalInjury = minOf(data.externalInjury + actual, data.effectiveMaxExternalInjury)
                        state.totalHealedEI += actual
                        state.remainingDurability -= actual * config.einjuryRate
                        state.remainingDurability = floor(state.remainingDurability * 100) / 100
                        healedSomething = true
                    }
                }
            }

            // Check if all full
            if (isAllFull(player, data, config)) {
                toRemove.add(uuid)
                continue
            }

            // Check durability exhausted after this tick
            if (state.remainingDurability <= 0 && !healedSomething) {
                player.sendMessage("<red>医疗物品耐久已耗尽，治疗中断。".toComponent())
                toRemove.add(uuid)
                continue
            }

            if (healedSomething) {
                config.soundHeal.play(player)
            }

            // ActionBar
            val cur = state.remainingDurability
            val max = config.maxDurability
            val durStr = if (cur == floor(cur)) "${cur.toInt()}/${max.toInt()}" else "${"%.1f".format(cur)}/${max.toInt()}"
            player.sendActionBar("<aqua>治疗中... 抬头以退出治疗模式 <gray>耐久: $durStr".toComponent())
        }

        for (uuid in toRemove) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null) exit(player)
            else states.remove(uuid)
        }
    }
}

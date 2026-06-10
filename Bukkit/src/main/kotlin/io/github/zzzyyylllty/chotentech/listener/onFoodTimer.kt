package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.defExternalInjuryRegen
import io.github.zzzyyylllty.chotentech.data.defInternalInjuryRegen
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import java.util.concurrent.ConcurrentHashMap

private val lastLocations = ConcurrentHashMap<String, Location>()
private val pressureTickCounters = ConcurrentHashMap<String, Int>()

@Awake(LifeCycle.ACTIVE)
fun foodTimer() {
    submit(period = 20) {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue

            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            val uuid = player.uniqueId.toString()

            // === Movement detection ===
            val lastLoc = lastLocations[uuid]
            val currentLoc = player.location
            val moved = lastLoc != null && (currentLoc.x != lastLoc.x || currentLoc.z != lastLoc.z)
            lastLocations[uuid] = currentLoc.clone()

            // === Stamina ===
            if (player.isSprinting) {
                data.stamina = maxOf(data.stamina - 1.0, 0.0)
            }
            if (player.isSneaking) {
                data.stamina = minOf(data.stamina + 1.5, data.maxStamina)
            } else if (moved && player.isOnGround) {
                data.stamina = minOf(data.stamina + 0.7, data.maxStamina)
            } else if (!moved) {
                data.stamina = minOf(data.stamina + 1.0, data.maxStamina)
            }

            // === Injury regen ===
            data.externalInjury = minOf(data.externalInjury + defExternalInjuryRegen, data.maxExternalInjury)
            data.internalInjury = minOf(data.internalInjury + defInternalInjuryRegen, data.maxInternalInjury)

            // === Consumption accumulation ===
            data.consumption += 1.0

            if (player.isSprinting) {
                data.consumption += if (data.stamina < data.maxStamina * 0.3) 30.0 else 10.0
            }

            // === Potion effects ===
            for (effect in player.activePotionEffects) {
                val level = effect.amplifier + 1
                if (effect.type == PotionEffectType.HUNGER) {
                    data.consumption += 100.0 * level
                } else if (effect.type == PotionEffectType.SATURATION) {
                    data.customHunger = minOf(data.customHunger + 1.0 * level, data.maxCustomHunger)
                    data.customSaturation = minOf(data.customSaturation + 1.0 * level, data.maxCustomHunger, data.customHunger)
                }
            }

            // === Consumption threshold check ===
            if (data.consumption >= 100) {
                if (data.customSaturation >= 5) {
                    data.customHunger = maxOf(data.customHunger - 0.01 * data.consumption, 0.0)
                    data.customSaturation = maxOf(data.customSaturation - 0.02 * data.consumption, 0.0)
                } else {
                    data.customHunger = maxOf(data.customHunger - 0.02 * data.consumption, 0.0)
                }
                data.consumption = 0.0
            }

            // === Pressure system (every 5 seconds) ===
            val tickCount = pressureTickCounters.getOrDefault(uuid, 0) + 1
            pressureTickCounters[uuid] = tickCount
            if (tickCount >= 5) {
                pressureTickCounters[uuid] = 0
                if (data.customSaturation > data.maxCustomSaturation * 0.5) {
                    data.stress = maxOf(data.stress - 10.0, 0.0)
                    if (data.customSaturation > data.maxCustomSaturation * 0.8) {
                        data.stress = maxOf(data.stress - 20.0, 0.0)
                        data.sanity = minOf(data.sanity + 1.0, data.effectiveMaxSanity)
                    }
                }
            }

            // === Vanilla food level sync ===
            val stamThreshold = data.maxStamina * 0.15
            val hungerThreshold = data.maxCustomHunger * 0.1
            if (data.customHunger > hungerThreshold && data.stamina >= stamThreshold) {
                player.foodLevel = 16
                player.saturation = 0f
                player.exhaustion = 0f
            } else {
                player.foodLevel = 5
                player.saturation = 0f
                player.exhaustion = 0f
            }
        }
    }
}

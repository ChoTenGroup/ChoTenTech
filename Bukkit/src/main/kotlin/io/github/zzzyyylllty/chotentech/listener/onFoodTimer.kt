package io.github.zzzyyylllty.chotentech.listener

import ink.ptms.um.Mythic
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import java.util.concurrent.ConcurrentHashMap

private val lastLocations = ConcurrentHashMap<String, Location>()
private val pressureTickCounters = ConcurrentHashMap<String, Int>()
private var nutritionDecayCounter = 0

@Awake(LifeCycle.ACTIVE)
fun foodTimer() {
    submit(period = 20) {
        // === Nutrition decay (every 10s) ===
        nutritionDecayCounter++
        if (nutritionDecayCounter >= 10) {
            nutritionDecayCounter = 0
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                val data = playerDataMap[player.uniqueId.toString()] ?: continue
                data.grain = maxOf(data.grain - 0.1, 0.0)
                data.vitamin = maxOf(data.vitamin - 0.1, 0.0)
                data.protein = maxOf(data.protein - 0.1, 0.0)
                data.fat = maxOf(data.fat - 0.1, 0.0)
                io.github.zzzyyylllty.chotentech.data.NutritionManager.sync(player)
            }
        }

        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue

            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            val uuid = player.uniqueId.toString()

            // === Movement detection ===
            val lastLoc = lastLocations[uuid]
            val currentLoc = player.location
            val moved = lastLoc != null && (currentLoc.x != lastLoc.x || currentLoc.z != lastLoc.z)
            lastLocations[uuid] = currentLoc.clone()

            // === Hand Stamina ===
            data.handStamina = minOf(data.handStamina + 1.0, data.maxHandStamina)

            // === Stamina (with nutrition regen multiplier) ===
            val staminaRegenMult = io.github.zzzyyylllty.chotentech.manager.AttributeManager.getAttributeValue(player, "stamina_regen_multiplier")
            if (player.isSprinting) {
                data.stamina = maxOf(data.stamina - 1.0, 0.0)
            } else {
                if (player.isSneaking) {
                    data.stamina = minOf(data.stamina + 2.5 * staminaRegenMult, data.maxStamina)
                } else if (moved && player.isOnGround) {
                    data.stamina = minOf(data.stamina + 1.0 * staminaRegenMult, data.maxStamina)
                } else if (!moved) {
                    data.stamina = minOf(data.stamina + 2.0 * staminaRegenMult, data.maxStamina)
                }
            }
            // === Jump consumption (airborne, not sprinting) ===
            if (!player.isOnGround && !player.isSprinting) {
                data.stamina = maxOf(data.stamina - 0.5, 0.0)
            }

            // === Injury regen (from attribute system) ===
            data.externalInjury = minOf(data.externalInjury + data.externalInjuryRegen, data.maxExternalInjury)
            data.internalInjury = minOf(data.internalInjury + data.internalInjuryRegen, data.maxInternalInjury)

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

@Awake(LifeCycle.ACTIVE)
fun staminaBarSync() {
    submitAsync(period = 5) {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            player.exp = (data.stamina / data.maxStamina).toFloat().coerceIn(0f, 1f)
        }
    }
}

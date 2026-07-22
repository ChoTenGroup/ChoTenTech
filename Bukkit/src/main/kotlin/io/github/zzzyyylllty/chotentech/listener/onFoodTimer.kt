package io.github.zzzyyylllty.chotentech.listener

import ink.ptms.um.Mythic
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap

private val mm = MiniMessage.miniMessage()
private const val NO_STAMINA_MSG = "<red>耐力不足，请稍作休息..."
private const val MARSHMALLOW_DEATH_MSG = "<red>因摄入过量油腻棉花糖而死亡"

private val lastLocations = ConcurrentHashMap<String, Location>()
private val pressureTickCounters = ConcurrentHashMap<String, Int>()
private val lastSprintEndTime = ConcurrentHashMap<String, Long>()
private var nutritionDecayCounter = 0
private var marshmallowDecayCounter = 0
private var chocoDecayCounter = 0

private var foodTimerTask: PlatformExecutor.PlatformTask? = null
private var staminaBarTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun foodTimer() {
    foodTimerTask = submit(period = 20) {
        // === Marshmallow decay (every 60s) ===
        marshmallowDecayCounter++
        if (marshmallowDecayCounter >= 60) {
            marshmallowDecayCounter = 0
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                val data = playerDataMap[player.uniqueId.toString()] ?: continue
                if (data.marshmallowCounter <= 0) continue
                val decay = if (data.marshmallowCounter > 50) 8 else 2
                data.marshmallowCounter = maxOf(data.marshmallowCounter - decay, 0)
            }
        }

        // === Choco decay (every 60s, stops at 2) ===
        chocoDecayCounter++
        if (chocoDecayCounter >= 60) {
            chocoDecayCounter = 0
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                val data = playerDataMap[player.uniqueId.toString()] ?: continue
                if (data.chocoCounter <= 2) continue
                data.chocoCounter -= 1
            }
        }

        // === Nutrition decay (every 10s) ===
        nutritionDecayCounter++
        if (nutritionDecayCounter >= 10) {
            nutritionDecayCounter = 0
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                val data = playerDataMap[player.uniqueId.toString()] ?: continue
                data.grain = maxOf(data.grain - (if (data.grain > 50) 0.2 else 0.1), 0.0)
                data.vitamin = maxOf(data.vitamin - (if (data.vitamin > 50) 0.2 else 0.1), 0.0)
                data.protein = maxOf(data.protein - (if (data.protein > 50) 0.2 else 0.1), 0.0)
                data.fat = maxOf(data.fat - (if (data.fat > 50) 0.2 else 0.1), 0.0)
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
                // 体力耗尽时停止奔跑并提示
                if (data.stamina <= 0.0) {
                    player.isSprinting = false
                    (player as Audience).sendActionBar(mm.deserialize(NO_STAMINA_MSG))
                }
            } else {
                // 奔跑结束后 5 秒（100 tick）才开始恢复
                val sprintEndTime = lastSprintEndTime[uuid]
                if (sprintEndTime != null && System.currentTimeMillis() - sprintEndTime < 5000) {
                    // 还在冷却中，不恢复
                } else if (moved && player.isOnGround) {
                    data.stamina = minOf(data.stamina + 2.0 * staminaRegenMult, data.maxStamina)
                } else if (!moved) {
                    data.stamina = minOf(data.stamina + 4.0 * staminaRegenMult, data.maxStamina)
                }
            }
            // 记录奔跑结束时间
            if (!player.isSprinting && lastSprintEndTime[uuid] == null) {
                lastSprintEndTime[uuid] = System.currentTimeMillis()
            }
            if (player.isSprinting) {
                lastSprintEndTime.remove(uuid)
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

                // === Marshmallow counter effects ===
            if (data.marshmallowCounter > 0) {
                io.github.zzzyyylllty.chotentech.function.MarshmallowManager.updateEffects(player, data.marshmallowCounter)
                if (data.marshmallowCounter >= 121) {
                    player.damage(10000.0)
                    player.sendMessage(MARSHMALLOW_DEATH_MSG)
                }
            } else {
                io.github.zzzyyylllty.chotentech.function.MarshmallowManager.removeModifiers(player)
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
    staminaBarTask = submitAsync(period = 5) {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            player.exp = (data.stamina / data.maxStamina).toFloat().coerceIn(0f, 1f)
        }
    }
}

@SubscribeEvent
fun onPlayerToggleSprint(event: PlayerToggleSprintEvent) {
    if (!event.isSprinting) return
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    val data = playerDataMap[player.uniqueId.toString()] ?: return
    if (data.stamina < data.maxStamina * 0.25) {
        event.isCancelled = true
        (player as Audience).sendActionBar(mm.deserialize(NO_STAMINA_MSG))
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelFoodTimers() {
    foodTimerTask?.cancel()
    staminaBarTask?.cancel()
    lastLocations.clear()
    pressureTickCounters.clear()
    lastSprintEndTime.clear()
}

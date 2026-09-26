package io.github.zzzyyylllty.chotentech.listener

import ink.ptms.um.Mythic
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.isSafeWorld
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap

private val mm = mmUtil
private const val NO_STAMINA_MSG = "<#E08A98>耐力不足，请稍作休息..."
private const val STAMINA_RECOVERED_MSG = "<#8FD4A6>体力已恢复，可以继续奔跑"
private const val MARSHMALLOW_DEATH_MSG = "<#E08A98>因摄入过量油腻棉花糖而死亡"
// 体力低于 SPRINT_LOCK_RATIO 时禁止疾跑,直至体力回到 SPRINT_UNLOCK_RATIO 以上才解锁(迟滞区间,避免在阈值附近反复上锁/解锁)。
// 取消疾跑事件不会向客户端回发同步包,只拦截事件会导致客户端本地保持疾跑而服务端不扣体力(无限疾跑),
// 因此必须配合饥饿值同步(<=6 时原版客户端无法疾跑)从源头禁止
private const val SPRINT_LOCK_RATIO = 0.15
private const val SPRINT_UNLOCK_RATIO = 0.85

private val lastLocations = ConcurrentHashMap<String, Location>()
private val pressureTickCounters = ConcurrentHashMap<String, Int>()
private val lastSprintEndTime = ConcurrentHashMap<String, Long>()

/** 低体力疾跑锁: 存在该 uuid = 体力曾低于 15%,禁止疾跑; 体力回到 85% 以上解除 */
private val sprintLocked = ConcurrentHashMap<String, Boolean>()
private var nutritionDecayCounter = 0
private var marshmallowDecayCounter = 0
private var chocoDecayCounter = 0

private var foodTimerTask: PlatformExecutor.PlatformTask? = null
private var staminaBarTask: PlatformExecutor.PlatformTask? = null

/** 是否处于基地区域(base- 前缀的 WorldGuard 区域):基地内视为完整安全屋 */
private fun isInBaseRegion(player: Player): Boolean =
    playerRegions[player.uniqueId.toString()]?.any { it.startsWith("base-") } == true

@Awake(LifeCycle.ACTIVE)
fun foodTimer() {
    foodTimerTask = submit(period = 20) {
        // === Marshmallow decay (every 60s) ===
        marshmallowDecayCounter++
        if (marshmallowDecayCounter >= 60) {
            marshmallowDecayCounter = 0
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                if (isSafeWorld(player)) continue
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
                if (isSafeWorld(player)) continue
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
                if (isSafeWorld(player) || isInBaseRegion(player)) continue
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

            // === 安全世界 或 基地区域:完整安全屋,不消耗,维持满值 ===
            if (isSafeWorld(player) || isInBaseRegion(player)) {
                data.sanity = data.effectiveMaxSanity
                data.stress = 0.0
                data.stamina = data.maxStamina
                data.handStamina = data.maxHandStamina
                data.customHunger = data.maxCustomHunger
                data.customSaturation = minOf(data.maxCustomSaturation, data.customHunger)
                data.consumption = 0.0
                data.externalInjury = minOf(data.externalInjury + data.externalInjuryRegen, data.maxExternalInjury)
                data.internalInjury = minOf(data.internalInjury + data.internalInjuryRegen, data.maxInternalInjury)
                player.foodLevel = 20
                player.saturation = 5f
                player.exhaustion = 0f
                // 基地/安全世界为无限体力:满体力直接解除疾跑锁
                updateSprintLock(player, data, uuid)
                continue
            }

            // === 体力/手部体力:消耗与恢复 ===
            applyStaminaTick(player, data, uuid)

            // === 低体力疾跑锁:低于 15% 上锁,回到 85% 以上解锁 ===
            updateSprintLock(player, data, uuid)

            // === Injury regen (from attribute system) ===
            data.externalInjury = minOf(data.externalInjury + data.externalInjuryRegen, data.maxExternalInjury)
            data.internalInjury = minOf(data.internalInjury + data.internalInjuryRegen, data.maxInternalInjury)

            // === 自然生命恢复:每秒恢复 health_regen × health_regen_multiplier,每次消耗 0.02 饱食/饥饿(优先扣饱和) ===
            // (原版回复要求食物≥18,而本系统强制饥饿值为16/5,原版回复永不触发)
            val regenMult = io.github.zzzyyylllty.chotentech.manager.AttributeManager.getAttributeValue(player, "health_regen_multiplier")
            if (data.healthRegen > 0 && regenMult > 0 && !io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager.isDowned(player.uniqueId)) {
                val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                if (player.health < maxHp && data.customSaturation + data.customHunger >= 0.02) {
                    val cost = 0.02
                    if (data.customSaturation >= cost) {
                        data.customSaturation -= cost
                    } else {
                        data.customHunger = maxOf(data.customHunger - (cost - data.customSaturation), 0.0)
                        data.customSaturation = 0.0
                    }
                    player.health = minOf(player.health + data.healthRegen * regenMult, maxHp)
                }
            }

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
            // 体力被锁(<15%)或饥饿过低时饥饿值同步为 5:原版客户端饥饿 <=6 时无法疾跑,从源头禁止低体力疾跑
            val hungerThreshold = data.maxCustomHunger * 0.1
            if (data.customHunger > hungerThreshold && !isSprintLocked(uuid)) {
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

/**
 * 体力/手部体力 每 tick 消耗与恢复。
 * 基地区域内体力不消耗(无限体力),故此处仅非基地区域、非安全世界执行。
 */
private fun applyStaminaTick(player: Player, data: PlayerData, uuid: String) {
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
        // 负重体力消耗倍率：0-20kg 不变，之后每 kg +2.5%
        val weightMult = io.github.zzzyyylllty.chotentech.function.WeightManager.getCachedWeight(player.uniqueId)
            ?.let { w -> if (w > 20) 1.0 + (w - 20) * 0.025 else 1.0 } ?: 1.0
        data.stamina = maxOf(data.stamina - 1.0 * weightMult, 0.0)
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
}

/** 该玩家当前是否处于低体力疾跑锁(禁止疾跑)状态 */
private fun isSprintLocked(uuid: String): Boolean = sprintLocked[uuid] == true

/**
 * 低体力疾跑锁:体力低于 15% 时上锁并立即停止正在进行的疾跑;
 * 解锁条件为体力回到 85% 以上(迟滞区间,避免在 15% 附近反复上锁/解锁)。
 */
private fun updateSprintLock(player: Player, data: PlayerData, uuid: String) {
    if (isSprintLocked(uuid)) {
        if (data.stamina >= data.maxStamina * SPRINT_UNLOCK_RATIO) {
            sprintLocked.remove(uuid)
            (player as Audience).sendActionBar(mm.deserialize(STAMINA_RECOVERED_MSG))
        }
        return
    }
    if (data.stamina < data.maxStamina * SPRINT_LOCK_RATIO) {
        sprintLocked[uuid] = true
        // 若服务端仍在疾跑状态则立即复位,避免客户端本地保持疾跑导致无限疾跑
        player.isSprinting = false
        (player as Audience).sendActionBar(mm.deserialize(NO_STAMINA_MSG))
    }
}

@Awake(LifeCycle.ACTIVE)
fun staminaBarSync() {
    staminaBarTask = submit(period = 5) {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            player.exp = (data.stamina / data.maxStamina).toFloat().coerceIn(0f, 1f)
        }
    }
}

/** 安全世界/基地:原版饥饿强制满值,拦截一切饥饿变化(含其他插件来源) */
@SubscribeEvent
fun onFoodLevelChange(event: FoodLevelChangeEvent) {
    if (event.entity !is Player) return
    val player = event.entity as Player
    if (!isSafeWorld(player) && !isInBaseRegion(player)) return
    event.isCancelled = true
    if (player.foodLevel != 20) player.foodLevel = 20
    player.saturation = 5f
    player.exhaustion = 0f
}

@SubscribeEvent
fun onPlayerToggleSprint(event: PlayerToggleSprintEvent) {
    if (!event.isSprinting) return
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    // 安全世界/基地内为无限体力,不受疾跑锁限制
    if (isSafeWorld(player) || isInBaseRegion(player)) return
    val uuid = player.uniqueId.toString()
    val data = playerDataMap[uuid] ?: return
    // 已上锁时拦截;若事件先于定时器发生(体力已低于阈值)则一并拦截并补上锁
    if (isSprintLocked(uuid) || data.stamina < data.maxStamina * SPRINT_LOCK_RATIO) {
        val newlyLocked = !isSprintLocked(uuid)
        sprintLocked[uuid] = true
        event.isCancelled = true
        // 若服务端仍在疾跑状态则立即复位并同步给客户端(数据实体写回,不触发事件)
        player.isSprinting = false
        // 仅在刚上锁时提示,避免客户端反复尝试疾跑导致刷屏
        if (newlyLocked) (player as Audience).sendActionBar(mm.deserialize(NO_STAMINA_MSG))
    }
}

/** 退出时清理疾跑锁与相关缓存,避免重进后沿用上一局的锁状态 */
@SubscribeEvent
fun onQuitClearStaminaState(event: PlayerQuitEvent) {
    val uuid = event.player.uniqueId.toString()
    sprintLocked.remove(uuid)
    lastSprintEndTime.remove(uuid)
    lastLocations.remove(uuid)
}

@Awake(LifeCycle.DISABLE)
fun cancelFoodTimers() {
    foodTimerTask?.cancel()
    staminaBarTask?.cancel()
    lastLocations.clear()
    pressureTickCounters.clear()
    lastSprintEndTime.clear()
    sprintLocked.clear()
}

package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PowerManager
import io.github.zzzyyylllty.chotentech.event.NoclipEnterEvent
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.function.noclip.ConditionResult
import io.github.zzzyyylllty.chotentech.function.noclip.NoclipConditionChecker
import io.github.zzzyyylllty.chotentech.data.NoclipPoint
import io.github.zzzyyylllty.chotentech.function.noclip.RandomNoclipSelector
import io.github.zzzyyylllty.chotentech.listener.checkPlayerIsInNoclipPoint
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.chotentech.kether.evalKetherValue
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/** 切行等级：从 10 级起算的有效等级（1-9 级无加成） */
fun getNoclippingBonusLevels(player: Player): Int {
    val level = try { PlayerDataManager(player).getLevel("noclipping") ?: 0L } catch (_: Exception) { 0L }
    return maxOf(0, level.toInt() - 9)
}

/** 切行等级对成功率的加成（百分点） */
fun getNoclippingSuccessBonus(player: Player): Double = getNoclippingBonusLevels(player) * 0.5

/** 切行等级对耗时倍率的缩减 */
fun getNoclippingTimeMultiplier(player: Player): Double {
    val bonus = getNoclippingBonusLevels(player)
    return maxOf(1.0 - bonus * 0.005, 0.1)
}

private val playerInNoclip = ConcurrentHashMap<String, String>()
private val lastNoclipSuccessTime = ConcurrentHashMap<String, Long>()
private val rapidNoclipCounter = ConcurrentHashMap<String, Int>()
private val lastNoclipSuccessHistory = ConcurrentHashMap<String, MutableList<String>>()
private val consecutiveFailures = ConcurrentHashMap<String, Int>()
private val autoNoclipGuideSent = ConcurrentHashMap<String, String>()

private var noclipTask: PlatformExecutor.PlatformTask? = null
private var autoNoclipTask: PlatformExecutor.PlatformTask? = null
private var sharedNoclipTask: PlatformExecutor.PlatformTask? = null

fun cleanupNoclipState(uuid: String) {
    playerInNoclip.remove(uuid)
}

fun resetNoclipExpState(uuid: String) {
    rapidNoclipCounter.remove(uuid)
    lastNoclipSuccessTime.remove(uuid)
    lastNoclipSuccessHistory.remove(uuid)
    consecutiveFailures.remove(uuid)
}

@Awake(LifeCycle.ACTIVE)
fun noclipTime() {
    noclipTask = submitAsync (period = 20) {

        for (player in Bukkit.getOnlinePlayers()) {

            val k = player.uniqueId.toString()

            val data = playerDataMap[k]

            if (data == null) {
                warning("$k Data is null!")
                continue
            }

            if (data.noclipping) {

                // Cancel if player left the noclip area (all noclips except elevator)
                val np = data.noclipPoint
                if (np != null && !PowerManager.elevatorNoclipPlayers.contains(k)
                    && !checkPlayerIsInNoclipPoint(player, np)) {
                    submit {
                        ChoTenDataHelper(player).exitNoclip()
                        PowerManager.unmarkElevatorNoclip(k)
                    }
                    player.sendActionBar("<#FF6666>切出取消".toComponent())
                    continue
                }

                if ((data.noclipTimeEnd ?: 0) > System.currentTimeMillis()) {
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 1F, 1F)

                } else {

                    val successRate = (data.noclipPoint?.basicSuccessRate ?: 60.0) + getNoclippingSuccessBonus(player)
                    if ((0 until 100).random() <= successRate) {

                        val isNewNoclip = data.noclipPoint?.name?.let { name ->
                            data.activeLevelStr?.let { levelKey ->
                                data.exploreredNoclips[levelKey]?.contains(name) != true
                            }
                        } ?: false

                        data.noclipPoint?.name?.let { data.exploreredNoclips[data.activeLevelStr]?.add(it) ?: data.activeLevelStr?.let { key -> data.exploreredNoclips.put(key, hashSetOf(it)) } }
                        val selectedLocation = data.noclipPoint?.let { selectTargetLocation(player, it) }
                        val selectedTargetLevel = selectTargetLevel(data.noclipPoint?.targetLevel)
                        NoclipEvent(player, data.activeLevel?.name, data.noclipPoint?.name, selectedTargetLevel).call()
                        // 切出成功
                        val npForDropbag = playerDataMap[k]?.noclipPoint
                        submit {
                            val location = selectedLocation
                            if (npForDropbag != null) {
                                NoclipConditionChecker.enforceDropbagOnSuccess(player, npForDropbag)
                            }
                            location?.let { player.teleport(it) }
                            ChoTenDataHelper(player).exitNoclip()
                            PowerManager.unmarkElevatorNoclip(k)
                        }

                        // Exp calculation
                        val levelNoclipKey = "${data.activeLevelStr}::${data.noclipPoint?.name}"
                        val history = lastNoclipSuccessHistory[k] ?: mutableListOf()
                        if (levelNoclipKey in history) {
                            // duplicate noclip in last 3 successes — 0 exp
                        } else {
                            consecutiveFailures.remove(k)
                            var exp = 10.0
                            // 首次切出该切出点时经验 ×3
                            if (isNewNoclip) exp *= 3.0

                            val lastTime = lastNoclipSuccessTime[k]
                            val fiveMinAgo = System.currentTimeMillis() - 300_000
                            if (lastTime != null && lastTime > fiveMinAgo) {
                                rapidNoclipCounter[k] = (rapidNoclipCounter[k] ?: 0) + 1
                            } else {
                                rapidNoclipCounter[k] = 0
                            }
                            lastNoclipSuccessTime[k] = System.currentTimeMillis()
                            exp += ((rapidNoclipCounter[k] ?: 0) * 2).coerceAtMost(20)

                            history.add(levelNoclipKey)
                            if (history.size > 3) history.removeAt(0)
                            lastNoclipSuccessHistory[k] = history

                            PlayerDataManager(player).addExp("noclipping", exp)
                            PlayerDataManager(player).checkUpdateLevel("noclipping")
                        }
                        player.sendActionBar("<gradient:green:yellow>切出成功".toComponent())
                        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1F, 0.5F)
                    } else {
                        // 切出失败
                        val damage = data.noclipPoint?.damage
                        submit {
                            if (damage != null && (data.noclipPoint?.damage ?: 0.0) > 0) {
                                // 运行主线程任务
                                if (!player.isDead) player.damage(damage)
                            }
                        }
                        val failCount = consecutiveFailures.merge(k, 1) { old, _ -> old + 1 } ?: 1
                        if (failCount <= 2) {
                            PlayerDataManager(player).addExp("noclipping", 3.0)
                            PlayerDataManager(player).checkUpdateLevel("noclipping")
                        }
                        player.sendActionBar("<gradient:gold:red>切出失败".toComponent())
                        ChoTenDataHelper(player).exitNoclip()
                        PowerManager.unmarkElevatorNoclip(k)
                    }
                }
            } else {
                val level = data.activeLevel ?: continue
                if ((data.getCooldownLeftLong() ?: 0) > 0L) continue

                val enabledNames = RandomNoclipSelector.getEnabledNoclips(k, level)
                var foundNoclip: NoclipPoint? = null
                for (noclipPoint in level.noclips) {
                    if (noclipPoint.autoNoclip) continue
                    if (noclipPoint.name != null && noclipPoint.name !in enabledNames) continue
                    if (!checkPlayerIsInNoclipPoint(player, noclipPoint)) continue
                    foundNoclip = noclipPoint
                    break
                }

                val noclipName = foundNoclip?.name
                val lastNoclip = playerInNoclip[k]

                if (foundNoclip != null) {
                    val firstEnter = noclipName != null && noclipName != lastNoclip
                    if (firstEnter) {
                        playerInNoclip[k] = noclipName
                        // 进入切出点即触发 NoclipEnterEvent(而不是执行切出时)
                        submit { NoclipEnterEvent(player, level.id, foundNoclip.name).call() }
                    }

                    val condResult = NoclipConditionChecker.checkConditions(player, foundNoclip)
                    if (condResult is ConditionResult.Allowed) {
                        if (firstEnter) {
                            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.7F)
                            player.sendComponent("<#66CCFF>已找到切出点<white>$noclipName</white>，低头下蹲确认。")
                            val target = ChoTenTech.levelsList[foundNoclip.targetLevel]
                            target?.let {
                                player.sendComponent("<#66CCFF>本切出点预计通往：${target.prefix}${target.name}")
                            } ?: {

                                player.sendComponent("<#66CCFF>无法找到本切出点的目标。")
                            }
                        }
                    } else {
                        val msg = when (condResult) {
                            is ConditionResult.Blocked -> condResult.message
                            is ConditionResult.NeedsSubmission -> condResult.message
                            else -> null
                        } ?: "需要满足条件"
                        player.sendActionBar(msg.toComponent())
                        // 首次进入条件切出点:聊天提示如何提交物品满足条件(之后仅显示 actionbar,不刷屏)
                        if (firstEnter) {
                            player.sendComponent(NoclipConditionChecker.buildConditionGuide(foundNoclip))
                        }
                    }
                } else if (lastNoclip != null) {
                    playerInNoclip.remove(k)
                }
            }
        }
    }
}


/** 自动切出检测：每 1 秒检查玩家是否在 autoNoclip 切出点内 */
@Awake(LifeCycle.ACTIVE)
fun autoNoclipTimer() {
    autoNoclipTask = submit(period = 20L) {
        for (player in Bukkit.getOnlinePlayers()) {
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            // 已经在切出中或正在冷却中则跳过
            if (data.noclipping) continue
            if ((data.getCooldownLeftLong() ?: 0) > 0L) continue

            val level = data.activeLevel ?: continue
            val enabledNames = RandomNoclipSelector.getEnabledNoclips(player.uniqueId.toString(), level)
            var inAutoPoint = false
            for (noclipPoint in level.noclips) {
                if (!noclipPoint.autoNoclip) continue
                if (noclipPoint.name != null && noclipPoint.name !in enabledNames) continue
                if (!checkPlayerIsInNoclipPoint(player, noclipPoint)) continue
                inAutoPoint = true

                val condResult = NoclipConditionChecker.checkConditions(player, noclipPoint)
                if (condResult !is ConditionResult.Allowed) {
                    val msg = when (condResult) {
                        is ConditionResult.Blocked -> condResult.message
                        is ConditionResult.NeedsSubmission -> condResult.message
                        else -> null
                    } ?: "需要满足条件"
                    player.sendActionBar(msg.toComponent())
                    // 首次进入条件切出点:聊天提示如何提交物品满足条件
                    val k = player.uniqueId.toString()
                    val npName = noclipPoint.name ?: "unnamed"
                    if (autoNoclipGuideSent[k] != npName) {
                        autoNoclipGuideSent[k] = npName
                        player.sendComponent(NoclipConditionChecker.buildConditionGuide(noclipPoint))
                    }
                    continue
                }

                // 自动开始切出
                data.noclipPoint = noclipPoint
                val timeMult = getNoclippingTimeMultiplier(player)
                data.noclipTimeEnd = System.currentTimeMillis() + (noclipPoint.noclippingTime * timeMult * 1000).roundToLong()
                data.noclipCooldownEnd = System.currentTimeMillis() + (noclipPoint.cooldown * 1000).roundToLong()
                data.noclipping = true
                NoclipEnterEvent(player, level.id, noclipPoint.name).call()
                player.playSound(player, Sound.ENTITY_ENDER_EYE_LAUNCH, 1F, 0.5F)
                player.sendActionBar("<green>保持在切出点内".toComponent())
                break
            }
            if (!inAutoPoint) autoNoclipGuideSent.remove(player.uniqueId.toString())
        }
    }
}

@Awake(LifeCycle.ACTIVE)
fun sharedNoclipUpdateTimer() {
    sharedNoclipTask = submit(period = 40L) {
        for ((sessionKey, session) in ChoTenTech.sharedNoclipSessions) {
            val parts = sessionKey.split("::", limit = 2)
            if (parts.size < 2) continue
            val levelId = parts[0]
            val noclipName = parts[1]

            val level = ChoTenTech.levelsList[levelId] ?: continue
            val noclipPoint = level.noclips.find { it.name == noclipName } ?: continue

            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId.toString()
                if (checkPlayerIsInNoclipPoint(player, noclipPoint)) {
                    session.playersInRange.add(uuid)
                } else {
                    session.playersInRange.remove(uuid)
                    session.paidBy.remove(uuid)
                }
            }

            if (session.playersInRange.isEmpty()) {
                session.unlocked = false
                session.paidBy.clear()
                ChoTenTech.sharedNoclipSessions.remove(sessionKey)
            }
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelNoclipTimers() {
    noclipTask?.cancel()
    autoNoclipTask?.cancel()
    sharedNoclipTask?.cancel()
    playerInNoclip.clear()
    lastNoclipSuccessTime.clear()
    rapidNoclipCounter.clear()
    lastNoclipSuccessHistory.clear()
    consecutiveFailures.clear()
    autoNoclipGuideSent.clear()
}

private fun selectTargetLocation(player: Player, noclipPoint: NoclipPoint): Location? {
    val weighted = noclipPoint.targetLocations
    if (weighted.isNullOrEmpty()) return noclipPoint.targetLocation

    val resolved = weighted.map { w ->
        Pair(w.location, resolveWeight(player, w.weightExpr))
    }
    val totalWeight = resolved.sumOf { it.second }
    if (totalWeight <= 0) return noclipPoint.targetLocation

    var random = Math.random() * totalWeight
    for ((loc, weight) in resolved) {
        random -= weight
        if (random <= 0) return loc
    }
    return resolved.last().first
}

private fun resolveWeight(player: Player, expr: String): Double {
    val num = expr.toDoubleOrNull()
    if (num != null) return num.coerceAtLeast(0.0)
    val result = expr.evalKetherValue(player)
    return (result as? Number)?.toDouble() ?: 0.0
}

private fun selectTargetLevel(targetLevel: String?): String? {
    if (targetLevel == null) return null
    val parts = targetLevel.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size <= 1) return targetLevel
    return parts.random()
}
package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PowerManager
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.function.noclip.ConditionResult
import io.github.zzzyyylllty.chotentech.function.noclip.NoclipConditionChecker
import io.github.zzzyyylllty.chotentech.data.NoclipPoint
import io.github.zzzyyylllty.chotentech.function.noclip.RandomNoclipSelector
import io.github.zzzyyylllty.chotentech.listener.checkPlayerIsInNoclipPoint
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.sertraline.function.kether.evalKetherValue
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import kotlin.math.roundToLong

@Awake(LifeCycle.ACTIVE)
fun noclipTime() {
    submitAsync (period = 20) {

        for (player in Bukkit.getOnlinePlayers()) {

            val k = player.uniqueId.toString()

            val data = playerDataMap[k]

            if (data == null) {
                warning("$k Data is null!")
                continue
            }

            if (data.noclipping) {

                if ((data.noclipTimeEnd ?: 0) > System.currentTimeMillis()) {

//                        sendRefactorTitle(
//                            player,
//                            " ",
//                            "<gradient:green:#006600>在${tl.toInt()}秒后切出 <white>- <gray>${data.noclipPoint?.name}",
//                            0,
//                            10,
//                            0
//                        )
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 1F, 1F)

                } else {

                    if ((0..100).random() <= (data.noclipPoint?.basicSuccessRate ?: 60.0)) {

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
                        player.sendComponent("<gradient:green:yellow>切出成功")
                        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1F, 0.5F)
//                            sendRefactorTitle(player, " ", "<gradient:green:yellow>切出成功", 0, 10, 0)
                    } else {
                        // 切出失败
                        val damage = data.noclipPoint?.damage
                        submit {
                            if (damage != null && (data.noclipPoint?.damage ?: 0.0) > 0) {
                                // 运行主线程任务
                                if (!player.isDead) player.damage(damage)
                            }
                        }
                        player.sendComponent("<gradient:gold:red>切出失败，受到 ${data.noclipPoint?.damage} 伤害")
//                            sendRefactorTitle(player, " ", "<gradient:gold:red>切出失败", 0, 10, 0)
                        ChoTenDataHelper(player).exitNoclip()
                        PowerManager.unmarkElevatorNoclip(k)
                    }
                }
            }
        }
    }
}

/** 自动切出检测：每 1 秒检查玩家是否在 autoNoclip 切出点内 */
@Awake(LifeCycle.ACTIVE)
fun autoNoclipTimer() {
    submitAsync(period = 20L) {
        for (player in Bukkit.getOnlinePlayers()) {
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            // 已经在切出中或正在冷却中则跳过
            if (data.noclipping) continue
            if ((data.getCooldownLeftLong() ?: 0) > 0L) continue

            val level = data.activeLevel ?: continue
            val enabledNames = RandomNoclipSelector.getEnabledNoclips(player.uniqueId.toString(), level)
            for (noclipPoint in level.noclips) {
                if (!noclipPoint.autoNoclip) continue
                if (noclipPoint.name != null && noclipPoint.name !in enabledNames) continue
                if (!checkPlayerIsInNoclipPoint(player, noclipPoint)) continue

                // Check conditions — skip silently if not met (auto-noclip is passive)
                val condResult = NoclipConditionChecker.checkConditions(player, noclipPoint)
                if (condResult !is ConditionResult.Allowed) continue

                // 自动开始切出
                data.noclipPoint = noclipPoint
                data.noclipTimeEnd = System.currentTimeMillis() + (noclipPoint.noclippingTime * 1000).roundToLong()
                data.noclipCooldownEnd = System.currentTimeMillis() + (noclipPoint.cooldown * 1000).roundToLong()
                data.noclipping = true
                player.playSound(player, Sound.ENTITY_ENDER_EYE_LAUNCH, 1F, 0.5F)
                player.sendComponent("<#66CCFF>正在自动切出... 目标: <white>${noclipPoint.name ?: "Unknown"}")
                break
            }
        }
    }
}

@Awake(LifeCycle.ACTIVE)
fun sharedNoclipUpdateTimer() {
    submitAsync(period = 40L) {
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
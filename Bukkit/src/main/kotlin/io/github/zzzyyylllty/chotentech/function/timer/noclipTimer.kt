package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PowerManager
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.listener.checkPlayerIsInNoclipPoint
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.Sound
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

            val tl = data.noclipTimeEnd


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
                        NoclipEvent(player, data.activeLevel?.name, data.noclipPoint?.name, data.noclipPoint?.targetLevel).call()
                        // 切出成功
                        submit {
                            val location = playerDataMap[k]?.noclipPoint?.targetLocation
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
            for (noclipPoint in level.noclips) {
                if (!noclipPoint.autoNoclip) continue
                if (!checkPlayerIsInNoclipPoint(player, noclipPoint)) continue

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
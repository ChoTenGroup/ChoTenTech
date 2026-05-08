package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.Sound
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import kotlin.time.Clock

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
                    }
                }
            }
        }
    }
}
package io.github.zzzyyylllty.chotentech.feature

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.listener.checkPlayerRegion
import io.github.zzzyyylllty.chotentech.util.serialize.toUUID
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private var windowsTask: PlatformExecutor.PlatformTask? = null
private val windowsLevels = listOf<String>("level-3","level-4","level-9","level-881","level-58")

/** 被窗户伤害的玩家UUID集合，用于死亡消息判断 */
val windowDamagedPlayers = ConcurrentHashMap.newKeySet<String>()

val isFirstTimeLookWindow = ConcurrentHashMap.newKeySet<String>()


@SubscribeEvent
fun onPlayerQuitCleanupWindow(event: PlayerQuitEvent) {
    val player = event.player.uniqueId.toString()
    windowDamagedPlayers.remove(player)
    isFirstTimeLookWindow.remove(player)
}

@Awake(LifeCycle.ACTIVE)
fun checkWindows() {
    windowsTask = submit(period = 30) {
        for((key, value) in playerRegions) {
            if (value.any { it in windowsLevels }) {
                val player = Bukkit.getPlayer(key.toUUID()) ?: continue

                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                if (player.isDead) continue
                val location = player.location
                val x = location.x
                val y = location.y
                val z = location.z
                val world = location.world
                var check = false
                blockCheckLoop@ for (dx in -1..1) {
                    for (dy in 0..1) {
                        for (dz in -1..1) {
                            val block = world.getBlockAt((x + dx).roundToInt(), (y + dy).roundToInt(), (z + dz).roundToInt())
                            if (block.type == Material.WHITE_STAINED_GLASS) {
                                check = true
                                break@blockCheckLoop // 退出所有内部循环
                            }
                        }
                    }
                }
                if (check) {
                    windowDamagedPlayers += key
                    submit {
                        ChoTenDataHelper(player).removeSanity(30.0)
                        player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1f, 0.5f)
                        if (isFirstTimeLookWindow.contains(player.uniqueId.toString())) {
                            if (player.health > 2) player.damage(player.health * 0.4)
                            isFirstTimeLookWindow += player.uniqueId.toString()
                        } else {
                            if (player.health > 30) player.damage(player.health * 0.4) else {
                                player.addPotionEffect(PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1000, 12))
                            }
                        }
                    }
                } else {
                    val blockr = player.rayTraceBlocks(4.0)
                    if (blockr?.hitBlock?.type == Material.WHITE_STAINED_GLASS) {
                        windowDamagedPlayers += key
                        submit {
                            ChoTenDataHelper(player).removeSanity(10.0)
                            player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1f, 1f)
                            if (player.health > 5) player.damage(player.health * 0.25)
                        }
                    }
                }
            }
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelWindowsTask() {
    windowsTask?.cancel()
}

package io.github.zzzyyylllty.chotentech.feature

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.util.serialize.toUUID
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import kotlin.math.roundToInt

@Awake(LifeCycle.ACTIVE)
fun checkWindows() {
    submitAsync(period = 40) {
        for((key, value) in playerRegions) {
            if (value.contains("level-4") || value.contains("level-881") || value.contains("level-9")) {
                val player = Bukkit.getPlayer(key.toUUID()) ?: continue
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                val location = player.location
                val x = location.x.roundToInt()
                val y = location.y.roundToInt()
                val z = location.z.roundToInt()
                val world = location.world
                var check = false
                blockCheckLoop@ for (dx in -1..1) {
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            val block = world.getBlockAt(x + dx, y + dy, z + dz)
                            if (block.type == Material.WHITE_STAINED_GLASS) {
                                check = true
                                break@blockCheckLoop // 退出所有内部循环
                            }
                        }
                    }
                }
                if (check) {
                    submit {
                        player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1f, 2f)
                        player.damage(player.health / 2)
                    }
                } else {
                    val blockr = player.rayTraceBlocks(5.0)
                    if (blockr?.hitBlock?.type == Material.WHITE_STAINED_GLASS) submit {
                        player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_PLACE, 1f, 1f)
                        player.damage(player.health / 4)
                    }
                }
            }
        }
    }
}

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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import kotlin.math.roundToInt

@Awake(LifeCycle.ACTIVE)
fun checkWindows() {
    submitAsync(period = 20) {
        for((key, value) in playerRegions) {
            if (value.contains("level-4") || value.contains("level-881") || value.contains("level-9")) {
                val player = Bukkit.getPlayer(key.toUUID()) ?: continue
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                if (player.isDead) continue
                val location = player.location
                val x = location.x.roundToInt()
                val y = location.y.roundToInt()
                val z = location.z.roundToInt()
                val world = location.world
                var check = false
                blockCheckLoop@ for (dx in -1..1) {
                    for (dy in 0..1) {
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
                        ChoTenDataHelper(player).removeSanity(10.0)
                        player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1f, 0.5f)
                        player.addPotionEffect(PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1000, 12))
                    }
                } else {
                    val blockr = player.rayTraceBlocks(7.0)
                    if (blockr?.hitBlock?.type == Material.WHITE_STAINED_GLASS) submit {
                        ChoTenDataHelper(player).removeSanity(10.0)
                        player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1f, 1f)
                        player.damage(player.health / 2)
                    }
                }
            }
        }
    }
}

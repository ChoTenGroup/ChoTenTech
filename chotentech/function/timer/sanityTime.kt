package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import taboolib.common.LifeCycle
import taboolib.common.LifeCycle.ACTIVE
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

@Awake(LifeCycle.ACTIVE)
fun timeEffects() {
    submitAsync(period = 40) {
        for (k in ChoTenTech.dataMap.keys) {
            val player = Bukkit.getPlayer(UUID.fromString(k))
            val data = ChoTenTech.dataMap[k]
            if (player == null || data == null) continue
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR || player.health <= 0 || player.isDead || !player.world.name.equals("Chapter1",ignoreCase = true) || player.isDead) continue
                // 亮度
                val l: Location = player.location


                val blockbellowplayer = Location(l.world, l.x, l.y, l.z)
                val lightlevel: Int = blockbellowplayer.block.lightLevel.toInt()
                var stressTotal = 0.0
                var sanityTotal = 0.0

                var stressMuitiply = 1.0

                if (player.foodLevel <= 10.0) stressTotal += (15 - player.foodLevel) / 100

                if (data.stress >= 80.0) {
                    sanityTotal - 0.4
                } else if (data.stress >= 60.0) {
                    sanityTotal -= 0.1
                }

                if (data.sanity < 40.0) {
                    stressMuitiply += 1.0
                    sanityTotal += (40 - data.sanity) * 0.005
                } else if (data.sanity >= 80.0) {
                    stressMuitiply -= 0.5
                }

                if (data.darkness >= 20.0) {
                    sanityTotal -= ((floor(data.darkness / 20.0)) / ((data.sanity + 10) / 10))
                }

                if (data.stress >= 20.0) {
                    sanityTotal -= ((floor(data.stress / 20.0)) / ((data.sanity + 10) / 10)) / 10
                }


                stressTotal += lightAffect(lightlevel)


                data.sanity.let {
                    if (it >= 80.0) {
                        dataMap[k]?.darkness = data.darkness - 1.0
                    } else if (it <= 20.0) {
                        dataMap[k]?.darkness = data.darkness + ((20-it)/10)
                    }
                }
                data.stress = data.stress + (stressTotal * stressMuitiply)
                data.sanity = data.sanity + (sanityTotal)
                data.stress = antiOverflow(data.stress, 100.0)
                data.sanity = antiOverflow(data.sanity, data.maxSanity)
                data.darkness = antiOverflow(data.darkness, data.maxDarkness)

                ChoTenTech.dataMap[k] = data
            }
    }
}


fun lightAffect(light: Int): Double {
    if (light in 6..9) return 0.0
    if (light == 0) return 0.5
    if (light >= 14) return -0.5
    return ((7 - light) / 20.0)
}

fun antiOverflow(value: Double, max: Double): Double {
    if (value > max) return max
    if (value < 0.0) return 0.0
    return value
}

fun antiOverflow(value: Int, max: Int): Int {
    if (value > max) return max
    if (value < 0) return 0
    return value
}

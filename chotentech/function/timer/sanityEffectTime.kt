package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.function.sender.sendComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.LifeCycle.ACTIVE
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.util.random
import taboolib.platform.util.kill
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

@Awake(LifeCycle.ACTIVE)
fun sanityEffectTime() {
    submitAsync(period = 200) {
        for (k in ChoTenTech.dataMap.keys) {
            val player = Bukkit.getPlayer(UUID.fromString(k)) ?: continue
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR || player.health <= 0 || player.isDead || !player.world.name.equals("Chapter1",ignoreCase = true) || player.isDead) continue
            val data = ChoTenTech.dataMap[k] ?: continue
            val list = config.getStringList("darkness").toMutableList()
            list.add("你听到了一声奇怪的动静")

            (data.darkness/data.maxDarkness*100).let {

                if (it >= 100.0)
                    submit {
                        player.addPotionEffect(PotionEffect(PotionEffectType.INSTANT_DAMAGE, 200, 10))
                    }

                if (it >= 80.0) {
                    submit {
                        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 250, 2))
                        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 250, 2))
                    }
                    player.sendComponent("<dark_red>${list.random()}")
                } else if (it >= 60.0) {
                    submit {
                        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 250, 1))
                        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 250, 0))
                    }
                    if (random(1, 10) >= 7) {
                        player.sendComponent("<dark_red>${list.random()}")
                    }
                } else if (it >= 40.0) {
                    submit {
                        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 250, 0))
                        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 250, 0))
                    }
                    if (random(1, 10) >= 4) {
                        player.sendComponent("<dark_red>${list.random()}")
                    }
                } else if (it <= 10.0) {
                    submit {
                        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 250, 0))
                        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 250, 0))
                    }

                }
            }
        }
    }
}


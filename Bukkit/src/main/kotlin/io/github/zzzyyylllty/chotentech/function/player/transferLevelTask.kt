package io.github.zzzyyylllty.chotentech.function.player

import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import java.time.Duration


fun transferLevelTask(
    player: Player,
    level: Level,
) {

    submit{
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 10, 10))
    }

    submitAsync {
        val (world, region, cluster, number, name, sdclass, author, tip, noclips) = level
        var title = "NOT_SET"
        var looptime = 0

        val displaysdclass = sdclass.prefix + sdclass.classname
        val preparetitle: String = if (number == null) "Enigmatic Level" else "Level $number"
        val length = preparetitle.length

        HUDManager(player).removeHUD("levelname")
        for (time in 1..length) {
            title = preparetitle.take(time)
            val hud = "<gray>$title<dark_gray>_"
            HUDManager(player).addHUD("levelname", hud, 0.25)
        }

        submitAsync(period = 5, delay = 0) {
            if (looptime >= length) {
                cancel()
            } else {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 5.0f, 1.8f)
                title = preparetitle.take(looptime + 1)
                // title键入效果
                player.showTitle(Title.title("<gray><b>$title<dark_gray>_".toComponent(), "".toComponent(), Title.Times.times(
                    Duration.ofMillis(0),
                    Duration.ofMillis(5000),
                    Duration.ofMillis(0)
                )))
                looptime++

            }
        }

        submitAsync(delay = 5L * (length + 4)) {

            player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 5.0f, 1.3f)

            player.showTitle(Title.title("<white><b>$preparetitle".toComponent(), "<i>${level.prefix + level.name}".toComponent(), Title.Times.times(
                Duration.ofMillis(1000),
                Duration.ofMillis(5000),
                Duration.ofMillis(2000)
            )))

        }
    }
}
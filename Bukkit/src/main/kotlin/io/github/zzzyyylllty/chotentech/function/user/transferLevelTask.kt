package io.github.zzzyyylllty.chotentech.function.user

import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.function.timer.sendRefactorMessages
import io.github.zzzyyylllty.chotentech.function.timer.sendRefactorTitle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync


fun transferLevelTask(
    player: Player,
    level: Level,
) {

    submitAsync {
        val (region, cluster, number, name, sdclass, author, tip, noclips) = level
        var title = "NOT_SET"

        val displaysdclass = sdclass.prefix + sdclass.classname
        var looptime = 0
        val preparetitle: String = "Level $number"

        submitAsync(period = 5, delay = 0) {
            if (looptime >= preparetitle.length) {
                cancel()
            } else {
                title = preparetitle.substring(0, looptime + 1)
                // title键入效果
                sendRefactorTitle(player, "<gray><b>$title<dark_gray>_", "", 0, 20, 0)
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 5.0f, 1.8f)
                looptime++
            }
        }

        submitAsync(delay = 5L * (preparetitle.length + 2)) {

            // 播放完整title
            player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 5.0f, 1.3f)
            sendRefactorTitle(
                player,
                "<white><b>$title",
                "$displaysdclass $name",
                    1,
                    6,
                    2
                )
            }
}
}
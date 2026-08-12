package io.github.zzzyyylllty.chotentech.function.player

import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.function.submit
import java.time.Duration


fun transferLevelTask(
    player: Player,
    level: Level,
) {

    submit{
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 10, 10))
    }

    submit {
        val (world, region, cluster, number, name, sdclass, author, tip, noclips) = level
        var title = "NOT_SET"
        var looptime = 0

        val displaysdclass = sdclass.prefix + sdclass.classname
        val preparetitle: String = if (number == null) "Enigmatic Level" else "Level $number"
        val length = preparetitle.length

        val titleMode = ProfileManager.getSharedData(player.uniqueId.toString()).settings.get("levelTitleMode")

        HUDManager(player).removeHUD("levelname")

        if (titleMode == "on") {
            for (time in 1..length) {
                title = preparetitle.take(time)
                val hud = "<gray>$title<dark_gray>_"
                HUDManager(player).addHUD("levelname", hud, 0.25)
            }

            submit(period = 5, delay = 0) {
                if (looptime >= length) {
                    cancel()
                } else {
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 5.0f, 1.8f)
                    title = preparetitle.take(looptime + 1)
                    player.showTitle(Title.title("<gray><b>$title<dark_gray>_".toComponent(), "".toComponent(), Title.Times.times(
                        Duration.ofMillis(0),
                        Duration.ofMillis(5000),
                        Duration.ofMillis(0)
                    )))
                    looptime++
                }
            }
        }

        if (titleMode != "off") {
            val titleDelay = if (titleMode == "on") 5L * (length + 4) else 0L
            submit(delay = titleDelay) {
                player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 5.0f, 1.3f)

                player.showTitle(Title.title("<white><b>$preparetitle".toComponent(), "<i>${level.prefix + level.name}".toComponent(), Title.Times.times(
                    Duration.ofMillis(1000),
                    Duration.ofMillis(5000),
                    Duration.ofMillis(2000)
                )))
            }
        }

        submit(delay = 5L * (length + 12)) {
            val setting = ProfileManager.getSharedData(player.uniqueId.toString()).settings.get("levelChatMessage")
            if (setting == "true") {
                val levelTag = if (number != null) "<gradient:#FFD700:#FFA500>Level $number</gradient>" else "<dark_aqua>Enigmatic Level</dark_aqua>"
                val msg = buildString {
                    appendLine("<gray>━━━━━━━━━━━━━━━━━━</gray>")
                    append("<bold>$levelTag</bold> <gray>|</gray> <#CCCCFF>${level.prefix}${level.name}</#CCCCFF>")
                    if (cluster.isNotEmpty()) appendLine().append("<gray>层群 $cluster</gray>")
                    appendLine()
                    appendLine("<gray>✎ $author</gray>")
                    append("<gray>━━━━━━━━━━━━━━━━━━</gray>")
                }
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg))
            }
        }
    }
}
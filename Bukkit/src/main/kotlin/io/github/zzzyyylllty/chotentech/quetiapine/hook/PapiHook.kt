package io.github.zzzyyylllty.chotentech.quetiapine.hook
import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.quetiapine.data.QuetiapineDataBridge
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.quetiapine.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.quetiapine.util.minimessage.mmLegacyAmpersandUtil
import io.github.zzzyyylllty.chotentech.quetiapine.util.minimessage.mmLegacySectionUtil
import io.github.zzzyyylllty.chotentech.quetiapine.util.toComponent
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import kotlin.collections.random
import kotlin.let
import kotlin.math.round
import kotlin.text.drop
import kotlin.text.lowercase
import kotlin.text.replace
import kotlin.text.split
import kotlin.text.take
import kotlin.toString

val vaildOptions = listOf("ampersand", "adventure", "section")

object ExamplePapiHook : PlaceholderExpansion {

    override val identifier: String = "quetiapine"

    // 处理在线玩家请求
    override fun onPlaceholderRequest(player: Player?, args: String): String {

        val split = args.split(":")

        var option: String? = null

        if (vaildOptions.contains(split.last())) {
            option = split.last()
        }

        val parsed = if (option != null) args.removeSuffix(":$option") else args

        val str = placeholder(player, parsed)

        return when (option) {
            null -> str
            "adventure" -> str.legacyToMiniMessage()
            "ampersand" -> mmLegacyAmpersandUtil.serialize(str.replace("§", "&").toComponent())
            "section" -> mmLegacySectionUtil.serialize(str.replace("§", "&").toComponent())
            else -> str
        }
    }

    // 处理离线玩家请求
    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return onPlaceholderRequest(player?.player, args)
    }

}

fun placeholder(player: Player?, args: String): String {

    val parsed = args.removeSurrounding("{", "}")
    var list = parsed.split("_")
    val pm = player?.let { PlayerDataManager(it) }

    when (list[0].lowercase()) {
        "level" -> {
            val levelName = list.getOrNull(1) ?: return "Level not found"
            val section = list.getOrNull(2) ?: return pm?.getLevel(levelName).toString()
            when (section.lowercase()) {
                "name" -> {
                    val display = list.getOrNull(3) ?: "default"
                    return levels[levelName]?.displayNames[display].toString()
                }
                "display" -> {
                    val display = list.getOrNull(3) ?: "default"
                    return pm?.getLevel(levelName)?.let { levels[levelName]?.level?.getCurrentLevelDisplay(it, display) }.toString()
                }
                "raw" -> {
                    val display = list.getOrNull(3) ?: "default"
                    return pm?.getLevel(levelName).toString()
                }
                "min" -> {
                    return levels[levelName]?.level?.min.toString()
                }
                "max" -> {
                    return levels[levelName]?.level?.getMaxLevel(player).toString()
                }
                "rawmax" -> {
                    return levels[levelName]?.level?.max.toString()
                }
                "start" -> {
                    return levels[levelName]?.level?.start.toString()
                }
                "exp" -> {
                    return pm?.getExp(levelName).toString()
                }
                "require" -> {
                    val level = list.getOrNull(3) ?: return pm?.getNextLevelExp(levelName).toString()
                    return levels[levelName]?.level?.getRequiredExp(player, level.toLong()).toString()
                }
                "next" -> {
                    return pm?.getNextLevelExp(levelName).toString()
                }
                "percent" -> {
                    val next = pm?.getNextLevelExp(levelName) ?: return "0"
                    return ((pm?.getExp(levelName) ?: 0.0) / next * 100).toString()
                }
            }
        }
    }
    return "Error"
}

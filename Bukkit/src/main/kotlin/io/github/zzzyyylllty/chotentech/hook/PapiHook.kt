package io.github.zzzyyylllty.chotentech.hook
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerHudMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.replacePlaceholderSafety
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import taboolib.platform.compat.replacePlaceholder
import kotlin.math.round

object ExamplePapiHook : PlaceholderExpansion {

    override val identifier: String = "chotentech"

    // 处理在线玩家请求
    override fun onPlaceholderRequest(player: Player?, args: String): String {

        var list = args.split("_")

        when (list[0].lowercase()) {
            "idexists" -> return PlayerDataManager.isIDExist(list[1]).toString()
            "playername" -> return PlayerDataManager.getDataByCID(list[1])?.playerName.toString()
            "uuidbycid" -> return PlayerDataManager.getDataByCID(list[1])?.uuid ?: "null"
            "uuidbyname" -> return PlayerDataManager.getDataByName(list[1])?.uuid ?: "null"
            "cid" -> return if (list.size == 1) player?.player?.getData()?.cid?.id.toString()
            else requestDataByName(list[1])?.cid?.id.toString()
            "randomname" -> {
                val first = config.getStringList("names.first").random()
                val last = config.getStringList("names.last").random()
                return "$first $last"
            }
            "sanity" -> return (round((player?.player?.getData()?.sanity ?: 0.0)*100)/100).toString()
            "maxsanity" -> return (round((player?.player?.getData()?.maxSanity ?: 0.0)*100)/100).toString()
            "darkness" -> return (round((player?.player?.getData()?.darkness ?: 0.0)*100)/100).toString()
            "maxdarkness" -> return (round((player?.player?.getData()?.maxDarkness ?: 0.0)*100)/100).toString()
            "level" -> {
                if (player == null) return "Player Not Found"
                val data = player.getData() ?: return "..."
                val level = data.activeLevel
                return (if (list.size == 1) {
                    level?.number
                }
                else {
                    when (list[1]) {
                        "major" -> {
                            if (data.noclipping) return "<#66CCFF>切出倒计时 ${data.getTimeLeftLong() ?: "..."}"
                            if (level == null) return "???"
                            val number = level.number
                            return HUDManager(player).getHUDContent("levelname",if (number == null) "Enigmatic" else "Level $number")
                        }
                        "sub" -> if (data.noclipping) "<#66DD25>切出点: ${data.noclipPoint?.name ?: "..."}" else level?.name ?: "???"
                        "fullraw" -> {
                            if (level == null) return "???"
                            val number = level.number
                            if (number == null) "Enigmatic" else "Level $number"
                        }
                        "number" -> if (level == null) "???" else level.number ?: "Enigmatic"
                        "name" -> level?.name ?: "???"
                        "sdclass" -> level?.survivalDifficulty?.classname ?: "N/A"
                        "one" -> level?.survivalDifficulty?.one ?: "N/A"
                        "two" -> level?.survivalDifficulty?.two ?: "N/A"
                        "three" -> level?.survivalDifficulty?.three ?: "N/A"
                        "sdprefix" -> level?.survivalDifficulty?.prefix ?: ""
                        "prefix" -> level?.prefix ?: ""
                        "tip" -> level?.tip ?: ""
                        "tip1" -> level?.tip?.take(18) ?: ""
                        "tip2" -> level?.tip?.drop(18)?.take(18) ?: ""
                        "tip3" -> level?.tip?.drop(36)?.take(18) ?: ""
                        else -> "Unknown"
                    }
                }).toString()
            }
            "hud" -> {
                val split = list[1].split("?:")
                val first = split[0]
                val last = if (split.size >= 2) split[1] else "null"
                return player?.let { HUDManager(it) }?.getHUDContent(first, last.replace("@","%").replacePlaceholderSafety(player)) ?: last.replace("@","%").replacePlaceholderSafety(player)
            }
        }
        return "Error"
    }

    // 处理离线玩家请求
    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return onPlaceholderRequest(player?.player, args)
    }

    fun requestDataByUUID(uuid: String): PlayerData? {
        return PlayerDataManager.getData(uuid)
    }

    fun requestDataByName(name: String): PlayerData? {
        return PlayerDataManager.getDataByName(name)
    }

    fun requestDataByCID(cid: String): PlayerData? {
        return PlayerDataManager.getDataByCID(cid)
    }
}

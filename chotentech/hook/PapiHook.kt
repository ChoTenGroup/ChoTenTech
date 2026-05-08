package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.user.getData
import io.github.zzzyyylllty.chotentech.function.user.getOfflineDataById
import io.github.zzzyyylllty.chotentech.function.user.getOfflineDataByName
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import kotlin.math.round

object PapiHook : PlaceholderExpansion {

    // 变量前缀
    override val identifier: String = "chotentech"

    // 变量操作
    /**
     * %chotentech_idexists_1000000%
     * %chotentech_playeruuid_1000000%
     * %chotentech_playername_1000000%
     * %chotentech_uid_ChoTenChan%
     * %chotentech_uid%
     *
     * */
    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {

        var list = args.split("_")

        when (list[0].toLowerCase()) {
            "idexists" -> return SQLDataBase().getIDExists(list[1]).toString()
            "playername" -> return SQLDataBase().getUserInDatabaseByUserID(list[1])?.playerName.toString()
            "playeruuid" -> return list[1].getOfflineDataById()?.userId ?: "null"
            "uid" -> return if (list.size == 1) player?.player?.getData()?.userId ?: "null"
                else list[1].getOfflineDataByName()?.userId ?: "null"
            "randomname" -> {
                val first = config.getStringList("names.first").random()
                val last = config.getStringList("names.last").random()
                return "$first $last"
            }
            "stress" -> return (round((player?.player?.getData()?.stress ?: 0.0)*100)/100).toString()
            "sanity" -> return (round((player?.player?.getData()?.sanity ?: 0.0)*100)/100).toString()
            "maxsanity" -> return (round((player?.player?.getData()?.maxSanity ?: 0.0)*100)/100).toString()
            "darkness" -> return (round((player?.player?.getData()?.darkness ?: 0.0)*100)/100).toString()
            "maxdarkness" -> return (round((player?.player?.getData()?.maxDarkness ?: 0.0)*100)/100).toString()
        }
        return "Error"
    }
}

fun generateLevelIcon(level: Int) {
    val supercrown = level % 1024
    val crown = level % 256
    val sun = level % 64
    val moon = level % 16
    val star = level % 4
    var returns = ""
    repeat (supercrown) { returns = ("$returns♛") }
    repeat (crown) { returns = ("$returns👑") }
    repeat (sun) { returns = ("$returns☀") }
    repeat (moon) { returns = ("$returns🌙") }
    repeat (star) { returns = ("$returns⭐") }
}
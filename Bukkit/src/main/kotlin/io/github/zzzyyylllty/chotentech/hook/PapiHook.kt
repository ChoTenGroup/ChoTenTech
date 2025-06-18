package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.user.getData
import io.github.zzzyyylllty.chotentech.function.user.getOfflineDataById
import io.github.zzzyyylllty.chotentech.function.user.getOfflineDataByName
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion

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
    override fun onPlaceholderRequest(player: Player?, args: String): String {

        var list = args.split("_")

        when (list[0]) {
            "idexists" -> return SQLDataBase().getIDExists(list[1]).toString()
            "playername" -> return SQLDataBase().getUserInDatabaseByUserID(list[1])?.playerName.toString()
            "playeruuid" -> return list[1].getOfflineDataById()?.userId ?: "null"
            "uid" -> return if (list.size == 1) player?.getData()?.userId ?: "null"
                else list[1].getOfflineDataByName()?.userId ?: "null"
        }

        return "Error"
    }
}
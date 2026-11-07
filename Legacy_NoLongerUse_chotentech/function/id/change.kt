package io.github.zzzyyylllty.chotentech.function.id

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.user.getData
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.util.random
import kotlin.math.floor
import kotlin.math.pow

fun changeUserID(player: Player, changeId: String) {
    val data = player.getData()
    if (data != null) {
        if (SQLDataBase().getIDExists(changeId)) throw IllegalArgumentException("userId must be unique")
        val modifiedData = data.copy(userId = changeId)
        dataMap[player.uniqueId.toString()] = modifiedData
        info("data: $data ||| modified: $modifiedData")
        SQLDataBase().saveInDatabase(player)
    } else throw NullPointerException()
}

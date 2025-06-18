package io.github.zzzyyylllty.chotentech.function.id

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.util.random
import kotlin.math.floor
import kotlin.math.pow

fun changeUserID(player: Player, changeId: String) {
    val modifiedData = dataMap[player.uniqueId.toString()]?.copy(
        userId = changeId
    )
    if (modifiedData != null) dataMap[player.uniqueId.toString()] = modifiedData
    SQLDataBase().saveInDatabase(player)
}

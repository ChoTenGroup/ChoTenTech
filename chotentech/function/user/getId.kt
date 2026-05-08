package io.github.zzzyyylllty.chotentech.function.user

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.timer.sendRefactorMessages
import io.github.zzzyyylllty.chotentech.function.timer.sendRefactorTitle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import java.util.UUID


fun Player.getData(): PlayerData? {
    val player = this
    var user = dataMap[player.uniqueId.toString()]
    if (user == null) {
        SQLDataBase().getUserInDatabase(player)?.let { dataMap[player.uniqueId.toString()] = it }
        return SQLDataBase().getUserInDatabase(player)
    }
    return user
}

fun UUID.getOfflineDataByUUID(): PlayerData? {
    var user = dataMap[this.toString()]
    if (user == null) {
        SQLDataBase().getUserInDatabasebyUUID(this.toString())?.let { dataMap[this.toString()] = it }
        return SQLDataBase().getUserInDatabasebyUUID(this.toString())
    }
    return user
}
fun String.getOfflineDataByName(): PlayerData? {
    return SQLDataBase().getUserInDatabase(this)
}
fun String.getOfflineDataById(): PlayerData? {
    return SQLDataBase().getUserInDatabaseByUserID(this)
}
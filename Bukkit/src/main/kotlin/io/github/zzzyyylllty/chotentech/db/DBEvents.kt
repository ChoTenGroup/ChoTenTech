package io.github.zzzyyylllty.chotentech.db

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.function.id.generateUserID
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import kotlin.toString

@SubscribeEvent(EventPriority.LOWEST)
fun playerLoadDBEvent(e: PlayerJoinEvent) {
    dataMap[e.player.uniqueId.toString()] = SQLDataBase().getUserInDatabase(e.player) ?: emptyData(e.player.name, e.player.uniqueId.toString())
}

@SubscribeEvent(EventPriority.LOWEST)
fun playerSaveDBEvent(e: PlayerQuitEvent) {
    SQLDataBase().saveInDatabase(e.player)
}

fun wipeData(player: Player){
    dataMap[player.uniqueId.toString()] = emptyData(player.name, player.uniqueId.toString())
}

fun emptyData(name: String, uuid: String): PlayerData {
    return PlayerData(
        playerName = name,
        playerUUID = uuid,
        userId = generateUserID(),
        noclipCooldown = 0.0,
        noclipPoint = null,
        noclipTimeLeft = 0.0,
        noclipping = false,
    )
}

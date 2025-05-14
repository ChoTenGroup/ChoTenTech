package io.github.zzzyyylllty.chotentech.db

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent(EventPriority.LOWEST)
fun playerLoadDBEvent(e: PlayerJoinEvent) {
    dataMap[e.player.uniqueId] = SQLDataBase().getUserInDatabase(e.player) ?: PlayerData(
        playerName = e.player.name,
        playerUUID = e.player.uniqueId.toString(),
        userId = "1000000",
    )
}

@SubscribeEvent(EventPriority.LOWEST)
fun playerSaveDBEvent(e: PlayerQuitEvent) {
    SQLDataBase().saveInDatabase(e.player)
}



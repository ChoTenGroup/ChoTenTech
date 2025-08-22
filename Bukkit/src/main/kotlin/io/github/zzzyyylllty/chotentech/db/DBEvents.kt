package io.github.zzzyyylllty.chotentech.db

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.function.id.generateUserID
import io.github.zzzyyylllty.chotentech.function.sender.sendComponent
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import kotlin.toString

@SubscribeEvent()
fun playerLoadDBEvent(e: PlayerJoinEvent) {
    submitAsync{
            info("loading data for player ${e.player.name}")
            val data = SQLDataBase().getUserInDatabasebyUUID(e.player.uniqueId.toString())
            if (data != null) dataMap[e.player.uniqueId.toString()] = data else {
                wipeData(player = e.player)
            }
            SQLDataBase().saveInDatabase(e.player)
        }
}

@SubscribeEvent()
fun playerSaveDBEvent(e: PlayerQuitEvent) {
    submitAsync {
        SQLDataBase().saveInDatabase(e.player)
    }
}

fun wipeData(player: Player){
    info("wiping data for player ${player.name}")
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

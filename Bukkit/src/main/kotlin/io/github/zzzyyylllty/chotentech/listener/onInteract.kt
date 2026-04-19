package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.event.SubscribeEvent
import kotlin.collections.get

@SubscribeEvent
fun onPlayerInteractWithDisabledBlocks(event: PlayerInteractEvent) {

    val player: Player = event.player
    val block = event.clickedBlock?.type?.name ?: return

    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    if (block.contains("CANDLE") || block.contains("POT") || block.contains("COPPER") || block.contains("SIGN") || block == "CHISELED_BOOKSHELF" || block == "LODESTONE") {
        event.isCancelled = true
    }

    if (player.inventory.itemInMainHand.type.name.contains("BUCKET")) {
        event.isCancelled = true
    }
}


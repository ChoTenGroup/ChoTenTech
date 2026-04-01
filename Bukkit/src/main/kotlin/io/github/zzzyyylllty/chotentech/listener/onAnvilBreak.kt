package io.github.zzzyyylllty.chotentech.listener

import com.destroystokyo.paper.event.block.AnvilDamagedEvent
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import taboolib.common.platform.event.SubscribeEvent
import kotlin.collections.get

@SubscribeEvent
fun onAnvilBreak(event: AnvilDamagedEvent) {

    event.isCancelled = true

}

@SubscribeEvent
fun onTakeLecternBook(event: PlayerTakeLecternBookEvent) {

    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    event.isCancelled = true

}

@SubscribeEvent
fun onFlowerPotManipulate(event: PlayerFlowerPotManipulateEvent) {

    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    event.isCancelled = true

}
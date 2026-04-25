package io.github.zzzyyylllty.chotentech.listener

//import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
//import io.github.zzzyyylllty.sertraline.Sertraline
//import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
//import org.bukkit.Bukkit
//import org.bukkit.GameMode
//import org.bukkit.Material
//import org.bukkit.Sound
//import org.bukkit.entity.Player
//import org.bukkit.event.block.Action
//import org.bukkit.event.entity.EntityDamageEvent
//import org.bukkit.event.player.PlayerInteractEvent
//import org.bukkit.inventory.EquipmentSlot
//import org.bukkit.inventory.ItemStack
//import taboolib.common.platform.event.SubscribeEvent
//import taboolib.common.util.random
//import java.util.Random
//import java.util.concurrent.ConcurrentHashMap
//import kotlin.collections.get
//
//
//@SubscribeEvent
//fun onPlaceBannedEntity(event: Player) {
//
//    val player: Player = event.player
//    val block = event.clickedBlock?.type?.name ?: return
//
//    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
//
//    if (block.contains("CANDLE") || block.contains("POT") || block == "CAULDRON" || block.contains("COPPER") || block.contains("SIGN") || block == "CHISELED_BOOKSHELF" || block == "LODESTONE") {
//        event.isCancelled = true
//    }
//
//    if (player.inventory.itemInMainHand.type.name.contains("BUCKET")) {
//        event.isCancelled = true
//    }
//}

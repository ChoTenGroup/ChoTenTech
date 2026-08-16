package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.util.random
import taboolib.platform.type.BukkitProxyEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.get

private val waterCooldowns = ConcurrentHashMap<String, Long>()

@SubscribeEvent(priority = EventPriority.HIGHEST)
fun onPlayerInteractWithDisabledBlocks(event: PlayerInteractEvent) {

    val player: Player = event.player
    val block = event.clickedBlock?.type?.name ?: return

    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    if (block.contains("CANDLE") || block.contains("POT") || block == "CAULDRON"  || block == "CAKE" || block.contains("COPPER") || block.contains("SIGN") || block == "CHISELED_BOOKSHELF" || block == "LODESTONE") {
        event.isCancelled = true
    }

    if (player.inventory.itemInMainHand.type.name.contains("BUCKET")) {
        event.isCancelled = true
    }
}


@SubscribeEvent
fun onPlayerInteractWithWaterDispenser(event: PlayerInteractEvent) {

    if (event.hand == EquipmentSlot.OFF_HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
    val player = event.player
    val block = event.clickedBlock ?: return
    if (playerDataMap[player.uniqueId.toString()]?.activeLevelStr != "level-4") return

    if (block.type == Material.TRIPWIRE_HOOK) {
        val key = "${block.world.name}:${block.x}:${block.y}:${block.z}"
        val cooldownEnd = waterCooldowns[key]
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            player.sendStringAsComponent("<red>这个饮水机看上去没有水了。")
            player.playSound(player.eyeLocation, Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1f, 0.7f)
            return
        }

        val s = Sertraline.api().buildItem("almond_water_gray", player)!!
        player.sendStringAsComponent("<gray>你从饮水机接了一瓶杏仁水。")
        TakeWaterEvent(player).callEvent()
        player.playSound(player.eyeLocation, Sound.ITEM_BOTTLE_FILL, 1f, 0.7f)
        block.world.dropItemNaturally(block.location, s)

        if (random(1.0,2.0) <= 1.2) waterCooldowns[key] = System.currentTimeMillis() + 600_000L
    }
}

class TakeWaterEvent(
    val player: Player
): BukkitProxyEvent() {
}

@Awake(LifeCycle.DISABLE)
fun clearWaterCooldowns() {
    waterCooldowns.clear()
}

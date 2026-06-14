package io.github.zzzyyylllty.chotentech.feature.keydoor

import org.bukkit.Material
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.event.SubscribeEvent

object KeyDoorListener {

    @SubscribeEvent
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.IRON_DOOR) return

        val doorLoc = KeyDoorManager.getDoorBaseLocation(block) ?: return

        // Check config and open door (runs sync — NBT/config reads are fast)
        if (KeyDoorManager.tryOpenDoor(event.player, doorLoc)) {
            event.isCancelled = true
        }
    }
}

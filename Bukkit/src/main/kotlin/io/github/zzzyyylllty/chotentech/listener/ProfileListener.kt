package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.gui.ProfileCreationPending
import io.github.zzzyyylllty.chotentech.gui.ProfileDeletionPending
import io.github.zzzyyylllty.chotentech.gui.ProfileSelectionGUI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.concurrent.ConcurrentHashMap

object ProfileListener {

    /** Players who haven't confirmed a profile selection this session */
    val pendingSelection = ConcurrentHashMap.newKeySet<String>()

    /** Track players who already triggered the profile GUI this session */
    private val guiTriggered = ConcurrentHashMap.newKeySet<String>()

    /** Holding location before profile selection */
    private val holdingLocation: Location by lazy {
        val world = Bukkit.getWorlds().first()
        Location(world, 0.5, 1.0, 0.5)
    }

    /** Mark player as pending — teleport to holding, clear inv, reset GUI trigger, clear active profile */
    fun setPending(player: Player) {
        val uuid = player.uniqueId.toString()
        pendingSelection.add(uuid)
        player.inventory.clear()
        player.teleport(holdingLocation)
        guiTriggered.remove(uuid)
        // Clear active profile — player must explicitly select one in the GUI before playing
        val shared = ProfileManager.getSharedData(uuid)
        shared.activeProfileId = ""
        ProfileManager.saveSharedData(uuid)
    }

    /** Release from pending without further action — player selected a profile via GUI */
    fun releaseFromPending(player: Player) {
        val uuid = player.uniqueId.toString()
        pendingSelection.remove(uuid)
        guiTriggered.remove(uuid)
    }

    /** Confirm profile selection — release pending, restore inv, teleport to profile location */
    fun confirmSelection(player: Player) {
        val uuid = player.uniqueId.toString()
        pendingSelection.remove(uuid)
        ProfileManager.syncToSharedData(uuid, io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap[uuid] ?: return)
        ProfileManager.saveSharedData(uuid)
        ProfileManager.applyInventory(player)
        ProfileManager.teleportToProfileLocation(player)
    }

    @SubscribeEvent
    fun onJoinClearInventory(e: PlayerJoinEvent) {
        val player = e.player
        if (pendingSelection.contains(player.uniqueId.toString())) {
            player.inventory.clear()
        }
    }

    /** Block X/Z movement while pending, open GUI on first move */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onHoldPending(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        if (!pendingSelection.contains(uuid)) return

        // Ignore rotation-only
        val to = event.to ?: return
        if (event.from.x == to.x && event.from.z == to.z) return

        // Block X/Z movement
        if (event.from.x != to.x || event.from.z != to.z) {
            event.isCancelled = true
            player.teleport(holdingLocation)
            player.inventory.clear()
        }

        // Open GUI on first actual movement
        if (guiTriggered.add(uuid)) {
            ProfileSelectionGUI().open(player)
        }
    }

    /** Re-open GUI when pending player presses ESC — not triggered by plugin-initiated closeInventory() */
    @SubscribeEvent
    fun onReopenGui(event: InventoryCloseEvent) {
        if (event.reason != org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLAYER) return
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId.toString()
        if (!pendingSelection.contains(uuid)) return
        if (ProfileCreationPending.pending.containsKey(uuid) || ProfileDeletionPending.pending.containsKey(uuid)) return

        submit(delay = 1) {
            if (pendingSelection.contains(uuid)) {
                ProfileSelectionGUI().open(player)
            }
        }
    }

    /** Handle chat input for profile creation/deletion */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (ProfileCreationPending.handleChat(player, event.message)) {
            event.isCancelled = true
        } else if (ProfileDeletionPending.handleChat(player, event.message)) {
            event.isCancelled = true
        }
    }

    /** Clean up on quit */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId.toString()
        pendingSelection.remove(uuid)
        guiTriggered.remove(uuid)
        ProfileCreationPending.pending.remove(uuid)
        ProfileDeletionPending.pending.remove(uuid)
    }
}

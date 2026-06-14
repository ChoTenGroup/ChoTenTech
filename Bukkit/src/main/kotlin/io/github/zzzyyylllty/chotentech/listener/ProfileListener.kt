package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.gui.ProfileCreationPending
import io.github.zzzyyylllty.chotentech.gui.ProfileDeletionPending
import io.github.zzzyyylllty.chotentech.gui.ProfileSelectionGUI
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap

object ProfileListener {

    /** Track players who already triggered/have been shown the profile GUI this session */
    private val guiTriggered = ConcurrentHashMap.newKeySet<String>()

    /**
     * On first player move (MONITOR priority, for login plugin compatibility),
     * show the profile selection GUI if the player has not yet selected a profile
     * in this session.
     */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerFirstMove(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()

        // Only trigger once per session
        if (!guiTriggered.add(uuid)) return

        // Only trigger on actual movement, not just rotation
        val from = event.from
        val to = event.to ?: return
        if (from.x == to.x && from.y == to.y && from.z == to.z) return

        // Show the profile selection GUI
        val gui = ProfileSelectionGUI()
        gui.open(player)
    }

    /**
     * Handle chat input for profile name creation.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (ProfileCreationPending.handleChat(player, event.message)) {
            event.isCancelled = true
        } else if (ProfileDeletionPending.handleChat(player, event.message)) {
            event.isCancelled = true
        }
    }

    /**
     * Reset a player's GUI trigger state (for reloads or admin commands).
     */
    fun resetPlayer(uuid: String) {
        guiTriggered.remove(uuid)
    }

    /**
     * Called when player quits to clean up state.
     */
    fun onPlayerQuit(uuid: String) {
        guiTriggered.remove(uuid)
        ProfileCreationPending.pending.remove(uuid)
        ProfileDeletionPending.pending.remove(uuid)
    }
}

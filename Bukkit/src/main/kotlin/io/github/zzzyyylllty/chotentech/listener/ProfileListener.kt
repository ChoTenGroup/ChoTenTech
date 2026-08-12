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

    /** Players who haven't confirmed a profile selection this session (uuid -> 当前会话的 player 对象,用于防掉线重进竞态) */
    val pendingSelection = ConcurrentHashMap<String, Player>()

    /** Track players who already triggered the profile GUI this session (uuid -> player 对象) */
    private val guiTriggered = ConcurrentHashMap<String, Player>()

    /** Holding location before profile selection */
    private val holdingLocation: Location by lazy {
        val world = Bukkit.getWorlds().first()
        Location(world, 0.5, 1.0, 0.5)
    }

    /** Mark player as pending — teleport to holding, clear inv, reset GUI trigger, clear active profile */
    fun setPending(player: Player) {
        val uuid = player.uniqueId.toString()
        pendingSelection[uuid] = player
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
        val uuid = player.uniqueId.toString()
        val pending = pendingSelection[uuid] ?: return
        if (pending === player) {
            player.inventory.clear()
        }
    }

    /** Block X/Z movement while pending, open GUI on first move */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onHoldPending(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val pending = pendingSelection[uuid] ?: return
        // 掉线重进时旧连接的残留事件可能晚于新连接处理,只响应当前 pending 的 player 对象
        if (pending !== player) return

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
        if (guiTriggered.putIfAbsent(uuid, player) == null) {
            ProfileSelectionGUI().open(player)
        }
    }

    /** Re-open GUI when pending player presses ESC — not triggered by plugin-initiated closeInventory() */
    @SubscribeEvent
    fun onReopenGui(event: InventoryCloseEvent) {
        if (event.reason != org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLAYER) return
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId.toString()
        val pending = pendingSelection[uuid] ?: return
        if (pending !== player) return
        if (ProfileCreationPending.pending.containsKey(uuid) || ProfileDeletionPending.pending.containsKey(uuid)) return

        submit(delay = 1) {
            if (pendingSelection[uuid] === player) {
                ProfileSelectionGUI().open(player)
            }
        }
    }

    /** Handle chat input for profile creation/deletion */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        // 有挂起的创建/删除操作时才拦截
        if (!ProfileCreationPending.pending.containsKey(uuid) && !ProfileDeletionPending.pending.containsKey(uuid)) return
        // 取消必须在本事件线程内同步设置，否则消息已广播
        event.isCancelled = true
        val message = event.message
        // handleChat 会打开 GUI/传送/写库，需在主线程执行
        submit {
            if (!ProfileCreationPending.handleChat(player, message)) {
                ProfileDeletionPending.handleChat(player, message)
            }
        }
    }

    /** Clean up on quit */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        // 仅清理当前会话(同一 player 对象)的状态,防止掉线重进时旧连接的 quit 事件误清新连接的 pending 状态
        if (pendingSelection[uuid] === player) pendingSelection.remove(uuid)
        if (guiTriggered[uuid] === player) guiTriggered.remove(uuid)
        ProfileCreationPending.pending.remove(uuid)
        ProfileDeletionPending.pending.remove(uuid)
    }
}

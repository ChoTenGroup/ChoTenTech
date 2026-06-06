package io.github.zzzyyylllty.chotentech.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import taboolib.common.platform.function.submitAsync
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object NotificationQueue {

    private val messageQueues = ConcurrentHashMap<UUID, ConcurrentLinkedQueue<Component>>()
    private val processingPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val mm = MiniMessage.miniMessage()

    /** 入队一条 MiniMessage 格式的消息 */
    fun enqueue(uuid: UUID, message: String) {
        val parsed = mm.deserialize(message)
        val queue = messageQueues.computeIfAbsent(uuid) { ConcurrentLinkedQueue() }
        queue.add(parsed)
        startProcessing(uuid)
    }

    private fun startProcessing(uuid: UUID) {
        if (processingPlayers.putIfAbsent(uuid, true) != null) return
        processQueue(uuid)
    }

    private fun processQueue(uuid: UUID) {
        val queue = messageQueues[uuid]
        if (queue == null || queue.isEmpty()) {
            messageQueues.remove(uuid)
            processingPlayers.remove(uuid)
            return
        }
        val message = queue.poll()
        val player = Bukkit.getPlayer(uuid)
        if (player != null && player.isOnline && message != null) {
            player.sendMessage(message)
        }
        submitAsync(delay = 40L) {
            processQueue(uuid)
        }
    }

    fun clear(uuid: UUID) {
        messageQueues.remove(uuid)
        processingPlayers.remove(uuid)
    }

    fun clearAll() {
        messageQueues.clear()
        processingPlayers.clear()
    }
}

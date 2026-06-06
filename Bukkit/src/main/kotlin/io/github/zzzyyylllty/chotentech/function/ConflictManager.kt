package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ConflictStatus {
    PENDING, ACTIVE, FINISHED
}

data class ActiveConflict(
    val instanceId: String,
    val playerUuid: String,
    val playerName: String,
    val configId: String,
    val name: String,
    val status: ConflictStatus,
    val level: ResultLevel? = null,
    val startTime: Long,
    var endTime: Long? = null
)

object ConflictManager {

    private val playerConflicts = ConcurrentHashMap<String, ActiveConflict>()
    private val instanceCounter = AtomicLong(0)

    fun startConflict(player: Player, configId: String): ActiveConflict? {
        val config = conflictConfigs[configId] ?: return null
        val uuid = player.uniqueId.toString()
        // Replace any existing conflict for this player
        val instanceId = "conflict-${System.currentTimeMillis()}-${instanceCounter.incrementAndGet()}"
        val conflict = ActiveConflict(
            instanceId = instanceId,
            playerUuid = uuid,
            playerName = player.name,
            configId = configId,
            name = config.name,
            status = ConflictStatus.PENDING,
            startTime = System.currentTimeMillis()
        )
        playerConflicts[uuid] = conflict
        return conflict
    }

    fun activateConflict(playerUuid: String): Boolean {
        val conflict = playerConflicts[playerUuid] ?: return false
        if (conflict.status != ConflictStatus.PENDING) return false
        playerConflicts[playerUuid] = conflict.copy(status = ConflictStatus.ACTIVE)
        return true
    }

    fun setParticipantLevel(playerUuid: String, level: ResultLevel): Boolean {
        val conflict = playerConflicts[playerUuid] ?: return false
        if (conflict.status == ConflictStatus.FINISHED) return false
        playerConflicts[playerUuid] = conflict.copy(level = level)
        return true
    }

    fun endConflict(playerUuid: String): List<ReputationManager.ReputationChange>? {
        val conflict = playerConflicts[playerUuid] ?: return null
        if (conflict.status == ConflictStatus.FINISHED) return null

        val config = conflictConfigs[conflict.configId] ?: return null
        val level = conflict.level ?: return null
        val score = config.scores[level] ?: return null

        val player = Bukkit.getPlayer(playerUuid)
        val changes = mutableListOf<ReputationManager.ReputationChange>()
        if (player != null && player.isOnline) {
            val data = playerDataMap[playerUuid]
            if (data != null) {
                for ((faction, change) in score.changes) {
                    val current = data.reputation.getOrDefault(faction, 0L)
                    data.reputation[faction] = current + change
                    changes.add(ReputationManager.ReputationChange(faction, change))
                }
            }
        }

        playerConflicts[playerUuid] = conflict.copy(
            status = ConflictStatus.FINISHED,
            endTime = System.currentTimeMillis()
        )
        return changes.ifEmpty { null }
    }

    fun cancelConflict(playerUuid: String): Boolean {
        val conflict = playerConflicts[playerUuid] ?: return false
        if (conflict.status == ConflictStatus.FINISHED) return false
        playerConflicts[playerUuid] = conflict.copy(
            status = ConflictStatus.FINISHED,
            endTime = System.currentTimeMillis()
        )
        return true
    }

    fun getConflict(playerUuid: String): ActiveConflict? = playerConflicts[playerUuid]

    fun getActiveConflicts(): List<ActiveConflict> =
        playerConflicts.values.filter { it.status != ConflictStatus.FINISHED }

    fun getAllConflicts(): List<ActiveConflict> = playerConflicts.values.toList()

    fun cleanOldConflicts(maxAgeMs: Long = 3600000L) {
        val now = System.currentTimeMillis()
        playerConflicts.values.removeAll { conflict ->
            conflict.status == ConflictStatus.FINISHED &&
                conflict.endTime != null &&
                (now - conflict.endTime!!) > maxAgeMs
        }
    }
}

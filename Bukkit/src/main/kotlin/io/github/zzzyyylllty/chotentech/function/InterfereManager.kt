package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.interfereConfigs
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class InterfereStatus {
    PENDING, ACTIVE, FINISHED
}

data class ActiveInterfere(
    val instanceId: String,
    val playerUuid: String,
    val playerName: String,
    val configId: String,
    val name: String,
    val status: InterfereStatus,
    val factionLevels: Map<String, ResultLevel> = emptyMap(),
    val startTime: Long,
    var endTime: Long? = null
)

object InterfereManager {

    private val playerInterferes = ConcurrentHashMap<String, ActiveInterfere>()
    private val instanceCounter = AtomicLong(0)

    fun startInterfere(player: Player, configId: String): ActiveInterfere? {
        val config = interfereConfigs[configId] ?: return null
        val uuid = player.uniqueId.toString()
        // Replace any existing interfere for this player
        val instanceId = "interfere-${System.currentTimeMillis()}-${instanceCounter.incrementAndGet()}"
        val interfere = ActiveInterfere(
            instanceId = instanceId,
            playerUuid = uuid,
            playerName = player.name,
            configId = configId,
            name = config.name,
            status = InterfereStatus.PENDING,
            startTime = System.currentTimeMillis()
        )
        playerInterferes[uuid] = interfere
        return interfere
    }

    fun activateInterfere(playerUuid: String): Boolean {
        val interfere = playerInterferes[playerUuid] ?: return false
        if (interfere.status != InterfereStatus.PENDING) return false
        playerInterferes[playerUuid] = interfere.copy(status = InterfereStatus.ACTIVE)
        return true
    }

    fun setFactionLevel(playerUuid: String, faction: String, level: ResultLevel): Boolean {
        val interfere = playerInterferes[playerUuid] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        val newLevels = interfere.factionLevels.toMutableMap()
        newLevels[faction] = level
        playerInterferes[playerUuid] = interfere.copy(factionLevels = newLevels)
        return true
    }

    fun removeFactionLevel(playerUuid: String, faction: String): Boolean {
        val interfere = playerInterferes[playerUuid] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        val newLevels = interfere.factionLevels.toMutableMap()
        val removed = newLevels.remove(faction) != null
        if (removed) {
            playerInterferes[playerUuid] = interfere.copy(factionLevels = newLevels)
        }
        return removed
    }

    fun endInterfere(playerUuid: String): List<ReputationManager.ReputationChange>? {
        val interfere = playerInterferes[playerUuid] ?: return null
        if (interfere.status == InterfereStatus.FINISHED) return null

        val config = interfereConfigs[interfere.configId] ?: return null
        if (interfere.factionLevels.isEmpty()) return null

        val player = Bukkit.getPlayer(playerUuid)
        val changes = mutableListOf<ReputationManager.ReputationChange>()
        if (player != null && player.isOnline) {
            val data = playerDataMap[playerUuid]
            if (data != null) {
                for ((faction, level) in interfere.factionLevels) {
                    val score = config.scores[level] ?: continue
                    val change = score.changes[faction] ?: continue
                    val current = data.reputation.getOrDefault(faction, 0L)
                    data.reputation[faction] = current + change
                    changes.add(ReputationManager.ReputationChange(faction, change))
                    ReputationManager.addRecord(player, interfere.configId, faction, level, change)
                }
            }
        }

        playerInterferes[playerUuid] = interfere.copy(
            status = InterfereStatus.FINISHED,
            endTime = System.currentTimeMillis()
        )
        return changes.ifEmpty { null }
    }

    fun cancelInterfere(playerUuid: String): Boolean {
        val interfere = playerInterferes[playerUuid] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        playerInterferes[playerUuid] = interfere.copy(
            status = InterfereStatus.FINISHED,
            endTime = System.currentTimeMillis()
        )
        return true
    }

    fun getInterfere(playerUuid: String): ActiveInterfere? = playerInterferes[playerUuid]

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        playerInterferes.clear()
    }

    fun getActiveInterferes(): List<ActiveInterfere> =
        playerInterferes.values.filter { it.status != InterfereStatus.FINISHED }

    fun getAllInterferes(): List<ActiveInterfere> = playerInterferes.values.toList()

    fun cleanOldInterferes(maxAgeMs: Long = 3600000L) {
        val now = System.currentTimeMillis()
        playerInterferes.values.removeAll { interfere ->
            interfere.status == InterfereStatus.FINISHED &&
                interfere.endTime != null &&
                (now - interfere.endTime!!) > maxAgeMs
        }
    }
}

package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.*
import org.bukkit.entity.Player

object ReputationManager {

    fun getReputation(player: Player, faction: String): Long {
        return playerDataMap[player.uniqueId.toString()]?.reputation?.getOrDefault(faction, 0L) ?: 0L
    }

    fun setReputation(player: Player, faction: String, value: Long) {
        playerDataMap[player.uniqueId.toString()]?.reputation?.put(faction, value)
    }

    fun addReputation(player: Player, faction: String, amount: Long) {
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        val current = data.reputation.getOrDefault(faction, 0L)
        data.reputation[faction] = current + amount
    }

    fun getAffection(player: Player, npcId: String): Long {
        return playerDataMap[player.uniqueId.toString()]?.affection?.getOrDefault(npcId, 0L) ?: 0L
    }

    fun setAffection(player: Player, npcId: String, value: Long) {
        playerDataMap[player.uniqueId.toString()]?.affection?.put(npcId, value)
    }

    fun addAffection(player: Player, npcId: String, amount: Long) {
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        val current = data.affection.getOrDefault(npcId, 0L)
        data.affection[npcId] = current + amount
    }

    data class ReputationChange(
        val faction: String,
        val change: Long
    )

    /**
     * Apply reputation changes from completing a conflict at a given result level.
     * Returns the list of changes applied.
     */
    fun applyConflictReputation(player: Player, conflictId: String, level: ResultLevel): List<ReputationChange> {
        val config = conflictConfigs[conflictId] ?: return emptyList()
        val score = config.scores[level] ?: return emptyList()

        val data = playerDataMap[player.uniqueId.toString()] ?: return emptyList()
        val changes = mutableListOf<ReputationChange>()

        for ((faction, change) in score.changes) {
            val current = data.reputation.getOrDefault(faction, 0L)
            data.reputation[faction] = current + change
            changes.add(ReputationChange(faction, change))
        }

        return changes
    }

    /**
     * Generate a formatted reputation report message for a player.
     */
    fun formatReputationReport(player: Player, conflictName: String, level: ResultLevel, changes: List<ReputationChange>): String {
        val sb = StringBuilder()
        val data = playerDataMap[player.uniqueId.toString()] ?: return ""
        val levelDisplay = "${level.displayColor}${level.displayName}"

        sb.appendLine("<gold>===== 冲突: ${conflictName} - $levelDisplay <gold>=====")
        for (change in changes) {
            val current = data.reputation.getOrDefault(change.faction, 0L)
            val prev = current - change.change
            val prefix = if (change.change >= 0) "+" else ""
            val changeColor = if (change.change >= 0) "<green>" else "<red>"
            sb.appendLine("<gray>${change.faction}: <white>$prev <gray>-> <white>$current  $changeColor($prefix${change.change})")
        }
        return sb.toString()
    }
}

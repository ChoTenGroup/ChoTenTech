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

    /**
     * 记录一次杂项信誉变动（add/remove/set 等途径）。
     * 一次操作只写一条记录，[changes] 为本次涉及的阵营 -> 变动值，可同时包含多个阵营。
     */
    fun addMiscRecord(player: Player, changes: Map<String, Long>) {
        if (changes.isEmpty()) return
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        data.miscReputationRecords.add(
            MiscReputationRecord(
                time = System.currentTimeMillis(),
                change = changes.mapValuesTo(mutableMapOf()) { (_, change) -> ReputationChangeEntry(null, change) }
            )
        )
    }

    /** 记录一次单阵营的杂项信誉变动（便捷方法） */
    fun addMiscRecord(player: Player, faction: String, change: Long) {
        addMiscRecord(player, mapOf(faction to change))
    }

    /**
     * 记录一次干涉结算：一个干涉ID 每次结算一条记录，
     * [changes] 里的所有阵营会写进同一条记录的 [InterfereReputationRecord.change]。
     */
    fun addInterfereRecord(player: Player, interfereId: String, instanceId: String?, changes: List<ReputationChange>) {
        if (changes.isEmpty()) return
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        val record = InterfereReputationRecord(
            instanceId = instanceId,
            time = System.currentTimeMillis(),
            change = changes.associateTo(mutableMapOf()) { it.faction to ReputationChangeEntry(it.level?.name, it.change) }
        )
        data.interfereReputationRecords.getOrPut(interfereId) { mutableListOf() }.add(record)
    }

    /** 某玩家全部信誉记录（干涉 + 杂项），按时间升序 */
    fun getRecords(player: Player): List<ReputationRecordView> {
        val data = playerDataMap[player.uniqueId.toString()] ?: return emptyList()
        val list = mutableListOf<ReputationRecordView>()
        data.interfereReputationRecords.forEach { (interfereId, records) ->
            records.forEach { list += ReputationRecordView(interfereId, it.instanceId, it.time, it.change) }
        }
        data.miscReputationRecords.forEach { list += ReputationRecordView(null, null, it.time, it.change) }
        return list.sortedBy { it.time }
    }

    /** 某玩家全部信誉记录中涉及指定阵营的部分，按时间升序 */
    fun getRecordsOf(player: Player, faction: String): List<ReputationRecordView> =
        getRecords(player).filter { it.change.containsKey(faction) }

    /** 界面展示用的统一记录视图；[interfereId] 为 null 表示杂项途径 */
    data class ReputationRecordView(
        val interfereId: String?,
        val instanceId: String?,
        val time: Long,
        val change: Map<String, ReputationChangeEntry>
    )

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
        val change: Long,
        val level: ResultLevel? = null
    )

    /**
     * Apply reputation changes from completing a interfere at a given result level.
     * Returns the list of changes applied.
     */
    fun applyInterfereReputation(player: Player, interfereId: String, level: ResultLevel): List<ReputationChange> {
        val config = interfereConfigs[interfereId] ?: return emptyList()
        val score = config.scores[level] ?: return emptyList()

        val data = playerDataMap[player.uniqueId.toString()] ?: return emptyList()
        val changes = mutableListOf<ReputationChange>()

        for ((faction, change) in score.changes) {
            val current = data.reputation.getOrDefault(faction, 0L)
            data.reputation[faction] = current + change
            changes.add(ReputationChange(faction, change, level))
        }

        // 一次结算的所有阵营写进同一条记录
        addInterfereRecord(player, interfereId, null, changes)

        return changes
    }

    /**
     * Generate a formatted reputation report message for a player.
     */
    fun formatReputationReport(player: Player, interfereName: String, level: ResultLevel, changes: List<ReputationChange>): String {
        val sb = StringBuilder()
        val data = playerDataMap[player.uniqueId.toString()] ?: return ""
        val levelDisplay = "${level.displayColor}${level.displayName}"

        sb.appendLine("<#DFBD7C>===== 干扰: ${interfereName} - $levelDisplay <#DFBD7C>=====")
        for (change in changes) {
            val current = data.reputation.getOrDefault(change.faction, 0L)
            val prev = current - change.change
            val prefix = if (change.change >= 0) "+" else ""
            val changeColor = if (change.change >= 0) "<#8FD4A6>" else "<#E08A98>"
            sb.appendLine("<#A9B4C3>${change.faction}: <#F2F5FA>$prev <#A9B4C3>-> <#F2F5FA>$current  $changeColor($prefix${change.change})")
        }
        return sb.toString()
    }
}

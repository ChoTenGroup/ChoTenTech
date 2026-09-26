package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.interfereConfigs
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import java.util.UUID
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
    /** 阵营 -> 分数覆盖值；存在时结算与查询都用它，否则用配置中的默认分数 */
    val scoreOverrides: Map<String, Long> = emptyMap(),
    val startTime: Long,
    var endTime: Long? = null
)

/**
 * 干涉实例管理：以实例ID（instanceId）为主键，一个玩家可以同时持有多个干涉实例。
 * 同一玩家 + 同一配置最多只有一个未结束实例，重复 startInterfere 会替换掉旧的未结束实例。
 */
object InterfereManager {

    /** 实例ID -> 干涉实例 */
    private val interferes = ConcurrentHashMap<String, ActiveInterfere>()
    private val instanceCounter = AtomicLong(0)

    fun startInterfere(player: Player, configId: String): ActiveInterfere? {
        val config = interfereConfigs[configId] ?: return null
        val uuid = player.uniqueId.toString()
        // 同一玩家同一配置只保留一个未结束实例；已结束的历史实例保留，重复开启视为重开
        interferes.values.removeIf {
            it.playerUuid == uuid && it.configId == configId && it.status != InterfereStatus.FINISHED
        }
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
        interferes[instanceId] = interfere
        return interfere
    }

    fun activateInterfere(instanceId: String): Boolean {
        val interfere = interferes[instanceId] ?: return false
        if (interfere.status != InterfereStatus.PENDING) return false
        interferes[instanceId] = interfere.copy(status = InterfereStatus.ACTIVE)
        return true
    }

    fun setFactionLevel(instanceId: String, faction: String, level: ResultLevel): Boolean {
        val interfere = interferes[instanceId] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        val newLevels = interfere.factionLevels.toMutableMap()
        newLevels[faction] = level
        // 重新标记等级即回到配置默认分数，丢弃此前的覆盖值
        interferes[instanceId] = interfere.copy(
            factionLevels = newLevels,
            scoreOverrides = interfere.scoreOverrides - faction
        )
        return true
    }

    fun removeFactionLevel(instanceId: String, faction: String): Boolean {
        val interfere = interferes[instanceId] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        val newLevels = interfere.factionLevels.toMutableMap()
        val removed = newLevels.remove(faction) != null
        if (removed) {
            interferes[instanceId] = interfere.copy(
                factionLevels = newLevels,
                scoreOverrides = interfere.scoreOverrides - faction
            )
        }
        return removed
    }

    /** 读取某阵营当前记录的结果等级；未记录时为 null */
    fun getFactionLevel(instanceId: String, faction: String): ResultLevel? =
        interferes[instanceId]?.factionLevels?.get(faction)

    /** 读取某阵营在某结果等级下配置的默认分数；未配置时为 null */
    fun getDefaultFactionScore(instanceId: String, faction: String, level: ResultLevel): Long? {
        val interfere = interferes[instanceId] ?: return null
        return interfereConfigs[interfere.configId]?.scores?.get(level)?.changes?.get(faction)
    }

    /** 读取某阵营最终生效的分数：优先取覆盖值，其次取配置默认值；两者都没有时为 null */
    fun getEffectiveFactionScore(instanceId: String, faction: String): Long? {
        val interfere = interferes[instanceId] ?: return null
        interfere.scoreOverrides[faction]?.let { return it }
        val level = interfere.factionLevels[faction] ?: return null
        return getDefaultFactionScore(instanceId, faction, level)
    }

    /**
     * 标记某阵营的结果等级，并可选地覆盖该阵营的最终分数。
     * [scoreOverride] 为 null 时清除既有覆盖，回到配置默认分数。
     * 实例不存在或已结束时返回 false。
     */
    fun setFactionResult(instanceId: String, faction: String, level: ResultLevel, scoreOverride: Long?): Boolean {
        val interfere = interferes[instanceId] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        val newLevels = interfere.factionLevels.toMutableMap()
        newLevels[faction] = level
        val newOverrides = interfere.scoreOverrides.toMutableMap()
        if (scoreOverride == null) {
            newOverrides.remove(faction)
        } else {
            newOverrides[faction] = scoreOverride
        }
        interferes[instanceId] = interfere.copy(
            factionLevels = newLevels,
            scoreOverrides = newOverrides
        )
        return true
    }

    fun endInterfere(instanceId: String): List<ReputationManager.ReputationChange>? {
        val interfere = interferes[instanceId] ?: return null
        if (interfere.status == InterfereStatus.FINISHED) return null

        val config = interfereConfigs[interfere.configId] ?: return null
        if (interfere.factionLevels.isEmpty()) return null

        // 玩家不在线 / 数据缺失时不结算，也不标记结束，方便稍后重试，避免声望被静默丢弃
        val player = runCatching { Bukkit.getPlayer(UUID.fromString(interfere.playerUuid)) }.getOrNull() ?: return null
        if (!player.isOnline) return null
        val data = playerDataMap[interfere.playerUuid] ?: return null

        val changes = mutableListOf<ReputationManager.ReputationChange>()
        for ((faction, level) in interfere.factionLevels) {
            // 覆盖值优先于配置默认分数，两者都没有时跳过该阵营
            val change = interfere.scoreOverrides[faction]
                ?: config.scores[level]?.changes?.get(faction)
                ?: continue
            val current = data.reputation.getOrDefault(faction, 0L)
            data.reputation[faction] = current + change
            changes.add(ReputationManager.ReputationChange(faction, change, level))
        }
        // 本次结算涉及的所有阵营写进同一条记录
        ReputationManager.addInterfereRecord(player, interfere.configId, interfere.instanceId, changes)

        interferes[instanceId] = interfere.copy(
            status = InterfereStatus.FINISHED,
            endTime = System.currentTimeMillis()
        )
        return changes.ifEmpty { null }
    }

    fun cancelInterfere(instanceId: String): Boolean {
        val interfere = interferes[instanceId] ?: return false
        if (interfere.status == InterfereStatus.FINISHED) return false
        interferes[instanceId] = interfere.copy(
            status = InterfereStatus.FINISHED,
            endTime = System.currentTimeMillis()
        )
        return true
    }

    /** 按实例ID查找干涉实例（实例ID全局唯一） */
    fun getInterfere(instanceId: String): ActiveInterfere? = interferes[instanceId]

    /** 某玩家的全部实例（含已结束的历史实例），按开始时间升序 */
    fun getInterferes(playerUuid: String): List<ActiveInterfere> =
        interferes.values.filter { it.playerUuid == playerUuid }.sortedBy { it.startTime }

    /** 某玩家未结束的实例，按开始时间升序 */
    fun getActiveInterferes(playerUuid: String): List<ActiveInterfere> =
        getInterferes(playerUuid).filter { it.status != InterfereStatus.FINISHED }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        interferes.clear()
    }

    /** 全部未结束的实例，按开始时间升序 */
    fun getActiveInterferes(): List<ActiveInterfere> =
        interferes.values.filter { it.status != InterfereStatus.FINISHED }.sortedBy { it.startTime }

    /** 全部实例（含已结束），按开始时间升序 */
    fun getAllInterferes(): List<ActiveInterfere> = interferes.values.sortedBy { it.startTime }

    fun cleanOldInterferes(maxAgeMs: Long = 3600000L) {
        val now = System.currentTimeMillis()
        interferes.values.removeAll { interfere ->
            interfere.status == InterfereStatus.FINISHED &&
                interfere.endTime != null &&
                (now - interfere.endTime!!) > maxAgeMs
        }
    }
}

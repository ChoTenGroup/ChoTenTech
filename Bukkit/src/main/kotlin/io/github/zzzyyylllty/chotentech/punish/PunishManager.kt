package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.CurrencyUtil
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor
import taboolib.expansion.Length
import taboolib.expansion.Id
import taboolib.expansion.db
import taboolib.expansion.mapper
import taboolib.expansion.TableName
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PunishManager {

    // ==================== PtcObject Data Classes & Mappers ====================

    @TableName("punishment_records")
    class PunishmentRecord {
        @Id
        var id: Long = 0
        var targetUuid: String = ""
        var targetName: String = ""
        var operatorUuid: String = ""
        var operatorName: String = ""
        var type: String = ""
        @Length(-1)
        var reason: String = ""
        var durationMs: Long = 0L
        var issueTime: Long = 0L
        var expiryTime: Long = 0L
        var active: Boolean = false
        var silent: Boolean = false
        @Length(-1)
        var metaJson: String = ""
    }

    @TableName("wipe_backups")
    class WipeBackupRecord {
        // No @Id — auto-increment primary key
        var punishmentId: Long = 0L
        var targetUuid: String = ""
        var targetName: String = ""
        var scope: String = ""
        @Length(-1)
        var dataJson: String = ""
        var backupTime: Long = 0L
    }

    val punishmentMapper by mapper<PunishmentRecord>(db("config.yml", "database", "chotentech.db"))
    val wipeMapper by mapper<WipeBackupRecord>(db("config.yml", "database", "chotentech.db"))

    private val activeCache = ConcurrentHashMap<String, MutableList<PunishEntry>>()

    fun clearActiveCache() {
        activeCache.clear()
    }

    /** 启动/重载时从数据库回填未过期处罚到缓存，保证重启后封禁/禁言/AUTH 仍然生效 */
    fun reloadActiveCache() {
        activeCache.clear()
        val records = punishmentMapper.findAll { "active" eq true }
        for (record in records) {
            val type = runCatching { PunishType.valueOf(record.type) }.getOrNull() ?: continue
            val entry = PunishEntry(
                id = record.id,
                targetUuid = record.targetUuid,
                targetName = record.targetName,
                operatorUuid = record.operatorUuid.takeIf { it != "NULL" },
                operatorName = record.operatorName,
                type = type,
                reason = record.reason,
                durationMs = record.durationMs,
                issueTime = record.issueTime,
                expiryTime = record.expiryTime,
                active = record.active,
                silent = record.silent,
                meta = runCatching { jsonUtils.fromJson(record.metaJson, Map::class.java) }.getOrNull()
                    ?.filterValues { it is String } as Map<String, String>? ?: emptyMap()
            )
            if (!entry.isExpired) {
                activeCache.getOrPut(entry.targetUuid) { mutableListOf() }.add(entry)
            }
        }
    }

    var maxWarnBeforeBan: Int = 5
    var muteReasons: Map<String, Long> = emptyMap()
    var durationLimits: Map<String, Long> = mapOf("default" to 2_592_000_000L)

    fun reloadConfig() {
        val section = config.getConfigurationSection("punishment") ?: return
        maxWarnBeforeBan = section.getInt("max-warn-before-ban", 5)

        muteReasons = buildMap {
            val list = section.getMapList("mute-reasons")
            for (map in list) {
                val reason = map["reason"] as? String ?: continue
                val durStr = map["duration"] as? String ?: continue
                val (durMs, _) = parseDurationTokens(durStr)
                if (durMs > 0) put(reason, durMs)
            }
        }

        durationLimits = buildMap {
            section.getConfigurationSection("duration-limits")?.getKeys(false)?.forEach { key ->
                put(key, section.getLong("duration-limits.$key", 2_592_000_000L))
            }
        }
        if (!durationLimits.containsKey("default")) {
            durationLimits = durationLimits.toMutableMap().also { it["default"] = 2_592_000_000L }
        }
    }

    fun issue(entry: PunishEntry): PunishEntry {
        val metaJson = jsonUtils.toJson(entry.meta, Map::class.java)
        val now = System.currentTimeMillis()
        val expiryTime = if (entry.durationMs == -1L) -1L else now + entry.durationMs
        val finalEntry = entry.copy(issueTime = now, expiryTime = expiryTime)

        val record = PunishmentRecord().also {
            it.targetUuid = finalEntry.targetUuid
            it.targetName = finalEntry.targetName
            it.operatorUuid = finalEntry.operatorUuid ?: "NULL"
            it.operatorName = finalEntry.operatorName
            it.type = finalEntry.type.name
            it.reason = finalEntry.reason
            it.durationMs = finalEntry.durationMs
            it.issueTime = finalEntry.issueTime
            it.expiryTime = finalEntry.expiryTime
            it.active = true
            it.silent = finalEntry.silent
            it.metaJson = metaJson
        }
        val assignedId = punishmentMapper.insertAndGetKey(record)

        val withId = finalEntry.copy(id = assignedId)
        activeCache.getOrPut(withId.targetUuid) { mutableListOf() }.add(withId)
        writeHistory(withId)

        if (withId.type == PunishType.BAN && withId.meta["skipAuth"] != "true") {
            val authEntry = withId.copy(
                id = 0,
                type = PunishType.AUTH,
                durationMs = -1L,
                expiryTime = -1L,
                meta = mapOf("parentBanId" to assignedId.toString())
            )
            issue(authEntry)
        }

        return withId
    }

    fun insertWipeBackup(entry: WipeBackupEntry): Long {
        val record = WipeBackupRecord().also {
            it.punishmentId = entry.punishmentId
            it.targetUuid = entry.targetUuid
            it.targetName = entry.targetName
            it.scope = entry.scope
            it.dataJson = entry.dataJson
            it.backupTime = entry.backupTime
        }
        return wipeMapper.insertAndGetKey(record)
    }

    fun getWipeBackup(punishmentId: Long): WipeBackupEntry? {
        return wipeMapper.findOne {
            "punishment_id" eq punishmentId
        }?.let {
            WipeBackupEntry(
                id = 0L,
                punishmentId = it.punishmentId,
                targetUuid = it.targetUuid,
                targetName = it.targetName,
                scope = it.scope,
                dataJson = it.dataJson,
                backupTime = it.backupTime
            )
        }
    }

    fun deactivatePunishment(id: Long) {
        punishmentMapper.findByRowId(id)?.let { record ->
            record.id = id  // populate @Id for old records that don't have it set
            record.active = false
            punishmentMapper.update(record)
        }
        for ((_, entries) in activeCache) {
            entries.removeAll { it.id == id }
        }
    }

    fun deactivateAllForPlayer(uuid: String, type: PunishType? = null) {
        val records = punishmentMapper.findAll {
            "target_uuid" eq uuid
            if (type != null) "type" eq type.name
            "active" eq true
        }
        records.forEach { it.active = false; punishmentMapper.update(it) }
        activeCache.remove(uuid)
    }

    fun getActivePunishments(uuid: String): List<PunishEntry> {
        return activeCache[uuid]?.filter { it.active } ?: emptyList()
    }

    fun getActiveByType(uuid: String, type: PunishType): List<PunishEntry> {
        return activeCache[uuid]?.filter { it.active && it.type == type } ?: emptyList()
    }

    fun getWarnCount(uuid: String): Int {
        return activeCache[uuid]?.count { it.active && it.type == PunishType.WARN } ?: 0
    }

    fun isMuted(uuid: String): PunishEntry? {
        return activeCache[uuid]?.firstOrNull { it.active && it.type == PunishType.MUTE }
    }

    fun isBannedOrAuthLocked(uuid: String): PunishEntry? {
        return activeCache[uuid]?.firstOrNull {
            it.active && (it.type == PunishType.BAN || it.type == PunishType.AUTH)
        }
    }

    fun getBanEntry(uuid: String): PunishEntry? {
        return activeCache[uuid]?.firstOrNull { it.active && it.type == PunishType.BAN }
    }

    fun getAuthEntry(uuid: String): PunishEntry? {
        return activeCache[uuid]?.firstOrNull { it.active && it.type == PunishType.AUTH }
    }

    fun checkExpirations() {
        val now = System.currentTimeMillis()
        val expired = mutableListOf<PunishEntry>()

        for ((_, entries) in activeCache) {
            entries.filter { it.active && !it.isPermanent && it.expiryTime < now }.forEach {
                expired.add(it)
            }
        }

        for (entry in expired) {
            deactivatePunishment(entry.id)
            val uuid = try { UUID.fromString(entry.targetUuid) } catch (_: Exception) { null }
            val player = uuid?.let { Bukkit.getPlayer(it) }
            if (player != null && player.isOnline) {
                when (entry.type) {
                    PunishType.MUTE -> player.sendComponent("<green>你的禁言处罚已到期，现在可以正常发言了。")
                    PunishType.AUTH -> player.sendComponent("<green>你的身份验证处罚已到期。")
                    else -> {}
                }
            }
        }
    }

    fun restoreWipe(punishmentId: Long, operatorName: String, operatorUuid: String?): Boolean {
        val backup = getWipeBackup(punishmentId) ?: return false

        val snapshot = try {
            jsonUtils.fromJson(backup.dataJson, WipeSnapshot::class.java)
        } catch (e: Exception) {
            warning("Failed to deserialize wipe backup $punishmentId: ${e.message}")
            return false
        } ?: return false

        val uuid = try { UUID.fromString(backup.targetUuid) } catch (_: Exception) { null }
        val player = uuid?.let { Bukkit.getPlayer(it) }

        // Restore inventory
        if (snapshot.inventory != null && player != null) {
            try {
                val items = jsonUtils.fromJson(snapshot.inventory, Array<org.bukkit.inventory.ItemStack>::class.java)
                if (items != null) {
                    val leftover = mutableListOf<org.bukkit.inventory.ItemStack>()
                    for (item in items) {
                        if (item != null) leftover.addAll(player.inventory.addItem(item).values)
                    }
                    // 物品栏放不下的先放末影箱，再掉落到地上，避免还原时丢物
                    if (leftover.isNotEmpty()) {
                        val enderLeft = leftover.flatMap { player.enderChest.addItem(it).values }
                        for (item in enderLeft) {
                            player.world.dropItem(player.location, item)
                        }
                        player.sendComponent("<yellow>物品栏已满，部分还原物品已放入末影箱或掉落在地上")
                    }
                }
            } catch (_: Exception) {}
        }

        // Restore ender chest
        if (snapshot.enderChest != null && player != null) {
            try {
                val items = jsonUtils.fromJson(snapshot.enderChest, Array<org.bukkit.inventory.ItemStack>::class.java)
                if (items != null) {
                    items.forEachIndexed { index, item ->
                        if (item != null && index < player.enderChest.size) {
                            player.enderChest.setItem(index, item)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        deactivatePunishment(punishmentId)

        wipeMapper.findOne {
            "punishment_id" eq punishmentId
        }?.let {
            it.dataJson = "{\"restored\":true}"
            wipeMapper.update(it)
        }

        return true
    }

    fun captureWipeSnapshot(player: Player, scope: String): WipeSnapshot {
        val scopes = scope.split(",").map { it.trim().lowercase() }

        if ("all" in scopes) {
            return WipeSnapshot(
                inventory = serializeInventory(player),
                enderChest = serializeEnderChest(player),
                currencies = captureAllCurrencies(player),
                playerDataFields = emptyMap()
            )
        }

        var snapshot = WipeSnapshot()

        if ("inventory" in scopes) {
            snapshot = snapshot.copy(
                inventory = serializeInventory(player),
                enderChest = serializeEnderChest(player)
            )
        }

        val currencyScopes = scopes.filter { it != "inventory" && it != "all" }
        if (currencyScopes.isNotEmpty()) {
            val currencies = mutableMapOf<String, Long>()
            for (currencyId in currencyScopes) {
                try {
                    val balance = CurrencyUtil.getBalance(player, currencyId)
                    currencies[currencyId] = balance.toLong()
                } catch (_: Exception) {}
            }
            snapshot = snapshot.copy(currencies = currencies)
        }

        return snapshot
    }

    private fun serializeInventory(player: Player): String {
        val items = player.inventory.contents.filterNotNull().toTypedArray()
        return jsonUtils.toJson(items, Array<org.bukkit.inventory.ItemStack>::class.java)
    }

    private fun serializeEnderChest(player: Player): String {
        val items = player.enderChest.contents.filterNotNull().toTypedArray()
        return jsonUtils.toJson(items, Array<org.bukkit.inventory.ItemStack>::class.java)
    }

    private fun captureAllCurrencies(player: Player): Map<String, Long> {
        val currencies = mutableMapOf<String, Long>()
        val currencyIds = listOf("MEG", "BNTG", "UEC")
        for (id in currencyIds) {
            try {
                currencies[id] = CurrencyUtil.getBalance(player, id).toLong()
            } catch (_: Exception) {}
        }
        return currencies
    }

    fun clearWipedData(player: Player, scope: String) {
        val scopes = scope.split(",").map { it.trim().lowercase() }

        if ("all" in scopes) {
            player.inventory.clear()
            player.enderChest.clear()
            for (currencyId in listOf("MEG", "BNTG", "UEC")) {
                try {
                    val balance = CurrencyUtil.getBalance(player, currencyId)
                    CurrencyUtil.remove(player, currencyId, balance)
                } catch (_: Exception) {}
            }
            return
        }

        if ("inventory" in scopes) {
            player.inventory.clear()
            player.enderChest.clear()
        }

        for (scopeItem in scopes) {
            if (scopeItem == "inventory" || scopeItem == "all") continue
            try {
                val balance = CurrencyUtil.getBalance(player, scopeItem)
                CurrencyUtil.remove(player, scopeItem, balance)
            } catch (_: Exception) {}
        }
    }

    fun getMaxDuration(sender: org.bukkit.command.CommandSender): Long {
        var maxMs = durationLimits["default"] ?: 2_592_000_000L
        for ((perm, limit) in durationLimits) {
            if (perm == "default") continue
            if (sender.hasPermission(perm)) {
                if (limit == -1L) return -1L
                if (limit > maxMs) maxMs = limit
            }
        }
        return maxMs
    }

    private val historyFile by lazy { File(getDataFolder(), "punishment_history.json") }

    private fun writeHistory(entry: PunishEntry) {
        try {
            val line = jsonUtils.toJson(entry, PunishEntry::class.java)
            historyFile.appendText(line + "\n", Charsets.UTF_8)
        } catch (e: Exception) {
            warning("Failed to write punishment history: ${e.message}")
        }
    }

    fun getPlayerPunishmentHistory(uuid: String, page: Int = 1, pageSize: Int = 10): Pair<List<PunishEntry>, Int> {
        // Read from history file since we can't easily iterate DB results
        val all = mutableListOf<PunishEntry>()
        if (historyFile.exists()) {
            try {
                historyFile.readLines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val entry = jsonUtils.fromJson(line.trim(), PunishEntry::class.java)
                        if (entry != null && entry.targetUuid == uuid) {
                            all.add(entry)
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                warning("Failed to read punishment history: ${e.message}")
            }
        }
        all.sortByDescending { it.issueTime }
        val total = all.size
        val totalPages = if (total == 0) 1 else (total + pageSize - 1) / pageSize
        val p = page.coerceIn(1, totalPages)
        val start = (p - 1) * pageSize
        val end = minOf(start + pageSize, total)
        return Pair(all.subList(start, end), totalPages)
    }
}

private var punishTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun startPunishmentTimer() {
    PunishManager.reloadActiveCache()
    punishTask = submit(period = 200) {
        PunishManager.checkExpirations()
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelPunishmentTimer() {
    punishTask?.cancel()
    PunishManager.clearActiveCache()
}

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
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table
import taboolib.module.database.getHost
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PunishManager {

    private val host by lazy { config.getHost("database") }
    private val dataSource by lazy { host.createDataSource() }

    val punishmentTable = Table("punishment_records", host) {
        add { id() }
        add("target_uuid") {
            type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.KEY, ColumnOptionSQL.NOTNULL) }
        }
        add("target_name") {
            type(ColumnTypeSQL.VARCHAR, 128)
        }
        add("operator_uuid") {
            type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.KEY) }
        }
        add("operator_name") {
            type(ColumnTypeSQL.VARCHAR, 128)
        }
        add("type") {
            type(ColumnTypeSQL.VARCHAR, 32) { options(ColumnOptionSQL.KEY, ColumnOptionSQL.NOTNULL) }
        }
        add("reason") {
            type(ColumnTypeSQL.TEXT)
        }
        add("duration_ms") {
            type(ColumnTypeSQL.BIGINT) { options(ColumnOptionSQL.NOTNULL) }
        }
        add("issue_time") {
            type(ColumnTypeSQL.BIGINT) { options(ColumnOptionSQL.NOTNULL) }
        }
        add("expiry_time") {
            type(ColumnTypeSQL.BIGINT) { options(ColumnOptionSQL.NOTNULL) }
        }
        add("active") {
            type(ColumnTypeSQL.TINYINT) { options(ColumnOptionSQL.NOTNULL) }
        }
        add("silent") {
            type(ColumnTypeSQL.TINYINT)
        }
        add("meta_json") {
            type(ColumnTypeSQL.TEXT)
        }
    }

    val wipeBackupTable = Table("wipe_backups", host) {
        add { id() }
        add("punishment_id") {
            type(ColumnTypeSQL.BIGINT) { options(ColumnOptionSQL.NOTNULL) }
        }
        add("target_uuid") {
            type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.KEY, ColumnOptionSQL.NOTNULL) }
        }
        add("target_name") {
            type(ColumnTypeSQL.VARCHAR, 128)
        }
        add("scope") {
            type(ColumnTypeSQL.VARCHAR, 255) { options(ColumnOptionSQL.NOTNULL) }
        }
        add("data_json") {
            type(ColumnTypeSQL.LONGTEXT)
        }
        add("backup_time") {
            type(ColumnTypeSQL.BIGINT) { options(ColumnOptionSQL.NOTNULL) }
        }
    }

    private val activeCache = ConcurrentHashMap<String, MutableList<PunishEntry>>()

    var maxWarnBeforeBan: Int = 5
    var muteReasons: Map<String, Long> = emptyMap()
    var durationLimits: Map<String, Long> = mapOf("default" to 2_592_000_000L)

    init {
        punishmentTable.createTable(dataSource)
        wipeBackupTable.createTable(dataSource)
    }

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

        punishmentTable.insert(dataSource,
            "target_uuid", "target_name", "operator_uuid", "operator_name",
            "type", "reason", "duration_ms", "issue_time", "expiry_time",
            "active", "silent", "meta_json"
        ) {
            value(
                finalEntry.targetUuid,
                finalEntry.targetName,
                finalEntry.operatorUuid ?: "NULL",
                finalEntry.operatorName,
                finalEntry.type.name,
                finalEntry.reason,
                finalEntry.durationMs.toString(),
                finalEntry.issueTime.toString(),
                finalEntry.expiryTime.toString(),
                "1",
                if (finalEntry.silent) "1" else "0",
                metaJson
            )
        }

        var assignedId = 0L
        punishmentTable.select(dataSource) {
            rows("id")
            where("target_uuid" eq finalEntry.targetUuid)
            where("issue_time" eq finalEntry.issueTime.toString())
            limit(1)
        }.firstOrNull {
            assignedId = getString("id")?.toLongOrNull() ?: 0L
        }

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
        wipeBackupTable.insert(dataSource,
            "punishment_id", "target_uuid", "target_name", "scope", "data_json", "backup_time"
        ) {
            value(
                entry.punishmentId.toString(),
                entry.targetUuid,
                entry.targetName,
                entry.scope,
                entry.dataJson,
                entry.backupTime.toString()
            )
        }
        var id = 0L
        wipeBackupTable.select(dataSource) {
            rows("id")
            where("punishment_id" eq entry.punishmentId.toString())
            limit(1)
        }.firstOrNull {
            id = getString("id")?.toLongOrNull() ?: 0L
        }
        return id
    }

    fun getWipeBackup(punishmentId: Long): WipeBackupEntry? {
        return wipeBackupTable.select(dataSource) {
            rows("id", "punishment_id", "target_uuid", "target_name", "scope", "data_json", "backup_time")
            where("punishment_id" eq punishmentId.toString())
            limit(1)
        }.firstOrNull {
            WipeBackupEntry(
                id = getString("id")?.toLongOrNull() ?: 0L,
                punishmentId = getString("punishment_id")?.toLongOrNull() ?: 0L,
                targetUuid = getString("target_uuid") ?: "",
                targetName = getString("target_name") ?: "",
                scope = getString("scope") ?: "",
                dataJson = getString("data_json") ?: "{}",
                backupTime = getString("backup_time")?.toLongOrNull() ?: 0L
            )
        }
    }

    fun deactivatePunishment(id: Long) {
        punishmentTable.update(dataSource) {
            set("active", "0")
            where("id" eq id.toString())
        }
        for ((_, entries) in activeCache) {
            entries.removeAll { it.id == id }
        }
    }

    fun deactivateAllForPlayer(uuid: String, type: PunishType? = null) {
        if (type != null) {
            punishmentTable.update(dataSource) {
                set("active", "0")
                where("target_uuid" eq uuid)
                where("type" eq type.name)
                where("active" eq "1")
            }
        } else {
            punishmentTable.update(dataSource) {
                set("active", "0")
                where("target_uuid" eq uuid)
                where("active" eq "1")
            }
        }
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
                    for (item in items) {
                        if (item != null) player.inventory.addItem(item)
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

        wipeBackupTable.update(dataSource) {
            set("data_json", "{\"restored\":true}")
            where("punishment_id" eq punishmentId.toString())
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

@Awake(LifeCycle.ACTIVE)
fun startPunishmentTimer() {
    submitAsync(period = 200) {
        PunishManager.checkExpirations()
    }
}

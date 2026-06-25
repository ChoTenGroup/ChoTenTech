package io.github.zzzyyylllty.chotentech.function.backup

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import kotlin.math.abs

private data class NamedBackup(
    val file: File,
    val timestamp: Long
)

private val backupDir: File by lazy {
    File(getDataFolder(), "backups").also { it.mkdirs() }
}

private val filenameRegex = Regex("""backup-(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})\.json\.gz""")
private val filenameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

/** 1 hour in server ticks (20 TPS) */
private const val ONE_HOUR_TICKS = 20L * 60L * 60L

@Awake(LifeCycle.ACTIVE)
fun startHourlyBackup() {
    submitAsync(delay = ONE_HOUR_TICKS, period = ONE_HOUR_TICKS) {
        try {
            createBackup()
            cleanOldBackups()
        } catch (e: Exception) {
            warning("[BACKUP] 备份失败: ${e.message}")
            e.printStackTrace()
        }
    }
    devLog("[BACKUP] 每小时自动备份已启动 (3.6万像素)")
}

/**
 * 将在线玩家的 PlayerData 序列化并压缩保存
 */
fun createBackup() {
    if (playerDataMap.isEmpty()) {
        devLog("[BACKUP] 无在线玩家数据，跳过备份")
        return
    }

    val timestamp = LocalDateTime.now().format(filenameFormatter)
    val file = File(backupDir, "backup-$timestamp.json.gz")

    val root = com.google.gson.JsonObject()
    root.addProperty("backupTime", System.currentTimeMillis())
    root.addProperty("type", "playerdata")
    root.addProperty("version", 1)

    val playersObj = com.google.gson.JsonObject()
    for ((uuid, data) in playerDataMap) {
        playersObj.add(uuid, jsonUtils.toJsonTree(data, PlayerData::class.java))
    }
    root.add("players", playersObj)

    val json = jsonUtils.toJson(root)

    file.outputStream().use { out ->
        GZIPOutputStream(out).use { gzip ->
            gzip.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    devLog("[BACKUP] 备份完成: ${file.name} (${playerDataMap.size} 名玩家, ${file.length() / 1024} KB)")
}

/**
 * 逐渐删除模式:
 * - 小时级: 保留第 1, 2, 4, 8, 16 个最新备份
 * - 每日级: 保留离现在 1, 2, 3, 4, 5 天的备份
 */
fun cleanOldBackups() {
    val backups = backupDir.listFiles()
        ?.mapNotNull { file ->
            val match = filenameRegex.matchEntire(file.name) ?: return@mapNotNull null
            val ts = try {
                val ldt = LocalDateTime.parse(match.groupValues[1], filenameFormatter)
                ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) { null } ?: return@mapNotNull null
            NamedBackup(file, ts)
        }
        ?.sortedByDescending { it.timestamp }
        ?: return

    if (backups.isEmpty()) return

    val toKeep = mutableSetOf<Int>()

    // Hourly exponential: keep 1st, 2nd, 4th, 8th, 16th newest (indices 0, 1, 3, 7, 15)
    for (i in listOf(0, 1, 3, 7, 15)) {
        if (i < backups.size) toKeep.add(i)
    }

    // Daily: find the closest backup to 1-5 days old
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60L * 60L * 1000L
    for (days in 1..5) {
        val target = now - days * dayMs
        val closest = backups.indices.minByOrNull { i ->
            abs(backups[i].timestamp - target)
        }
        if (closest != null) toKeep.add(closest)
    }

    var deleted = 0
    for (i in backups.indices) {
        if (i !in toKeep && backups[i].file.exists()) {
            if (backups[i].file.delete()) deleted++
        }
    }

    if (deleted > 0) {
        devLog("[BACKUP] 清理完成: 删除 $deleted 个旧备份, 保留 ${toKeep.size} 个")
    }
}

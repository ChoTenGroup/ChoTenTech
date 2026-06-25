package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.getDataFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@CommandHeader(
    name = "backup",
    aliases = ["玩家备份", "bak"],
    permission = "chotentech.command.backup",
    description = "管理玩家数据备份",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object BackupCommand {

    private val backupDir: File by lazy { File(getDataFolder(), "backups") }
    private val restoreDir: File by lazy { File(getDataFolder(), "restore").also { it.mkdirs() } }

    private val filenameRegex = Regex("""backup-(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})\.json\.gz""")
    private val filenameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private data class BackupEntry(
        val file: File,
        val label: String,
        val time: LocalDateTime
    )

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            listBackups(sender)
        }
    }

    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            listBackups(sender)
        }
    }

    @CommandBody
    val info = subCommand {
        dynamic("filename") {
            suggestion<CommandSender> { _, _ ->
                listBackupFiles().map { it.label }
            }
            execute<CommandSender> { sender, context, _ ->
                val input = context["filename"]
                val entry = listBackupFiles().find { it.label == input }
                if (entry == null) {
                    sender.sendMessage("<red>未找到备份: $input".toComponent())
                    return@execute
                }
                sender.sendMessage("".toComponent())
                sender.sendMessage("<gold>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
                sender.sendMessage("<gold>┃ <yellow>备份文件信息".toComponent())
                sender.sendMessage("<gold>┃".toComponent())
                sender.sendMessage("<gold>┃ <gray>文件: <white>${entry.file.name}".toComponent())
                sender.sendMessage("<gold>┃ <gray>时间: <white>${
                    entry.time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }".toComponent())
                sender.sendMessage("<gold>┃ <gray>大小: <white>${entry.file.length() / 1024} KB".toComponent())
                sender.sendMessage("<gold>┃".toComponent())
                sender.sendMessage("<gold>┃ <green>恢复步骤:".toComponent())
                sender.sendMessage("<gold>┃ <gray>1. 手动解压 ${entry.file.name} 得到同名 .json 文件".toComponent())
                sender.sendMessage("<gold>┃ <gray>2. 将 .json 文件放入 restore 目录".toComponent())
                sender.sendMessage("<gold>┃ <gray>3. 执行 <white>/backup restore ${entry.label}".toComponent())
                sender.sendMessage("<gold>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
            }
        }
    }

    @CommandBody
    val restore = subCommand {
        dynamic("filename") {
            execute<CommandSender> { sender, context, _ ->
                restoreBackup(sender, context["filename"], null)
            }
            player("player") {
                execute<CommandSender> { sender, context, _ ->
                    restoreBackup(sender, context["filename"], context["player"])
                }
            }
        }
    }

    // ==================== 列表 ====================

    private fun listBackups(sender: CommandSender) {
        val entries = listBackupFiles()
        if (entries.isEmpty()) {
            sender.sendMessage("<red>暂无备份文件".toComponent())
            return
        }

        sender.sendMessage("".toComponent())
        sender.sendMessage("<gold>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
        sender.sendMessage("<gold>┃ <yellow>玩家数据备份列表</yellow> <gray>(${entries.size} 个)</gray>".toComponent())
        sender.sendMessage("<gold>┃".toComponent())
        sender.sendMessage("<gold>┃ <gray>/backup info <文件名> 查看详情与恢复步骤".toComponent())
        sender.sendMessage("<gold>┃".toComponent())

        entries.forEach { entry ->
            val size = when {
                entry.file.length() < 1024 -> "${entry.file.length()} B"
                entry.file.length() < 1024 * 1024 -> "${entry.file.length() / 1024} KB"
                else -> "${"%.1f".format(entry.file.length() / (1024.0 * 1024.0))} MB"
            }
            val timeStr = entry.time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            sender.sendMessage("<gold>┃ <white>$timeStr <gray>$size <yellow>${entry.label}".toComponent())
        }
        sender.sendMessage("<gold>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
    }

    // ==================== 还原 ====================

    private fun restoreBackup(sender: CommandSender, filename: String, targetName: String?) {
        val file = File(restoreDir, "$filename.json")
        if (!file.exists()) {
            sender.sendMessage("<red>未找到还原文件: $filename.json".toComponent())
            sender.sendMessage("<red>请先将解压后的 JSON 文件放入 restore 目录".toComponent())
            return
        }

        try {
            val json = file.readText(Charsets.UTF_8)
            val root = jsonUtils.fromJson(json, com.google.gson.JsonObject::class.java)

            val backupTime = root.get("backupTime")?.asLong ?: 0L
            val playersObj = root.getAsJsonObject("players")
            if (playersObj == null) {
                sender.sendMessage("<red>备份文件格式错误: 缺少 players 字段".toComponent())
                return
            }

            val timeStr = if (backupTime > 0) {
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(backupTime),
                    ZoneId.systemDefault()
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            } else "未知"

            val entries = playersObj.entrySet().toList()
            if (entries.isEmpty()) {
                sender.sendMessage("<red>备份文件中没有玩家数据".toComponent())
                return
            }

            sender.sendMessage("<gray>开始从备份 ($timeStr) 恢复数据...".toComponent())

            var restored = 0
            var skipped = 0
            var kicked = 0

            for ((i, entry) in entries.withIndex()) {
                val uuid = entry.key
                val element = entry.value

                // 解析 PlayerData
                val playerData: PlayerData = try {
                    jsonUtils.fromJson(element, PlayerData::class.java)
                } catch (e: Exception) {
                    sender.sendMessage("<red>  跳过 $uuid: 数据解析失败 (${e.message})".toComponent())
                    skipped++
                    continue
                }

                // 如果指定了玩家，只还原匹配的
                if (targetName != null) {
                    val target = Bukkit.getPlayerExact(targetName)
                    val targetUuid = target?.uniqueId?.toString()
                    if (uuid != targetUuid && playerData.playerName != targetName) {
                        continue
                    }
                }

                // 踢掉在线的玩家，确保下次登录重新加载
                val online = Bukkit.getPlayerExact(playerData.playerName)
                if (online != null && online.isOnline) {
                    online.kick(Component.text("§c管理员正在恢复你的数据，请重新加入"))
                    kicked++
                }

                // 清理内存缓存
                ProfileManager.releaseAll(uuid)
                playerDataMap.remove(uuid)

                // 写入数据库: 共享数据
                val shared = ProfileManager.getSharedData(uuid)
                shared.tape = playerData.tape
                shared.goldentape = playerData.goldentape
                shared.achievements = playerData.achievements
                shared.cosmetics = playerData.cosmetics
                ProfileManager.saveSharedData(uuid)

                // 写入数据库: profile 数据
                val stripped = ProfileManager.stripSharedFields(playerData)
                ProfileManager.saveProfileData(uuid, playerData.profileId, stripped)

                // 如果 PlayerData 里有 attribute modifiers，也恢复
                if (playerData.attributeModifiers != null) {
                    val uuidObj = java.util.UUID.fromString(uuid)
                    val attrData = io.github.zzzyyylllty.chotentech.manager.AttributeManager.getPlayerPersistentData(uuidObj)
                    attrData.modifiers = playerData.attributeModifiers!!
                    io.github.zzzyyylllty.chotentech.manager.AttributeManager.savePlayerData(attrData)
                }

                restored++
                sender.sendMessage("<green>  §8[$i]<green>已恢复 <yellow>${playerData.playerName} §8($uuid) §7- profile ${playerData.profileId}".toComponent())
            }

            sender.sendMessage("".toComponent())
            sender.sendMessage("<green>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
            sender.sendMessage("<green>┃ <yellow>还原完成".toComponent())
            sender.sendMessage("<green>┃ <gray>来源: <white>$timeStr".toComponent())
            sender.sendMessage("<green>┃ <gray>成功: <green>$restored".toComponent())
            if (skipped > 0) sender.sendMessage("<green>┃ <gray>跳过: <red>$skipped".toComponent())
            if (kicked > 0) sender.sendMessage("<green>┃ <gray>踢出: <yellow>$kicked 人".toComponent())
            sender.sendMessage("<green>┃".toComponent())
            sender.sendMessage("<green>┃ <gray>被踢出的玩家重新加入后自动加载恢复的数据".toComponent())
            sender.sendMessage("<green>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())

        } catch (e: Exception) {
            sender.sendMessage("<red>还原失败: ${e.message}".toComponent())
            e.printStackTrace()
        }
    }

    // ==================== 工具 ====================

    private fun listBackupFiles(): List<BackupEntry> {
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()
            ?.mapNotNull { file ->
                val match = filenameRegex.matchEntire(file.name) ?: return@mapNotNull null
                val ldt = try {
                    LocalDateTime.parse(match.groupValues[1], filenameFormatter)
                } catch (_: Exception) { null } ?: return@mapNotNull null
                BackupEntry(
                    file = file,
                    label = match.groupValues[1],
                    time = ldt
                )
            }
            ?.sortedByDescending { it.time }
            ?: emptyList()
    }
}

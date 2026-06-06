package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.int
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.function.submitAsync

@CommandHeader(
    name = "chotentech-mute",
    aliases = ["cttmute", "mute"],
    permission = "chotentech.command.mute",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object MuteCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved
                    val targetPlayer = getOnlinePlayerByName(targetName)

                    val parsed = parseMuteArgs(argument, PunishManager.muteReasons)
                    var durationMs = parsed.durationMs
                    var reason = parsed.reason

                    if (durationMs <= 0L && reason.isNotBlank()) {
                        val matchedReason = PunishManager.muteReasons.entries.firstOrNull {
                            reason.startsWith(it.key, ignoreCase = true)
                        }
                        if (matchedReason != null) {
                            durationMs = matchedReason.value
                            val afterReason = reason.substring(matchedReason.key.length).trim()
                            if (afterReason.isNotBlank()) reason = afterReason
                        }
                    }
                    if (durationMs <= 0L) durationMs = 600_000L

                    val maxMs = PunishManager.getMaxDuration(sender)
                    if (maxMs != -1L && durationMs > maxMs) {
                        durationMs = maxMs
                        sender.sendComponent("<yellow>你的权限最大允许 ${formatDurationMs(maxMs)} 的禁言。已按最大时长执行。")
                    }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val entry = PunishEntry(
                        targetUuid = targetUuid,
                        targetName = targetName,
                        operatorUuid = operatorUuid,
                        operatorName = operatorName,
                        type = PunishType.MUTE,
                        reason = reason,
                        durationMs = durationMs,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1,
                        silent = parsed.silent
                    )
                    val result = PunishManager.issue(entry)

                    if (!parsed.silent && targetPlayer != null) {
                        targetPlayer.sendComponent("<red>你已被禁言！时长: ${formatDurationMs(durationMs)}，理由: $reason")
                    }

                    val durationStr = if (durationMs == -1L) "永久" else formatDurationMs(durationMs)
                    sender.sendComponent("<green>已禁言 <yellow>$targetName<green>，时长: <yellow>$durationStr<green>，ID: <yellow>${result.id}")
                    if (!parsed.silent) {
                        Bukkit.broadcast("<yellow>$targetName <red>已被管理员 <yellow>$operatorName <red>禁言，时长: <yellow>$durationStr".toComponent())
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-kick",
    aliases = ["cttkick", "kick"],
    permission = "chotentech.command.kick",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object KickCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved
                    val targetPlayer = getOnlinePlayerByName(targetName)

                    val parsed = parseKickArgs(argument)
                    val reason = parsed.reason.ifBlank { "你已被踢出服务器" }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val entry = PunishEntry(
                        targetUuid = targetUuid,
                        targetName = targetName,
                        operatorUuid = operatorUuid,
                        operatorName = operatorName,
                        type = PunishType.KICK,
                        reason = reason,
                        durationMs = 0L,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1,
                        silent = parsed.silent
                    )
                    PunishManager.issue(entry)

                    if (targetPlayer != null) {
                        targetPlayer.kick(net.kyori.adventure.text.Component.text(reason))
                    }

                    sender.sendComponent("<green>已踢出 <yellow>$targetName<green>，理由: $reason")
                    if (!parsed.silent) {
                        Bukkit.broadcast("<yellow>$targetName <red>已被管理员 <yellow>$operatorName <red>踢出服务器".toComponent())
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-warn",
    aliases = ["cttwarn", "warn"],
    permission = "chotentech.command.warn",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object WarnCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved
                    val targetPlayer = getOnlinePlayerByName(targetName)

                    val parsed = parseWarnArgs(argument)
                    val reason = parsed.reason.ifBlank { "违反服务器规则" }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val entry = PunishEntry(
                        targetUuid = targetUuid,
                        targetName = targetName,
                        operatorUuid = operatorUuid,
                        operatorName = operatorName,
                        type = PunishType.WARN,
                        reason = reason,
                        durationMs = 0L,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1
                    )
                    val result = PunishManager.issue(entry)
                    val warnCount = PunishManager.getWarnCount(targetUuid)

                    sender.sendComponent("<green>已警告 <yellow>$targetName<green>，累计警告: <yellow>$warnCount<green>，ID: <yellow>${result.id}")
                    if (targetPlayer != null) {
                        targetPlayer.sendComponent("<red>你已被警告！理由: $reason (累计 $warnCount 次)")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-ban",
    aliases = ["cttban", "ban"],
    permission = "chotentech.command.ban",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object BanCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved
                    val targetPlayer = getOnlinePlayerByName(targetName)

                    val parsed = parseBanArgs(argument)
                    var durationMs = parsed.durationMs
                    val reason = parsed.reason.ifBlank { "违反服务器规则" }

                    if (durationMs <= 0L) durationMs = -1L

                    val maxMs = PunishManager.getMaxDuration(sender)
                    if (maxMs != -1L && durationMs != -1L && durationMs > maxMs) {
                        durationMs = maxMs
                        sender.sendComponent("<yellow>你的权限最大允许 ${formatDurationMs(maxMs)} 的封禁。已按最大时长执行。")
                    }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val meta = mutableMapOf<String, String>()
                    if (parsed.skipAuth) meta["skipAuth"] = "true"

                    val entry = PunishEntry(
                        targetUuid = targetUuid,
                        targetName = targetName,
                        operatorUuid = operatorUuid,
                        operatorName = operatorName,
                        type = PunishType.BAN,
                        reason = reason,
                        durationMs = durationMs,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1,
                        silent = parsed.silent,
                        meta = meta
                    )
                    val result = PunishManager.issue(entry)

                    val durationStr = if (durationMs == -1L) "永久" else formatDurationMs(durationMs)
                    val authNote = if (!parsed.skipAuth) " (附带AUTH验证)" else ""
                    sender.sendComponent("<green>已封禁 <yellow>$targetName<green>，时长: <yellow>$durationStr<green>$authNote，ID: <yellow>${result.id}")

                    if (targetPlayer != null) {
                        val kickMsg = buildString {
                            append("你已被封禁！\n")
                            append("理由: $reason\n")
                            append("时长: $durationStr")
                            if (!parsed.skipAuth) append("\n请主动联系管理员解除AUTH验证")
                        }
                        targetPlayer.kick(net.kyori.adventure.text.Component.text(kickMsg))
                    }

                    if (!parsed.silent) {
                        Bukkit.broadcast("<yellow>$targetName <red>已被管理员 <yellow>$operatorName <red>封禁，时长: <yellow>$durationStr".toComponent())
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-authpunish",
    aliases = ["cttauth", "authpunish"],
    permission = "chotentech.command.authpunish",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object AuthPunishCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved
                    val targetPlayer = getOnlinePlayerByName(targetName)

                    val parsed = parseAuthArgs(argument)
                    val durationMs = parsed.durationMs
                    val reason = parsed.reason.ifBlank { "需要进行身份验证" }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val entry = PunishEntry(
                        targetUuid = targetUuid,
                        targetName = targetName,
                        operatorUuid = operatorUuid,
                        operatorName = operatorName,
                        type = PunishType.AUTH,
                        reason = reason,
                        durationMs = durationMs,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1,
                        silent = parsed.silent
                    )
                    val result = PunishManager.issue(entry)

                    val durationStr = if (durationMs == -1L) "永久" else formatDurationMs(durationMs)
                    sender.sendComponent("<green>已对 <yellow>$targetName <green>添加AUTH验证，时长: <yellow>$durationStr<green>，ID: <yellow>${result.id}")

                    if (targetPlayer != null) {
                        targetPlayer.kick(net.kyori.adventure.text.Component.text("你被要求进行身份验证！\n理由: $reason\n时长: $durationStr\n请主动联系管理员解除"))
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-wipe",
    aliases = ["cttwipe", "wipe"],
    permission = "chotentech.command.wipe",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object WipeCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved
                    val targetPlayer = getOnlinePlayerByName(targetName)

                    val parsed = parseWipeArgs(argument)
                    val scope = parsed.scope
                    if (scope == null) {
                        sender.sendComponent("<red>请指定清除范围: all / inventory / 货币ID(如MEG,BNTG)")
                        sender.sendComponent("<red>用法: /wipe <玩家> <作用域> [理由]")
                        return@submitAsync
                    }
                    val reason = parsed.reason.ifBlank { "违反服务器规则" }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val snapshot = if (targetPlayer != null) {
                        PunishManager.captureWipeSnapshot(targetPlayer, scope)
                    } else {
                        WipeSnapshot()
                    }

                    val entry = PunishEntry(
                        targetUuid = targetUuid,
                        targetName = targetName,
                        operatorUuid = operatorUuid,
                        operatorName = operatorName,
                        type = PunishType.WIPE,
                        reason = reason,
                        durationMs = 0L,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1,
                        meta = mapOf("scope" to scope)
                    )
                    val result = PunishManager.issue(entry)

                    val backupEntry = WipeBackupEntry(
                        punishmentId = result.id,
                        targetUuid = targetUuid,
                        targetName = targetName,
                        scope = scope,
                        dataJson = io.github.zzzyyylllty.chotentech.util.jsonUtils.toJson(snapshot, WipeSnapshot::class.java),
                        backupTime = System.currentTimeMillis()
                    )
                    PunishManager.insertWipeBackup(backupEntry)

                    if (targetPlayer != null) {
                        PunishManager.clearWipedData(targetPlayer, scope)
                        targetPlayer.sendComponent("<red>你的数据已被管理员清除！范围: $scope，理由: $reason")
                    } else {
                        sender.sendComponent("<yellow>玩家离线，物品栏数据将在下次上线时清除。备份已保存。")
                    }

                    sender.sendComponent("<green>已清除 <yellow>$targetName <green>的数据，范围: <yellow>$scope<green>，ID: <yellow>${result.id}")
                    sender.sendComponent("<green>备份已保存。可使用 <yellow>/resumewipe ${result.id} <green>还原")
                }
            }
            suggestion<CommandSender> { _, _ ->
                val base = listOf("all", "inventory", "MEG", "BNTG", "UEC")
                Bukkit.getOnlinePlayers().map { it.name } + base
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-resumewipe",
    aliases = ["cttresumewipe", "resumewipe"],
    permission = "chotentech.command.resumewipe",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ResumeWipeCommand {
    @CommandBody
    val main = mainCommand {
        int("punishment_id") {
            execute<CommandSender> { sender, context, _ ->
                submitAsync {
                    val punishId = context["punishment_id"]?.toLongOrNull()
                    if (punishId == null || punishId <= 0) {
                        sender.sendComponent("<red>请输入有效的处罚ID")
                        return@submitAsync
                    }

                    val operatorUuid = if (sender is Player) sender.uniqueId.toString() else null
                    val operatorName = if (sender is ConsoleCommandSender) "CONSOLE" else sender.name

                    val success = PunishManager.restoreWipe(punishId, operatorName, operatorUuid)
                    if (success) {
                        sender.sendComponent("<green>已还原处罚 ID: <yellow>$punishId <green>的数据")
                    } else {
                        sender.sendComponent("<red>未找到处罚 ID: $punishId 的备份数据，或还原失败")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                emptyList()
            }
        }
    }
}

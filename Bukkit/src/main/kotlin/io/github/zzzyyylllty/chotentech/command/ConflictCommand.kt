package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import io.github.zzzyyylllty.chotentech.function.ConflictManager
import io.github.zzzyyylllty.chotentech.function.ConflictStatus
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.gui.ConflictMenu
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-conflict",
    aliases = ["cttconflict", "conflict"],
    permission = "chotentech.command.conflict",
    description = "Conflict management for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ConflictCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val start = subCommand {
        player("target") {
            dynamic("configId") {
                execute<CommandSender> { sender, context, _ ->
                    val target = context.player("target").castSafely<Player>()!!
                    val configId = context["configId"]
                    val config = conflictConfigs[configId]
                    if (config == null) {
                        sender.sendComponent("<red>未找到冲突配置: $configId")
                        return@execute
                    }
                    val conflict = ConflictManager.startConflict(target, configId) ?: run {
                        sender.sendComponent("<red>无法为 ${target.name} 创建冲突实例")
                        return@execute
                    }
                    sender.sendComponent("<green>已为 <yellow>${target.name}</yellow> 创建冲突: ${config.name}")
                    sender.sendComponent("<gray>实例ID: ${conflict.instanceId} | 状态: ${conflict.status}")
                    sender.sendComponent("<gray>使用 /conflict activate ${target.name} 将其激活")
                }
                suggestion<CommandSender> { _, _ ->
                    conflictConfigs.keys.toList()
                }
            }
        }
    }

    @CommandBody
    val activate = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                val uuid = target.uniqueId.toString()
                if (ConflictManager.activateConflict(uuid)) {
                    val c = ConflictManager.getConflict(uuid)
                    sender.sendComponent("<green>已激活 <yellow>${target.name}</yellow> 的冲突")
                    if (c != null) {
                        sender.sendComponent("<gray>配置: ${c.name} | 状态: ${c.status}")
                    }
                } else {
                    sender.sendComponent("<red>无法激活 ${target.name} 的冲突，可能不存在或已开始")
                }
            }
        }
    }

    @CommandBody
    val setlevel = subCommand {
        player("target") {
            dynamic("faction") {
                suggestion<CommandSender> { sender, context ->
                    val target = context.player("target").castSafely<Player>()
                    if (target != null) {
                        val conflict = ConflictManager.getConflict(target.uniqueId.toString())
                        val config = conflict?.let { conflictConfigs[it.configId] }
                        config?.scores?.flatMap { it.value.changes.keys }?.distinct() ?: emptyList()
                    } else {
                        conflictConfigs.values.flatMap { it.scores.flatMap { e -> e.value.changes.keys } }.distinct()
                    }
                }
                dynamic("level") {
                    execute<CommandSender> { sender, context, _ ->
                        val target = context.player("target").castSafely<Player>()!!
                        val faction = context["faction"]
                        val levelName = context["level"].uppercase()
                        val level = runCatching { ResultLevel.valueOf(levelName) }.getOrNull()
                        if (level == null) {
                            val names = ResultLevel.entries.joinToString(" ") { "${it.displayName}(${it.name})" }
                            sender.sendComponent("<red>未知结果等级，可用: $names")
                            return@execute
                        }
                        val uuid = target.uniqueId.toString()
                        if (ConflictManager.setFactionLevel(uuid, faction, level)) {
                            sender.sendComponent("<green>已将 <yellow>${target.name}</yellow> 的阵营 <aqua>$faction</aqua> 结果设为 ${level.displayColor}${level.displayName}")
                        } else {
                            sender.sendComponent("<red>设定失败，${target.name} 没有进行中的冲突")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("FULL_SUCCESS", "SUCCESS", "NEUTRAL", "FAIL", "FULL_FAIL")
                    }
                }
            }
        }
    }

    @CommandBody
    val end = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                val uuid = target.uniqueId.toString()
                val changes = ConflictManager.endConflict(uuid)
                if (changes == null) {
                    sender.sendComponent("<red>无法结束 ${target.name} 的冲突，可能不存在、已结束、或未设置结果等级")
                    return@execute
                }
                val c = ConflictManager.getConflict(uuid)
                val factionDisplay = c?.factionLevels?.entries?.joinToString("<gray>, ") { (f, l) -> "${l.displayColor}$f:${l.displayName}" } ?: "<gray>无结果等级"
                sender.sendComponent("<green><yellow>${target.name}</yellow> 的冲突已结束: ${c?.name ?: ""}")
                sender.sendComponent("<gray>结果: $factionDisplay")
                sender.sendComponent("<gold>声望变更:")
                for (change in changes) {
                    val prefix = if (change.change >= 0) "+" else ""
                    val changeColor = if (change.change >= 0) "<green>" else "<red>"
                    sender.sendComponent("<gray>  ${change.faction}: $changeColor$prefix${change.change}")
                }
            }
        }
    }

    @CommandBody
    val cancel = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                val uuid = target.uniqueId.toString()
                if (ConflictManager.cancelConflict(uuid)) {
                    sender.sendComponent("<red>已取消 <yellow>${target.name}</yellow> 的冲突，不应用声望变更")
                } else {
                    sender.sendComponent("<red>无法取消 ${target.name} 的冲突，可能不存在或已结束")
                }
            }
        }
    }

    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val active = ConflictManager.getActiveConflicts()
            if (active.isEmpty()) {
                sender.sendComponent("<gray>当前没有正在进行的冲突")
                return@execute
            }
            sender.sendComponent("<gold>=== 正在进行的冲突 (${active.size}) ===")
            for (c in active) {
                val levelStr = if (c.factionLevels.isNotEmpty()) {
                    c.factionLevels.entries.joinToString("<gray>, ") { (f, l) -> "${l.displayColor}$f:${l.displayName}" }
                } else when (c.status) {
                    ConflictStatus.PENDING -> "<yellow>未开始"
                    ConflictStatus.ACTIVE -> "<aqua>进行中"
                    else -> "<gray>无结果"
                }
                val statusColor = when (c.status) {
                    ConflictStatus.PENDING -> "<yellow>"
                    ConflictStatus.ACTIVE -> "<aqua>"
                    else -> "<gray>"
                }
                sender.sendComponent("<yellow>${c.playerName} <gray>| <green>${c.name} <gray>| ${statusColor}${c.status} <gray>| $levelStr")
            }
        }
    }

    @CommandBody
    val info = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                val uuid = target.uniqueId.toString()
                val c = ConflictManager.getConflict(uuid)
                if (c == null) {
                    sender.sendComponent("<red>${target.name} 没有进行中的冲突")
                    return@execute
                }
                sender.sendComponent("<gold>=== 冲突信息: ${c.instanceId} ===")
                sender.sendComponent("<gray>玩家: <white>${c.playerName}")
                sender.sendComponent("<gray>配置: <white>${c.name}")
                sender.sendComponent("<gray>配置ID: <white>${c.configId}")
                sender.sendComponent("<gray>状态: <aqua>${c.status}")
                if (c.factionLevels.isNotEmpty()) {
                    sender.sendComponent("<gray>阵营结果:")
                    for ((faction, level) in c.factionLevels) {
                        sender.sendComponent("<gray>  $faction: ${level.displayColor}${level.displayName}")
                    }
                } else {
                    val statusDisplay = when (c.status) {
                        ConflictStatus.PENDING -> "<yellow>未开始"
                        ConflictStatus.ACTIVE -> "<aqua>进行中"
                        else -> "<gray>无结果"
                    }
                    sender.sendComponent("<gray>结果: $statusDisplay")
                }
                sender.sendComponent("<gray>开始时间: <white>${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(c.startTime))}")
                if (c.endTime != null) {
                    sender.sendComponent("<gray>结束时间: <white>${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(c.endTime!!))}")
                }
            }
        }
    }

    @CommandBody
    val menu = subCommand {
        execute<Player> { sender, _, _ ->
            ConflictMenu().open(sender)
        }
    }

    @CommandBody
    val apply = subCommand {
        player("target") {
            dynamic("conflictId") {
                dynamic("level") {
                    execute<CommandSender> { sender, context, _ ->
                        val target = context.player("target").castSafely<Player>()!!
                        val conflictId = context["conflictId"]
                        val levelName = context["level"].uppercase()
                        val level = runCatching { ResultLevel.valueOf(levelName) }.getOrNull()
                        if (level == null) {
                            val names = ResultLevel.entries.joinToString(" ") { "${it.displayName}(${it.name})" }
                            sender.sendComponent("<red>未知结果等级，可用: $names")
                            return@execute
                        }
                        val config = conflictConfigs[conflictId]
                        if (config == null) {
                            sender.sendComponent("<red>未找到冲突配置: $conflictId")
                            return@execute
                        }
                        val changes = ReputationManager.applyConflictReputation(target, conflictId, level)
                        if (changes.isEmpty()) {
                            sender.sendComponent("<red>该结果等级未配置声望变化")
                            return@execute
                        }
                        val levelDisplay = "${level.displayColor}${level.displayName}"
                        sender.sendComponent("<gold>已为 ${target.name} 应用冲突 ${config.name} $levelDisplay <gold>声望变更:")
                        for (change in changes) {
                            val prefix = if (change.change >= 0) "+" else ""
                            val changeColor = if (change.change >= 0) "<green>" else "<red>"
                            sender.sendComponent("<gray>  ${change.faction}: $changeColor$prefix${change.change}")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("FULL_SUCCESS", "SUCCESS", "NEUTRAL", "FAIL", "FULL_FAIL")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                conflictConfigs.keys.toList()
            }
        }
    }
}

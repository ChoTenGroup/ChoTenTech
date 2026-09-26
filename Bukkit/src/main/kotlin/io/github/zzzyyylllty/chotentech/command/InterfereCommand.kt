package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.interfereConfigs
import io.github.zzzyyylllty.chotentech.function.InterfereManager
import io.github.zzzyyylllty.chotentech.function.InterfereStatus
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.gui.InterfereMenu
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
    name = "chotentech-interfere",
    aliases = ["cttinterfere", "interfere"],
    permission = "chotentech.command.interfere",
    description = "Interfere management for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object InterfereCommand {

    private val LEVEL_SUGGESTIONS = listOf("FULL_SUCCESS", "SUCCESS", "NEUTRAL", "FAIL", "FULL_FAIL")

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
                    val config = interfereConfigs[configId]
                    if (config == null) {
                        sender.sendComponent("<#E08A98>未找到干扰配置: $configId")
                        return@execute
                    }
                    val interfere = InterfereManager.startInterfere(target, configId) ?: run {
                        sender.sendComponent("<#E08A98>无法为 ${target.name} 创建干扰实例")
                        return@execute
                    }
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${target.name}</#EFDF8F> 创建干扰: ${config.name}")
                    sender.sendComponent("<#A9B4C3>实例ID: <#F2F5FA>${interfere.instanceId} <#A9B4C3>| 状态: ${interfere.status}")
                    sender.sendComponent("<#A9B4C3>使用 /interfere activate ${interfere.instanceId} 将其激活")
                }
                suggestion<CommandSender> { _, _ ->
                    interfereConfigs.keys.toList()
                }
            }
        }
    }

    @CommandBody
    val activate = subCommand {
        dynamic("instanceId") {
            suggestion<CommandSender> { _, _ ->
                InterfereManager.getActiveInterferes().map { it.instanceId }
            }
            execute<CommandSender> { sender, context, _ ->
                val instanceId = context["instanceId"]
                val interfere = InterfereManager.getInterfere(instanceId)
                if (interfere == null) {
                    sender.sendComponent("<#E08A98>未找到干涉实例: $instanceId")
                    return@execute
                }
                if (InterfereManager.activateInterfere(instanceId)) {
                    sender.sendComponent("<#8FD4A6>已激活 <#EFDF8F>${interfere.playerName}</#EFDF8F> 的干涉: ${interfere.name}")
                    sender.sendComponent("<#A9B4C3>实例ID: <#F2F5FA>${interfere.instanceId} <#A9B4C3>| 状态: <#84D1CB>进行中")
                } else {
                    sender.sendComponent("<#E08A98>无法激活 $instanceId，可能已开始或已结束")
                }
            }
        }
    }

    @CommandBody
    val setlevel = subCommand {
        dynamic("instanceId") {
            suggestion<CommandSender> { _, _ ->
                InterfereManager.getActiveInterferes().map { it.instanceId }
            }
            dynamic("faction") {
                suggestion<CommandSender> { _, context ->
                    val interfere = InterfereManager.getInterfere(context["instanceId"])
                    val config = interfere?.let { interfereConfigs[it.configId] }
                    config?.scores?.flatMap { it.value.changes.keys }?.distinct()
                        ?: interfereConfigs.values.flatMap { c -> c.scores.flatMap { e -> e.value.changes.keys } }.distinct()
                }
                dynamic("level") {
                    execute<CommandSender> { sender, context, _ ->
                        val instanceId = context["instanceId"]
                        val faction = context["faction"]
                        val level = runCatching { ResultLevel.valueOf(context["level"].uppercase()) }.getOrNull()
                        if (level == null) {
                            val names = ResultLevel.entries.joinToString(" ") { "${it.displayName}(${it.name})" }
                            sender.sendComponent("<#E08A98>未知结果等级，可用: $names")
                            return@execute
                        }
                        val interfere = InterfereManager.getInterfere(instanceId)
                        if (interfere == null) {
                            sender.sendComponent("<#E08A98>未找到干涉实例: $instanceId")
                            return@execute
                        }
                        if (InterfereManager.setFactionLevel(instanceId, faction, level)) {
                            sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${interfere.playerName}</#EFDF8F> 的阵营 <#84D1CB>$faction</#84D1CB> 结果设为 ${level.displayColor}${level.displayName}")
                        } else {
                            sender.sendComponent("<#E08A98>设定失败，干涉实例已结束")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        LEVEL_SUGGESTIONS
                    }
                }
            }
        }
    }

    @CommandBody
    val end = subCommand {
        dynamic("instanceId") {
            suggestion<CommandSender> { _, _ ->
                InterfereManager.getActiveInterferes().map { it.instanceId }
            }
            execute<CommandSender> { sender, context, _ ->
                val instanceId = context["instanceId"]
                val before = InterfereManager.getInterfere(instanceId)
                if (before == null) {
                    sender.sendComponent("<#E08A98>未找到干涉实例: $instanceId")
                    return@execute
                }
                if (before.status == InterfereStatus.FINISHED) {
                    sender.sendComponent("<#E08A98>$instanceId 已经结束了")
                    return@execute
                }
                val changes = InterfereManager.endInterfere(instanceId) ?: run {
                    sender.sendComponent("<#E08A98>结算失败，$instanceId 仍未结束（需玩家在线、已设置结果等级且配置存在）")
                    return@execute
                }
                val c = InterfereManager.getInterfere(instanceId) ?: before
                val factionDisplay = c.factionLevels.entries.joinToString("<#A9B4C3>, ") { (f, l) -> "${l.displayColor}$f:${l.displayName}" }
                sender.sendComponent("<#8FD4A6><#EFDF8F>${c.playerName}</#EFDF8F> 的干涉已结束: ${c.name}")
                sender.sendComponent("<#A9B4C3>结果: $factionDisplay")
                sender.sendComponent("<#DFBD7C>声望变更:")
                for (change in changes) {
                    val prefix = if (change.change >= 0) "+" else ""
                    val changeColor = if (change.change >= 0) "<#8FD4A6>" else "<#E08A98>"
                    sender.sendComponent("<#A9B4C3>  ${change.faction}: $changeColor$prefix${change.change}")
                }
            }
        }
    }

    @CommandBody
    val cancel = subCommand {
        dynamic("instanceId") {
            suggestion<CommandSender> { _, _ ->
                InterfereManager.getActiveInterferes().map { it.instanceId }
            }
            execute<CommandSender> { sender, context, _ ->
                val instanceId = context["instanceId"]
                val interfere = InterfereManager.getInterfere(instanceId)
                if (interfere == null) {
                    sender.sendComponent("<#E08A98>未找到干涉实例: $instanceId")
                    return@execute
                }
                if (InterfereManager.cancelInterfere(instanceId)) {
                    sender.sendComponent("<#E08A98>已取消 <#EFDF8F>${interfere.playerName}</#EFDF8F> 的干涉 ${interfere.name}，不应用声望变更")
                } else {
                    sender.sendComponent("<#E08A98>无法取消 $instanceId，可能已结束")
                }
            }
        }
    }

    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sendList(sender, null, null)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                sendList(sender, target.uniqueId.toString(), target.name)
            }
        }
    }

    @CommandBody
    val info = subCommand {
        dynamic("instanceId") {
            suggestion<CommandSender> { _, _ ->
                InterfereManager.getAllInterferes().map { it.instanceId }
            }
            execute<CommandSender> { sender, context, _ ->
                val instanceId = context["instanceId"]
                val c = InterfereManager.getInterfere(instanceId)
                if (c == null) {
                    sender.sendComponent("<#E08A98>未找到干涉实例: $instanceId")
                    return@execute
                }
                sender.sendComponent("<#DFBD7C>=== 干扰信息: ${c.instanceId} ===")
                sender.sendComponent("<#A9B4C3>玩家: <#F2F5FA>${c.playerName}")
                sender.sendComponent("<#A9B4C3>配置: <#F2F5FA>${c.name}")
                sender.sendComponent("<#A9B4C3>配置ID: <#F2F5FA>${c.configId}")
                sender.sendComponent("<#A9B4C3>状态: <#84D1CB>${c.status}")
                if (c.factionLevels.isNotEmpty()) {
                    sender.sendComponent("<#A9B4C3>阵营结果:")
                    for ((faction, level) in c.factionLevels) {
                        sender.sendComponent("<#A9B4C3>  $faction: ${level.displayColor}${level.displayName}")
                    }
                } else {
                    val statusDisplay = when (c.status) {
                        InterfereStatus.PENDING -> "<#EFDF8F>未开始"
                        InterfereStatus.ACTIVE -> "<#84D1CB>进行中"
                        else -> "<#A9B4C3>无结果"
                    }
                    sender.sendComponent("<#A9B4C3>结果: $statusDisplay")
                }
                sender.sendComponent("<#A9B4C3>开始时间: <#F2F5FA>${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(c.startTime))}")
                if (c.endTime != null) {
                    sender.sendComponent("<#A9B4C3>结束时间: <#F2F5FA>${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(c.endTime!!))}")
                }
            }
        }
    }

    @CommandBody
    val menu = subCommand {
        execute<Player> { sender, _, _ ->
            InterfereMenu().open(sender)
        }
    }

    @CommandBody
    val apply = subCommand {
        player("target") {
            dynamic("interfereId") {
                dynamic("level") {
                    execute<CommandSender> { sender, context, _ ->
                        val target = context.player("target").castSafely<Player>()!!
                        val interfereId = context["interfereId"]
                        val levelName = context["level"].uppercase()
                        val level = runCatching { ResultLevel.valueOf(levelName) }.getOrNull()
                        if (level == null) {
                            val names = ResultLevel.entries.joinToString(" ") { "${it.displayName}(${it.name})" }
                            sender.sendComponent("<#E08A98>未知结果等级，可用: $names")
                            return@execute
                        }
                        val config = interfereConfigs[interfereId]
                        if (config == null) {
                            sender.sendComponent("<#E08A98>未找到干扰配置: $interfereId")
                            return@execute
                        }
                        val changes = ReputationManager.applyInterfereReputation(target, interfereId, level)
                        if (changes.isEmpty()) {
                            sender.sendComponent("<#E08A98>该结果等级未配置声望变化")
                            return@execute
                        }
                        val levelDisplay = "${level.displayColor}${level.displayName}"
                        sender.sendComponent("<#DFBD7C>已为 ${target.name} 应用干扰 ${config.name} $levelDisplay <#DFBD7C>声望变更:")
                        for (change in changes) {
                            val prefix = if (change.change >= 0) "+" else ""
                            val changeColor = if (change.change >= 0) "<#8FD4A6>" else "<#E08A98>"
                            sender.sendComponent("<#A9B4C3>  ${change.faction}: $changeColor$prefix${change.change}")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        LEVEL_SUGGESTIONS
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                interfereConfigs.keys.toList()
            }
        }
    }

    /** 列出未结束的干涉实例；[filterUuid] 非空时只列出该玩家的 */
    private fun sendList(sender: CommandSender, filterUuid: String?, filterName: String?) {
        val active = if (filterUuid != null) {
            InterfereManager.getActiveInterferes(filterUuid)
        } else {
            InterfereManager.getActiveInterferes()
        }
        val who = filterName?.let { "$it 的" } ?: ""
        if (active.isEmpty()) {
            sender.sendComponent("<#A9B4C3>当前没有${who}正在进行的干扰")
            return
        }
        sender.sendComponent("<#DFBD7C>=== ${who}正在进行的干扰 (${active.size}) ===")
        for (c in active) {
            val levelStr = if (c.factionLevels.isNotEmpty()) {
                c.factionLevels.entries.joinToString("<#A9B4C3>, ") { (f, l) -> "${l.displayColor}$f:${l.displayName}" }
            } else when (c.status) {
                InterfereStatus.PENDING -> "<#EFDF8F>未开始"
                InterfereStatus.ACTIVE -> "<#84D1CB>进行中"
                else -> "<#A9B4C3>无结果"
            }
            val statusColor = when (c.status) {
                InterfereStatus.PENDING -> "<#EFDF8F>"
                InterfereStatus.ACTIVE -> "<#84D1CB>"
                else -> "<#A9B4C3>"
            }
            sender.sendComponent("<#EFDF8F>${c.playerName} <#A9B4C3>| <#8FD4A6>${c.name} <#A9B4C3>| ${statusColor}${c.status} <#A9B4C3>| $levelStr")
            sender.sendComponent("<#6E7A8C>  ${c.instanceId}")
        }
    }
}

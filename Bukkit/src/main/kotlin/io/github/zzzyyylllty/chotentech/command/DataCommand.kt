package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync

@CommandHeader(
    name = "chotentech-data",
    aliases = ["cttdata"],
    permission = "chotentech.command.data",
    description = "Data Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ChoTenDataCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val help = subCommand {
        createModernHelper()
    }

    @CommandBody
    val editor  = subCommand {
        execute<Player> { sender, context, argument ->
            val target = !(sender.getData()?.editorMode ?: false)
            sender.getData()?.editorMode = target
            sender.sendComponent("<aqua>编辑器模式已设为 $target。")
        }
    }

    @CommandBody
    val hud = subCommand {
        player("user") {
            dynamic("key") {
                dynamic("content") {
                    dynamic("time") {
                        execute<CommandSender> { sender, context, argument ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!

                                HUDManager(player!!).addHUD(context["key"], context["content"], context["time"].toDouble())

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<red>${context["key"]+","+ context["content"]+","+ context["time"].toDouble()}")

                            }
                        }
                        suggestion<CommandSender> { sender, context ->
                            listOf("10.0")
                        }
                    }
                    suggestion<CommandSender> { sender, context ->
                        listOf("content")
                    }
                }
                suggestion<CommandSender> { sender, context ->
                    listOf("key")
                }
            }
        }
    }

    @CommandBody
    val sanity = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    dynamic("value") {
                        execute<CommandSender> { sender, context, argument ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "SANITY" -> data.sanity
                                    "MAXSANITY" -> data.effectiveMaxSanity
                                    "SANITYREGEN" -> data.sanityRegeneration
                                    "STRESS" -> data.stress
                                    "DARKNESS" -> data.darkness
                                    "MAXDARKNESS" -> data.effectiveMaxDarkness
                                    else -> {
                                        sender.sendComponent("未知的变量类型")
                                        return@submitAsync
                                    }
                                }

                                val finalValue = when (context["operation"]) {
                                    "+" -> varValue + context["value"].toDouble()
                                    "-" -> varValue - context["value"].toDouble()
                                    "=" -> context["value"].toDouble()
                                    else -> {
                                        sender.sendComponent("未知的运算类型")
                                        return@submitAsync
                                    }
                                }

                                when (context["type"]) {
                                    "SANITY" -> data.sanity = minOf(maxOf(finalValue, 0.0), data.effectiveMaxSanity)
                                    "MAXSANITY" -> data.maxSanity = maxOf(finalValue, 0.0)
                                    "SANITYREGEN" -> data.sanityRegeneration = maxOf(finalValue, 0.0)
                                    "STRESS" -> data.stress = minOf(maxOf(finalValue, 0.0), 100.0)
                                    "DARKNESS" -> data.darkness = minOf(maxOf(finalValue, 0.0), data.effectiveMaxDarkness)
                                    "MAXDARKNESS" -> data.maxDarkness = maxOf(finalValue, 0.0)
                                    else -> {

                                    }
                                }

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<red>${context["type"]} = <yellow>$finalValue")

                            }
                        }
                    }
                    suggestion<CommandSender> { sender, context ->
                        listOf("+","-","=")
                    }
                }
                suggestion<CommandSender> { sender, context ->
                    listOf("STRESS","SANITY","MAXSANITY","DARKNESS","MAXDARKNESS")
                }
            }
        }
    }

    @CommandBody
    val warehouse = subCommand {
        player("user") {
            dynamic("action") {
                dynamic("value") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("user").castSafely<Player>()!!
                        val data = playerDataMap[player.uniqueId.toString()] ?: run {
                            sender.sendComponent("<red>玩家数据为空")
                            return@execute
                        }
                        when (context["action"]) {
                            "set" -> {
                                val slots = context["value"].toIntOrNull()
                                if (slots == null || slots < 1) {
                                    sender.sendComponent("<red>格数必须为大于 0 的整数")
                                    return@execute
                                }
                                data.warehouseBaseSlots = slots
                                sender.sendComponent("<green>已将 ${player.name} 的仓库基础格数设为 <yellow>$slots")
                            }
                            "reset" -> {
                                data.warehouseBaseSlots = null
                                sender.sendComponent("<green>已重置 ${player.name} 的仓库基础格数为默认值")
                            }
                            else -> sender.sendComponent("<red>未知操作，可用: set <数量>, reset")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("36", "54", "72")
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("set", "reset")
                }
            }
        }
    }

    @CommandBody
    val reputation = subCommand {
        player("user") {
            dynamic("faction") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val faction = context["faction"].uppercase()
                    val value = ReputationManager.getReputation(player, faction)
                    sender.sendComponent("<gold>$faction <gray>- 信誉: <yellow>$value")
                }
                dynamic("operation") {
                    dynamic("value") {
                        execute<CommandSender> { sender, context, _ ->
                            val player = context.player("user").castSafely<Player>()!!
                            val faction = context["faction"].uppercase()
                            val value = context["value"].toLongOrNull()
                            if (value == null) {
                                sender.sendComponent("<red>数值必须为整数")
                                return@execute
                            }
                            when (context["operation"]) {
                                "+" -> ReputationManager.addReputation(player, faction, value)
                                "-" -> ReputationManager.addReputation(player, faction, -value)
                                "=" -> ReputationManager.setReputation(player, faction, value)
                                else -> {
                                    sender.sendComponent("<red>未知操作，可用: + - =")
                                    return@execute
                                }
                            }
                            val current = ReputationManager.getReputation(player, faction)
                            sender.sendComponent("<green>已将 ${player.name} 的 $faction 信誉设为 <yellow>$current")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                listOf("MEG", "BNTG", "UEC")
            }
        }
    }

    @CommandBody
    val affection = subCommand {
        player("user") {
            dynamic("npc") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val npc = context["npc"]
                    val value = ReputationManager.getAffection(player, npc)
                    sender.sendComponent("<gold>$npc <gray>- 好感度: <yellow>$value")
                }
                dynamic("operation") {
                    dynamic("value") {
                        execute<CommandSender> { sender, context, _ ->
                            val player = context.player("user").castSafely<Player>()!!
                            val npc = context["npc"]
                            val value = context["value"].toLongOrNull()
                            if (value == null) {
                                sender.sendComponent("<red>数值必须为整数")
                                return@execute
                            }
                            when (context["operation"]) {
                                "+" -> ReputationManager.addAffection(player, npc, value)
                                "-" -> ReputationManager.addAffection(player, npc, -value)
                                "=" -> ReputationManager.setAffection(player, npc, value)
                                else -> {
                                    sender.sendComponent("<red>未知操作，可用: + - =")
                                    return@execute
                                }
                            }
                            val current = ReputationManager.getAffection(player, npc)
                            sender.sendComponent("<green>已将 ${player.name} 对 $npc 的好感度设为 <yellow>$current")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
            }
        }
    }

    @CommandBody
    val conflict = subCommand {
        player("user") {
            dynamic("conflictId") {
                dynamic("level") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("user").castSafely<Player>()!!
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
                            sender.sendComponent("<red>未找到冲突事件: $conflictId")
                            return@execute
                        }
                        val changes = ReputationManager.applyConflictReputation(player, conflictId, level)
                        if (changes.isEmpty()) {
                            sender.sendComponent("<red>该结果等级未配置声望变化")
                            return@execute
                        }
                        val report = ReputationManager.formatReputationReport(player, config.name, level, changes)
                        for (line in report.lines()) {
                            if (line.isNotBlank()) sender.sendComponent(line)
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("PERFECT", "SUCCESS", "NEUTRAL", "FAIL", "FULL_FAIL")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                conflictConfigs.keys.toList()
            }
        }
    }
}


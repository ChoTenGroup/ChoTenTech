package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.getData
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
                                val player = context.player("user").castSafely<Player>()

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
                                val player = context.player("user")
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "SANITY" -> data.sanity
                                    "MAXSANITY" -> data.maxSanity
                                    "STRESS" -> data.stress
                                    "DARKNESS" -> data.darkness
                                    "MAXDARKNESS" -> data.maxDarkness
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
                                    "SANITY" -> data.sanity = minOf(maxOf(finalValue, 0.0), data.maxSanity)
                                    "MAXSANITY" -> data.maxSanity = maxOf(finalValue, 0.0)
                                    "STRESS" -> data.stress = minOf(maxOf(finalValue, 0.0), 100.0)
                                    "DARKNESS" -> data.darkness = minOf(maxOf(finalValue, 0.0), data.maxDarkness)
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
                        val player = context.player("user")
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
}


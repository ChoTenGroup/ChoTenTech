package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.function.sender.sendComponent
import io.github.zzzyyylllty.chotentech.function.sender.sendDebug
import org.bukkit.command.CommandSender
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
    val sanitySystem = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    dynamic("value") {
                        execute<CommandSender> { sender, context, argument ->
                            submitAsync {
                                val player = context.player("user")
                                val data = dataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "STRESS" -> data.stress
                                    "SANITY" -> data.sanity
                                    "MAXSANITY" -> data.maxSanity
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
                                    "STRESS" -> data.stress = finalValue
                                    "SANITY" -> data.sanity = finalValue
                                    "MAXSANITY" -> data.maxSanity = finalValue
                                    "DARKNESS" -> data.darkness = finalValue
                                    "MAXDARKNESS" -> data.maxDarkness = finalValue
                                    else -> {

                                    }
                                }

                                sender.sendDebug("<red>${context["type"]} = <yellow>$finalValue")

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
}


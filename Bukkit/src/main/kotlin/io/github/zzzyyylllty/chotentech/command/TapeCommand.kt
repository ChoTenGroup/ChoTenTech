package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
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
    name = "tape",
    aliases = ["磁带"],
    permission = "chotentech.command.tape",
    description = "磁带货币管理",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object TapeCommand {

    @CommandBody
    val main = mainCommand { createModernHelper() }

    @CommandBody
    val add = subCommand {
        player("user") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val amount = context["amount"].toLongOrNull()
                    if (amount == null || amount < 1) {
                        sender.sendComponent("<red>数量必须为正整数")
                        return@execute
                    }
                    CurrencyManager.addBalance(player, "tape", amount)
                    sender.sendComponent("<green>已给予 ${player.name} $amount ${CurrencyManager.displayName("tape")}")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("1", "5", "10") }
            }
        }
    }

    @CommandBody
    val give = subCommand {
        player("user") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val amount = context["amount"].toLongOrNull()
                    if (amount == null || amount < 1) {
                        sender.sendComponent("<red>数量必须为正整数")
                        return@execute
                    }
                    CurrencyManager.addBalance(player, "tape", amount)
                    sender.sendComponent("<green>已给予 ${player.name} $amount ${CurrencyManager.displayName("tape")}")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("1", "5", "10") }
            }
        }
    }

    @CommandBody
    val remove = subCommand {
        player("user") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val amount = context["amount"].toLongOrNull()
                    if (amount == null || amount < 1) {
                        sender.sendComponent("<red>数量必须为正整数")
                        return@execute
                    }
                    if (CurrencyManager.removeBalance(player, "tape", amount)) {
                        sender.sendComponent("<green>已从 ${player.name} 扣除 $amount ${CurrencyManager.displayName("tape")}")
                    } else {
                        sender.sendComponent("<red>${player.name} 的${CurrencyManager.displayName("tape")}不足")
                    }
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("1", "5", "10") }
            }
        }
    }

    @CommandBody
    val take = subCommand {
        player("user") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val amount = context["amount"].toLongOrNull()
                    if (amount == null || amount < 1) {
                        sender.sendComponent("<red>数量必须为正整数")
                        return@execute
                    }
                    if (CurrencyManager.removeBalance(player, "tape", amount)) {
                        sender.sendComponent("<green>已从 ${player.name} 扣除 $amount ${CurrencyManager.displayName("tape")}")
                    } else {
                        sender.sendComponent("<red>${player.name} 的${CurrencyManager.displayName("tape")}不足")
                    }
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("1", "5", "10") }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        player("user") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val amount = context["amount"].toLongOrNull()
                    if (amount == null || amount < 0) {
                        sender.sendComponent("<red>数量必须为非负整数")
                        return@execute
                    }
                    CurrencyManager.setBalance(player, "tape", amount)
                    sender.sendComponent("<green>已将 ${player.name} 的${CurrencyManager.displayName("tape")}设为 $amount")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("0", "1", "5") }
            }
        }
    }
}

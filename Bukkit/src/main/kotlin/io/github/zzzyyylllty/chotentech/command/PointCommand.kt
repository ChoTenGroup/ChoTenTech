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
    name = "point",
    aliases = ["bntg", "贸易点"],
    permission = "chotentech.command.point",
    description = "BNTG贸易点管理",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object PointCommand {

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
                    CurrencyManager.addBalance(player, "point", amount)
                    sender.sendComponent("<green>已给予 ${player.name} $amount ${CurrencyManager.displayName("point")}")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("100", "1000", "10000") }
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
                    CurrencyManager.addBalance(player, "point", amount)
                    sender.sendComponent("<green>已给予 ${player.name} $amount ${CurrencyManager.displayName("point")}")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("100", "1000", "10000") }
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
                    if (CurrencyManager.removeBalance(player, "point", amount)) {
                        sender.sendComponent("<green>已从 ${player.name} 扣除 $amount ${CurrencyManager.displayName("point")}")
                    } else {
                        sender.sendComponent("<red>${player.name} 的${CurrencyManager.displayName("point")}不足")
                    }
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("100", "1000", "10000") }
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
                    if (CurrencyManager.removeBalance(player, "point", amount)) {
                        sender.sendComponent("<green>已从 ${player.name} 扣除 $amount ${CurrencyManager.displayName("point")}")
                    } else {
                        sender.sendComponent("<red>${player.name} 的${CurrencyManager.displayName("point")}不足")
                    }
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("100", "1000", "10000") }
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
                    CurrencyManager.setBalance(player, "point", amount)
                    sender.sendComponent("<green>已将 ${player.name} 的${CurrencyManager.displayName("point")}设为 $amount")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("0", "100", "1000") }
            }
        }
    }
}

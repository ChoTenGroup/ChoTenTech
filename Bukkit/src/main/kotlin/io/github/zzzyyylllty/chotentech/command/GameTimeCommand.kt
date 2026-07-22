package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
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
    name = "gametime",
    aliases = ["gtm", "游戏时间"],
    permission = "chotentech.command.gametime",
    description = "游戏时间管理",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object GameTimeCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

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
                    val data = player.getData() ?: run {
                        sender.sendComponent("<red>无法获取 ${player.name} 的数据")
                        return@execute
                    }
                    data.gameTimeHours += amount
                    val formatted = GameTimeManager.formatOneLine(data.gameTimeHours)
                    sender.sendComponent("<green>已将 ${player.name} 的游戏时间快进 $amount 小时")
                    sender.sendComponent("<green>当前: $formatted")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("1", "24", "720", "8760") }
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
                    val data = player.getData() ?: run {
                        sender.sendComponent("<red>无法获取 ${player.name} 的数据")
                        return@execute
                    }
                    data.gameTimeHours = maxOf(data.gameTimeHours - amount, 0L)
                    val formatted = GameTimeManager.formatOneLine(data.gameTimeHours)
                    sender.sendComponent("<green>已将 ${player.name} 的游戏时间倒退 $amount 小时")
                    sender.sendComponent("<green>当前: $formatted")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("1", "24", "720", "8760") }
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
                    val data = player.getData() ?: run {
                        sender.sendComponent("<red>无法获取 ${player.name} 的数据")
                        return@execute
                    }
                    data.gameTimeHours = amount
                    val formatted = GameTimeManager.formatOneLine(data.gameTimeHours)
                    sender.sendComponent("<green>已将 ${player.name} 的游戏时间设为 $amount 小时")
                    sender.sendComponent("<green>当前: $formatted")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("0", "8760", "17520", "26280") }
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("user") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("user").castSafely<Player>()!!
                val data = player.getData() ?: run {
                    sender.sendComponent("<red>无法获取 ${player.name} 的数据")
                    return@execute
                }
                val hours = data.gameTimeHours
                val formatted = GameTimeManager.formatOneLine(data.gameTimeHours)
                val y = GameTimeManager.getYear(hours)
                val m = GameTimeManager.getMonth(hours)
                val d = GameTimeManager.getDay(hours)
                val h = GameTimeManager.getHour(hours)
                sender.sendComponent("<green>${player.name} 的游戏时间:")
                sender.sendComponent("<green>  $formatted")
                sender.sendComponent("<green>  总小时: $hours | 年: $y 月: $m 日: $d 时: $h")
            }
        }
    }
}

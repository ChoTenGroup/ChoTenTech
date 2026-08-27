package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.function.ReputationManager
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
    name = "repumeg",
    aliases = ["rmeg"],
    permission = "chotentech.command.repumeg",
    description = "MEG声望管理",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object RepuMegCommand {

    private const val FACTION = "MEG"
    private const val REASON = "<#6E7A8C>(杂项扣除)"

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
                        sender.sendComponent("<#E08A98>数量必须为正整数")
                        return@execute
                    }
                    ReputationManager.addReputation(player, FACTION, amount)
                    ReputationManager.addRecord(player, null, FACTION, null, amount)
                    sender.sendComponent("<#8FD4A6>已给予 ${player.name} $amount MEG声望 $REASON")
                    player.sendComponent("<#DFBD7C>MEG声望 <#8FD4A6>+$amount $REASON")
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
                        sender.sendComponent("<#E08A98>数量必须为正整数")
                        return@execute
                    }
                    ReputationManager.addReputation(player, FACTION, amount)
                    ReputationManager.addRecord(player, null, FACTION, null, amount)
                    sender.sendComponent("<#8FD4A6>已给予 ${player.name} $amount MEG声望 $REASON")
                    player.sendComponent("<#DFBD7C>MEG声望 <#8FD4A6>+$amount $REASON")
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
                        sender.sendComponent("<#E08A98>数量必须为正整数")
                        return@execute
                    }
                    if (ReputationManager.getReputation(player, FACTION) < amount) {
                        sender.sendComponent("<#E08A98>${player.name} 的MEG声望不足")
                        return@execute
                    }
                    ReputationManager.addReputation(player, FACTION, -amount)
                    ReputationManager.addRecord(player, null, FACTION, null, -amount)
                    sender.sendComponent("<#8FD4A6>已从 ${player.name} 扣除 $amount MEG声望 $REASON")
                    player.sendComponent("<#DFBD7C>MEG声望 <#E08A98>-$amount $REASON")
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
                        sender.sendComponent("<#E08A98>数量必须为正整数")
                        return@execute
                    }
                    if (ReputationManager.getReputation(player, FACTION) < amount) {
                        sender.sendComponent("<#E08A98>${player.name} 的MEG声望不足")
                        return@execute
                    }
                    ReputationManager.addReputation(player, FACTION, -amount)
                    ReputationManager.addRecord(player, null, FACTION, null, -amount)
                    sender.sendComponent("<#8FD4A6>已从 ${player.name} 扣除 $amount MEG声望 $REASON")
                    player.sendComponent("<#DFBD7C>MEG声望 <#E08A98>-$amount $REASON")
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
                        sender.sendComponent("<#E08A98>数量必须为非负整数")
                        return@execute
                    }
                    val delta = amount - ReputationManager.getReputation(player, FACTION)
                    ReputationManager.setReputation(player, FACTION, amount)
                    if (delta != 0L) {
                        ReputationManager.addRecord(player, null, FACTION, null, delta)
                    }
                    sender.sendComponent("<#8FD4A6>已将 ${player.name} 的MEG声望设为 $amount")
                }
                suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("0", "100", "1000") }
            }
        }
    }
}

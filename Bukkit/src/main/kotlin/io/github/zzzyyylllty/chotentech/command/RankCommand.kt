package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.RankSelectionMenu
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-rank",
    aliases = ["cttr", "rank"],
    permission = "chotentech.command.rank",
    permissionDefault = PermissionDefault.TRUE
)
object RankCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) RankSelectionMenu().open(sender)
            else sender.sendMessage("§cPlease use this command as a player.")
        }
    }

    @CommandBody
    val open = subCommand {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) RankSelectionMenu().open(sender)
            else sender.sendMessage("§cPlease use this command as a player.")
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                RankSelectionMenu().open(target)
            }
        }
    }
}

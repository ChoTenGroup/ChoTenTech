package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.LevelBrowseMenu
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-levels",
    aliases = ["cttl", "cttlevels"],
    permission = "chotentech.command.levels",
    description = "层级浏览菜单",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object LevelsCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) LevelBrowseMenu().open(sender)
            else sender.sendMessage("§c请指定玩家: /cttlevels <player>")
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                LevelBrowseMenu().open(target)
            }
        }
    }

    @CommandBody
    val open = subCommand {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) LevelBrowseMenu().open(sender)
            else sender.sendMessage("§c请指定玩家: /cttlevels open <player>")
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                LevelBrowseMenu().open(target)
            }
        }
    }
}

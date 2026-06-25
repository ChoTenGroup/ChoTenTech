package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.ProfileSelectionGUI
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "profile",
    aliases = ["profiles", "玩家档案"],
    permission = "chotentech.command.profile",
    description = "打开玩家档案管理菜单",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ProfileCommand {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            ProfileSelectionGUI().open(sender)
        }
    }

    @CommandBody
    val menu = subCommand {
        execute<Player> { sender, _, _ ->
            ProfileSelectionGUI().open(sender)
        }
    }
}

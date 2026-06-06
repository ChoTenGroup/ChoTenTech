package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.CosmeticMenu
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "cosmetics",
    aliases = ["cosmetic", "decoration", "装饰"],
    permission = "chotentech.command.cosmetics",
    description = "打开装饰品菜单",
    permissionMessage = "",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object CosmeticCommand {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            CosmeticMenu().open(sender)
        }
    }

    @CommandBody
    val menu = subCommand {
        execute<Player> { sender, _, _ ->
            CosmeticMenu().open(sender)
        }
    }
}

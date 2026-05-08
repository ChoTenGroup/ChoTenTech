package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.SecureContainerMenu
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "securecontainer",
    aliases = ["sc"],
    permission = "chotentech.command.securecontainer",
    permissionDefault = PermissionDefault.TRUE,
)
object SecureContainerCommand {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            SecureContainerMenu().open(sender)
        }
    }
}

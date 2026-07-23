package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.SettingsMenu
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "chotentechsettings",
    aliases = ["ctts", "设置"],
    permission = "chotentech.command.settings",
    description = "玩家设置 GUI",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object SettingsCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            val player = if (sender is Player) sender else return@execute
            SettingsMenu().open(player)
        }
    }
}

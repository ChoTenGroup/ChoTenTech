package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.ProfileInfoGUI
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "profileinfo",
    aliases = ["pi", "档案信息"],
    permission = "chotentech.command.profileinfo",
    description = "玩家档案信息GUI",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object ProfileInfoCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val open = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                val viewer = if (sender is Player) sender else target
                ProfileInfoGUI(target).open(viewer)
                if (sender != viewer) {
                    sender.sendComponent("<green>已为 ${target.name} 打开档案信息界面")
                }
            }
        }
    }
}

package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "pvp",
    aliases = [],
    permission = "chotentech.command.pvp",
    description = "切换PVP模式",
    permissionMessage = "",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object PvpCommand {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            val data = sender.getData() ?: return@execute
            data.pvpEnabled = !data.pvpEnabled
            if (data.pvpEnabled) {
                sender.sendComponent("<red>⚔ PVP模式已<dark_red>开启</dark_red>，你现在可以与其他开启PVP的玩家战斗")
            } else {
                sender.sendComponent("<green>☮ PVP模式已<dark_green>关闭</dark_green>，你将无法攻击其他玩家")
            }
        }
    }

    @CommandBody
    val on = subCommand {
        execute<Player> { sender, _, _ ->
            val data = sender.getData() ?: return@execute
            data.pvpEnabled = true
            sender.sendComponent("<red>⚔ PVP模式已<dark_red>开启")
        }
    }

    @CommandBody
    val off = subCommand {
        execute<Player> { sender, _, _ ->
            val data = sender.getData() ?: return@execute
            data.pvpEnabled = false
            sender.sendComponent("<green>☮ PVP模式已<dark_green>关闭")
        }
    }
}

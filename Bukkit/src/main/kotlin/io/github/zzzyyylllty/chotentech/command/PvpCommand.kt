package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "pvpEnabled",
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
            val uuid = sender.uniqueId.toString()
            val shared = ProfileManager.getSharedData(uuid)
            shared.settings.values["pvpEnabled"] = (!shared.settings.getBoolean("pvpEnabled")).toString()
            sender.getData()?.pvpEnabled = shared.settings.getBoolean("pvpEnabled")
            if (shared.settings.getBoolean("pvpEnabled")) {
                sender.sendComponent("<#E08A98>⚔ PVP模式已<#B46572>开启</#B46572>，你现在可以与其他开启PVP的玩家战斗")
            } else {
                sender.sendComponent("<#8FD4A6>☮ PVP模式已<#6EA982>关闭</#6EA982>，你将无法攻击其他玩家")
            }
            ProfileManager.saveSharedData(uuid)
        }
    }

    @CommandBody
    val on = subCommand {
        execute<Player> { sender, _, _ ->
            val uuid = sender.uniqueId.toString()
            val shared = ProfileManager.getSharedData(uuid)
            shared.settings.values["pvpEnabled"] = "true"
            sender.getData()?.pvpEnabled = true
            sender.sendComponent("<#E08A98>⚔ PVP模式已<#B46572>开启")
            ProfileManager.saveSharedData(uuid)
        }
    }

    @CommandBody
    val off = subCommand {
        execute<Player> { sender, _, _ ->
            val uuid = sender.uniqueId.toString()
            val shared = ProfileManager.getSharedData(uuid)
            shared.settings.values["pvpEnabled"] = "false"
            sender.getData()?.pvpEnabled = false
            sender.sendComponent("<#8FD4A6>☮ PVP模式已<#6EA982>关闭")
            ProfileManager.saveSharedData(uuid)
        }
    }
}

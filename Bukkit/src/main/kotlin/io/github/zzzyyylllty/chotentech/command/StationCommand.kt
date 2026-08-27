package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.station.StationManager
import io.github.zzzyyylllty.chotentech.station.StationMenu
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-station",
    aliases = ["cttstation", "station"],
    description = "合成站系统",
    newParser = true
)
object StationCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val open = subCommand {
        dynamic("stationId") {
            suggestion<org.bukkit.command.CommandSender>(uncheck = true) { _, _ ->
                StationManager.stations.keys.toList()
            }
            execute<Player> { sender, context, _ ->
                val stationId = context["stationId"]
                StationMenu.open(sender, stationId)
            }
            player("player") {
                execute<org.bukkit.command.CommandSender> { sender, context, _ ->
                    val stationId = context["stationId"]
                    val targetName = context["player"]
                    val target = org.bukkit.Bukkit.getPlayerExact(targetName)
                    if (target == null) {
                        sender.sendMessage("<#E08A98>玩家 $targetName 不在线".toComponent())
                        return@execute
                    }
                    StationMenu.open(target, stationId)
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<org.bukkit.command.CommandSender> { sender, _, _ ->
            if (!sender.hasPermission("chotentech.admin")) {
                sender.sendMessage("§c你没有权限执行此命令")
                return@execute
            }
            StationManager.loadAll()
            sender.sendMessage("§a[Station] 合成站配置已重载! 共 ${StationManager.stations.size} 个")
        }
    }
}

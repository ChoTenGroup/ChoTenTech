package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

private val DEFAULT_SPAWN by lazy {
    Bukkit.getWorld("Area0")?.let { Location(it, -7.0, 14.0, 6.0) }
}

@CommandHeader(
    name = "backspawn",
    aliases = ["bspawn"],
    permission = "chotentech.command.backspawn",
    description = "传送到个人重生点",
    permissionMessage = "",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object BackSpawnCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            val player = sender as? Player ?: return@execute
            val point = playerDataMap[player.uniqueId.toString()]?.respawnPoint
            if (point != null) {
                point.toLocation()?.let { player.teleportAsync(it) }
            } else {
                DEFAULT_SPAWN?.let { player.teleportAsync(it) }
            }
        }
    }
}

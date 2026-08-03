package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.function.savepoint.SavePointManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

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
            val loc = SavePointManager.getRespawnLocation(player)
            if (loc != null) {
                player.teleportAsync(loc)
            } else {
                player.sendMessage("<red>你没有设置存档点".toComponent())
            }
        }
    }
}

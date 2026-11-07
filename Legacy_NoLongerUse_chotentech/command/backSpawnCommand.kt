package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.id.changeUserID
import io.github.zzzyyylllty.chotentech.function.sender.sendComponent
import io.github.zzzyyylllty.chotentech.function.sender.sendDebug
import io.github.zzzyyylllty.sertraline.function.kether.runKether
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync

@CommandHeader(
    name = "backspawn",
    aliases = ["bs"],
    permission = "chotentech.command.backspawn",
    description = "Debug Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object ChoTenBackSpawnCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, context, argument ->
            val bukkitPlayer = sender as Player
            bukkitPlayer.respawnLocation?.let { bukkitPlayer.teleportAsync(it) }
        }
    }


}

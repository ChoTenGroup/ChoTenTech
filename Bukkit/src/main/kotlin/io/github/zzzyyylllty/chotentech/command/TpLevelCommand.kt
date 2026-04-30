package io.github.zzzyyylllty.chotentech.command

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.reloadCustomConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.command.component.CommandComponentDynamic
import taboolib.common.platform.command.component.CommandComponentLiteral
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.runningPlatform
import taboolib.common.platform.function.submit
import taboolib.module.lang.asLangText
import taboolib.module.nms.MinecraftVersion.minecraftVersion

@CommandHeader(
    name = "tplevel",
    aliases = ["tpl"],
    permission = "chotentech.command.tplevel",
    description = "tplevel Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object TpLevelCommand {

    @CommandBody
    val main = mainCommand {
        dynamic("level"){
            execute<Player> { sender, context, argument ->
                val level = levelsList[context["level"]]
                    ?: levelsList["level-${context["level"]}"]
                if (level == null) {
                    sender.sendMessage("Level not found.")
                } else {
                    sender.sendMessage("Teleporting to Level: ${level.name}")
                    val reg = WorldEditRegionConverter.convertToRegion(level.region)
                    val teleportLocation = Location(level.getWorld(), reg.center.x(), reg.center.y(), reg.center.z())
                    submit { sender.teleport(teleportLocation) }
                }
            }
            suggestion<Player> { sender, context ->
                levelsList.keys.toList()
            }
        }
    }
}

@CommandHeader(
    name = "tpnoclip",
    aliases = ["tpn"],
    permission = "chotentech.command.tpnoclip",
    description = "tpnoclip Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object TpNoclipCommand {

    @CommandBody
    val main = mainCommand {
        dynamic("noclip"){
            execute<Player> { sender, context, argument ->
                val level = sender.getData()?.activeLevel
                if (level == null) {
                    sender.sendMessage("Level not found.")
                } else {
                    val noclip = context["noclip"]
                    for (n in sender.getData()?.activeLevel?.noclips!!) {
                        if (n.name == noclip) {
                            sender.teleportAsync(n.noclipPointLocation1.toLocation(sender.location.world))
                        }
                    }
                }
            }
            suggestion<Player> { sender, context ->
                sender.getData()?.activeLevel?.noclips?.map { it.name!! }
            }
        }
    }
}

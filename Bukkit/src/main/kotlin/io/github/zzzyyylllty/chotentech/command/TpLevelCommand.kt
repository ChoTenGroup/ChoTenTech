package io.github.zzzyyylllty.chotentech.command

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.noclip.RandomNoclipSelector
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.function.submit

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
                    submit {
                        sender.teleport(teleportLocation)
                        ChoTenDataHelper(sender).updateLevel()
                    }
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
            execute<Player> { sender, context, _ ->
                val level = sender.getData()?.activeLevel
                if (level == null) {
                    sender.sendMessage("Level not found.")
                } else {
                    val noclipName = context["noclip"]
                    for (n in level.noclips) {
                        if (n.name == noclipName) {
                            sender.teleportAsync(n.noclipPointLocation1.toLocation(sender.location.world))
                        }
                    }
                }
            }
            suggestion<Player> { sender, _ ->
                val level = sender.getData()?.activeLevel ?: return@suggestion emptyList()
                val enabled = RandomNoclipSelector.getEnabledNoclips(sender.uniqueId.toString(), level)
                level.noclips.mapNotNull { noclip ->
                    noclip.name?.let { name ->
                        if (name in enabled) name else "$name [随机禁用]"
                    }
                }
            }
        }
    }
}

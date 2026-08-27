package io.github.zzzyyylllty.chotentech.command

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
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
        dynamic("level") {
            suggestion<Player> { _, _ ->
                levelsList.keys.toList()
            }
            execute<Player> { sender, context, _ ->
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
            dynamic("noclip") {
                suggestion<Player> { _, context ->
                    val levelKey = context["level"]
                    val level = levelsList[levelKey] ?: levelsList["level-$levelKey"]
                    level?.noclips?.mapNotNull { it.name } ?: emptyList()
                }
                execute<Player> { sender, context, _ ->
                    val levelKey = context["level"]
                    val level = levelsList[levelKey] ?: levelsList["level-$levelKey"]
                    if (level == null) {
                        sender.sendComponent("<#E08A98>层级 $levelKey 不存在")
                        return@execute
                    }
                    val noclipName = context["noclip"]
                    val point = level.noclips.find { it.name == noclipName }
                    if (point == null) {
                        sender.sendComponent("<#E08A98>切出点 $noclipName 不存在")
                        return@execute
                    }
                    val world = point.noclipPointWorld?.let { Bukkit.getWorld(it) }
                        ?: level.getWorld()
                        ?: sender.world
                    val v1 = point.noclipPointLocation1
                    val v2 = point.noclipPointLocation2
                    val cx = (v1.x + v2.x) / 2.0
                    val cy = (v1.y + v2.y) / 2.0
                    val cz = (v1.z + v2.z) / 2.0
                    submit {
                        sender.teleport(Location(world, cx, cy, cz))
                        sender.sendComponent("<#8FD4A6>已传送到 <#EFDF8F>${level.name}<#8FD4A6> 的切出点 <#EFDF8F>$noclipName")
                    }
                }
            }
        }
    }
}

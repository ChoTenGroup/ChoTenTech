package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.RespawnPoint
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player

@CommandHeader(
    name = "setspawn",
    aliases = ["sspawn"],
    permission = "chotentech.command.setspawn",
    description = "设置玩家重生点（管理员）",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object SetSpawnCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendStringAsComponent("<red>用法: /setspawn <玩家> <世界> <x> <y> <z>")
        }
        player("target") {
            dynamic("world") {
                suggestion<CommandSender> { _, _ ->
                    Bukkit.getWorlds().map { it.name }
                }
                dynamic("x") {
                    suggestion<CommandSender> { _, _ -> listOf("0") }
                    dynamic("y") {
                        suggestion<CommandSender> { _, _ -> listOf("0") }
                        dynamic("z") {
                            suggestion<CommandSender> { _, _ -> listOf("0") }
                            execute<CommandSender> { sender, context, _ ->
                                val target = Bukkit.getPlayerExact(context["target"])
                                if (target == null) {
                                    sender.sendStringAsComponent("<red>玩家 ${context["target"]} 不在线！")
                                    return@execute
                                }

                                val world = Bukkit.getWorld(context["world"])
                                if (world == null) {
                                    sender.sendStringAsComponent("<red>世界 ${context["world"]} 不存在！")
                                    return@execute
                                }

                                val x = context["x"].toDoubleOrNull()
                                val y = context["y"].toDoubleOrNull()
                                val z = context["z"].toDoubleOrNull()
                                if (x == null || y == null || z == null) {
                                    sender.sendStringAsComponent("<red>坐标必须为有效数字！")
                                    return@execute
                                }

                                val data = playerDataMap[target.uniqueId.toString()]
                                if (data == null) {
                                    sender.sendStringAsComponent("<red>玩家数据未加载！")
                                    return@execute
                                }

                                data.respawnPoint = RespawnPoint(
                                    world = world.name,
                                    x = x,
                                    y = y,
                                    z = z,
                                    yaw = 0f,
                                    pitch = 0f
                                )
                                sender.sendStringAsComponent("<green>已将 ${target.name} 的重生点设为 ${world.name} $x $y $z")
                                target.sendStringAsComponent("<green>管理员已将你的重生点设为 ${world.name} $x $y $z")
                            }
                        }
                    }
                }
            }
        }
    }
}

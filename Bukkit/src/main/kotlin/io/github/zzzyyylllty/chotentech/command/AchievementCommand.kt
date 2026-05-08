package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.gui.AchievementMenu
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-achievement",
    aliases = ["cttach", "cttachievement"],
    permission = "chotentech.command.achievement",
    description = "成就系统管理",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object AchievementCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val complete = subCommand {
        player("player") {
            dynamic("achievement") {
                execute<CommandSender> { sender, context, argument ->
                    val target = context.player("player").castSafely<Player>() ?: return@execute
                    val achId = context["achievement"]

                    if (AchievementManager.complete(target, achId)) {
                        sender.sendComponent("<green>已为 <yellow>${target.name} <green>完成成就: <yellow>${AchievementManager.achievements[achId]?.name ?: achId}")
                        target.sendComponent("<green>恭喜！你获得了成就: <yellow>${AchievementManager.achievements[achId]?.name ?: achId}")
                    } else {
                        if (AchievementManager.achievements[achId] == null) {
                            sender.sendComponent("<red>成就 $achId 不存在")
                        } else {
                            sender.sendComponent("<red>玩家 ${target.name} 已完成该成就")
                        }
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    AchievementManager.achievements.keys.toList()
                }
            }
        }
    }

    @CommandBody
    val open = subCommand {
        execute<Player> { sender, _, _ ->
            AchievementMenu().open(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                AchievementMenu().open(target)
            }
        }
    }

    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent("<gold>===== 成就列表 =====")
            for ((id, ach) in AchievementManager.achievements) {
                val cat = AchievementManager.categories[ach.category]?.name ?: ach.category
                sender.sendComponent("<gray>$id <white>- <yellow>${ach.name} <dark_gray>($cat)")
            }
            sender.sendComponent("<gold>共 ${AchievementManager.achievements.size} 个成就")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent("<yellow>正在重载成就配置...")
            io.github.zzzyyylllty.chotentech.ChoTenTech.reloadAchievements()
            sender.sendComponent("<green>成就配置已重载。当前 ${AchievementManager.achievements.size} 个成就")
        }
    }
}

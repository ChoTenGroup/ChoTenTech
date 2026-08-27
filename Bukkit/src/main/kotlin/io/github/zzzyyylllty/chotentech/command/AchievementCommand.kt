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
                        sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${target.name} <#8FD4A6>完成成就: <#EFDF8F>${AchievementManager.achievements[achId]?.name ?: achId}")
                        target.sendComponent("<#8FD4A6>恭喜！你获得了成就: <#EFDF8F>${AchievementManager.achievements[achId]?.name ?: achId}")
                    } else {
                        if (AchievementManager.achievements[achId] == null) {
                            sender.sendComponent("<#E08A98>成就 $achId 不存在")
                        } else {
                            sender.sendComponent("<#E08A98>玩家 ${target.name} 已完成该成就")
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
            sender.sendComponent("<#DFBD7C>===== 成就列表 =====")
            for ((id, ach) in AchievementManager.achievements) {
                val cat = AchievementManager.categories[ach.category]?.name ?: ach.category
                sender.sendComponent("<#A9B4C3>$id <#F2F5FA>- <#EFDF8F>${ach.name} <#6E7A8C>($cat)")
            }
            sender.sendComponent("<#DFBD7C>共 ${AchievementManager.achievements.size} 个成就")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent("<#EFDF8F>正在重载成就配置...")
            io.github.zzzyyylllty.chotentech.ChoTenTech.reloadAchievements()
            sender.sendComponent("<#8FD4A6>成就配置已重载。当前 ${AchievementManager.achievements.size} 个成就")
        }
    }
}

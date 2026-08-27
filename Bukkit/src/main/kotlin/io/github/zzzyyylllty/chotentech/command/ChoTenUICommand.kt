package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.AchievementMenu
import io.github.zzzyyylllty.chotentech.gui.AllQuestMenu
import io.github.zzzyyylllty.chotentech.gui.CosmeticMenu
import io.github.zzzyyylllty.chotentech.gui.FriendMenu
import io.github.zzzyyylllty.chotentech.gui.LevelBrowseMenu
import io.github.zzzyyylllty.chotentech.gui.OrganizationMenu
import io.github.zzzyyylllty.chotentech.gui.ProfileInfoGUI
import io.github.zzzyyylllty.chotentech.gui.ProfileSelectionGUI
import io.github.zzzyyylllty.chotentech.gui.RankSelectionMenu
import io.github.zzzyyylllty.chotentech.gui.SettingsMenu
import io.github.zzzyyylllty.chotentech.gui.TeamMenu
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-ui",
    aliases = ["cttui"],
    permission = "chotentech.command.ui",
    description = "ChoTenTech 玩家界面指令",
    permissionMessage = "",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object ChoTenUICommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val settings = subCommand {
        playerArg("设置") { SettingsMenu().open(it) }
    }

//    @CommandBody
//    val menu = subCommand {
//        player("target") {
//            execute<CommandSender> { sender, context, _ ->
//                val target = context.player("target").castSafely<Player>() ?: return@execute
//                val viewer = if (sender is Player) sender else target
//                ProfileInfoGUI(target).open(viewer)
//                if (sender != viewer) {
//                    sender.sendComponent("<#8FD4A6>已为 ${target.name} 打开玩家菜单")
//                }
//            }
//        }
//    }

    @CommandBody
    val quests = subCommand {
        playerArg("任务列表") { AllQuestMenu().open(it) }
    }

    @CommandBody
    val profile = subCommand {
        playerArg("角色档案") { ProfileSelectionGUI().open(it) }
    }

    @CommandBody
    val rank = subCommand {
        playerArg("称号") { RankSelectionMenu().open(it) }
    }

    @CommandBody
    val levels = subCommand {
        playerArg("层级") { LevelBrowseMenu().open(it) }
    }

    @CommandBody
    val cosmetics = subCommand {
        playerArg("装扮") { CosmeticMenu().open(it) }
    }

    @CommandBody
    val achievement = subCommand {
        playerArg("成就") { AchievementMenu().open(it) }
    }

    @CommandBody
    val team = subCommand {
        playerArg("队伍") { TeamMenu().open(it) }
    }

    @CommandBody
    val friend = subCommand {
        playerArg("好友") { FriendMenu().open(it) }
    }

    @CommandBody
    val org = subCommand {
        playerArg("组织") { OrganizationMenu().open(it) }
    }

    /** 为子命令生成两个分支:/cttui <子命令> 打开自己的界面,/cttui <子命令> <玩家> 为目标玩家打开 */
    private fun CommandComponent.playerArg(title: String, open: (Player) -> Unit) {
        execute<Player> { sender, _, _ ->
            open(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                open(target)
                if (sender != target) {
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${target.name} <#8FD4A6>打开${title}界面")
                }
            }
        }
    }
}

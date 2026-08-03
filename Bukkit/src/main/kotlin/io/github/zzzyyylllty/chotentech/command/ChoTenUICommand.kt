package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.AchievementMenu
import io.github.zzzyyylllty.chotentech.gui.AllQuestMenu
import io.github.zzzyyylllty.chotentech.gui.CosmeticMenu
import io.github.zzzyyylllty.chotentech.gui.FriendMenu
import io.github.zzzyyylllty.chotentech.gui.LevelBrowseMenu
import io.github.zzzyyylllty.chotentech.gui.OrganizationMenu
import io.github.zzzyyylllty.chotentech.gui.ProfileSelectionGUI
import io.github.zzzyyylllty.chotentech.gui.RankSelectionMenu
import io.github.zzzyyylllty.chotentech.gui.SettingsMenu
import io.github.zzzyyylllty.chotentech.gui.TeamMenu
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
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
        execute<Player> { sender, _, _ ->
            SettingsMenu().open(sender)
        }
    }

    @CommandBody
    val quests = subCommand {
        player("user") {
            execute<CommandSender> { sender, context, _ ->
                val user = context.player("user").castSafely<Player>() ?: return@execute
                AllQuestMenu().open(user)
            }
        }
    }

    @CommandBody
    val profile = subCommand {
        execute<Player> { sender, _, _ ->
            ProfileSelectionGUI().open(sender)
        }
    }

    @CommandBody
    val rank = subCommand {
        execute<Player> { sender, _, _ ->
            RankSelectionMenu().open(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                RankSelectionMenu().open(target)
            }
        }
    }

    @CommandBody
    val levels = subCommand {
        execute<Player> { sender, _, _ ->
            LevelBrowseMenu().open(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                LevelBrowseMenu().open(target)
            }
        }
    }

    @CommandBody
    val cosmetics = subCommand {
        execute<Player> { sender, _, _ ->
            CosmeticMenu().open(sender)
        }
    }

    @CommandBody
    val achievement = subCommand {
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
    val team = subCommand {
        execute<Player> { sender, _, _ ->
            TeamMenu().open(sender)
        }
    }

    @CommandBody
    val friend = subCommand {
        execute<Player> { sender, _, _ ->
            FriendMenu().open(sender)
        }
    }

    @CommandBody
    val org = subCommand {
        execute<Player> { sender, _, _ ->
            OrganizationMenu().open(sender)
        }
    }
}

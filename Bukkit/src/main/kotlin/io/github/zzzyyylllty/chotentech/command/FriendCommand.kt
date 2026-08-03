package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.friend.FriendManager
import io.github.zzzyyylllty.chotentech.gui.FriendMenu
import io.github.zzzyyylllty.chotentech.gui.FriendRequestMenu
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "friend",
    aliases = ["haoyou", "fy"],
    permission = "chotentech.command.friend",
    permissionMessage = "",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object FriendCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val gui = subCommand {
        execute<Player> { sender, _, _ ->
            FriendMenu().open(sender)
        }
    }

    @CommandBody
    val requests = subCommand {
        execute<Player> { sender, _, _ ->
            FriendRequestMenu().open(sender)
        }
    }

    @CommandBody
    val add = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val requester = (sender as? Player) ?: return@execute
                val target = context.player("target").castSafely<Player>() ?: return@execute

                if (requester.uniqueId == target.uniqueId) {
                    requester.sendMessage("<red>不能添加自己为好友".toComponent())
                    return@execute
                }

                if (FriendManager.sendRequest(requester, target)) {
                    requester.sendMessage("<green>已向 <yellow>${target.name} <green>发送好友申请".toComponent())
                    target.sendMessage("<gold>■ <yellow>${requester.name} <white>向你发送了好友申请，输入 <yellow>/friend requests <white>处理".toComponent())
                } else {
                    requester.sendMessage("<red>无法发送申请：对方已是好友、申请已存在或你的好友数量已达上限".toComponent())
                }
            }
        }
    }

    @CommandBody
    val accept = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = (sender as? Player) ?: return@execute
                val requester = context.player("target").castSafely<Player>() ?: run {
                    sender.sendComponent("<red>该玩家不在线")
                    return@execute
                }

                if (FriendManager.accept(target, requester.uniqueId)) {
                    target.sendMessage("<green>已同意 <yellow>${requester.name} <green>的好友申请".toComponent())
                    requester.sendMessage("<green>✦ <yellow>${target.name} <green>同意了你的好友申请！".toComponent())
                } else {
                    target.sendMessage("<red>无法添加该好友（好友数量已达上限或申请不存在）".toComponent())
                }
            }
        }
    }

    @CommandBody
    val deny = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = (sender as? Player) ?: return@execute
                val requester = context.player("target").castSafely<Player>() ?: run {
                    sender.sendComponent("<red>该玩家不在线")
                    return@execute
                }

                if (FriendManager.deny(target, requester.uniqueId)) {
                    target.sendMessage("<gray>已拒绝 <white>${requester.name} <gray>的好友申请".toComponent())
                    requester.sendMessage("<gray>✦ <yellow>${target.name} <gray>拒绝了你的好友申请".toComponent())
                } else {
                    target.sendMessage("<red>该申请不存在".toComponent())
                }
            }
        }
    }

    @CommandBody
    val remove = subCommand {
        dynamic("name") {
            execute<CommandSender> { sender, context, _ ->
                val player = (sender as? Player) ?: return@execute

                val targetId = resolvePlayerId(context["name"])
                if (targetId == null) {
                    sender.sendComponent("<red>找不到玩家 <white>${context["name"]}")
                    return@execute
                }

                val targetName = Bukkit.getOfflinePlayer(targetId).name ?: context["name"]
                if (FriendManager.remove(player, targetId)) {
                    player.sendMessage("<red>已删除好友 <yellow>$targetName".toComponent())
                    val target = Bukkit.getPlayer(targetId)
                    if (target != null && target.isOnline) {
                        target.sendMessage("<gray>✦ <yellow>${player.name} <gray>将你从好友列表中删除了".toComponent())
                    }
                } else {
                    player.sendMessage("<red>$targetName <red>不是你的好友".toComponent())
                }
            }
        }
    }

    private fun resolvePlayerId(name: String): java.util.UUID? {
        Bukkit.getPlayer(name)?.let { return it.uniqueId }
        val offline = Bukkit.getOfflinePlayer(name)
        return if (offline.hasPlayedBefore()) offline.uniqueId else null
    }
}

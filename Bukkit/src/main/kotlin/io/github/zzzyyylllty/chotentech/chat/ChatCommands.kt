package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.team.TeamManager
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chat",
    aliases = ["ch", "channel"],
    permission = "chotentech.command.chat",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false,
)
object ChatCommands {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ -> showInfo(sender) }
        dynamic("channel") {
            execute<Player> { sender, context, _ -> switch(sender, context["channel"]) }
            dynamic("message") {
                execute<Player> { sender, context, _ -> send(sender, context["channel"], context["message"]) }
            }
        }
    }

    @CommandBody
    val list = subCommand {
        execute<Player> { sender, _, _ -> showInfo(sender) }
    }

    private fun showInfo(sender: Player) {
        val data = ChatPlayerDataManager.get(sender)
        val currentKey = data.activeChannel
        val current = ChannelManager.get(currentKey) ?: ChannelManager.defaultChannel()
        sender.sendComponent("<gold>===== 聊天频道 =====")
        sender.sendComponent("<gray>当前频道: <yellow>${current?.displayName ?: "无"}")
        for (channel in ChannelManager.all()) {
            val marker = if (channel.key == current?.key) "<green>●" else "<gray>○"
            val prefix = if (channel.quickPrefix.isNotEmpty()) " <gray>前缀: <white>${channel.quickPrefix}" else ""
            val perm = if (channel.permission.isNotEmpty() && !sender.hasPermission(channel.permission)) " <red>[无权限]" else ""
            sender.sendComponent(" $marker <white>${channel.displayName} <gray>(${channel.key})$prefix$perm")
        }
        sender.sendComponent("<gray>用法: <white>/ch <频道> <gray>切换 · <white>/ch <频道> <消息> <gray>直接发送")
    }

    private fun switch(sender: Player, channelKey: String) {
        val channel = ChannelManager.get(channelKey)
        if (channel == null) {
            sender.sendComponent("<red>频道 <yellow>$channelKey<red> 不存在。使用 <yellow>/ch <red>查看可用频道。")
            return
        }
        if (channel.party && TeamManager.getTeam(sender.uniqueId) == null) {
            sender.sendComponent("<red>组队频道需要先加入队伍。")
            return
        }
        if (channel.permission.isNotEmpty() && !sender.hasPermission(channel.permission)) {
            sender.sendComponent("<red>你没有使用 <yellow>${channel.displayName}<red> 频道的权限。")
            return
        }
        val data = ChatPlayerDataManager.get(sender)
        data.activeChannel = channel.key
        ChatPlayerDataManager.save(sender.uniqueId)
        sender.sendComponent("<green>已切换到频道 <yellow>${channel.displayName}<green>。")
    }

    private fun send(sender: Player, channelKey: String, message: String) {
        val channel = ChannelManager.get(channelKey)
        if (channel == null) {
            sender.sendComponent("<red>频道 <yellow>$channelKey<red> 不存在。")
            return
        }
        ChatDispatcher.send(sender, channel, message)
    }
}

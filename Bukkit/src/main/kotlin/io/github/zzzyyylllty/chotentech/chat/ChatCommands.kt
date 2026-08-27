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
        sender.sendComponent("<#DFBD7C>===== 聊天频道 =====")
        sender.sendComponent("<#A9B4C3>当前频道: <#EFDF8F>${current?.displayName ?: "无"}")
        for (channel in ChannelManager.all()) {
            val marker = if (channel.key == current?.key) "<#8FD4A6>●" else "<#A9B4C3>○"
            val prefix = if (channel.quickPrefix.isNotEmpty()) " <#A9B4C3>前缀: <#F2F5FA>${channel.quickPrefix}" else ""
            val perm = if (channel.permission.isNotEmpty() && !sender.hasPermission(channel.permission)) " <#E08A98>[无权限]" else ""
            sender.sendComponent(" $marker <#F2F5FA>${channel.displayName} <#A9B4C3>(${channel.key})$prefix$perm")
        }
        sender.sendComponent("<#A9B4C3>用法: <#F2F5FA>/ch <频道> <#A9B4C3>切换 · <#F2F5FA>/ch <频道> <消息> <#A9B4C3>直接发送")
    }

    private fun switch(sender: Player, channelKey: String) {
        val channel = ChannelManager.get(channelKey)
        if (channel == null) {
            sender.sendComponent("<#E08A98>频道 <#EFDF8F>$channelKey<#E08A98> 不存在。使用 <#EFDF8F>/ch <#E08A98>查看可用频道。")
            return
        }
        if (channel.party && TeamManager.getTeam(sender.uniqueId) == null) {
            sender.sendComponent("<#E08A98>组队频道需要先加入队伍。")
            return
        }
        if (channel.permission.isNotEmpty() && !sender.hasPermission(channel.permission)) {
            sender.sendComponent("<#E08A98>你没有使用 <#EFDF8F>${channel.displayName}<#E08A98> 频道的权限。")
            return
        }
        val data = ChatPlayerDataManager.get(sender)
        data.activeChannel = channel.key
        ChatPlayerDataManager.save(sender.uniqueId)
        sender.sendComponent("<#8FD4A6>已切换到频道 <#EFDF8F>${channel.displayName}<#8FD4A6>。")
    }

    private fun send(sender: Player, channelKey: String, message: String) {
        val channel = ChannelManager.get(channelKey)
        if (channel == null) {
            sender.sendComponent("<#E08A98>频道 <#EFDF8F>$channelKey<#E08A98> 不存在。")
            return
        }
        ChatDispatcher.send(sender, channel, message)
    }
}

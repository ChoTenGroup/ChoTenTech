package io.github.zzzyyylllty.chotentech.chat

import ink.ptms.chemdah.taboolib.common.platform.DelayTo
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.punish.PunishManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.bukkit.Bukkit
import org.bukkit.command.ConsoleCommandSender
import taboolib.common.LifeCycle
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

/**
 * 聊天事件路由：禁言拦截 -> 前缀/活动频道选择 -> ChatDispatcher 校验渲染 -> 注册渲染器并过滤收件人。
 * 使用 Paper 官方推荐的 ChatRenderer 机制（docs.papermc.io/paper/dev/chat-events）：
 * 投递时按查看者单独渲染，控制台查看者输出纯文本控制台格式。
 */
object ChatListener {

    // HIGHEST：服务器上可能还有其他聊天插件先格式化消息，最后执行以覆盖它们
    

@SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        if (event.isCancelled) return
        if (PunishManager.isMuted(player.uniqueId.toString()) != null) {
            event.isCancelled = true
            return
        }

        // 本插件为唯一聊天处理器，事件消息即玩家原始输入的纯文本组件
        val rawText = (event.message() as? TextComponent)?.content().orEmpty()
        if (rawText.isBlank()) {
            event.isCancelled = true
            return
        }
        devLog("ChatListener: ${player.name} raw='$rawText'")

        // 快速前缀路由（如 ".g hello"），未命中则使用玩家的活动频道
        var text = rawText
        var channel = ChannelManager.matchQuickPrefix(rawText).let { (c, rest) ->
            if (c != null) {
                text = rest
                c
            } else {
                null
            }
        }
        if (channel == null) {
            val activeKey = ChatPlayerDataManager.get(player.uniqueId).activeChannel
            channel = ChannelManager.get(activeKey) ?: ChannelManager.defaultChannel()
        }
        if (channel == null) {
            event.isCancelled = true
            player.sendComponent("<#E08A98>没有可用的聊天频道。")
            return
        }
        if (text.isBlank()) {
            event.isCancelled = true
            player.sendComponent("<#EFDF8F>消息内容不能为空。")
            return
        }
        devLog("ChatListener: ${player.name} channel=${channel.key} text='$text'")

        val prepared = ChatDispatcher.prepare(player, channel, text) ?: run {
            event.isCancelled = true
            return
        }

        // 注册渲染器：玩家查看者显示频道格式组件，控制台查看者显示纯文本控制台格式
        val consoleComponent = Component.text(prepared.consoleLine)
        event.renderer(ChatRenderer { _, _, _, viewer ->
            if (viewer is ConsoleCommandSender) consoleComponent else prepared.component
        })

        // 过滤收件人：控制台始终保留（由渲染器输出控制台格式行）
        val viewers = event.viewers()
        viewers.clear()
        prepared.recipients.forEach(viewers::add)
        viewers.add(Bukkit.getConsoleSender())

        devLog("ChatListener: ${player.name} delivered [${channel.key}] recipients=${prepared.recipients.size}")
    }
}

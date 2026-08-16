package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player
import taboolib.platform.compat.replacePlaceholder

/**
 * 聊天格式渲染（安全核心）。
 *
 * 安全约定：
 * 1. 玩家消息内容（<message>）绝不经过 PAPI / 自定义占位符解析，仅按以下规则渲染：
 *    - 有 chotentech.chat.color 权限：strict MiniMessage 解析（允许颜色格式），并剥离 click/hover 事件；
 *    - 无权限：标签转义为纯文本。
 * 2. PAPI 占位符（%xxx%）只会在格式字符串（管理员配置）上解析，解析顺序在消息注入之前。
 */
object ChatFormatter {

    private const val COLOR_PERMISSION = "chotentech.chat.color"

    /** 仅用于解析玩家消息：strict 模式 + 递归剥离点击/悬浮/插入事件，杜绝交互注入 */
    private val messageMiniMessage: MiniMessage by lazy {
        MiniMessage.builder()
            .strict(true)
            .postProcessor { component -> stripInteractions(component) }
            .build()
    }

    /**
     * 递归剥离组件树上的 click/hover/insertion 事件。
     * MiniMessage 的 postProcessor 只作用于根组件，嵌套子节点上的交互事件
     * （如 `<red><click:run_command:say hi>x</click></red>`）会绕过剥离，
     * 必须逐层清理，否则玩家可构造钓鱼消息（点击即以受害者身份执行命令/打开网址）。
     */
    private fun stripInteractions(component: Component): Component {
        val cleaned = component.clickEvent(null).hoverEvent(null).insertion(null)
        return if (component.children().isEmpty()) {
            cleaned
        } else {
            cleaned.children(component.children().map(::stripInteractions))
        }
    }

    /** 解析玩家消息为组件。绝不解析任何占位符。 */
    fun parseMessage(player: Player, text: String): Component {
        if (text.isBlank()) return Component.empty()
        return if (player.hasPermission(COLOR_PERMISSION)) {
            try {
                messageMiniMessage.deserialize(text)
            } catch (e: Exception) {
                Component.text(messageMiniMessage.escapeTags(text))
            }
        } else {
            Component.text(messageMiniMessage.escapeTags(text))
        }
    }

    /**
     * 构建完整聊天组件与控制台行。
     * @param filteredMessage 已通过敏感词过滤的消息文本
     * @return (玩家收到的组件, 控制台纯文本行)
     */
    fun build(sender: Player, channel: ChatChannel, filteredMessage: String, teamName: String?): Pair<Component, String> {
        val messageComponent = parseMessage(sender, filteredMessage)
        val component = buildWithTags(
            formatStr = channel.formatFor(sender),
            player = sender,
            messageComponent = messageComponent,
            tags = mapOf(
                "username" to Component.text(sender.name),
                "display_name" to sender.displayName(),
                "channel" to mmUtil.deserialize(channel.displayName),
                "team_name" to Component.text(teamName ?: "无"),
            ),
        )

        val consoleLine = channel.consoleFormat()
            .replace("<username>", sender.name)
            .replace("<display_name>", sender.name)
            .replace("<message>", filteredMessage)
            .replace("<channel>", channel.displayName)
            .replace("<team_name>", teamName ?: "无")
            .replacePlaceholder(sender)
            .stripMiniMessageTags()

        return component to consoleLine
    }

    /**
     * 通用格式渲染：PAPI 只解析格式串本身；<message> 与 tags 中的自定义标签注入预解析组件。
     * 供频道聊天与私聊共用，保证消息内容绝不进入占位符解析。
     */
    fun buildWithTags(
        formatStr: String,
        player: Player,
        messageComponent: Component,
        tags: Map<String, Component>,
    ): Component {
        val resolved = formatStr.replacePlaceholder(player)
        return try {
            mmUtil.deserialize(
                resolved,
                TagResolver.resolver(
                    TagResolver.resolver("message", Tag.selfClosingInserting(messageComponent)),
                    *tags.map { (k, v) -> TagResolver.resolver(k, Tag.selfClosingInserting(v)) }.toTypedArray(),
                ),
            )
        } catch (e: Exception) {
            // 格式串配置错误时兜底，聊天不被中断
            Component.text(player.name).append(Component.text(": ")).append(messageComponent)
        }
    }

    /** 剥离 MiniMessage 标签，仅用于控制台纯文本输出 */
    fun String.stripMiniMessageTags(): String = replace(Regex("<[^>]*>"), "")

    /** 从组件树提取纯文本（adventure 4.19 已移除 plainText，用遍历替代），不产生任何颜色码 */
    fun Component.toPlainText(): String {
        val sb = StringBuilder()
        fun walk(c: Component) {
            if (c is TextComponent) sb.append(c.content())
            c.children().forEach(::walk)
        }
        walk(this)
        return sb.toString()
    }
}

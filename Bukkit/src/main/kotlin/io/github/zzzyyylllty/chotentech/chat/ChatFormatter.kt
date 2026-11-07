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
 *    - 有 chotentech.chat.color 权限：lenient MiniMessage 解析（允许颜色格式），并剥离 click/hover 事件；
 *    - 无权限：所有 MiniMessage 标签原样显示，消息为纯文本。
 * 2. PAPI 占位符（%xxx%）只会在格式字符串（管理员配置）上解析，解析顺序在消息注入之前。
 */
object ChatFormatter {

    private const val COLOR_PERMISSION = "chotentech.chat.color"

    /**
     * 用于解析玩家消息：lenient 模式 + 递归剥离交互事件。
     *
     * lenient（宽松）模式下未注册的标签作为纯文本显示，不会抛异常，
     * 既避免了 strict 模式对未知标签/无效转义的 ParsingException，
     * 也完全不再需要 escapeTags——后者会引入 `\<`、`\[` 等可见反斜杠。
     */
    private val messageMiniMessage: MiniMessage by lazy {
        MiniMessage.builder()
            .postProcessor { component -> stripInteractions(component) }
            .build()
    }

    /** 无颜色权限玩家的解析器：不注册任何格式标签，输入内容全部按纯文本显示 */
    private val plainMiniMessage: MiniMessage by lazy {
        MiniMessage.builder()
            .tags(TagResolver.empty())
            .postProcessor { component -> stripInteractions(component) }
            .build()
    }

    /**
     * 递归剥离组件树上的 click/hover/insertion 事件。
     * MiniMessage 的 postProcessor 只作用于根组件，嵌套子节点上的交互事件
     * （如 `<#E08A98><click:run_command:say hi>x</click></#E08A98>`）会绕过剥离，
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

    /**
     * 解析玩家消息为组件。绝不解析任何占位符。
     *
     * - 有颜色权限：lenient 解析（标签正常渲染），剥离交互事件；
     * - 无颜色权限：TagResolver.empty() 使所有标签原样显示，消息为纯文本。
     *
     * 不再使用 strict + escapeTags 组合——后者会引入可见的 `\<` / `\[` 反斜杠。
     */
    fun parseMessage(player: Player, text: String): Component {
        if (text.isBlank()) return Component.empty()
        val mm = if (player.hasPermission(COLOR_PERMISSION)) messageMiniMessage else plainMiniMessage
        return mm.deserialize(text)
    }

    /**
     * 构建完整聊天组件与控制台行。
     * @param filteredMessage 已通过敏感词过滤、且提及已替换为 [@token] 标记的消息文本
     * @param mentions 提及解析结果（null 或无命中时与原行为一致）
     * @return (默认查看者组件, 控制台纯文本行)
     */
    fun build(
        sender: Player,
        channel: ChatChannel,
        filteredMessage: String,
        teamName: String?,
        mentions: ChatMention.Result? = null,
    ): Pair<Component, String> {
        // 默认组件用发送者自身视角生成（发送者永远“命中”自己发起的提及，用于兜底）；
        // 实际投递时按每个查看者调用 buildForViewer 重新渲染消息部分。
        val messageComponent = ChatMention.render(sender, sender, filteredMessage)
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
            // 控制台行还原提及原文（[@token] → @token）；必须在 <message> 注入之后（标记在消息文本里）
            .replace(Regex("\\[@([A-Za-z0-9_:\\*]+)]")) { "@${it.groupValues[1]}" }
            .replace("<channel>", channel.displayName)
            .replace("<team_name>", teamName ?: "无")
            .replacePlaceholder(sender)
            .stripMiniMessageTags()

        return component to consoleLine
    }

    /**
     * 按查看者重新渲染整条聊天消息：格式串部分与 [build] 相同，
     * 消息部分经 [ChatMention.render] 按查看者对提及分色（文本段按发送者颜色权限解析）。
     * 供 Prepared.componentFor（ChatRenderer / 分组投递）调用。
     */
    fun buildForViewer(viewer: Player, prepared: ChatDispatcher.Prepared): Component = buildWithTags(
        formatStr = prepared.channel.formatFor(prepared.sender),
        player = prepared.sender,
        messageComponent = ChatMention.render(prepared.sender, viewer, prepared.messageText),
        tags = mapOf(
            "username" to Component.text(prepared.sender.name),
            "display_name" to prepared.sender.displayName(),
            "channel" to mmUtil.deserialize(prepared.channel.displayName),
            "team_name" to Component.text(prepared.teamName ?: "无"),
        ),
    )

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

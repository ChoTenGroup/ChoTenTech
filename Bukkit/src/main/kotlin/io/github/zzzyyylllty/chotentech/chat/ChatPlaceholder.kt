package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import org.bukkit.entity.Player

/**
 * 聊天消息占位符：玩家在消息中输入 [token] 会被替换为对应内容。
 *
 * 替换结果为 MiniMessage 字符串，随消息一起按玩家颜色权限渲染
 * （由 ChatFormatter.parseMessage 负责 strict 解析与交互事件剥离）。
 * 新增占位符只需在 [init] 里调用 [register] 注册一行，其他模块也可直接调用 [register] 扩展。
 */
object ChatPlaceholder {

    /** token（不含方括号） -> 解析函数。匹配消息中的 [token] */
    private val resolvers = linkedMapOf<String, (Player) -> String>()

    init {
        // [item] — 主手物品显示名（空手则替换为空串）
        register("item") { player ->
            val item = player.inventory.itemInMainHand
            if (item.type.isAir) return@register ""
            mmUtil.serialize(item.displayName())
        }
    }

    /** 注册占位符：消息中出现 [token] 时替换为 resolver 返回的 MiniMessage 字符串 */
    fun register(token: String, resolver: (Player) -> String) {
        resolvers[token] = resolver
    }

    /** 替换消息中所有已注册的占位符；单个解析失败时替换为空串，不中断消息 */
    fun resolve(player: Player, text: String): String {
        var result = text
        for ((token, resolver) in resolvers) {
            val tag = "[$token]"
            if (tag in result) {
                val replacement = runCatching { resolver(player) }.getOrNull() ?: ""
                result = result.replace(tag, replacement)
            }
        }
        return result
    }
}

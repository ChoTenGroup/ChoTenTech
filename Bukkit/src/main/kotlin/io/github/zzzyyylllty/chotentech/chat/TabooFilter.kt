package io.github.zzzyyylllty.chotentech.chat

import org.bukkit.entity.Player
import taboolib.module.configuration.Configuration

/**
 * 敏感词过滤（filter.yml）。
 * words 列表按普通字符串匹配（大小写不敏感）；regex 列表按正则匹配。
 */
object TabooFilter {

    private val words = mutableListOf<String>()
    private val regexList = mutableListOf<Regex>()
    private var replacement = "*"
    private var bypassPermission = "chotentech.chat.filter.bypass"

    fun reload(cfg: Configuration) {
        words.clear()
        regexList.clear()
        replacement = cfg.getString("replacement", "*") ?: "*"
        bypassPermission = cfg.getString("bypass-permission", bypassPermission) ?: bypassPermission
        cfg.getStringList("words").forEach {
            if (it.isNotBlank()) words += it.lowercase()
        }
        cfg.getStringList("regex").forEach {
            runCatching { Regex(it) }.getOrNull()?.let(regexList::add)
        }
    }

    /** 过滤文本。有 bypass 权限时原样返回。 */
    fun filter(player: Player, text: String): String {
        if (text.isBlank() || player.hasPermission(bypassPermission)) return text
        var result = text
        for (word in words) {
            if (word.isBlank()) continue
            result = Regex("(?i)" + Regex.escape(word)).replace(result) { m ->
                if (replacement.length == 1) replacement.repeat(m.value.length) else replacement
            }
        }
        for (r in regexList) {
            result = r.replace(result) { m ->
                if (replacement.length == 1) replacement.repeat(m.value.length) else replacement
            }
        }
        return result
    }
}

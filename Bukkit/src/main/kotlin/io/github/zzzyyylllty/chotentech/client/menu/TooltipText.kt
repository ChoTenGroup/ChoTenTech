package io.github.zzzyyylllty.chotentech.client.menu

/**
 * tooltip 文本的分行规则。
 *
 * ## 为什么支持三种分隔符
 *
 * 服务端把多行 tooltip 塞进**一个字符串属性**里（协议里每个属性值就是
 * 一个字符串），所以需要约定分隔符。这里同时接受：
 *
 * - `\n` —— 真正的换行符（Java/Kotlin 字符串里的 `"\n"`）
 * - `|`  —— 竖线
 * - 字面量 `\n` —— 反斜杠后跟字母 n，两个字符
 *
 * 第三种看起来多余，但它是最常见的一类「明明写了换行却不换行」：
 * 配置写在 YAML 里时 `"\n"` 是转义后的真换行，而写在
 * `'...\n...'`（单引号）里就是两个字面字符。与其让每个调用方
 * 都记住这件事，不如解析时直接认。
 *
 * ## 空行的处理
 *
 * 连续分隔符产生的空行**保留**——菜单里的空行是有意的排版手段
 * （分组之间留白）。但首尾的空行会去掉，因为那通常是拼接时
 * 多写了一个分隔符，不是排版意图。
 *
 * 这与客户端侧 `cn.gtemc.ulshelper.menu.Tooltips` 的行为必须一致，
 * 否则同一个 tooltip 在两条轨上显示不一样。
 */
object TooltipText {

    /** tooltip 最大行数。与客户端的 `MAX_TOOLTIP_LINES` 保持一致。 */
    const val MAX_LINES = 16

    fun splitLines(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()

        // 先把字面量 \n 统一成真换行，再按两种分隔符切。
        // 顺序很重要：如果先按真换行切，字面量 \n 会被留在行内，
        // 之后再替换也来不及了。
        val normalized = raw
            .replace("\\n", "\n")
            .replace('|', '\n')

        val lines = normalized.split('\n').map { it.trim() }

        // 去掉首尾空行，但保留中间的空行
        var start = 0
        var end = lines.size
        while (start < end && lines[start].isEmpty()) start++
        while (end > start && lines[end - 1].isEmpty()) end--

        if (start >= end) return emptyList()
        return lines.subList(start, end).take(MAX_LINES)
    }
}

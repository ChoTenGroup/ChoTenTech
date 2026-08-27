package io.github.zzzyyylllty.chotentech.util.minimessage

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

fun List<Any?>.legacyToMiniMessage(): List<Any?> {
    if ((this as? List<String>) == null) return this
    val list = mutableListOf<String>()
    this.forEach { list.add(it.legacyToMiniMessage()) }
    return list
}
fun String.legacyToMiniMessage(): String {
    var text = this

    // 1. 先处理 Hex 颜色代码 - 旧版 &x&6&6&c&c&f&f
    // 匹配形式：&x&6&6&c&c&f&f （固定6个16进制字符）
    val legacyHexPattern = Regex("&x((&[0-9a-fA-F]){6})")
    text = legacyHexPattern.replace(text) { matchResult ->
        if (isInsidePlaceholder(text, matchResult.range)) {
            matchResult.value
        } else {
            val hexChars = matchResult.groupValues[1].chunked(2).joinToString("") { it.substring(1) }
            "<#$hexChars>"
        }
    }

    // 2. 处理现代形式的 Hex Hex代码：&#bad9e8 或 &#bccdde
    val modernHexPattern = Regex("&#([0-9a-fA-F]{6})")
    text = modernHexPattern.replace(text) { matchResult ->
        if (isInsidePlaceholder(text, matchResult.range)) {
            matchResult.value
        } else {
            "<#${matchResult.groupValues[1].lowercase()}>"
        }
    }

    // 3. 处理颜色代码（旧版，无大小写敏感）
    val colorMap = mapOf(
        '0' to "<#2A2F3A>",
        '1' to "<#637FAF>",
        '2' to "<#6EA982>",
        '3' to "<#5FA8A3>",
        '4' to "<#B46572>",
        '5' to "<#977FB8>",
        '6' to "<#DFBD7C>",
        '7' to "<#A9B4C3>",
        '8' to "<#6E7A8C>",
        '9' to "<#94AFE8>",
        'a' to "<#8FD4A6>",
        'b' to "<#84D1CB>",
        'c' to "<#E08A98>",
        'd' to "<#CDAEEF>",
        'e' to "<#EFDF8F>",
        'f' to "<#F2F5FA>"
    )

    for ((code, tag) in colorMap) {
        val pattern = Regex("[&§]$code", RegexOption.IGNORE_CASE)
        text = pattern.replace(text) { matchResult ->
            if (isInsidePlaceholder(text, matchResult.range)) {
                matchResult.value
            } else {
                tag
            }
        }
    }

    // 4. 处理格式代码
    val formatMap = mapOf(
        'k' to "<obfuscated>",
        'l' to "<bold>",
        'm' to "<strikethrough>",
        'n' to "<underlined>",
        'o' to "<italic>"
    )

    for ((code, tag) in formatMap) {
        val pattern = Regex("[&§]$code", RegexOption.IGNORE_CASE)
        text = pattern.replace(text) { matchResult ->
            if (isInsidePlaceholder(text, matchResult.range)) {
                matchResult.value
            } else {
                tag
            }
        }
    }

    // 5. 处理重置代码
    text = Regex("[&§]r", RegexOption.IGNORE_CASE).replace(text) { matchResult ->
        if (isInsidePlaceholder(text, matchResult.range)) {
            matchResult.value
        } else {
            "<reset>"
        }
    }

    return text
}

fun isInsidePlaceholder(text: String, range: IntRange): Boolean {
    val start = text.lastIndexOf("\${", range.first)
    val end = text.indexOf("}$", range.last)

    return start != -1 && end != -1 && start < range.first && end > range.last
}

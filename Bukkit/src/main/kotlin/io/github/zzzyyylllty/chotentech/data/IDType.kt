package io.github.zzzyyylllty.chotentech.data

import net.momirealms.craftengine.bukkit.api.CraftEngineImages
import net.momirealms.craftengine.core.font.Image
import net.momirealms.craftengine.core.util.Key
import taboolib.common.util.length

data class IDType(
    val id: String = "NORMAL",
    var displayName: String = "Unknown",
    var color: String = "Unknown",
    var icon: String?,
    var startChar: String?,
    var char: String?,
    var endChar: String?,
) {
    fun generateLine(cid: Long): String {
        var line = "<font:account>"
        val length = cid.length()
        startChar?.let { line += CraftEngineImages.byId(Key.of(it))?.toRawStr() }
        char?.let {
            val char = CraftEngineImages.byId(Key.of(it))?.toRawStr()
            repeat(length) {
                line += char
            }
        }
        endChar?.let { line += CraftEngineImages.byId(Key.of(it))?.toRawStr() }
        line += "</font>"
        return line
    }
}

data class IDData(
    var id: Long,
    var type: String,
){
}

fun Image?.toRawStr(): String? {
    if (this == null) return ""
    // 取左上角字符作为图标；坐标越界会抛 IndexOutOfBoundsException，兜底返回 null
    return try {
        String(Character.toChars(codepointAt(0, 0)))
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}
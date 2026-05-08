package io.github.zzzyyylllty.chotentech.data

import net.momirealms.craftengine.bukkit.api.CraftEngineImages
import net.momirealms.craftengine.core.font.BitmapImage
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

fun BitmapImage?.toRawStr(): String? {
    if (this == null) return ""
    val row = rows()
    val column = columns()
    return String(Character.toChars(codepointAt(row, column)))
}
package io.github.zzzyyylllty.chotentech.util

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player
import taboolib.common.platform.function.severe
import java.util.Locale

val loc: Locale? by lazy { Locale.getDefault() }

fun String.toLowerCase(): String {
    return lowercase(loc ?: Locale.getDefault())
}
fun String.toUpperCase(): String {
    return uppercase(loc ?: Locale.getDefault())
}

fun String.replacePlaceholderSafety(player: Player?): String {
    return try {
        PlaceholderAPI.setPlaceholders(player, this)
    } catch (ex: NoClassDefFoundError) {
        severe("PlaceholderAPI Not Found")
        this
    }
}
fun List<String>.replacePlaceholderSafety(player: Player?): List<String> {
    return try {
        PlaceholderAPI.setPlaceholders(player, this)
    } catch (ex: NoClassDefFoundError) {
        severe("PlaceholderAPI Not Found")
        this
    }
}

fun Any?.asListEnhanced() : List<String>? {
    if (this == null) return null
    val thisList = if (this is List<*>) this else listOf(this)
    val list = mutableListOf<String>()
    for (string in thisList) {
        if (string == null) continue
        list.addAll(string.toString().split("\n","<br>", ignoreCase = true))
    }
    if (!list.isEmpty() && list.last() == "") list.removeLast()
    return list
}

fun Any?.asListedStringEnhanded() : String? {
    return this.asListEnhanced()?.joinToString("\n")
}

package io.github.zzzyyylllty.chotentech.util

inline fun <reified T> isListOfType(list: List<*>): Boolean {
    return list.all { it is T }
}
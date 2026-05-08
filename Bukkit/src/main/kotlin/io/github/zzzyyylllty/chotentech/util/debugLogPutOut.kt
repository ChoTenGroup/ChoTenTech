package io.github.zzzyyylllty.chotentech.util

import io.github.zzzyyylllty.chotentech.ChoTenTech.consoleSender
import io.github.zzzyyylllty.chotentech.ChoTenTech.devMode
import taboolib.common.platform.function.submitAsync

fun devLog(input: String) {
    submitAsync { if (devMode) consoleSender.sendMessage(input.toComponent()) }
}

fun devMode(b: Boolean) {
    devMode = b
}
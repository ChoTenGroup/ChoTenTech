package io.github.zzzyyylllty.chotentech.attribute.util

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.consoleSender
import io.github.zzzyyylllty.chotentech.attribute.logger.debugS

fun devLog(input: String) {
    if (ChoTenTech.devMode) debugS(input)
}

fun devMode(b: Boolean) {
    ChoTenTech.devMode = b
}
package io.github.zzzyyylllty.chotentech.quetiapine.util

import io.github.zzzyyylllty.chotentech.quetiapine.logger.debugS
import io.github.zzzyyylllty.chotentech.ChoTenTech.devMode
import taboolib.common.platform.function.submitAsync

fun devLog(input: String) {
    submitAsync { if (devMode) debugS(input) }
}

fun devMode(b: Boolean) {
    devMode = b
}
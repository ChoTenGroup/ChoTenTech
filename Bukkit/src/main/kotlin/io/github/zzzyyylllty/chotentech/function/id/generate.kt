package io.github.zzzyyylllty.chotentech.function.id

import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import taboolib.common.platform.function.info
import taboolib.common.util.random
import kotlin.math.floor
import kotlin.math.pow

fun generateUserID(): String {
    val db = SQLDataBase()
    for (times in 6..10) {
        repeat(times-4) {
            val rand = random(floor(10.0.pow(times)), floor(10.0.pow(times-1)))
            info("Generated: $rand")
            if (!db.getIDExists(rand.toString())) return rand.toString()
        }
    }
    throw IllegalStateException("Repeat times too much!")
}

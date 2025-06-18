package io.github.zzzyyylllty.chotentech.function.id

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import taboolib.common.platform.function.info
import taboolib.common.util.random
import kotlin.math.floor
import kotlin.math.pow

fun generateUserID(): String {
    for (times in 6..10) {
        repeat(times-4) {
            val rand = random(floor(10.0.pow(times)).toInt(), floor(10.0.pow(times-1)).toInt()+1)
            info("Generated: $rand")
            if (!isPlayerExists(rand.toString())) return rand.toString()
        }
    }
    throw IllegalStateException("Repeat times too much!")
}

fun isPlayerExists(uuid: String): Boolean {
    return if (dataMap[uuid] == null) SQLDataBase().getUUIDExists(uuid) else true
}
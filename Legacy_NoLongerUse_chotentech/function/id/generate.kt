package io.github.zzzyyylllty.chotentech.function.id

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import taboolib.common.platform.function.info
import taboolib.common.util.random
import kotlin.math.floor
import kotlin.math.pow

val bannedRegex = listOf("89","64","1989","0604","1949","1942","918","1223")

fun generateUserID(): String {
    for (times in 5..20) {
        repeat(times) {
            val rand = random(floor(10.0.pow(times)).toInt(), floor(10.0.pow(times-1)).toInt()+1)
            info("Generated: $rand")
            var isBanned = false
            if (!SQLDataBase().getIDExists(rand.toString()))
                for (id in bannedRegex) {
                    if (rand.toString().contains(id)) isBanned = true
            }
            if (!isBanned) return rand.toString()
        }
    }
    throw IllegalStateException("Repeat times too much!")
}

fun isPlayerExists(uuid: String): Boolean {
    return if (dataMap[uuid] == null) SQLDataBase().getUUIDExists(uuid) else true
}
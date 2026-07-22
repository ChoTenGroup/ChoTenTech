package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
import org.bukkit.entity.Player
import taboolib.module.kether.KetherParser
import taboolib.module.kether.combinationParser
import taboolib.module.kether.script

@KetherParser(["gametime", "gt"], shared = true)
fun actionGameTime() = combinationParser {
    it.group(text(), long().option()).apply(it) { option, value ->
        now {
            val player = script().sender?.castSafely<Player>() ?: error("Player required")
            val helper = ChoTenDataHelper(player)
            when (option) {
                "set" -> helper.setGameTimeHours(value!!)
                "add" -> helper.addGameTimeHours(value!!)
                "remove" -> helper.removeGameTimeHours(value!!)
                "get" -> helper.getGameTimeHours()
                "year" -> GameTimeManager.getYear(helper.getGameTimeHours())
                "month" -> GameTimeManager.getMonth(helper.getGameTimeHours())
                "day" -> GameTimeManager.getDay(helper.getGameTimeHours())
                "hour" -> GameTimeManager.getHour(helper.getGameTimeHours())
                "format" -> GameTimeManager.formatOneLine(helper.getGameTimeHours())
                else -> error("Unknown gametime action: $option")
            }
        }
    }
}

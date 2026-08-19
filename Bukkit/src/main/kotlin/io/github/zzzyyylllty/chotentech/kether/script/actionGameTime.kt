package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
import org.bukkit.entity.Player
import taboolib.library.kether.ArgTypes
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionTake
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser

@KetherParser(["gametime", "gt"], shared = true)
fun actionGameTime() = scriptParser {
    val option = it.nextParsedAction()
    val value = try {
        it.mark()
        it.next(ArgTypes.LONG)
    } catch (ex: Throwable) {
        it.reset()
        null
    }
    actionTake {
        val sender = script().sender
        run(option).thenApply { option ->
            val player = sender?.castSafely<Player>() ?: error("Player required")
            val helper = ChoTenDataHelper(player)
            when (option.toString()) {
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

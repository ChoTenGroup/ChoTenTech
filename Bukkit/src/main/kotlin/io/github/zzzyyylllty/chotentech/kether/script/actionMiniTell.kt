package io.github.zzzyyylllty.chotentech.kether.script

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionTake
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser
import taboolib.module.kether.str

@KetherParser(["minitell", "mtell"], shared = true)
fun actionMiniTell() = scriptParser {
    val message = it.nextParsedAction()
    val mm = MiniMessage.miniMessage()
    actionTake {
        val sender = script().sender?.castSafely<CommandSender>()
        run(message).str { s -> (sender as Audience).sendMessage(mm.deserialize(s)) }
    }
}

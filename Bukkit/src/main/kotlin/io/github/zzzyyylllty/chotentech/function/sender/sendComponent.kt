package io.github.zzzyyylllty.chotentech.function.sender

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender

fun CommandSender.sendComponent(message: String) {
    val mm = MiniMessage.miniMessage()
    this.sendMessage(mm.deserialize(message))
}

fun CommandSender.sendDebug(message: String) {
    val mm = MiniMessage.miniMessage()
    this.sendMessage(mm.deserialize("<gradient:aqua:light_purple:red>< ChoTenTech ></gradient><gray> > <white>$message"))
}

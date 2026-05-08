package io.github.zzzyyylllty.chotentech.function.player

import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.command.CommandSender

fun CommandSender.sendComponent(message: String) {
    sendMessage(message.toComponent())
}
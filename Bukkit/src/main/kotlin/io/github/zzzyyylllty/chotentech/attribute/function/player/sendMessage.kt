package io.github.zzzyyylllty.chotentech.attribute.function.player

import io.github.zzzyyylllty.chotentech.attribute.util.toComponent
import org.bukkit.command.CommandSender

fun CommandSender.sendComponent(message: String) {
    if (message.isNotEmpty()) sendMessage(message.toComponent())
}
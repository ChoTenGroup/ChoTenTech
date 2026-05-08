package io.github.zzzyyylllty.chotentech.player

import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.command.CommandSender

fun CommandSender.sendComponent(message: String) {
    sendMessage(message.legacyToMiniMessage().toComponent())
}
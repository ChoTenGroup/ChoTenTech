package io.github.zzzyyylllty.chotentech.util.minimessage

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

val mmUtil = MiniMessage.miniMessage()
val mmStrictUtil = MiniMessage.builder().strict(true).build()
val mmPlainUtil = PlainTextComponentSerializer.plainText()
val mmLegacyAmpersandUtil = LegacyComponentSerializer.legacyAmpersand()
val mmLegacySectionUtil = LegacyComponentSerializer.legacySection()
val mmJsonUtil = GsonComponentSerializer.gson()
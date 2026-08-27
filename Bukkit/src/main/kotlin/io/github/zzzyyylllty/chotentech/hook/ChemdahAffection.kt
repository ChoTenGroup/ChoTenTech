package io.github.zzzyyylllty.chotentech.hook

import ink.ptms.chemdah.api.event.collect.ConversationEvents
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.devLog
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onChemChat(e: ConversationEvents.Begin) {
    val data = e.session.player.getData()
    val affection = if (data == null) 0L else data.affection[e.session.variables["id"]] ?: 0
    devLog("onChemChat 信任: npcIds=${e.conversation.npcId.id.map { it.value }} 值=$affection")
    // session.variables["title"] overrides conversation.option.title in chat theme (ThemeChat.kt:268)
    e.session.variables["title"] = "${e.session.conversation.option.title} §7($affection 信任)"

}

package io.github.zzzyyylllty.chotentech.hook

import ink.ptms.chemdah.api.event.collect.ConversationEvents
import io.github.zzzyyylllty.chotentech.data.getData
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onChemChat(e: ConversationEvents.Begin) {
    val npcId = e.conversation.npcId.id.firstOrNull()
    val affection = npcId?.value?.let { e.session.player.getData()?.affection[it] } ?: 0
        // session.variables["title"] overrides conversation.option.title in chat theme (ThemeChat.kt:268)
        e.session.variables["title"] = "${e.session.conversation.option.title} §7($affection 好感)"

}

package io.github.zzzyyylllty.chotentech.hook

import ink.ptms.chemdah.api.event.collect.ConversationEvents
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.devLog
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onChemChat(e: ConversationEvents.Begin) {
    val data = e.session.player.getData()
    val affection = if (data == null) 0L else resolveAffection(e, data)

    devLog("onChemChat 信任: npcIds=${e.conversation.npcId.id.map { it.value }} 值=$affection")
        // session.variables["title"] overrides conversation.option.title in chat theme (ThemeChat.kt:268)
        e.session.variables["title"] = "${e.session.conversation.option.title} §7($affection 信任)"

}

/**
 * 解析对话对应的 NPC 好感度。
 * 对话配置的 npc id 可能有多条, 且 Chemdah 匹配 NPC 时不区分大小写,
 * 而 affection 的 key 是严格区分大小写的(如 MaoSe), 因此遍历所有候选并先精确匹配、再忽略大小写回退。
 */
private fun resolveAffection(e: ConversationEvents.Begin, data: PlayerData): Long {
    val affection = data.affection
    val candidates = mutableListOf<String>()
    // 对话配置中声明的所有 npc id(玩家实际交互的实体必在其中)
    e.conversation.npcId.id.forEach { candidates += it.value }
    e.session.source.name.let { if (it.isNotBlank()) candidates += it }
    for (candidate in candidates) {
        if (candidate.isEmpty()) continue
        affection[candidate]?.let { return it }
        affection.entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.value?.let { return it }
    }
    return 0L
}

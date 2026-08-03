package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.entity.Player
import taboolib.module.kether.KetherParser
import taboolib.module.kether.combinationParser
import taboolib.module.kether.script

@KetherParser(["affection", "aff"], shared = true)
fun actionAffection() = combinationParser {
    it.group(text(), text(), long().option(), command("marked", then = text()).option()).apply(it) { npcId, option, value, mark  ->
        now {
            val player = script().sender?.castSafely<Player>() ?: error("Player required")
            val helper = ChoTenDataHelper(player)

            // Mark guard: if this mark was already consumed by the same NPC, cancel the operation
            if (mark != null && helper.data.affectionMarks[mark]?.contains(npcId) == true) {
                return@now
            }

            when (option) {
                "set" -> helper.setAffection(npcId, value!!)
                "add" -> helper.addAffection(npcId, value!!)
                "remove" -> helper.removeAffection(npcId, value!!)
                "get" -> helper.getAffection(npcId)
                "reset" -> helper.resetAffection(npcId)
                else -> error("Unknown affection action: $option")
            }

            // Record the mark after a successful operation
            if (mark != null) {
                helper.data.affectionMarks.computeIfAbsent(mark) { mutableSetOf() }.add(npcId)
            }
        }
    }
}

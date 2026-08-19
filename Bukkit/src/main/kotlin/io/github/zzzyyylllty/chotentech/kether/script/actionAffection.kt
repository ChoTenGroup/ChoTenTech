package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.entity.Player
import taboolib.library.kether.ArgTypes
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionTake
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser
import java.util.concurrent.CompletableFuture

@KetherParser(["affection", "aff"], shared = true)
fun actionAffection() = scriptParser {
    val npcId = it.nextParsedAction()
    val option = it.nextParsedAction()
    val value = try {
        it.mark()
        it.next(ArgTypes.LONG)
    } catch (ex: Throwable) {
        it.reset()
        null
    }
    val mark = try {
        it.mark()
        it.expect("marked")
        it.next(ArgTypes.ACTION)
    } catch (ex: Throwable) {
        it.reset()
        null
    }
    actionTake {
        val sender = script().sender
        val markFuture = if (mark != null) run(mark) else CompletableFuture.completedFuture<Any?>(null)
        run(npcId).thenCompose { npcId ->
            run(option).thenCompose { option ->
                val player = sender?.castSafely<Player>() ?: error("Player required")
                val helper = ChoTenDataHelper(player)
                markFuture.thenApply { mark ->
                    applyAffection(helper, npcId.toString(), option.toString(), value, mark?.toString())
                }
            }
        }
    }
}

private fun applyAffection(helper: ChoTenDataHelper, npcId: String, option: String, value: Long?, mark: String?) {
    // Mark guard: if this mark was already consumed by the same NPC, cancel the operation
    if (mark != null && helper.data.affectionMarks[mark]?.contains(npcId) == true) {
        return
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

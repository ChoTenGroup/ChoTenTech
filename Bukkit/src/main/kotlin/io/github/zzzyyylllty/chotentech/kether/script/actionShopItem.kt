package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.library.kether.ArgTypes
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionTake
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser
import java.util.concurrent.CompletableFuture

@KetherParser(["shopitem"], shared = true)
fun actionShopItem() = scriptParser {
    val action = it.nextParsedAction()
    val shopId = it.nextParsedAction()
    val itemKey = it.nextParsedAction()
    val playerName = try {
        it.mark()
        it.next(ArgTypes.ACTION)
    } catch (ex: Throwable) {
        it.reset()
        null
    }
    actionTake {
        val sender = script().sender
        val playerFuture = if (playerName != null) run(playerName) else CompletableFuture.completedFuture<Any?>(null)
        run(action).thenCompose { a ->
            run(shopId).thenCompose { b ->
                run(itemKey).thenCompose { c ->
                    playerFuture.thenApply { p ->
                        applyShopItem(sender, a.toString(), b.toString(), c.toString(), p?.toString())
                    }
                }
            }
        }
    }
}

private fun applyShopItem(sender: ProxyCommandSender?, action: String, shopId: String, itemKey: String, playerName: String?) {
    val player = if (playerName != null) {
        Bukkit.getPlayerExact(playerName) ?: error("Player $playerName not found")
    } else {
        sender?.castSafely<Player>() ?: error("Player required")
    }
    val uuid = player.uniqueId.toString()
    val data = playerDataMap[uuid] ?: error("Player data not found for ${player.name}")
    val fullKey = "$shopId:$itemKey"

    when (action) {
        "unlock" -> data.unlockedShopItems.add(fullKey)
        "lock" -> data.unlockedShopItems.remove(fullKey)
        else -> error("Unknown shopitem action: $action (expected unlock/lock)")
    }
}

package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.module.kether.KetherParser
import taboolib.module.kether.combinationParser
import taboolib.module.kether.script

@KetherParser(["shopitem"], shared = true)
fun actionShopItem() = combinationParser {
    it.group(
        text(), // action: unlock / lock
        text(), // shopId
        text(), // itemKey
        text().option() // player name (optional)
    ).apply(it) { action, shopId, itemKey, playerName ->
        now {
            val player = if (playerName != null) {
                Bukkit.getPlayerExact(playerName) ?: error("Player $playerName not found")
            } else {
                script().sender?.castSafely<Player>() ?: error("Player required")
            }
            val uuid = player.uniqueId.toString()
            val data = playerDataMap[uuid] ?: error("Player data not found for ${player.name}")
            val fullKey = "$shopId:$itemKey"

            when (action) {
                "unlock" -> {
                    data.unlockedShopItems.add(fullKey)
                }
                "lock" -> {
                    data.unlockedShopItems.remove(fullKey)
                }
                else -> error("Unknown shopitem action: $action (expected unlock/lock)")
            }
        }
    }
}

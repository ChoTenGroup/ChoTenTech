package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.function.noclip.NoclipConditionChecker
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onSignalNoclipInteract(event: PlayerInteractEvent) {
    if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
    val player = event.player
    val handItem = player.inventory.itemInMainHand
    if (handItem.type.isAir) return

    val data = playerDataMap[player.uniqueId.toString()] ?: return
    val level = data.activeLevel ?: return

    for (noclip in level.noclips) {
        if (noclip.conditions?.signal == null) continue
        if (NoclipConditionChecker.trySignal(player, noclip, handItem)) return
    }
}

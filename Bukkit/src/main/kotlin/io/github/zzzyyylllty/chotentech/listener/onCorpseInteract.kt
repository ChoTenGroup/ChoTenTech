package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import io.github.zzzyyylllty.chotentech.gui.DeathDropMenu
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onCorpseInteract(event: PlayerInteractAtEntityEvent) {
    val stand = event.rightClicked as? ArmorStand ?: return
    val player = event.player

    // 检测到重启后残留的遗物 — 数据已丢失，直接清除
    if (!DeathDropManager.hasDeathDropKey(stand)) return
    val dropData = DeathDropManager.getDropData(stand)
    if (dropData == null) {
        DeathDropManager.removeStand(stand)
        player.sendMessage("§c这个遗物已过期。")
        return
    }

    event.isCancelled = true

    if (dropData.items.isEmpty()) {
        player.sendMessage("§c这些遗物已经没有物品了。")
        DeathDropManager.cleanup(dropData.entityUuid)
        return
    }

    DeathDropMenu(dropData).open(player)
}

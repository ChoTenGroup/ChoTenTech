package io.github.zzzyyylllty.chotentech.feature.projectile

import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.event.SubscribeEvent

object ProjectileListener {

    @SubscribeEvent
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val item = event.item ?: return
        if (item.type.isAir || item.amount <= 0) return
        val player = event.player

        val projectileId = Sertraline.api().getId(item) ?: return

        if (!ProjectileManager.hasProjectile(projectileId)) return

        event.isCancelled = true

        val visual = item.clone().apply { amount = 1 }
        item.amount--
        player.updateInventory()

        ProjectileManager.throwProjectile(player, projectileId, visual)
    }
}

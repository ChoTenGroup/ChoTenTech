package io.github.zzzyyylllty.chotentech.feature.projectile

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.GameMode
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
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

        // 基地区域禁止使用投掷物
        val regions = playerRegions[player.uniqueId.toString()]
        if (regions?.any { it.startsWith("base-") } == true) {
            player.sendMessage("§c基地内禁止使用投掷物")
            return
        }

        // 正在温雷中 → 投掷已烧过引信的雷
        if (ProjectileManager.isCooking(player)) {
            ProjectileManager.throwCookedProjectile(player, item)
            return
        }

        // 潜行 + 右键 → 开始温雷（手上燃烧引信）
        if (player.isSneaking) {
            ProjectileManager.startCooking(player, projectileId, item)
            return
        }

        // 普通投掷
        val visual = item.clone().apply { amount = 1 }
        if (player.gameMode != GameMode.CREATIVE) {
            item.amount--
            player.updateInventory()
        }
        ProjectileManager.throwProjectile(player, projectileId, visual)
    }

    @SubscribeEvent
    fun onPlayerHeldItem(event: PlayerItemHeldEvent) {
        ProjectileManager.cancelCooking(event.player)
    }

    @SubscribeEvent
    fun onPlayerQuit(e: PlayerQuitEvent) {
        ProjectileManager.cancelCooking(e.player, showMessage = false)
    }
}

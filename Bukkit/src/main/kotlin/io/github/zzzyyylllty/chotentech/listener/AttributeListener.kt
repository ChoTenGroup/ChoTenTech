package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

/**
 * 属性系统监听器
 */
object AttributeListener {

    @SubscribeEvent
    fun onJoin(e: PlayerJoinEvent) {
        // 清理残留的治疗减速修饰符
        AttributeManager.removeModifiersByAttribute(e.player.uniqueId, "movement_speed", ModifierSource.HEALING)
        // 初始化基础属性当前值
        AttributeManager.initBasicAttributeCurrentForPlayer(e.player.uniqueId)
        // 同步+缓存
        AttributeManager.updatePlayer(e.player, async = true)
    }

    @SubscribeEvent
    fun onRespawn(e: PlayerRespawnEvent) {
    }

    /** isdenymove >= 1.0 时仅拦截 XZ 平面移动(无法行走/转向),y 轴(跳跃/下落)与纯转头放行 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onMove(e: PlayerMoveEvent) {
        if (e.isCancelled) return
        val player = e.player
        if (AttributeManager.getAttributeValue(player, "deny_move") < 1.0) return
        val from = e.from
        val to = e.to ?: return
        // 仅 XZ 平面位置变化才拦截;纯 y 变化(跳跃/下落)与纯转头放行
        if (from.x == to.x && from.z == to.z) return
        e.isCancelled = true
        // 取消事件回滚位置快照;若 xz 仍有漂移(客户端本地预测),只回滚 xz 保留 y
        if (player.location.x != from.x || player.location.z != from.z) {
            player.teleport(from.clone().apply {
                y = player.location.y
                yaw = player.location.yaw
                pitch = player.location.pitch
            })
        }
    }

    @SubscribeEvent
    fun onQuit(e: PlayerQuitEvent) {
        AttributeManager.clearCache(e.player.uniqueId)
    }

    @SubscribeEvent
    fun onInventoryClose(e: InventoryCloseEvent) {
        val player = e.player
        if (player is Player) {
            AttributeManager.updatePlayer(player, async = true)
        }
    }

    @SubscribeEvent
    fun onItemHeld(e: PlayerItemHeldEvent) {
        AttributeManager.updatePlayer(e.player, async = true)
    }

    @SubscribeEvent
    fun onSwapHand(e: PlayerSwapHandItemsEvent) {
        AttributeManager.updatePlayer(e.player, async = false)
    }

    @SubscribeEvent
    fun onWorldChange(e: PlayerChangedWorldEvent) {
    }
}

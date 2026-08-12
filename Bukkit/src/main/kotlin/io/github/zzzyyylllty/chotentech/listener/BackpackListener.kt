package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.backpack.BackpackConfig
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackManager
import io.github.zzzyyylllty.chotentech.function.backpack.BackpackGUI
import io.github.zzzyyylllty.chotentech.function.backpack.BackpackGUI.openBackpack
import io.github.zzzyyylllty.chotentech.function.backpack.BackpackGUI.removePlayerSessions
import org.bukkit.Material
import org.bukkit.entity.ItemFrame
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag
import java.util.UUID

object BackpackListener {

    /** 由主类在 onEnable / reload 时调用 */
    fun init() {
        BackpackConfig.load()
    }

    /** 由主类在 onDisable 时调用 */
    fun disable() {
        BackpackGUI.saveAll()
    }

    /**
     * 检测物品是否为背包物品
     */
    private fun isBackpackItem(item: org.bukkit.inventory.ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        return item.getItemTag()[BackpackConfig.tag]?.asString() != null
    }

    /** 禁止将背包物品放入展示框等实体，防止刷物 */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val entity = event.rightClicked
        if (entity !is ItemFrame) return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (!isBackpackItem(item)) return

        event.isCancelled = true
        player.sendMessage("§c已禁止与本实体互动以防止刷物")
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        val tag = item.getItemTag()
        val typeId = tag[BackpackConfig.tag]?.asString() ?: return

        val type = BackpackConfig.getType(typeId)
        if (type == null) {
            player.sendMessage("§c未知的背包类型: $typeId")
            return
        }

        event.isCancelled = true

        // 确保每个物理背包物品有唯一 UUID，用于独立储存
        val itemUUID = tag[BackpackConfig.uuidTag]?.asString() ?: run {
            val newUUID = UUID.randomUUID().toString()
            tag[BackpackConfig.uuidTag] = newUUID
            tag.saveTo(item)
            newUUID
        }

        // 刷物检测：检查同一 UUID 是否已被其他玩家打开
        val previousHolder = BackpackManager.activeSessions[itemUUID]
        if (previousHolder != null && previousHolder != player.uniqueId) {
            val bp = BackpackManager.getOrCreateBackpack(itemUUID, type.id, type.slots)
            val flagged = BackpackManager.flagDupe(bp, player.uniqueId, previousHolder)
            BackpackManager.updateBackpack(flagged)
            player.sendMessage("§c此背包已被禁用（检测到疑似刷物行为）")
            return
        }

        player.openBackpack(type, itemUUID)
    }

    /** 玩家退出时清理其所有活跃背包会话 */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        removePlayerSessions(event.player.uniqueId)
    }
}

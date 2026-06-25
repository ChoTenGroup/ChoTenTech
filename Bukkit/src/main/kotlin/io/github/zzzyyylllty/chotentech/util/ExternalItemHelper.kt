package io.github.zzzyyylllty.chotentech.util

import cn.gtemc.itembridge.api.ItemBridge
import cn.gtemc.itembridge.core.BukkitItemBridge
import ink.ptms.chemdah.taboolib.common.platform.Plugin
import io.github.zzzyyylllty.chotentech.logger.infoS
import io.github.zzzyyylllty.chotentech.logger.severeS
import io.github.zzzyyylllty.sertraline.util.ExternalItemHelper.itemBridgeAll
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack


object ExternalItemHelper {
    var itemBridge: ItemBridge<ItemStack?, Player?>? =
        BukkitItemBridge.builder()
            .onHookSuccess({ p -> infoS("Hooked External item source: $p") })
            .onHookFailure({ p, e -> severeS("Failed to hook External item " + p + ", because " + e.message) })
            .detectSupportedPlugins()
            .build()
    fun build(player: Player, plugin: String, name: String): ItemStack? {
        return try {
            itemBridgeAll?.build(plugin, player, name)?.get()
        } catch (e: NoSuchElementException) {
            null
        }
    }
}
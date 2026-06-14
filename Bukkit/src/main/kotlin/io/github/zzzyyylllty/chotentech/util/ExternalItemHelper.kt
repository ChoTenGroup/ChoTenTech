package io.github.zzzyyylllty.chotentech.util

import cn.gtemc.itembridge.api.ItemBridge
import cn.gtemc.itembridge.core.BukkitItemBridge
import io.github.zzzyyylllty.chotentech.logger.infoS
import io.github.zzzyyylllty.chotentech.logger.severeS
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack


object ExternalItemHelper {
    var itemBridge: ItemBridge<ItemStack?, Player?>? =
        BukkitItemBridge.builder()
            .onHookSuccess({ p -> infoS("Hooked External item source: $p") })
            .onHookFailure({ p, e -> severeS("Failed to hook External item " + p + ", because " + e.message) })
            .detectSupportedPlugins()
            .build()

}
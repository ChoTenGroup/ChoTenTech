package io.github.zzzyyylllty.chotentech.util

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack



object ExternalItemHelper {

    fun build(player: Player?, plugin: String, id: String): ItemStack? {
        return try {
            player?.let { io.github.zzzyyylllty.sertraline.util.ExternalItemHelper.build(it, plugin, id) }
                ?: io.github.zzzyyylllty.sertraline.util.ExternalItemHelper.buildNoPlayer(plugin, id)
        } catch (e: NoSuchElementException) {
            null
        }
    }

}
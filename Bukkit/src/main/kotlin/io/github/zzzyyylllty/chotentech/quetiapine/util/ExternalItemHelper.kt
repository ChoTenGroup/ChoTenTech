package io.github.zzzyyylllty.chotentech.quetiapine.util

import cn.gtemc.itembridge.core.BukkitItemBridge
import io.github.zzzyyylllty.chotentech.quetiapine.logger.severeS
import io.github.zzzyyylllty.sertraline.logger.infoS
import io.github.zzzyyylllty.sertraline.util.ExternalItemHelper
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack



//object ExternalItemHelper {
//
//    private val itemBridge: Any? by lazy {
//        try {
//            BukkitItemBridge.builder()
//                .detectSupportedPlugins()
//                .build()
//        } catch (e: Throwable) {
//            severeS("ItemBridge not available: ${e.message}")
//            null
//        }
//    }
//
//    fun build(player: Player, plugin: String, name: String): ItemStack? {
//        val bridge = itemBridge ?: return null
//        return try {
//            bridge.javaClass.getMethod("build", String::class.java, Any::class.java, String::class.java)
//                .invoke(bridge, plugin, player, name)
//                .let { (it as? java.util.Optional<*>)?.get() as? ItemStack }
//        } catch (e: Throwable) {
//            severeS("ItemBridge build error for $plugin:$name — ${e::class.simpleName}: ${e.message}")
//            null
//        }
//    }
//}

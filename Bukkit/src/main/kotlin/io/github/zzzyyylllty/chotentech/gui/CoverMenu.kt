package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.math.ceil

class CoverMenu {

    private val coverTitle by lazy { config.getString("insurance.cover-title", "取回物品") ?: "取回物品" }

    fun open(player: Player) {
        val data = player.getData() ?: return
        val items = data.returnedInsuranceItems.toMutableList()

        if (items.isEmpty()) {
            player.sendMessage("§c没有待取回的物品。")
            return
        }

        val rows = ceil(items.size / 9.0).toInt().coerceIn(1, 6)

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${coverTitle}".toComponent())) {
            rows(rows.coerceAtMost(6))

            onBuild { _, inv ->
                items.forEachIndexed { index, serialized ->
                    val item = ItemSerializer.deserializeAny(serialized)
                    if (item != null && index < inv.size) {
                        inv.setItem(index, item)
                    }
                }
            }

            onClick(lock = false) { event ->
                val slot = event.rawSlot
                if (slot < 0 || slot >= event.view.topInventory.size) {
                    event.isCancelled = true
                    return@onClick
                }
                // 允许自由取走物品
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val pd = p.getData() ?: return@onClose

                // 重新序列化剩余物品
                val remaining = mutableListOf<String>()
                val topInv = event.view.topInventory
                for (slot in 0 until topInv.size) {
                    val item = topInv.getItem(slot)
                    if (item != null && !item.type.isAir) {
                        ItemSerializer.serializeNbt(item)?.let { remaining.add(it) }
                    }
                }
                pd.returnedInsuranceItems.clear()
                pd.returnedInsuranceItems.addAll(remaining)
                saveData(p)
            }
        }
    }
}

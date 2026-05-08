package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.entity.Player
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.math.ceil

class SecureContainerMenu {

    companion object {
        private fun slots(): Int = config.getInt("secure-container.slots", 9).coerceIn(1, 54)
        private fun title(): String = config.getString("secure-container.title", "安全箱") ?: "安全箱"
    }

    fun open(player: Player) {
        val data = player.getData() ?: return
        val maxSlots = slots()
        val rowCount = ceil(maxSlots / 9.0).toInt()

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${title()}".toComponent())) {
            rows(rowCount)

            onBuild { _, inv ->
                val contents = data.secureContainer
                contents.forEach { (slot, base64) ->
                    if (slot < maxSlots) {
                        ItemSerializer.deserialize(base64)?.let { inv.setItem(slot, it) }
                    }
                }
            }

            onClick(lock = false) { event ->
                val slot = event.rawSlot
                if (slot < 0 || slot >= maxSlots) {
                    event.isCancelled = true
                }
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val pd = p.getData() ?: return@onClose

                val contents = linkedMapOf<Int, String>()
                for (slot in 0 until maxSlots) {
                    val item = event.inventory.getItem(slot)
                    if (item != null && !item.type.isAir) {
                        ItemSerializer.serialize(item)?.let { contents[slot] = it }
                    }
                }
                pd.secureContainer = contents
                saveData(p)
            }
        }
    }
}

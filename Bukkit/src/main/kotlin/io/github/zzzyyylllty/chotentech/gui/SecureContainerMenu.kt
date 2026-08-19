package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.ClickType
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

class SecureContainerMenu {

    companion object {
        private val CONTENT_SLOTS = listOf(
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        private fun slots(): Int = config.getInt("secure-container.slots", 9).coerceIn(1, CONTENT_SLOTS.size)
        private fun title(): String = config.getString("secure-container.title", "安全箱") ?: "安全箱"
    }

    fun open(player: Player) {
        val data = player.getData() ?: return
        val maxSlots = slots()
        val lockedItem = guiItem(XMaterial.GRAY_STAINED_GLASS_PANE, "<gray>未解锁格", listOf("<!i><dark_gray>该格暂不可用"))

        fun loadContents(inv: Inventory) {
            CONTENT_SLOTS.forEachIndexed { index, rawSlot ->
                if (index >= maxSlots) {
                    inv.setItem(rawSlot, lockedItem)
                    return@forEachIndexed
                }
                val item = data.secureContainer[index]?.let { ItemSerializer.deserializeAny(it) }
                inv.setItem(rawSlot, item)
            }
        }

        fun isLockedSlot(rawSlot: Int): Boolean {
            val index = CONTENT_SLOTS.indexOf(rawSlot)
            return index >= maxSlots
        }

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${title()}".toComponent())) {
            rows(6)
            handLocked(false)

            map(
                "CCC------",
                "---------",
                "-.......-",
                "-.......-",
                "-.......-",
                "---------"
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<!i> <yellow>点击 <green>打开菜单"), model = "kangelitem:void"))
            set('-', guiItem(XMaterial.COMPASS, "<aqua><bold> ", listOf("<!i> <dark_gray>这里什么都没有"), model = "kangelitem:void"))

            onBuild { _, inv -> loadContents(inv) }

            onClick(lock = false) { event ->
                if (event.clickType == ClickType.DRAG) {
                    val drag = event.dragEventOrNull()
                    if (drag != null && drag.rawSlots.any(::isLockedSlot)) {
                        event.isCancelled = true
                        event.clicker.playSound(event.clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        return@onClick
                    }
                }

                val rawSlot = event.rawSlot
                if (rawSlot < 0 || rawSlot >= event.view.topInventory.size) return@onClick
                if (isLockedSlot(rawSlot)) {
                    event.isCancelled = true
                    event.clicker.playSound(event.clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    return@onClick
                }

                when (getSlot(rawSlot)) {
                    'C' -> {
                        event.isCancelled = true
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${event.clicker.name}")
                        event.clicker.closeInventory()
                    }
                    '-' -> event.isCancelled = true
                }
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val pd = p.getData() ?: return@onClose
                val topInv = event.view.topInventory
                val contents = linkedMapOf<Int, String>()

                CONTENT_SLOTS.forEachIndexed { index, rawSlot ->
                    if (index >= maxSlots) return@forEachIndexed
                    val item = topInv.getItem(rawSlot)
                    if (item != null && !item.type.isAir) {
                        ItemSerializer.serializeNbt(item)?.let { contents[index] = it }
                    }
                }
                pd.secureContainer = contents
                saveData(p)
            }
        }
    }
}

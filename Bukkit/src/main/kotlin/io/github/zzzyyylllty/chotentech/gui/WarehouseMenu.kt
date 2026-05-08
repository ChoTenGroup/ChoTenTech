package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.command.WarehouseCommand
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.data.WarehouseConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import taboolib.library.xseries.XMaterial
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

class WarehouseMenu {

    companion object {
        private const val ITEMS_PER_PAGE = 21
        private val CONTENT_SLOTS = listOf(
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        /** Convert page + content slot index to a fixed warehouse key (1-based). */
        private fun fixedKey(page: Int, slotIndex: Int): Int {
            return page * ITEMS_PER_PAGE + slotIndex + 1
        }

        /** Total pages needed to hold maxItems items. */
        private fun totalPagesByCapacity(maxItems: Int): Int {
            return ((maxItems + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE).coerceAtLeast(1)
        }
    }

    fun open(player: Player) {
        val data = player.getData() ?: return
        val baseIdef = data.subTag ?: WarehouseCommand.getCurrentBaseIdef(player) ?: run {
            player.sendMessage("§c你没有加入任何基地，也无法检测到所在的基地！")
            return
        }
        open(player, baseIdef)
    }

    fun open(player: Player, baseIdef: String) {
        val data = player.getData() ?: return
        val maxItems = WarehouseConfig.getMaxSlots(player, baseIdef)
        val title = WarehouseConfig.getTitle(baseIdef)
        val contents = data.warehouseContents.getOrPut(baseIdef) { linkedMapOf() }
        var currentPage = 0

        fun totalPages(): Int = totalPagesByCapacity(maxItems)

        fun loadPage(inv: Inventory) {
            CONTENT_SLOTS.forEachIndexed { i, slot ->
                val item = contents[fixedKey(currentPage, i)]?.let { ItemSerializer.deserialize(it) }
                inv.setItem(slot, item)
            }
        }

        fun savePage(inv: Inventory) {
            CONTENT_SLOTS.forEachIndexed { i, slot ->
                val key = fixedKey(currentPage, i)
                val item = inv.getItem(slot)

                if (item == null || item.type.isAir) {
                    contents.remove(key)
                } else if (!contents.containsKey(key)) {
                    // New item in a previously empty slot — check capacity
                    if (contents.size >= maxItems) {
                        inv.setItem(slot, null)
                    } else {
                        ItemSerializer.serialize(item)?.let { contents[key] = it }
                    }
                } else {
                    ItemSerializer.serialize(item)?.let { contents[key] = it }
                }
            }
        }

        fun updateInfo(inv: Inventory) {
            val pages = totalPages()
            inv.setItem(7, buildItem(if (currentPage > 0) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                name = if (currentPage > 0) "§a上一页" else "§7已是第一页"
            }.apply {
                val m = itemMeta ?: return@apply
                m.itemModel = NamespacedKey.fromString(if (currentPage > 0) "kangelitem:previous" else "kangelitem:previous_no")
                setItemMeta(m)
            })
            inv.setItem(49, buildItem(XMaterial.CHEST) {
                name = "§e仓库信息"
                lore += "§7物品: §e${contents.size} §7/ §e$maxItems"
                lore += "§7页数: §e${currentPage + 1} §7/ §e$pages"
            })
            inv.setItem(8, buildItem(if (currentPage < pages - 1) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                name = if (currentPage < pages - 1) "§a下一页" else "§7已是最后一页"
            }.apply {
                val m = itemMeta ?: return@apply
                m.itemModel = NamespacedKey.fromString(if (currentPage < pages - 1) "kangelitem:next" else "kangelitem:next_no")
                setItemMeta(m)
            })
        }

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${title}".toComponent())) {
            rows(6)
            handLocked(false)

            map(
                "CCC   D<>",
                "         ",
                " ....... ",
                " ....... ",
                " ....... ",
                "         "
            )

            set('C', buildItem(XMaterial.COMPASS) {
                name = "§b§l菜单"
                lore += "§7点击打开菜单"
            }.apply {
                val m = itemMeta ?: return@apply
                m.itemModel = NamespacedKey.fromString("kangelitem:void")
                setItemMeta(m)
            })

            onBuild { _, inv ->
                loadPage(inv)
                updateInfo(inv)
            }

            onClick(lock = false) { event ->
                val rawSlot = event.rawSlot
                if (rawSlot < 0 || rawSlot >= event.view.topInventory.size) return@onClick

                when (getSlot(rawSlot)) {
                    'C' -> {
                        event.isCancelled = true
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${event.clicker.name}")
                        event.clicker.closeInventory()
                    }
                    '<' -> {
                        event.isCancelled = true
                        if (currentPage > 0) {
                            event.clicker.playSound(event.clicker.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
                            savePage(event.view.topInventory)
                            currentPage--
                            loadPage(event.view.topInventory)
                            updateInfo(event.view.topInventory)
                        }
                    }
                    '>' -> {
                        event.isCancelled = true
                        if (currentPage < totalPages() - 1) {
                            event.clicker.playSound(event.clicker.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
                            savePage(event.view.topInventory)
                            currentPage++
                            loadPage(event.view.topInventory)
                            updateInfo(event.view.topInventory)
                        }
                    }
                    'D' -> {
                        event.isCancelled = true
                        event.clicker.playSound(event.clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    }
                }
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val pd = p.getData() ?: return@onClose
                savePage(event.inventory)
                pd.warehouseContents[baseIdef] = contents
                saveData(p)
            }
        }
    }
}

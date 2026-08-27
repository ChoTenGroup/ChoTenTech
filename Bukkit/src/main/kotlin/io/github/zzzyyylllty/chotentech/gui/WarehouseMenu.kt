package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.command.WarehouseCommand
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.data.WarehouseConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import taboolib.library.xseries.XMaterial
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import taboolib.module.ui.ClickType
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo

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
        // 容量外的格子:屏障标记,不可使用
        val lockedItem = guiItem(XMaterial.LIGHT_GRAY_STAINED_GLASS_PANE, "<#E08A98>未解锁格", listOf("<!i><#A9B4C3>该格暂不可用"))

        fun totalPages(): Int = totalPagesByCapacity(maxItems)

        /** 该 raw slot 是否为容量外的屏障格 */
        fun isUnavailableSlot(rawSlot: Int): Boolean {
            val slotIndex = CONTENT_SLOTS.indexOf(rawSlot)
            return slotIndex != -1 && fixedKey(currentPage, slotIndex) > maxItems
        }

        fun loadPage(inv: Inventory) {
            CONTENT_SLOTS.forEachIndexed { i, slot ->
                val key = fixedKey(currentPage, i)
                if (key > maxItems) {
                    inv.setItem(slot, lockedItem)
                    return@forEachIndexed
                }
                // 先按 NBT 反序列化(1.21.4 组件系统),失败回退旧 Gson 格式(存量数据)
                val item = contents[key]?.let { ItemSerializer.deserializeNbt(it) ?: ItemSerializer.deserialize(it) }
                inv.setItem(slot, item)
            }
        }

        fun savePage(inv: Inventory) {
            CONTENT_SLOTS.forEachIndexed { i, slot ->
                val key = fixedKey(currentPage, i)
                if (key > maxItems) return@forEachIndexed
                val item = inv.getItem(slot)

                if (item == null || item.type.isAir) {
                    contents.remove(key)
                } else if (!contents.containsKey(key)) {
                    // New item in a previously empty slot — check capacity
                    if (contents.size >= maxItems) {
                        inv.setItem(slot, null)
                    } else {
                        ItemSerializer.serializeNbt(item)?.let { contents[key] = it }
                    }
                } else {
                    ItemSerializer.serializeNbt(item)?.let { contents[key] = it }
                }
            }
        }

        fun updateInfo(inv: Inventory) {
            val pages = totalPages()
            inv.setItem(7, guiItem(
                if (currentPage > 0) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE,
                if (currentPage > 0) "<#8FD4A6>上一页" else "<#A9B4C3>已是第一页",
                model = if (currentPage > 0) "kangelitem:previous" else "kangelitem:previous_no"
            ))
            inv.setItem(49, guiItem(XMaterial.CHEST, "<#EFDF8F>仓库信息", listOf(
                loreInfo("物品", "<#EFDF8F>${contents.size} <#A9B4C3>/ <#EFDF8F>$maxItems"),
                loreInfo("页数", "<#EFDF8F>${currentPage + 1} <#A9B4C3>/ <#EFDF8F>$pages"),
                loreInfo("额外说明", " "),
                loreInfo("存储", "<#E08A98>不同基地之间的仓库独立"),
                loreInfo("拓展", "<#84D1CB>可通过完成特殊任务、取得特殊权限拓展仓库")
            )))
            inv.setItem(8, guiItem(
                if (currentPage < pages - 1) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE,
                if (currentPage < pages - 1) "<#8FD4A6>下一页" else "<#A9B4C3>已是最后一页",
                model = if (currentPage < pages - 1) "kangelitem:next" else "kangelitem:next_no"
            ))
        }

        player.openMenu<Chest>(mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>${title}".toComponent())) {
            rows(6)
            handLocked(false)

            map(
                "CCC---D<>",
                "---------",
                "-.......-",
                "-.......-",
                "-.......-",
                "---------",
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>菜单", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>打开菜单"), model = "kangelitem:void"))
            set('-', guiItem(XMaterial.COMPASS, "<#84D1CB><bold> ", listOf("<!i> <#6E7A8C>这里什么都没有"), model = "kangelitem:void"))
            set('D', guiItem(XMaterial.COMPASS, "<#84D1CB><bold> ", listOf("<!i> <#6E7A8C>这里什么都没有"), model = "kangelitem:void"))

            onBuild { _, inv ->
                loadPage(inv)
                updateInfo(inv)
            }

            onClick(lock = false) { event ->
                // 拖拽:一次拖拽可能跨多个格子(多格时 rawSlot = -1),只要涉及屏障格就整体取消
                if (event.clickType == ClickType.DRAG) {
                    val drag = event.dragEventOrNull()
                    if (drag != null && drag.rawSlots.any { isUnavailableSlot(it) }) {
                        event.isCancelled = true
                        event.clicker.playSound(event.clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        return@onClick
                    }
                }

                val rawSlot = event.rawSlot
                // 负数(-1/-2/-999 等)不是容器内的格子,不涉及屏障
                if (rawSlot < 0 || rawSlot >= event.view.topInventory.size) return@onClick

                // 容量外的屏障格:禁止放入/取走
                if (isUnavailableSlot(rawSlot)) {
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
                    '-' -> {
                        // 填充格：禁止交互
                        event.isCancelled = true
                    }
                }
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val pd = p.getData() ?: return@onClose
                savePage(event.view.topInventory)
                pd.warehouseContents[baseIdef] = contents
                saveData(p)
            }
        }
    }
}

package io.github.zzzyyylllty.chotentech.attribute.crossbow

import io.github.zzzyyylllty.chotentech.attribute.logger.sendStringAsComponent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import io.github.zzzyyylllty.chotentech.util.guiItem

/**
 * 箭矢夹GUI
 * 玩家右键箭矢夹时打开，可放入合法箭矢（带有carrow NBT的物品）
 * 上方2行展示已装填的箭矢（槽位10-17, 19-26），底部为玩家背包
 * 防刷物：每次操作验证箭矢夹是否仍然存在且amount>0
 */
object ArrowMagazineGUI {

    private val ARROW_SLOTS = (10..17).toList() + (19..26).toList()

    fun open(player: Player, magazine: ItemStack, magazineSlot: Int) {
        val contents = CrossbowManager.getMagazineContents(magazine)

        player.openMenu<Chest>("§8箭矢夹") {
            rows(4)
            handLocked(false)

            map(
                "#########",
                "#       #",
                "#       #",
                "#########"
            )

            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set(31, guiItem(XMaterial.BARRIER, "<red>关闭"))

            // 填充装弹区
            onBuild { _, inventory ->
                renderArrowSlots(inventory, contents)
            }

            // 全局点击处理
            onClick(lock = false) { event ->
                if (validateMagazine(player, magazineSlot)) {
                    event.isCancelled = true
                    return@onClick
                }

                val topSize = event.view.topInventory.size // 36
                val rawSlot = event.rawSlot

                if (rawSlot < topSize) {
                    // 点击GUI区域
                    event.isCancelled = true
                    val slotIndex = ARROW_SLOTS.indexOf(rawSlot)
                    if (slotIndex >= 0) {
                        handleTakeOut(player, magazine, magazineSlot, slotIndex)
                    }
                } else {
                    // 点击玩家背包区域
                    event.isCancelled = true
                    val clicked = event.cursorItem ?: event.currentItem ?: return@onClick
                    if (!CrossbowManager.isArrowItem(clicked)) return@onClick

                    val curMag = player.inventory.getItem(magazineSlot)?.let {
                        if (CrossbowManager.isArrowMagazine(it)) it else null
                    } ?: run {
                        player.sendStringAsComponent("§c箭矢夹物品不存在！")
                        player.closeInventory()
                        return@onClick
                    }

                    val curContents = CrossbowManager.getMagazineContents(curMag)
                    if (curContents.size >= CrossbowManager.MAGAZINE_CAPACITY) {
                        player.sendStringAsComponent("§c箭矢夹已满！")
                        return@onClick
                    }

                    // 放入箭矢并扣减来源
                    val arrowToAdd = clicked.clone().apply { amount = 1 }
                    val newContents = curContents + arrowToAdd
                    val newMag = CrossbowManager.setMagazineContents(curMag, newContents)
                    clicked.amount -= 1
                    player.inventory.setItem(magazineSlot, newMag)

                    submit(delay = 1) {
                        if (player.isOnline) open(player, newMag, magazineSlot)
                    }
                }
            }
        }
    }

    /** 填充装弹区物品 */
    private fun renderArrowSlots(inventory: org.bukkit.inventory.Inventory, contents: List<ItemStack>) {
        for (i in 0 until CrossbowManager.MAGAZINE_CAPACITY) {
            val slot = ARROW_SLOTS[i]
            if (i < contents.size) {
                val arrow = contents[i].clone().apply {
                    itemMeta = itemMeta?.also { meta ->
                        val lore = meta.lore?.toMutableList() ?: mutableListOf()
                        lore.add("")
                        lore.add("§7右键取出")
                        meta.lore = lore
                    }
                }
                inventory.setItem(slot, arrow)
            } else {
                inventory.setItem(slot, XMaterial.AIR.parseItem())
            }
        }
    }

    /** 从箭矢夹取出一支箭 */
    private fun handleTakeOut(player: Player, magazine: ItemStack, magazineSlot: Int, index: Int) {
        val curMag = player.inventory.getItem(magazineSlot)?.let {
            if (CrossbowManager.isArrowMagazine(it)) it else null
        } ?: return

        val contents = CrossbowManager.getMagazineContents(curMag)
        if (index >= contents.size) return

        val removed = contents[index].clone()
        val leftover = player.inventory.addItem(removed)
        if (leftover.isNotEmpty()) {
            player.world.dropItem(player.location, leftover.values.first())
        }

        val newContents = contents.toMutableList().apply { removeAt(index) }
        val newMag = CrossbowManager.setMagazineContents(curMag, newContents)
        player.inventory.setItem(magazineSlot, newMag)

        submit(delay = 1) {
            if (player.isOnline) open(player, newMag, magazineSlot)
        }
    }

    /** 验证箭矢夹是否有效 */
    private fun validateMagazine(player: Player, magazineSlot: Int): Boolean {
        val item = when {
            magazineSlot in 0..35 -> player.inventory.getItem(magazineSlot)
            magazineSlot == 40 -> player.inventory.itemInOffHand.takeIf { it.type != Material.AIR }
            else -> null
        }
        if (item == null || item.type.isAir || item.amount <= 0 || !CrossbowManager.isArrowMagazine(item)) {
            player.sendStringAsComponent("§c箭矢夹物品不存在！")
            player.closeInventory()
            return true
        }
        return false
    }

    /** 查找玩家背包中的箭矢夹所在槽位 */
    fun findMagazineSlot(player: Player): Int {
        for (i in 0 until 36) {
            val item = player.inventory.getItem(i)
            if (item != null && CrossbowManager.isArrowMagazine(item)) return i
        }
        if (CrossbowManager.isArrowMagazine(player.inventory.itemInOffHand)) return 40
        return -1
    }
}

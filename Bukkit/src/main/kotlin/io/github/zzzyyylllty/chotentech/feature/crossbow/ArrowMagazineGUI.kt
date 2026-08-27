package io.github.zzzyyylllty.chotentech.feature.crossbow

import io.github.zzzyyylllty.chotentech.logger.sendStringAsComponent
import io.github.zzzyyylllty.sertraline.Sertraline
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
 * 玩家右键箭矢夹时打开，可放入合法箭矢（由 sertraline_id 识别）
 * 上方2行展示已装填的箭矢（槽位10-17, 19-26），底部为玩家背包
 */
object ArrowMagazineGUI {

    private val ARROW_SLOTS = (10..17).toList() + (19..26).toList()

    fun open(player: Player, magazine: ItemStack, magazineSlot: Int) {
        val arrowIds = CrossbowManager.getMagazineContents(magazine)
        val capacity = CrossbowManager.getMagazineCapacity(magazine)

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
            set(31, guiItem(XMaterial.BARRIER, "<#E08A98>关闭"))

            onBuild { _, inventory ->
                renderArrowSlots(player, inventory, arrowIds, capacity)
            }

            onClick(lock = false) { event ->
                if (validateMagazine(player, magazineSlot)) {
                    event.isCancelled = true
                    return@onClick
                }

                val topSize = event.view.topInventory.size
                val rawSlot = event.rawSlot

                if (rawSlot < topSize) {
                    event.isCancelled = true
                    val slotIndex = ARROW_SLOTS.indexOf(rawSlot)
                    if (slotIndex >= 0) {
                        handleTakeOut(player, magazineSlot, slotIndex)
                    }
                } else {
                    event.isCancelled = true
                    val clicked = event.cursorItem ?: event.currentItem ?: return@onClick
                    val arrowId = Sertraline.api().getId(clicked) ?: return@onClick
                    if (!CrossbowManager.hasArrow(arrowId)) return@onClick

                    val curMag = player.inventory.getItem(magazineSlot)?.let {
                        if (CrossbowManager.isArrowMagazine(it)) it else null
                    } ?: run {
                        player.sendStringAsComponent("§c箭矢夹物品不存在！")
                        player.closeInventory()
                        return@onClick
                    }

                    val curIds = CrossbowManager.getMagazineContents(curMag)
                    if (curIds.size >= capacity) {
                        player.sendStringAsComponent("§c箭矢夹已满！")
                        return@onClick
                    }

                    val newIds = curIds + arrowId
                    val newMag = CrossbowManager.setMagazineContents(curMag, newIds)
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
    private fun renderArrowSlots(player: Player, inventory: org.bukkit.inventory.Inventory, arrowIds: List<String>, capacity: Int) {
        for (i in 0 until capacity) {
            val slot = ARROW_SLOTS[i]
            if (i < arrowIds.size) {
                val arrowItem = buildDisplayArrow(player, arrowIds[i])
                inventory.setItem(slot, arrowItem)
            } else {
                inventory.setItem(slot, XMaterial.AIR.parseItem())
            }
        }
    }

    /** 构建带 lore 提示的箭矢展示物品 */
    private fun buildDisplayArrow(player: Player, arrowId: String): ItemStack {
        val base = CrossbowManager.buildArrowItem(arrowId, player)
            ?: ItemStack(Material.ARROW).also {
                val meta = it.itemMeta ?: return@also
                meta.setDisplayName("§f$arrowId")
                it.itemMeta = meta
            }
        return base.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = meta.lore?.toMutableList() ?: mutableListOf()
                lore.add("")
                lore.add("§7右键取出")
                meta.lore = lore
            } ?: return@apply
        }
    }

    /** 从箭矢夹取出一支箭 */
    private fun handleTakeOut(player: Player, magazineSlot: Int, index: Int) {
        val curMag = player.inventory.getItem(magazineSlot)?.let {
            if (CrossbowManager.isArrowMagazine(it)) it else null
        } ?: return

        val arrowIds = CrossbowManager.getMagazineContents(curMag)
        if (index >= arrowIds.size) return

        val arrowId = arrowIds[index]
        val item = CrossbowManager.buildArrowItem(arrowId, player) ?: ItemStack(Material.ARROW).also {
            val meta = it.itemMeta ?: return@also
            meta.setDisplayName("§f$arrowId")
            it.itemMeta = meta
        }

        val leftover = player.inventory.addItem(item)
        if (leftover.isNotEmpty()) {
            player.world.dropItem(player.location, leftover.values.first())
        }

        val newIds = arrowIds.toMutableList().apply { removeAt(index) }
        val newMag = CrossbowManager.setMagazineContents(curMag, newIds)
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

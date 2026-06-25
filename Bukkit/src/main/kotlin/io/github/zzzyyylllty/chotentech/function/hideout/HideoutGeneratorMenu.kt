package io.github.zzzyyylllty.chotentech.function.hideout

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

class HideoutGeneratorMenu(private val baseKey: String) {

    private val fuelSlots = listOf(11, 12, 13, 14, 15)

    fun open(player: Player) {
        val data = player.getData() ?: return
        val hideout = data.hideoutData[baseKey] ?: return

        player.openMenu<Chest>(mmJsonUtil.serialize(
            "<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>发电机 - 燃料管理".toComponent()
        )) {
            rows(6)
            handLocked(false)

            map(
                "CCC     <>",
                "         ",
                "  FFFFF  ",
                "         ",
                "    I    ",
                "         "
            )

            set('C', ItemStack(Material.COMPASS).apply {
                editMeta { m ->
                    m.displayName("<b><aqua>返回".toComponent())
                    m.lore(listOf("<gray>返回基地总览".toComponent()))
                    m.itemModel = NamespacedKey.fromString("kangelitem:void")
                }
            }) {
                clicker.closeInventory()
                HideoutMainMenu(baseKey).open(clicker)
            }

            onBuild { _, inv ->
                for (i in fuelSlots.indices) {
                    val idx = i
                    val itemStr = hideout.fuelInventory[idx]
                    val item = if (itemStr != null) {
                        ItemSerializer.deserializeNbt(itemStr) ?: buildPlaceholder(idx)
                    } else buildPlaceholder(idx)
                    inv.setItem(fuelSlots[idx], item)
                }
                inv.setItem(40, buildStatusItem(hideout))
            }

            onClick(lock = false) { event ->
                val raw = event.rawSlot
                if (raw < 0 || raw >= event.view.topInventory.size) return@onClick
                event.isCancelled = true

                val idx = fuelSlots.indexOf(raw)
                if (idx < 0) return@onClick

                val existing = hideout.fuelInventory[idx]
                if (existing != null) {
                    val item = ItemSerializer.deserializeNbt(existing)
                    if (item != null) {
                        hideout.fuelInventory.remove(idx)
                        event.clicker.inventory.addItem(item).values.forEach { leftover ->
                            event.clicker.world.dropItem(event.clicker.location, leftover)
                        }
                        event.clicker.playSound(event.clicker.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1f)
                    }
                } else {
                    val heldSlot = event.clicker.inventory.heldItemSlot
                    val held = event.clicker.inventory.getItem(heldSlot)
                    if (held != null && !held.type.isAir) {
                        val fuel = HideoutConfig.getFuelTypeByItem(held)
                        if (fuel != null) {
                            val clone = held.clone()
                            clone.amount = 1
                            ItemSerializer.serializeNbt(clone)?.let { hideout.fuelInventory[idx] = it }
                            held.amount--
                            if (held.amount <= 0) event.clicker.inventory.setItem(heldSlot, null)
                            event.clicker.playSound(event.clicker.location, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1f)
                            event.clicker.sendComponent("<green>已放入燃料: ${fuel.sertralineId?.let { ItemNameHelper.getDisplayName(it) } ?: fuel.id}")
                        } else {
                            event.clicker.sendComponent("<red>该物品不能作为燃料！")
                        }
                    }
                }
            }
        }
    }

    private fun buildPlaceholder(slot: Int): ItemStack = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
        editMeta { m ->
            m.displayName("<gray>燃料槽 ${slot + 1}".toComponent())
            m.lore(listOf("<gray>放入可燃烧的物品".toComponent()))
        }
    }

    private fun buildStatusItem(hideout: HideoutPlayerData): ItemStack = ItemStack(Material.FURNACE).apply {
        editMeta { m ->
            val capacity = HideoutManager.getFuelCapacity(hideout)
            val usage = HideoutManager.getEffectiveUsage(hideout)
            val running = hideout.fuelAmount > 0
            m.displayName("<gold>发电机状态".toComponent())
            val lore = mutableListOf(
                "<!i><gray>燃料: <yellow>${"%.1f".format(hideout.fuelAmount)} <gray>/ <yellow>${"%.0f".format(capacity)}",
                "<!i><gray>状态: ${if (running) "<green>运行中" else "<red>已停机"}",
                "<!i>",
                "<!i><gray>总消耗: <yellow>${"%.1f".format(usage)} <gray>/秒",
            ).map { it.toComponent() }.toMutableList()
            if (usage > 0) {
                lore += "<!i><gray>预计剩余: <yellow>${"%.0f".format(hideout.fuelAmount / usage)} <gray>秒".toComponent()
            }
            val save = HideoutManager.getFuelSavePercent(hideout)
            if (save > 0) {
                lore += "<!i><gray>燃料节省: <green>$save%".toComponent()
            }
            lore += "<!i>".toComponent()
            lore += "<!i><gray>提示: 手持燃料点击槽位放入".toComponent()
            m.lore(lore)
        }
    }
}

package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.feature.EndManager
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

class EndGUI {
    fun open(player: Player) {
        val total = EndManager.getTotalCount()
        val unlocked = EndManager.getUnlockedCount(player)
        val entries = EndManager.getEntries(player)

        player.openMenu<PageableChest<EndManager.EndEntry>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>结档案 [$unlocked/$total]".toComponent())
        ) {
            rows(6)

            map(
                "CCC  H<>",
                "         ",
                " ####### ",
                " ####### ",
                " ####### ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>菜单", listOf("<#A9B4C3>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            slotsBy('#')

            elements { entries }

            onGenerate { _, entry, _, _ ->
                buildEndItem(entry)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                buildSimpleItem(XMaterial.ARROW, "<#5FA8A3><b>上一页").apply {
                    val m = itemMeta ?: return@apply
                    m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:previous" else "kangelitem:previous_no")
                    setItemMeta(m)
                }
            }

            setNextPage(8) { _, page ->
                buildSimpleItem(XMaterial.ARROW, "<#5FA8A3><b>下一页").apply {
                    val m = itemMeta ?: return@apply
                    m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:next" else "kangelitem:next_no")
                    setItemMeta(m)
                }
            }
        }
    }

    private fun buildEndItem(entry: EndManager.EndEntry): ItemStack {
        val mat = try {
            XMaterial.valueOf(entry.template.icon.uppercase())?.parseItem()
        } catch (_: Exception) {
            null
        } ?: ItemStack(Material.END_STONE)

        val meta = mat.itemMeta ?: return mat

        if (entry.unlocked) {
            meta.displayName("${entry.template.color}<b>✦ ${entry.template.name}".toComponent())
            val lore = mutableListOf<String>()
            lore += loreInfo("结档ID", "<#F2F5FA>${entry.id}")
            lore += loreSpace()
            lore += loreDash("<#8FD4A6>✓ 已解锁")
            meta.lore(lore.toComponent())
        } else {
            meta.displayName("<#A9B4C3><b>?</b> ???".toComponent())
            val lore = mutableListOf<String>()
            lore += loreInfo("状态", "<#A9B4C3>尚未解锁")
            lore += loreSpace()
            lore += loreDash("<#E08A98>✗ 未解锁")
            meta.lore(lore.toComponent())
        }

        mat.setItemMeta(meta)
        return mat
    }

    private fun buildSimpleItem(material: XMaterial, name: String): ItemStack {
        val item = material.parseItem() ?: ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(name.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

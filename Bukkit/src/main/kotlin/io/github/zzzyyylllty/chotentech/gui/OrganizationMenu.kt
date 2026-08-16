package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.BaseManager.FactionInfo
import io.github.zzzyyylllty.chotentech.function.BaseManager.factions
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
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
import taboolib.module.ui.type.Chest

class OrganizationMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>超级组织".toComponent())) {
            rows(6)

            map(
                "CCC     ",
                "         ",
                " ####### ",
                " #M#B#U# ",
                " ####### ",
                "         "
            )

            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<!i> <yellow>点击 <green>打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            set('M', buildOrgItem(player, factions[0])) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickEvent().click.name == "RIGHT" || clickEvent().click.name == "SHIFT_RIGHT") {
                    ReputationRecordMenu(factions[0]).open(clicker)
                } else {
                    clicker.sendMessage("<gray>请 <yellow>右键 <gray>查看信誉记录".toComponent())
                }
            }

            set('B', buildOrgItem(player, factions[1])) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickEvent().click.name == "RIGHT" || clickEvent().click.name == "SHIFT_RIGHT") {
                    ReputationRecordMenu(factions[1]).open(clicker)
                } else {
                    clicker.sendMessage("<gray>请 <yellow>右键 <gray>查看信誉记录".toComponent())
                }
            }

            set('U', buildOrgItem(player, factions[2])) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickEvent().click.name == "RIGHT" || clickEvent().click.name == "SHIFT_RIGHT") {
                    ReputationRecordMenu(factions[2]).open(clicker)
                } else {
                    clicker.sendMessage("<gray>请 <yellow>右键 <gray>查看信誉记录".toComponent())
                }
            }
        }
    }

    private fun buildOrgItem(player: Player, faction: FactionInfo): ItemStack {
        val item = XMaterial.BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        NamespacedKey.fromString(faction.iconModel)?.let { meta.itemModel = it }
        meta.displayName("${faction.color}<b>${faction.displayName}</b> <gray>（${faction.tag}）".toComponent())

        val data = player.getData()
        val rep = data?.reputation?.getOrDefault(faction.tag, 0L) ?: 0L
        val base = data?.subTag?.let { BaseManager.baseMap[it] }
        val belong = if (base != null && base.faction.equals(faction.tag, ignoreCase = true)) base.name else "未加入"

        val lore = mutableListOf<String>()
        lore += loreInfo("缩写", faction.tag)
        lore += loreInfo("基地数量", BaseManager.getBasesByFaction(faction.tag).size.toString())
        lore += loreInfo("我的归属", belong)
        lore += loreInfo("信誉", "<white>$rep")
        lore += loreSpace()
        lore += loreSection("简介")
        faction.description.lines().forEach { lore += "<!i><gray>$it" }
        lore += loreSpace()
        lore += "<!i> <yellow>右键 <green>查看信誉记录"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

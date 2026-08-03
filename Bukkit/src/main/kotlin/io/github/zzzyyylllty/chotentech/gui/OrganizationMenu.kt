package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager.FactionInfo
import io.github.zzzyyylllty.chotentech.function.BaseManager.factions
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
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
            rows(3)

            map(
                "         ",
                "         ",
                "         "
            )

            factions.forEachIndexed { index, faction ->
                if (index >= 3) return@forEachIndexed
                set(11 + index * 2, buildOrgItem(player, faction)) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    if (clickEvent().click.name == "RIGHT" || clickEvent().click.name == "SHIFT_RIGHT") {
                        ReputationRecordMenu(faction).open(clicker)
                    } else {
                        clicker.sendMessage("<gray>请 <yellow>右键 <gray>查看信誉记录".toComponent())
                    }
                }
            }

            set(22, guiItem(XMaterial.BARRIER, "<red><bold>关闭", listOf("<!i> <yellow>点击 <red>关闭"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
            }
        }
    }

    private fun buildOrgItem(player: Player, faction: FactionInfo): ItemStack {
        val item = XMaterial.BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        NamespacedKey.fromString(faction.iconModel)?.let { meta.itemModel = it }
        meta.displayName("${faction.color}<b>${faction.displayName}</b> <gray>（${faction.tag}）".toComponent())

        val rep = player.getData()?.reputation?.getOrDefault(faction.tag, 0L) ?: 0L

        val lore = mutableListOf<String>()
        lore += loreSection("简介")
        faction.description.lines().forEach { lore += "<!i><gray>$it" }
        lore += loreSpace()
        lore += loreInfo("信誉", "<white>$rep")
        lore += loreSpace()
        lore += "<!i> <yellow>右键 <green>查看信誉记录"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

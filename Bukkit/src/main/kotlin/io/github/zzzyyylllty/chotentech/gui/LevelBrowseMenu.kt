package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

const val GUI_PREFIX = "<white><b>| </b><gold>"

class LevelBrowseMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val levels = levelsList.values.toList()

        player.openMenu<PageableChest<Level>>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>层级浏览".toComponent())) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<gray>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            slotsBy('@')

            elements { levels }

            onGenerate { p, level, _, _ ->
                buildLevelItem(level, data.exploreredLevels.contains(level.id), data.exploreredNoclips[level.id])
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<green>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<green>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildLevelItem(level: Level, explored: Boolean, usedNoclips: HashSet<String>?): ItemStack {
        if (!explored) {
            val item = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem() ?: ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = item.itemMeta ?: return item
            meta.displayName("<dark_gray><b>???</b>".toComponent())
            meta.lore(listOf(
                "<!i>${GUI_PREFIX}层群 <dark_gray>???</dark_gray> 层级 <dark_gray>???",
                "<!i>",
                "<!i>${GUI_PREFIX}生存难度: <dark_gray>???",
                "<!i>${GUI_PREFIX}描述: <dark_gray>???",
                "<!i>",
                "<!i>${GUI_PREFIX}<i><red>尚未探索"
            ).toComponent())
            item.setItemMeta(meta)
            return item
        }

        // 使用层级配置的 GUI 材质，默认 FILLED_MAP
        val mat = try {
            level.guiMaterial?.let { XMaterial.valueOf(it.uppercase())?.parseItem() }
                ?: XMaterial.FILLED_MAP.parseItem()
        } catch (_: Exception) {
            XMaterial.FILLED_MAP.parseItem()
        } ?: ItemStack(Material.FILLED_MAP)
        val meta = mat.itemMeta ?: return mat

        meta.displayName("<gold>${level.prefix}${level.name}</gold>".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i>${GUI_PREFIX}层群 ${level.cluster} 层级 ${level.number ?: "???"}"
        lore += "<!i>"
        lore += "<!i>${level.survivalDifficulty.prefix}<b>${level.survivalDifficulty.classname}</b> <gray>| <dark_gray>${level.survivalDifficulty.one}, ${level.survivalDifficulty.two}, ${level.survivalDifficulty.three}"
        if (level.dmz) lore += "<!i>${GUI_PREFIX}<red>非军事区: 不得主动攻击其他流浪者。"
        lore += "<!i>"
        lore += "<!i>${GUI_PREFIX}简介"
        lore += "<!i>${level.tip}"
        lore += "<!i>"
        lore += "<!i>${GUI_PREFIX}作者: <dark_gray>${level.author}"

        if (usedNoclips != null && usedNoclips.isNotEmpty()) {
            lore += "<!i>"
            lore += "<!i>${GUI_PREFIX}已探索的切出点"
            usedNoclips.forEach { name ->
                lore += "<!i> <green>· $name"
            }
        }

        meta.lore(lore.toComponent())
        mat.setItemMeta(meta)
        return mat
    }
}

package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

const val GUI_PREFIX = "<#F2F5FA><b>|</b> <!i>"

class LevelBrowseMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val levels = levelsList.values.toList()

        player.openMenu<PageableChest<Level>>(mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>层级浏览".toComponent())) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>菜单", listOf("<#A9B4C3>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            slotsBy('@')

            elements { levels }

            onGenerate { p, level, _, _ ->
                buildLevelItem(level, data.exploreredLevels.contains(level.id), data.exploreredNoclips[level.id])
            }

            onClick { _, level ->
                if (!data.exploreredLevels.contains(level.id)) return@onClick
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                NoclipBrowseMenu().open(player, level)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<#8FD4A6>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#8FD4A6>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildLevelItem(level: Level, explored: Boolean, usedNoclips: HashSet<String>?): ItemStack {
        if (!explored) {
            val item = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem() ?: ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = item.itemMeta ?: return item
            meta.displayName("<#6E7A8C><b>???</b>".toComponent())
            meta.lore(listOf(
                loreInfo("层群", "<#6E7A8C>???"),
                loreInfo("层级", "<#6E7A8C>???"),
                loreSpace(),
                loreInfo("生存难度", "<#6E7A8C>???"),
                loreInfo("描述", "<#6E7A8C>???"),
                loreSpace(),
                "<!i><#F2F5FA><b>|<reset> <!i><#E08A98><i>尚未探索"
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

        meta.displayName("<#DFBD7C>${level.prefix}${level.name}</#DFBD7C>".toComponent())

        val lore = mutableListOf<String>()
        lore += loreInfo("层群", level.cluster)
        lore += loreInfo("层级", level.number ?: "???")
        lore += loreSpace()
        lore += loreInfo("生存难度", "${level.survivalDifficulty.prefix}<b>${level.survivalDifficulty.classname}</b> <#A9B4C3>| <#6E7A8C>${level.survivalDifficulty.one}, ${level.survivalDifficulty.two}, ${level.survivalDifficulty.three}")
        if (level.dmz) lore += loreInfo("非军事区", "<#E08A98>不得主动攻击其他流浪者")
        lore += loreSpace()
        lore += loreSection("简介")
        lore += "<!i><#A9B4C3>${level.tip}"
        lore += loreSpace()
        lore += loreInfo("作者", "<#6E7A8C>${level.author}")

        if (usedNoclips != null && usedNoclips.isNotEmpty()) {
            lore += loreSpace()
            lore += loreSection("已探索的切出点")
            usedNoclips.forEach { name ->
                lore += loreDash("<#8FD4A6>$name")
            }
        }

        meta.lore(lore.toComponent())
        mat.setItemMeta(meta)
        return mat
    }
}

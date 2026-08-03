package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.NoclipPoint
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

class NoclipBrowseMenu {

    fun open(player: Player, level: Level) {
        val data = player.getData() ?: return
        val noclips = level.noclips
        val exploredNoclips = data.exploreredNoclips[level.id] ?: hashSetOf()

        player.openMenu<PageableChest<NoclipPoint>>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${level.prefix}${level.name} 切出点".toComponent())) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>返回", listOf("<gray>点击返回层级列表"), model = "kangelitem:void")) {
                LevelBrowseMenu().open(clicker)
            }

            slotsBy('@')

            elements { noclips }

            onGenerate { _, noclip, _, _ ->
                buildNoclipItem(noclip, exploredNoclips.contains(noclip.name))
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

    private fun buildNoclipItem(noclip: NoclipPoint, explored: Boolean): ItemStack {
        if (!explored) {
            val item = XMaterial.GRAY_DYE.parseItem() ?: ItemStack(Material.GRAY_DYE)
            val meta = item.itemMeta ?: return item
            meta.displayName("<dark_gray>${noclip.name ?: "???"}</dark_gray>".toComponent())
            meta.lore(listOf(
                loreInfo("状态", "<red>未解锁")
            ).toComponent())
            item.setItemMeta(meta)
            return item
        }

        val (icon, nameColor) = when {
            noclip.conditions != null -> XMaterial.YELLOW_DYE to "<yellow>"
            noclip.elevatorOnly -> XMaterial.CYAN_DYE to "<dark_aqua>"
            else -> XMaterial.LIGHT_BLUE_DYE to "<aqua>"
        }
        val mat = icon.parseItem() ?: ItemStack(Material.LIGHT_BLUE_DYE)
        val meta = mat.itemMeta ?: return mat

        meta.displayName("${nameColor}${noclip.name ?: "未命名切出点"}".toComponent())

        val displayLevel = noclip.targetLevel?.let { id ->
            levelsList[id]?.let { "${it.prefix}${it.name}" } ?: id
        } ?: "未知"
        val lore = mutableListOf<String>()
        lore += loreInfo("目标层级", "<yellow>$displayLevel")
        lore += loreInfo("基础成功率", "<yellow>${noclip.basicSuccessRate}%")
        lore += loreSpace()

        if (noclip.conditions != null) {
            lore += loreInfo("触发方式", "<yellow>条件触发")
            lore += loreSpace()
        }

        val world = noclip.noclipPointWorld ?: "未知"
        val loc1 = noclip.noclipPointLocation1
        val loc2 = noclip.noclipPointLocation2
        val center = loc1.clone().add(loc2).multiply(0.5)
        lore += loreSection("位置")
        lore += loreDash("<white>世界: <gray>$world")
        lore += loreDash("<white>坐标: <gray>${center.x.toInt()}, ${center.y.toInt()}, ${center.z.toInt()}")
        lore += loreSpace()

        if (noclip.targetLevel != null) {
            lore += loreSection("消耗")
            if (noclip.hunger > 0) lore += loreDash("<red>饥饿惩罚: ${noclip.hunger}")
            if (noclip.damage > 0) lore += loreDash("<red>失败伤害: ${noclip.damage}❤")
            if (noclip.cooldown > 0) lore += loreDash("<gray>冷却: ${noclip.cooldown}秒")
            lore += loreDash("<gray>切出时间: ${noclip.noclippingTime}秒")
        }

        if (noclip.autoNoclip) {
            lore += loreSpace()
            lore += loreInfo("特性", "<aqua>自动切出")
        }
        if (noclip.elevatorOnly) {
            lore += loreSpace()
            lore += loreInfo("特性", "<gold>仅限电梯")
        }

        meta.lore(lore.toComponent())
        mat.setItemMeta(meta)
        return mat
    }
}

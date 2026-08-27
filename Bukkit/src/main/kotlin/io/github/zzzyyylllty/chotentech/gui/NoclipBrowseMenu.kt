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

        player.openMenu<PageableChest<NoclipPoint>>(mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>${level.prefix}${level.name} 切出点".toComponent())) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>返回", listOf("<#A9B4C3>点击返回层级列表"), model = "kangelitem:void")) {
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
                guiItem(XMaterial.ARROW, "<#8FD4A6>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#8FD4A6>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildNoclipItem(noclip: NoclipPoint, explored: Boolean): ItemStack {
        if (!explored) {
            val item = XMaterial.GRAY_DYE.parseItem() ?: ItemStack(Material.GRAY_DYE)
            val meta = item.itemMeta ?: return item
            meta.displayName("<#6E7A8C>${noclip.name ?: "???"}</#6E7A8C>".toComponent())
            meta.lore(listOf(
                loreInfo("状态", "<#E08A98>未解锁")
            ).toComponent())
            item.setItemMeta(meta)
            return item
        }

        val (icon, nameColor) = when {
            noclip.conditions != null -> XMaterial.YELLOW_DYE to "<#EFDF8F>"
            noclip.elevatorOnly -> XMaterial.CYAN_DYE to "<#5FA8A3>"
            else -> XMaterial.LIGHT_BLUE_DYE to "<#84D1CB>"
        }
        val mat = icon.parseItem() ?: ItemStack(Material.LIGHT_BLUE_DYE)
        val meta = mat.itemMeta ?: return mat

        meta.displayName("${nameColor}${noclip.name ?: "未命名切出点"}".toComponent())

        val displayLevel = noclip.targetLevel?.let { id ->
            levelsList[id]?.let { "${it.prefix}${it.name}" } ?: id
        } ?: "未知"
        val lore = mutableListOf<String>()
        lore += loreInfo("目标层级", "<#EFDF8F>$displayLevel")
        lore += loreInfo("基础成功率", "<#EFDF8F>${noclip.basicSuccessRate}%")
        lore += loreSpace()

        if (noclip.conditions != null) {
            lore += loreInfo("触发方式", "<#EFDF8F>条件触发")
            lore += loreSpace()
        }

        val world = noclip.noclipPointWorld ?: "未知"
        val loc1 = noclip.noclipPointLocation1
        val loc2 = noclip.noclipPointLocation2
        val center = loc1.clone().add(loc2).multiply(0.5)
        lore += loreSection("位置")
        lore += loreDash("<#F2F5FA>世界: <#A9B4C3>$world")
        lore += loreDash("<#F2F5FA>坐标: <#A9B4C3>${center.x.toInt()}, ${center.y.toInt()}, ${center.z.toInt()}")
        lore += loreSpace()

        if (noclip.targetLevel != null) {
            lore += loreSection("消耗")
            if (noclip.hunger > 0) lore += loreDash("<#E08A98>饥饿惩罚: ${noclip.hunger}")
            if (noclip.damage > 0) lore += loreDash("<#E08A98>失败伤害: ${noclip.damage}❤")
            if (noclip.cooldown > 0) lore += loreDash("<#A9B4C3>冷却: ${noclip.cooldown}秒")
            lore += loreDash("<#A9B4C3>切出时间: ${noclip.noclippingTime}秒")
        }

        if (noclip.autoNoclip) {
            lore += loreSpace()
            lore += loreInfo("特性", "<#84D1CB>自动切出")
        }
        if (noclip.elevatorOnly) {
            lore += loreSpace()
            lore += loreInfo("特性", "<#DFBD7C>仅限电梯")
        }

        meta.lore(lore.toComponent())
        mat.setItemMeta(meta)
        return mat
    }
}

package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.Base
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
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
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem
import java.util.concurrent.ConcurrentHashMap


private val showAllMap = ConcurrentHashMap<String, Boolean>()

@Awake(LifeCycle.DISABLE)
fun clearBaseCodexMenuMaps() {
    showAllMap.clear()
}

class BaseCodexMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val uuid = player.uniqueId.toString()
        val showAll = showAllMap.getOrDefault(uuid, false)

        val bases = if (showAll) {
            BaseManager.baseMap.values.toList()
        } else {
            BaseManager.baseMap.values.filter { data.discoveredBases.contains(it.idef) }
                .ifEmpty { BaseManager.baseMap.values.toList() }
        }

        player.openMenu<PageableChest<Base>>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>基地图鉴".toComponent())) {
            rows(6)

            map(
                "CCC  HF<>",
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

            elements { bases }

            onGenerate { p, base, _, _ ->
                buildCodexItem(base, data.discoveredBases.contains(base.idef))
            }

            // 切换显示模式
            set('H', buildToggleItem(showAll)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                showAllMap[uuid] = !showAll
                clicker.closeInventory()
                BaseCodexMenu().open(clicker)
            }

            // 完成度统计
            set('F', buildStatsItem(player)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                // 刷新
                clicker.closeInventory()
                BaseCodexMenu().open(clicker)
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

    private fun buildToggleItem(showAll: Boolean): ItemStack {
        val icon = if (showAll) XMaterial.WRITABLE_BOOK else XMaterial.BOOK
        val item = icon.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        if (showAll) {
            meta.displayName("<gold>显示模式: <green>全部基地".toComponent())
            meta.lore(listOf(
                loreInfo("当前模式", "<green>显示所有基地"),
                "<!i> <yellow>点击 <green>切换为仅显示已发现"
            ).toComponent())
        } else {
            meta.displayName("<gold>显示模式: <gray>仅已发现".toComponent())
            meta.lore(listOf(
                loreInfo("当前模式", "<gray>仅显示已发现的基地"),
                "<!i> <yellow>点击 <green>切换为显示全部"
            ).toComponent())
        }

        item.setItemMeta(meta)
        return item
    }

    private fun buildStatsItem(player: Player): ItemStack {
        val data = player.getData() ?: return guiItem(XMaterial.BARRIER, "<red>数据错误")
        val total = BaseManager.baseMap.size
        val discovered = data.discoveredBases.count { BaseManager.baseMap.containsKey(it) }

        val item = XMaterial.COMPASS.parseItem() ?: ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item

        meta.displayName("<gold>图鉴进度".toComponent())
        meta.lore(listOf(
            loreInfo("已发现", "<green>$discovered <gray>/ <yellow>$total"),
            loreInfo("完成度", "<green>${if (total > 0) "%.1f".format(discovered.toDouble() / total * 100) else "0"}%")
        ).toComponent())

        item.setItemMeta(meta)
        return item
    }

    private fun buildCodexItem(base: Base, discovered: Boolean): ItemStack {
        if (!discovered) {
            val item = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem() ?: ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = item.itemMeta ?: return item

            meta.displayName("<dark_gray><b>???</b>".toComponent())
            meta.lore(listOf(
                loreInfo("基地", "<dark_gray>???"),
                loreInfo("阵营", "<dark_gray>???"),
                loreSpace(),
                "<!i><white><b>|<reset> <!i><gray><i>尚未发现该基地！"
            ).toComponent())
            item.setItemMeta(meta)
            return item
        }

        val mat = XMaterial.FILLED_MAP.parseItem() ?: ItemStack(Material.FILLED_MAP)
        val meta = mat.itemMeta ?: return mat

        val factionColor = when (base.faction.uppercase()) {
            "MEG" -> "<#ffdd00>"
            "BNTG" -> "<#33ffaa>"
            "UEC" -> "<#ff3d00>"
            else -> "<white>"
        }

        meta.displayName("<gold>${base.name}</gold>".toComponent())

        val lore = mutableListOf<String>()
        lore += loreInfo("基地编号", "<yellow>${base.idef}")
        lore += loreInfo("所属阵营", "$factionColor${base.faction}")
        lore += loreSpace()
        lore += loreSection("介绍")
        base.lore.forEach { lore += "<!i><#cccccc>$it" }
        lore += loreSpace()
        lore += loreInfo("区域", "<dark_gray>${base.region}")
        lore += loreInfo("完成版本", "${base.dev}")

        meta.lore(lore.toComponent())
        mat.setItemMeta(meta)
        return mat
    }
}

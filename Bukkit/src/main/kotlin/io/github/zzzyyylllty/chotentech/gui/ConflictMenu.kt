package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.ConflictConfig
import io.github.zzzyyylllty.chotentech.data.ConflictScore
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import io.github.zzzyyylllty.chotentech.function.ActiveConflict
import io.github.zzzyyylllty.chotentech.function.ConflictManager
import io.github.zzzyyylllty.chotentech.function.ConflictStatus
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace

class ConflictMenu {

    private val statusModelIndex = mapOf(
        ConflictStatus.PENDING to 0,
        ConflictStatus.ACTIVE to 1,
        ConflictStatus.FINISHED to 2
    )

    fun open(player: Player, showAll: Boolean = false) {
        val conflicts = if (showAll) {
            ConflictManager.getAllConflicts()
        } else {
            ConflictManager.getActiveConflicts()
        }

        if (conflicts.isEmpty()) {
            player.sendMessage("<#A9B4C3>当前没有冲突".toComponent())
            return
        }

        player.openMenu<PageableChest<ActiveConflict>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>冲突列表 [${conflicts.size}]".toComponent())
        ) {
            rows(6)

            map(
                "CCC  T <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            slotsBy('@')

            elements { conflicts }

            onGenerate { _, conflict, _, _ ->
                buildConflictItem(conflict)
            }

            onClick { event, conflict ->
                event.clicker.playSound(event.clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                val config = conflictConfigs[conflict.configId]
                if (config != null) {
                    openDetail(event.clicker, conflict, config)
                }
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            set('C', guiItem(XMaterial.COMPASS, "<#DFBD7C>关闭", model = "kangelitem:void")) {
                clicker.closeInventory()
            }

            set('T', guiItem(XMaterial.WRITABLE_BOOK, if (showAll) "<#84D1CB>显示: <#8FD4A6>全部" else "<#84D1CB>显示: <#EFDF8F>进行中", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>切换显示模式"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                open(clicker, !showAll)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<#5FA8A3><bold>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#5FA8A3><bold>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun openDetail(player: Player, conflict: ActiveConflict, config: ConflictConfig) {
        player.openMenu<PageableChest<Map.Entry<ResultLevel, ConflictScore>>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>${conflict.playerName} - ${config.name}".toComponent())
        ) {
            rows(6)

            map(
                "BBB    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            slotsBy('@')

            elements { config.scores.entries.toList() }

            onGenerate { _, entry, _, _ ->
                buildResultItem(entry.key, entry.value)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            set('B', guiItem(XMaterial.COMPASS, "<#DFBD7C>返回列表", model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                open(clicker)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<#5FA8A3><bold>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#5FA8A3><bold>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildConflictItem(conflict: ActiveConflict): ItemStack {
        val item = XMaterial.KNOWLEDGE_BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val modelNum = statusModelIndex[conflict.status] ?: 0
        meta.itemModel = NamespacedKey.fromString("kangelicon:conflict_$modelNum")

        val statusText = when (conflict.status) {
            ConflictStatus.PENDING -> "<#EFDF8F>未开始"
            ConflictStatus.ACTIVE -> "<#84D1CB>进行中"
            ConflictStatus.FINISHED -> "<#A9B4C3>已完成"
        }

        meta.displayName("<#DFBD7C>${conflict.playerName} <#A9B4C3>- ${conflict.name}".toComponent())

        val lore = mutableListOf<String>()
        lore += loreInfo("状态", statusText)
        if (conflict.factionLevels.isNotEmpty()) {
            lore += loreSection("阵营等级")
            for ((faction, level) in conflict.factionLevels) {
                lore += loreDash("<#A9B4C3>$faction: ${level.displayColor}${level.displayName}")
            }
            lore += loreSpace()
        } else {
            val noLevelText = when (conflict.status) {
                ConflictStatus.PENDING -> "<#EFDF8F>未开始"
                ConflictStatus.ACTIVE -> "<#84D1CB>进行中"
                else -> "<#A9B4C3>无结果"
            }
            lore += loreInfo("结果", "<#DFBD7C>$noLevelText")
        }
        lore += loreInfo("配置ID", "<#F2F5FA>${conflict.configId}")
        lore += loreSpace()
        lore += "<!i> <#EFDF8F>点击 <#8FD4A6>查看结果详情"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildResultItem(level: ResultLevel, score: ConflictScore): ItemStack {
        val item = XMaterial.PAPER.parseItem() ?: ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName("${level.displayColor}<b>${level.displayName} <#6E7A8C>(${level.name})".toComponent())

        val lore = mutableListOf<String>()
        if (score.changes.isEmpty()) {
            lore += "<!i><#F2F5FA><b>|<reset> <!i><#A9B4C3>无声望变化"
        } else {
            lore += loreSection("声望变化")
            for ((faction, change) in score.changes) {
                val prefix = if (change >= 0) "+" else ""
                val changeColor = if (change >= 0) "<#8FD4A6>" else "<#E08A98>"
                lore += loreDash("<#A9B4C3>$faction: $changeColor$prefix$change")
            }
        }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

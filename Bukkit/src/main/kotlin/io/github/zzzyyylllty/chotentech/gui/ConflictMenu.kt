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
            player.sendMessage("<gray>当前没有冲突".toComponent())
            return
        }

        player.openMenu<PageableChest<ActiveConflict>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>冲突列表 [${conflicts.size}]".toComponent())
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

            set('C', guiItem(XMaterial.COMPASS, "<gold>关闭", model = "kangelitem:void")) {
                clicker.closeInventory()
            }

            set('T', guiItem(XMaterial.WRITABLE_BOOK, if (showAll) "<aqua>显示: <green>全部" else "<aqua>显示: <yellow>进行中", listOf("<!i><gray>点击切换显示模式"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                open(clicker, !showAll)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<dark_aqua><bold>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<dark_aqua><bold>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun openDetail(player: Player, conflict: ActiveConflict, config: ConflictConfig) {
        player.openMenu<PageableChest<Map.Entry<ResultLevel, ConflictScore>>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${conflict.playerName} - ${config.name}".toComponent())
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

            set('B', guiItem(XMaterial.COMPASS, "<gold>返回列表", model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                open(clicker)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<dark_aqua><bold>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<dark_aqua><bold>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildConflictItem(conflict: ActiveConflict): ItemStack {
        val item = XMaterial.KNOWLEDGE_BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val modelNum = statusModelIndex[conflict.status] ?: 0
        meta.itemModel = NamespacedKey.fromString("kangelicon:conflict_$modelNum")

        val statusText = when (conflict.status) {
            ConflictStatus.PENDING -> "<yellow>未开始"
            ConflictStatus.ACTIVE -> "<aqua>进行中"
            ConflictStatus.FINISHED -> "<gray>已完成"
        }

        val levelText = when {
            conflict.level != null -> "${conflict.level!!.displayColor}${conflict.level!!.displayName}"
            conflict.status == ConflictStatus.PENDING -> "<yellow>未开始"
            conflict.status == ConflictStatus.ACTIVE -> "<aqua>进行中"
            else -> "<gray>无结果"
        }

        meta.displayName("<gold>${conflict.playerName} <gray>- ${conflict.name}".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><dark_gray><st>${" ".repeat(40)}</st>"
        lore += "<!i><gray>状态: $statusText"
        lore += "<!i><gray>结果: <gold>$levelText"
        lore += "<!i><gray>配置ID: <white>${conflict.configId}"
        lore += "<!i><dark_gray><st>${" ".repeat(40)}</st>"
        lore += "<!i><aqua>点击查看结果详情"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildResultItem(level: ResultLevel, score: ConflictScore): ItemStack {
        val item = XMaterial.PAPER.parseItem() ?: ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName("${level.displayColor}<b>${level.displayName} <dark_gray>(${level.name})".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><dark_gray><st>${" ".repeat(40)}</st>"

        if (score.changes.isEmpty()) {
            lore += "<!i><gray>无声望变化"
        } else {
            for ((faction, change) in score.changes) {
                val prefix = if (change >= 0) "+" else ""
                val changeColor = if (change >= 0) "<green>" else "<red>"
                lore += "<!i><gray>$faction: $changeColor$prefix$change"
            }
        }

        lore += "<!i><dark_gray><st>${" ".repeat(40)}</st>"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

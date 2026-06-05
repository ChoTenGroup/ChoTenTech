package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.RankConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.RankManager
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem

class RankSelectionMenu {

    fun open(player: Player) {
        val types = RankManager.getTypes().toList()
        if (types.isEmpty()) {
            player.sendMessage("<red>当前没有可用的称号类型。".toComponent())
            return
        }

        player.openMenu<Chest>(mmJsonUtil.serialize("<black><font:minecraft:fusionpixel10>称号选择".toComponent())) {
            rows(4)
            hidePlayerInventory()

            map(
                "#########",
                "# A B C #",
                "# D E F #",
                "#########"
            )

            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            val typeSlots = listOf('A', 'B', 'C', 'D', 'E', 'F')
            types.take(6).forEachIndexed { index, type ->
                val slot = typeSlots[index]
                val data = player.getData() ?: return@forEachIndexed
                val activeId = data.activeRanks[type]
                val activeName = RankManager.ranks[type]?.get(activeId)?.name ?: "无"

                set(slot, guiItem(XMaterial.BOOK, "<gold>$type", listOf("<gray>当前: <white>$activeName", "<green>点击查看"))) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    openRankList(clicker, type)
                }
            }
        }
    }

    private fun openRankList(player: Player, type: String) {
        val eligibleRanks = RankManager.getEligibleRanks(player, type)
        val data = player.getData() ?: return
        val activeId = data.activeRanks[type]

        player.openMenu<PageableChest<RankConfig>>(
            mmJsonUtil.serialize("<black><font:minecraft:fusionpixel10>${type} 称号".toComponent())
        ) {
            rows(6)
            hidePlayerInventory()

            map(
                "#########",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<B>###"
            )

            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }
            slotsBy('@')

            elements { eligibleRanks }

            onGenerate { _, rank, _, _ ->
                buildRankItem(rank, rank.id == activeId)
            }

            onClick { event, rank ->
                val clicker = event.clicker
                val pData = clicker.getData() ?: return@onClick

                if (pData.activeRanks[type] == rank.id) {
                    pData.activeRanks.remove(type)
                    clicker.sendMessage("§7已取消 $type 称号")
                } else {
                    if (RankManager.setPlayerRank(clicker, type, rank.id)) {
                        clicker.sendMessage("§a已选择称号: §f${rank.name}")
                        clicker.playSound(clicker.location, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f)
                    }
                }
                resetElementsCache()
                openRankList(clicker, type)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            set('B', guiItem(XMaterial.COMPASS, "<gold>返回")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                open(clicker)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<aqua>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<aqua>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildRankItem(rank: RankConfig, isActive: Boolean): ItemStack {
        val mat = if (isActive) XMaterial.KNOWLEDGE_BOOK else XMaterial.BOOK
        val item = mat.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val statusColor = if (isActive) "<green>" else "<gray>"
        val statusText = if (isActive) "已启用" else "点击选择"

        meta.displayName("${statusColor}<b>${rank.name}".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"

        if (rank.desc.isNotBlank()) {
            lore += "<!i><white>${rank.desc}"
            lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        }

        rank.display.forEach { (key, value) ->
            lore += "<!i><dark_aqua>$key: <white>$value"
        }

        if (rank.desc.isNotBlank() || rank.display.isNotEmpty()) {
            lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        }

        lore += "<!i>${statusColor}${statusText}"
        lore += "<!i><dark_gray>${rank.id}"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

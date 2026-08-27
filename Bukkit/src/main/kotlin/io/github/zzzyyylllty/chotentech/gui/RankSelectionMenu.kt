package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.RankConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.function.RankManager
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
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace

class RankSelectionMenu {

    companion object {
        private val typeNameMap = mapOf(
            "prefix" to "前缀",
            "suffix" to "后缀",
            "chatsuffix" to "聊天后缀",
            "chatcolor" to "聊天颜色",
            "namecolor" to "名字颜色",
            "joinmessage" to "进服消息",
            "quitmessage" to "退服消息"
        )
        private val displayKeyMap = mapOf(
            "current" to "全称",
            "short" to "缩写"
        )
    }

    fun open(player: Player) {
        val types = RankManager.getTypes().toList()
        if (types.isEmpty()) {
            player.sendMessage("<#E08A98>当前没有可用的称号类型。".toComponent())
            return
        }

        player.openMenu<PageableChest<String>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>称号选择".toComponent())
        ) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>菜单", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            slotsBy('@')

            elements { types }

            onGenerate { player, type, _, _ ->
                val effectiveName = RankManager.getEffectiveRank(player, type)?.name ?: "无"
                val effectiveFull = RankManager.getEffectiveRankDisplay(player, type, "current")
                val lore = mutableListOf(
                    loreInfo("当前", "<#F2F5FA>$effectiveName")
                )
                if (effectiveFull.isNotBlank() && (type == "prefix" || type == "suffix")) {
                    lore += "<!i><#A9B4C3>(全称: <#F2F5FA>$effectiveFull)"
                }
                lore += "<!i> <#EFDF8F>点击 <#8FD4A6>查看"
                guiItem(XMaterial.BOOK, "<#DFBD7C>${typeNameMap[type] ?: type}", lore)
            }

            onClick { event, type ->
                event.isCancelled = true
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                openRankList(clicker, type)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<#84D1CB>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#84D1CB>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun openRankList(player: Player, type: String) {
        val eligibleRanks = RankManager.getEligibleRanks(player, type)
        val effectiveId = RankManager.getEffectiveRank(player, type)?.id
        // 当前生效的称号置顶,其余保持优先级降序
        val orderedRanks = eligibleRanks.sortedByDescending { it.id == effectiveId }

        player.openMenu<PageableChest<RankConfig>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>${typeNameMap[type] ?: type}".toComponent())
        ) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>菜单", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>返回称号选择"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                open(clicker)
            }

            slotsBy('@')

            elements { orderedRanks }

            onGenerate { _, rank, _, _ ->
                buildRankItem(rank, rank.id == effectiveId)
            }

            onClick { event, rank ->
                event.isCancelled = true
                val clicker = event.clicker
                val clickerShared = ProfileManager.getSharedData(clicker.uniqueId.toString())

                if (clickerShared.activeRanks[type] == rank.id) {
                    clickerShared.activeRanks.remove(type)
                    clicker.sendMessage("§7已取消 ${typeNameMap[type] ?: type}")
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

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<#84D1CB>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#84D1CB>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildRankItem(rank: RankConfig, isActive: Boolean): ItemStack {
        val mat = if (isActive) XMaterial.KNOWLEDGE_BOOK else XMaterial.BOOK
        val item = mat.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val statusColor = if (isActive) "<#8FD4A6>" else "<#A9B4C3>"

        meta.displayName("${statusColor}<b>${rank.name}".toComponent())

        val lore = mutableListOf<String>()

        lore += loreInfo("名称", "<#F2F5FA>${rank.name}")

        if (rank.display.isNotEmpty()) {
            lore += loreSpace()
            lore += loreSection("展示方式")
            rank.display.forEach { (key, value) ->
                lore += "<!i><#bec9d3>${displayKeyMap[key] ?: key}：<#F2F5FA>$value"
            }
        }

        if (rank.desc.isNotBlank()) {
            lore += loreSpace()
            lore += loreSection("描述")
            wrapDesc(rank.desc).forEach { lore += "<!i><#c9d3be>$it" }
        }

        if (rank.get.isNotBlank()) {
            lore += loreSpace()
            lore += loreSection("获取方式")
            lore += "<!i><#d3c9be>${rank.get}"
        }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 按显示宽度智能分行:中文等全角字符记 2 宽,单行上限 maxWidth 半角宽;MiniMessage 标签不计宽且保持完整 */
    private fun wrapDesc(text: String, maxWidth: Int = 50): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        var width = 0
        val lineTags = StringBuilder()

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '<') {
                val end = text.indexOf('>', i + 1)
                if (end > i) {
                    val tag = text.substring(i, end + 1)
                    line.append(tag)
                    lineTags.append(tag)
                    i = end + 1
                    continue
                }
            }
            val w = if (c.code > 0xFF) 2 else 1
            if (width + w > maxWidth) {
                lines.add(line.toString())
                // 换行时延续上一行的标签,避免下一行失去颜色/格式
                line = StringBuilder(lineTags.toString())
                width = 0
            }
            line.append(c)
            width += w
            i++
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }
}

package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.InterfereConfig
import io.github.zzzyyylllty.chotentech.data.InterfereScore
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.interfereConfigs
import io.github.zzzyyylllty.chotentech.function.ActiveInterfere
import io.github.zzzyyylllty.chotentech.function.InterfereManager
import io.github.zzzyyylllty.chotentech.function.InterfereStatus
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

class InterfereMenu {

    private val statusModelIndex = mapOf(
        InterfereStatus.PENDING to 0,
        InterfereStatus.ACTIVE to 1,
        InterfereStatus.FINISHED to 2
    )

    fun open(player: Player, showAll: Boolean = false) {
        val interferes = if (showAll) {
            InterfereManager.getAllInterferes()
        } else {
            InterfereManager.getActiveInterferes()
        }

        if (interferes.isEmpty()) {
            player.sendMessage("<#A9B4C3>当前没有干扰".toComponent())
            return
        }

        player.openMenu<PageableChest<ActiveInterfere>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>干扰列表 [${interferes.size}]".toComponent())
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

            elements { interferes }

            onGenerate { _, interfere, _, _ ->
                buildInterfereItem(interfere)
            }

            onClick { event, interfere ->
                event.clicker.playSound(event.clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                val config = interfereConfigs[interfere.configId]
                if (config != null) {
                    openDetail(event.clicker, interfere, config)
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

    /** 供干涉事务面板跳转:按配置 ID 打开该玩家最新的对应实例结果详情,没有时返回 false */
    fun openDetail(player: Player, configId: String): Boolean {
        val interfere = InterfereManager.getInterferes(player.uniqueId.toString())
            .filter { it.configId == configId }
            .maxByOrNull { it.startTime }
            ?: return false
        val config = interfereConfigs[configId] ?: return false
        openDetail(player, interfere, config)
        return true
    }

    private fun openDetail(player: Player, interfere: ActiveInterfere, config: InterfereConfig) {
        player.openMenu<PageableChest<Map.Entry<ResultLevel, InterfereScore>>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>${interfere.playerName} - ${config.name}".toComponent())
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

    private fun buildInterfereItem(interfere: ActiveInterfere): ItemStack {
        val item = XMaterial.KNOWLEDGE_BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val modelNum = statusModelIndex[interfere.status] ?: 0
        meta.itemModel = NamespacedKey.fromString("kangelicon:interfere_$modelNum")

        val statusText = when (interfere.status) {
            InterfereStatus.PENDING -> "<#EFDF8F>未开始"
            InterfereStatus.ACTIVE -> "<#84D1CB>进行中"
            InterfereStatus.FINISHED -> "<#A9B4C3>已完成"
        }

        meta.displayName("<#DFBD7C>${interfere.playerName} <#A9B4C3>- ${interfere.name}".toComponent())

        val lore = mutableListOf<String>()
        lore += loreInfo("状态", statusText)
        if (interfere.factionLevels.isNotEmpty()) {
            lore += loreSection("阵营等级")
            for ((faction, level) in interfere.factionLevels) {
                lore += loreDash("<#A9B4C3>$faction: ${level.displayColor}${level.displayName}")
            }
            lore += loreSpace()
        } else {
            val noLevelText = when (interfere.status) {
                InterfereStatus.PENDING -> "<#EFDF8F>未开始"
                InterfereStatus.ACTIVE -> "<#84D1CB>进行中"
                else -> "<#A9B4C3>无结果"
            }
            lore += loreInfo("结果", "<#DFBD7C>$noLevelText")
        }
        lore += loreInfo("配置ID", "<#F2F5FA>${interfere.configId}")
        lore += loreInfo("实例ID", "<#6E7A8C>${interfere.instanceId}")
        lore += loreSpace()
        lore += "<!i> <#EFDF8F>点击 <#8FD4A6>查看结果详情"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildResultItem(level: ResultLevel, score: InterfereScore): ItemStack {
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

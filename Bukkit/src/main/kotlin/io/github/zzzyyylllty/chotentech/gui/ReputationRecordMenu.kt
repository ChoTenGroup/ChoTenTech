package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.interfereConfigs
import io.github.zzzyyylllty.chotentech.function.BaseManager.FactionInfo
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import java.text.SimpleDateFormat
import java.util.Date

class ReputationRecordMenu(private val faction: FactionInfo) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun open(player: Player) {
        // 一条记录可能同时影响多个阵营，这里取涉及当前阵营的记录，最新在前
        val records = ReputationManager.getRecordsOf(player, faction.tag).reversed()

        if (records.isEmpty()) {
            openEmpty(player)
            return
        }

        player.openMenu<PageableChest<ReputationManager.ReputationRecordView>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176>${faction.color}<font:minecraft:fusionpixel10>${faction.displayName} 信誉记录".toComponent())
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

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>返回", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>返回组织界面"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                OrganizationMenu().open(clicker)
            }

            slotsBy('@')

            elements { records }

            onGenerate { _, record, _, _ ->
                buildRecordItem(record)
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

    private fun openEmpty(player: Player) {
        player.openMenu<Chest>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176>${faction.color}<font:minecraft:fusionpixel10>${faction.displayName} 信誉记录".toComponent())
        ) {
            rows(3)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@X@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('X', guiItem(XMaterial.GRAY_DYE, "<#A9B4C3>暂无信誉记录", listOf(
                loreInfo("说明", "<#A9B4C3>参与干扰或管理员操作后会出现记录")
            ), model = "kangelitem:void"))

            set('Y', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>返回", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>返回组织界面"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                OrganizationMenu().open(clicker)
            }
        }
    }

    private fun buildRecordItem(record: ReputationManager.ReputationRecordView): ItemStack {
        val config = record.interfereId?.let { interfereConfigs[it] }

        val item = config?.icon?.let { matName ->
            runCatching { XMaterial.valueOf(matName.uppercase()) }.getOrNull()?.parseItem()
        } ?: XMaterial.PAPER.parseItem() ?: ItemStack(Material.PAPER)

        val meta = item.itemMeta ?: return item
        config?.itemModel?.let { NamespacedKey.fromString(it) }?.let { meta.itemModel = it }

        val title = config?.name ?: "杂项扣除"
        meta.displayName("<#DFBD7C>【$title】".toComponent())

        val lore = mutableListOf<String>()

        // 当前阵营在这条记录里的结果
        val entry = record.change[faction.tag]
        val level = entry?.type?.let { runCatching { ResultLevel.valueOf(it) }.getOrNull() }
        if (level != null) {
            lore += loreInfo("结果", "${level.displayColor}${level.displayName}")
        }
        val change = entry?.change ?: 0L
        val prefix = if (change >= 0) "+" else ""
        val changeColor = if (change >= 0) "<#8FD4A6>" else "<#E08A98>"
        lore += loreInfo("信誉", "$changeColor$prefix$change")
        if (record.interfereId == null) {
            lore += loreInfo("原因", "<#A9B4C3>杂项扣除")
        }

        // 同一次结算里其它阵营的连带变化
        val others = record.change.filterKeys { it != faction.tag }
        if (others.isNotEmpty()) {
            lore += loreSpace()
            lore += loreSection("同时变动")
            for ((otherFaction, other) in others) {
                val otherPrefix = if (other.change >= 0) "+" else ""
                val otherColor = if (other.change >= 0) "<#8FD4A6>" else "<#E08A98>"
                lore += loreDash("<#A9B4C3>$otherFaction: $otherColor$otherPrefix${other.change}")
            }
        }

        lore += loreSpace()
        lore += loreInfo("时间", "<#6E7A8C>${dateFormat.format(Date(record.time))}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

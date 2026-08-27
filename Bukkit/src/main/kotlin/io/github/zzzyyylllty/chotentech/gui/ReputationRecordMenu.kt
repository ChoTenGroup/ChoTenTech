package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.ReputationRecord
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager.FactionInfo
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
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
        val records = player.getData()?.reputationRecords?.filter { it.faction == faction.tag }?.reversed()
            ?: emptyList()

        if (records.isEmpty()) {
            openEmpty(player)
            return
        }

        player.openMenu<PageableChest<ReputationRecord>>(
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
                loreInfo("说明", "<#A9B4C3>参与冲突或管理员操作后会出现记录")
            ), model = "kangelitem:void"))

            set('Y', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>返回", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>返回组织界面"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                OrganizationMenu().open(clicker)
            }
        }
    }

    private fun buildRecordItem(record: ReputationRecord): ItemStack {
        val config = record.conflictId?.let { conflictConfigs[it] }

        val item = config?.icon?.let { matName ->
            runCatching { XMaterial.valueOf(matName.uppercase()) }.getOrNull()?.parseItem()
        } ?: XMaterial.PAPER.parseItem() ?: ItemStack(Material.PAPER)

        val meta = item.itemMeta ?: return item
        config?.itemModel?.let { NamespacedKey.fromString(it) }?.let { meta.itemModel = it }

        val title = config?.name ?: "杂项扣除"
        meta.displayName("<#DFBD7C>【$title】".toComponent())

        val lore = mutableListOf<String>()
        val level = record.level?.let { runCatching { ResultLevel.valueOf(it) }.getOrNull() }
        if (level != null) {
            lore += loreInfo("结果", "${level.displayColor}${level.displayName}")
        }
        val prefix = if (record.change >= 0) "+" else ""
        val changeColor = if (record.change >= 0) "<#8FD4A6>" else "<#E08A98>"
        lore += loreInfo("信誉", "$changeColor$prefix${record.change}")
        if (record.conflictId == null) {
            lore += loreInfo("原因", "<#A9B4C3>杂项扣除")
        }
        lore += loreSpace()
        lore += loreInfo("时间", "<#6E7A8C>${dateFormat.format(Date(record.time))}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

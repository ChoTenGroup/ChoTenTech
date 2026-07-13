package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.CoolNumberCategory
import io.github.zzzyyylllty.chotentech.data.CoolNumberInfo
import io.github.zzzyyylllty.chotentech.data.CoolNumberTier
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.function.CoolNumberManager
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submitAsync
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem
import taboolib.platform.util.nextChat

class CoolNumberMenu(
    private var selectedDigits: Int? = null,
    private var selectedCategory: CoolNumberCategory? = null
) {

    private val digitsList = listOf(4, 5, 6, 7, 8, 9, 10)
    private val categories = CoolNumberCategory.entries

    fun open(player: Player) {
        // refresh taken CIDs cache from in-memory data (fast, no DB)
        if (CoolNumberManager.isCacheStale()) {
            val taken = ChoTenTech.playerDataMap.values.map { it.cid.id.toString() }.toSet()
            CoolNumberManager.refreshTakenCids(taken)
        }
        // async load from DB to supplement
        submitAsync {
            val dbCids = DataManager.userDataMapper.listOf("cid") {}.filterNotNull()
            val all = (CoolNumberManager.getTakenCids() + dbCids).toSet()
            CoolNumberManager.refreshTakenCids(all)
        }
        openPageable(player)
    }

    private fun openPageable(player: Player) {
        val digit = selectedDigits
        val cat = selectedCategory

        val numbers = if (digit != null) {
            CoolNumberManager.generateNumbers(digit, cat, 0, 300)
        } else emptyList()

        player.openMenu<PageableChest<CoolNumberInfo>>(
            mmJsonUtil.serialize(
                "<black><font:minecraft:fusionpixel10>靓号商城".toComponent()
            )
        ) {
            rows(6)

            map(
                " 4567890 ",
                " ABCDEFG ",
                " ####### ",
                " ####### ",
                " ####### ",
                "  < S >  "
            )

            // ── digit buttons (row 0: slots 1-7) ──
            digitsList.forEachIndexed { idx, d ->
                val slot = idx + 1
                val active = digit == d
                set(slot, buildDigitItem(d, active)) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    CoolNumberMenu(d, cat).open(clicker)
                }
            }

            // ── category buttons (row 1: slots 10-16) ──
            categories.forEachIndexed { idx, c ->
                val pos = 10 + idx
                val active = cat == c
                set(pos, buildCategoryItem(c, active)) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    if (digit != null) {
                        CoolNumberMenu(digit, if (active) null else c).open(clicker)
                    } else {
                        player.sendMessage("§c请先选择位数")
                    }
                }
            }

            // ── number slots ──
            slotsBy('#')

            elements {
                if (numbers.isEmpty() || digit == null) {
                    val text = if (digit == null) "请先选择位数" else "无可用号码"
                    List(21) { CoolNumberInfo(text, 0, CoolNumberTier.NORMAL, 0, 0) }
                } else {
                    numbers.map { CoolNumberManager.buildCoolNumberInfo(it) }
                }
            }

            onGenerate { _, info, _, _ ->
                if (digit == null) buildPlaceholderItem("§7请先选择位数")
                else if (numbers.isEmpty()) buildPlaceholderItem("§7无可用号码")
                else buildNumberItem(info)
            }

            onClick { event, info ->
                if (info.number.all { it.isDigit() } && info.number.length in 4..10) {
                    event.clicker.playSound(event.clicker.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    CoolNumberConfirmMenu(info).open(event.clicker)
                }
            }

            setPreviousPage(7) { _, hasPrev ->
                guiItem(XMaterial.ARROW, "<green>上一页", model = if (hasPrev) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, hasNext ->
                guiItem(XMaterial.ARROW, "<green>下一页", model = if (hasNext) "kangelitem:next" else "kangelitem:next_no")
            }

            // ── Search button (slot 4 + 9*5 = 49) ──
            set(49, buildSearchItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                startSearch(clicker)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }
        }
    }

    private fun startSearch(player: Player) {
        player.sendMessage(" §a请输入要搜索的靓号（4-10位数字）")
        player.sendMessage(" §7输入 §ccancel §7取消搜索")

        player.nextChat(
            function = { message ->
                if (message.equals("cancel", ignoreCase = true)) {
                    player.sendMessage(" §7已取消搜索")
                    open(player)
                    return@nextChat
                }
                val result = CoolNumberManager.isSearchValid(message)
                when (result) {
                    is CoolNumberManager.CoolNumberSearchResult.Available -> {
                        CoolNumberConfirmMenu(result.info).open(player)
                    }
                    is CoolNumberManager.CoolNumberSearchResult.Invalid -> {
                        player.sendMessage(" §c${result.reason}")
                        startSearch(player)
                    }
                }
            },
            reuse = { player.sendMessage(" §c你已有待处理的输入") }
        )
    }

    // ── Item builders ──

    private fun buildDigitItem(digit: Int, active: Boolean): ItemStack {
        val item = XMaterial.LIME_STAINED_GLASS_PANE.parseItem() ?: ItemStack(Material.LIME_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        val color = if (active) "<green><b>" else "<gray>"
        meta.displayName("${color}$digit 位靓号".toComponent())
        val lore = mutableListOf(
            "<!i>${if (active) "<green>✓ 已选中" else "<gray>点击选择"}"
        )
        if (active) lore.add(0, "<!i><dark_gray><st>${" ".repeat(50)}</st>")
        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildCategoryItem(category: CoolNumberCategory, active: Boolean): ItemStack {
        val item = XMaterial.BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item
        val color = if (active) "<gold><b>" else "<white>"
        meta.displayName("${color}${category.displayName}".toComponent())
        meta.lore(listOf(
            "<!i><gray>${category.desc}",
            "<!i>${if (active) "<gold>✓" else "<gray>点击选择"}"
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildNumberItem(info: CoolNumberInfo): ItemStack {
        val item = XMaterial.PAPER.parseItem() ?: ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName("${info.tier.color}<b>${info.number}".toComponent())

        meta.lore(listOf(
            "<!i><dark_gray><st>${" ".repeat(50)}</st>",
            "<!i><gray>分数: <yellow>${info.score}",
            "<!i><gray>等级: ${info.tier.color}${info.tier.displayName}",
            "<!i><gray>贸易点: <green>${info.pointPrice}",
            "<!i><gray>磁带: <aqua>${info.tapePrice}",
            "<!i><dark_gray><st>${" ".repeat(50)}</st>",
            "<!i><green>点击查看详情"
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildSearchItem(): ItemStack {
        val item = XMaterial.COMPASS.parseItem() ?: ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item
        meta.displayName("<b><aqua>搜索靓号".toComponent())
        meta.lore(listOf(
            "<!i><gray>点击输入号码搜索",
            "<!i><gray>输入 cancel 取消",
            "<!i><dark_gray><st>${" ".repeat(50)}</st>",
            "<!i><gray>禁止: ${CoolNumberManager.bannedNumbers.joinToString(", ")}"
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildPlaceholderItem(text: String): ItemStack {
        val item = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem() ?: ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.displayName(text.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

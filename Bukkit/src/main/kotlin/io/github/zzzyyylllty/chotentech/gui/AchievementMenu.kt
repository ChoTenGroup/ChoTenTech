package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import io.github.zzzyyylllty.chotentech.util.guiItem
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

private val showHowtoMap = ConcurrentHashMap<String, Boolean>()

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

class AchievementMenu {
    fun open(player: Player) {
        val total = AchievementManager.getTotalCount()
        val completed = AchievementManager.getCompletedCount(player)
        val uuid = player.uniqueId.toString()
        val showHowto = showHowtoMap.getOrDefault(uuid, false)
        val sortedEntries = AchievementManager.getSortedEntries(player)

        player.openMenu<PageableChest<AchievementManager.AchievementEntry>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>成就列表 [$completed/$total]".toComponent())
        ) {
            rows(6)

            map(
                "CCC  H<>",
                "         ",
                " ####### ",
                " ####### ",
                " ####### ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<gray>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            slotsBy('#')

            elements { sortedEntries }

            onGenerate { _, entry, _, _ ->
                buildAchievementItem(entry, showHowto)
            }

            // 切换达成方法显示
            set('H', buildHowtoToggle(showHowto)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                showHowtoMap[uuid] = !showHowto
                // 重新打开菜单刷新
                clicker.closeInventory()
                AchievementMenu().open(clicker)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                buildSimpleItem(XMaterial.ARROW, "<dark_aqua><b>上一页").apply {
                    val m = itemMeta ?: return@apply
                    m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:previous" else "kangelitem:previous_no")
                    setItemMeta(m)
                }
            }

            setNextPage(8) { _, page ->
                buildSimpleItem(XMaterial.ARROW, "<dark_aqua><b>下一页").apply {
                    val m = itemMeta ?: return@apply
                    m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:next" else "kangelitem:next_no")
                    setItemMeta(m)
                }
            }
        }
    }

    private fun buildHowtoToggle(showHowto: Boolean): ItemStack {
        val icon = if (showHowto) XMaterial.WRITABLE_BOOK else XMaterial.BOOK
        val item = icon.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        meta.displayName(
            if (showHowto) "<gold>达成方法: <green>显示中".toComponent()
            else "<gold>达成方法: <gray>已隐藏".toComponent()
        )
        meta.lore(listOf(
            "<!i><gray>点击${if (showHowto) "隐藏" else "显示"}达成方法",
            "<!i><gray>开启后将看到成就的达成条件"
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildAchievementItem(
        entry: AchievementManager.AchievementEntry,
        showHowto: Boolean
    ): ItemStack {
        val isCompleted = entry.progress?.completed == true

        val mat = try {
            XMaterial.valueOf(entry.achievement.icon.uppercase())?.parseItem()
        } catch (_: Exception) {
            null
        } ?: ItemStack(Material.PAPER)

        val meta = mat.itemMeta ?: return mat

        // 物品模型 (未完成/已完成不同)
        val modelKey = when {
            isCompleted && entry.achievement.doneModel != null ->
                NamespacedKey.fromString(entry.achievement.doneModel)
            !isCompleted && entry.achievement.lockedModel != null ->
                NamespacedKey.fromString(entry.achievement.lockedModel)
            entry.achievement.model != null ->
                NamespacedKey.fromString(entry.achievement.model)
            else -> null
        }
        if (modelKey != null) meta.itemModel = modelKey

        // 名称
        meta.displayName(
            if (isCompleted) "<green><b>✦</b> ${entry.achievement.name}".toComponent()
            else "<gray><b>?</b> ${entry.achievement.name}".toComponent()
        )

        val cat = AchievementManager.categories[entry.achievement.category]
        val lore = mutableListOf<String>()

        // 分类
        lore += "<!i><gray>分类: ${cat?.name ?: entry.achievement.category}"
        lore += "<!i> "

        // 描述
        entry.achievement.description.forEach { lore += "<!i><#cccccc>$it" }
        lore += "<!i> "

        // 达成方法 (可隐藏)
        if (entry.achievement.howto.isNotEmpty()) {
            lore += "<!i><gold><b>达成方法:"
            entry.achievement.howto.forEach { lore += "<!i> <#ffaa66>· $it" }
            lore += "<!i> "
        }

        // 完成状态
        if (isCompleted) {
            lore += "<!i><green>✓ 已完成"
            val time = entry.progress?.completedTime
            if (time != null) {
                lore += "<!i><dark_gray>完成时间: ${dateFormat.format(Date(time))}"
            }
        } else {
            lore += "<!i><red>✗ 未完成"
        }

        meta.lore(lore.toComponent())
        mat.setItemMeta(meta)
        return mat
    }

    private fun buildSimpleItem(material: XMaterial, name: String): ItemStack {
        val item = material.parseItem() ?: ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(name.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

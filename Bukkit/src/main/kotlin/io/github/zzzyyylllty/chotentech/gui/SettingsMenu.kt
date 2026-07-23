package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

class SettingsMenu {

    companion object {
        private val soundPrefMap = mutableMapOf<String, Boolean>()
    }

    fun open(player: Player) {
        val data = player.getData() ?: return
        val uuid = player.uniqueId.toString()

        val settings = mutableListOf<SettingItem>()
        settings += SettingItem(
            id = "pvp",
            name = "PVP 模式",
            desc = "开启后可以与其他开启 PVP 的玩家战斗",
            icon = XMaterial.DIAMOND_SWORD,
            enabled = data.pvpEnabled,
            model = "kangelitem:void"
        )

        val soundEnabled = soundPrefMap.getOrDefault(uuid, true)
        settings += SettingItem(
            id = "sound",
            name = "界面音效",
            desc = "关闭后不再播放 GUI 点击等界面音效",
            icon = if (soundEnabled) XMaterial.NOTE_BLOCK else XMaterial.NOTE_BLOCK,
            enabled = soundEnabled
        )

        player.openMenu<PageableChest<SettingItem>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>玩家设置".toComponent())
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

            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            slotsBy('#')

            elements { settings }

            onGenerate { _, setting, _, _ ->
                buildSettingItem(setting)
            }

            onClick { event, setting ->
                val clicker = event.clicker
                when (setting.id) {
                    "pvp" -> {
                        val pd = clicker.getData() ?: return@onClick
                        pd.pvpEnabled = !pd.pvpEnabled
                        if (pd.pvpEnabled) {
                            clicker.sendComponent("<red>⚔ PVP模式已<dark_red>开启</dark_red>")
                        } else {
                            clicker.sendComponent("<green>☮ PVP模式已<dark_green>关闭</dark_green>")
                        }
                        if (soundPrefMap.getOrDefault(clicker.uniqueId.toString(), true)) {
                            clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        }
                        resetElementsCache()
                        open(clicker)
                    }
                    "sound" -> {
                        val current = soundPrefMap.getOrDefault(clicker.uniqueId.toString(), true)
                        soundPrefMap[clicker.uniqueId.toString()] = !current
                        if (!current) {
                            clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        }
                        resetElementsCache()
                        open(clicker)
                    }
                }
            }

            onPageChange { p ->
                if (soundPrefMap.getOrDefault(p.uniqueId.toString(), true)) {
                    p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
                }
            }

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<gray>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<dark_aqua><b>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<dark_aqua><b>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildSettingItem(setting: SettingItem): ItemStack {
        val icon = if (setting.enabled) setting.icon else XMaterial.GRAY_DYE
        val item = icon.parseItem() ?: return ItemStack(org.bukkit.Material.BARRIER)
        val meta = item.itemMeta ?: return item

        if (setting.model != null) {
            meta.itemModel = NamespacedKey.fromString(setting.model)
        }

        val statusColor = if (setting.enabled) "<green>" else "<red>"
        val statusText = if (setting.enabled) "已开启" else "已关闭"

        meta.displayName("<bold>${setting.name} ${statusColor}[$statusText]".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        lore += "<!i><gray>${setting.desc}"
        lore += "<!i>"
        lore += "<!i>${statusColor}点击${if (setting.enabled) "关闭" else "开启"}"
        lore += "<!i><dark_gray>${setting.id}"

        meta.lore(lore.map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private data class SettingItem(
        val id: String,
        val name: String,
        val desc: String,
        val icon: XMaterial,
        val enabled: Boolean,
        val model: String? = null
    )
}

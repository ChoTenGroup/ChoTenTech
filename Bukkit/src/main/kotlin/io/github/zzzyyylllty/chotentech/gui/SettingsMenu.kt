package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.profile.SettingDef
import io.github.zzzyyylllty.chotentech.data.profile.SettingRegistry
import io.github.zzzyyylllty.chotentech.data.profile.SettingType
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
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

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()
        val settings = ProfileManager.getSharedData(uuid).settings

        val settingItems = SettingRegistry.all.map { SettingItem(it) }

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

            elements { settingItems }

            onGenerate { _, setting, _, _ ->
                buildSettingItem(setting, settings)
            }

            onClick { event, setting ->
                val clicker = event.clicker
                val sid = clicker.uniqueId.toString()
                val s = ProfileManager.getSharedData(sid).settings
                val def = setting.def

                when (def.action) {
                    "pvp" -> {
                        s.values[def.id] = (!s.getBoolean(def.id)).toString()
                        clicker.getData()?.pvpEnabled = s.getBoolean(def.id)
                        if (s.getBoolean(def.id)) {
                            clicker.sendComponent("<red>⚔ PVP模式已<dark_red>开启</dark_red>")
                        } else {
                            clicker.sendComponent("<green>☮ PVP模式已<dark_green>关闭</dark_green>")
                        }
                        playClick(sid)
                    }
                    "scoreboard" -> {
                        s.values[def.id] = (!s.getBoolean(def.id)).toString()
                        val cmd = if (s.getBoolean(def.id)) "on" else "off"
                        clicker.performCommand("scoreboard $cmd")
                        val status = if (s.getBoolean(def.id)) "开启" else "关闭"
                        clicker.sendComponent("<gray>计分板已<white>$status")
                        playClick(sid)
                    }
                    "sound" -> {
                        s.values[def.id] = (!s.getBoolean(def.id)).toString()
                        if (!s.getBoolean(def.id)) {
                            clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        }
                    }
                    else -> {
                        when (def.type) {
                            SettingType.TOGGLE -> {
                                s.values[def.id] = (!s.getBoolean(def.id)).toString()
                                val status = if (s.getBoolean(def.id)) "开启" else "关闭"
                                clicker.sendComponent("<gray>${def.name}已<white>$status")
                                playClick(sid)
                            }
                            SettingType.CYCLE -> {
                                val options = def.options
                                val currentIdx = options.indexOfFirst { it.first == s.get(def.id) }
                                val nextIdx = (currentIdx + 1) % options.size
                                s.values[def.id] = options[nextIdx].first
                                clicker.sendComponent("<gray>${def.name}已切换至: <white>${options[nextIdx].second}")
                                playClick(sid)
                            }
                        }
                    }
                }
                ProfileManager.saveSharedData(sid)
                resetElementsCache()
                open(clicker)
            }

            onPageChange { p ->
                val sid = p.uniqueId.toString()
                if (ProfileManager.getSharedData(sid).settings.getBoolean("soundEnabled")) {
                    p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
                }
            }

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<!i> <yellow>点击 <green>打开菜单"), model = "kangelitem:void")) {
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

    private fun buildSettingItem(setting: SettingItem, settings: io.github.zzzyyylllty.chotentech.data.profile.PlayerSettings): ItemStack {
        val def = setting.def
        val enabled = when (def.type) {
            SettingType.TOGGLE -> settings.getBoolean(def.id)
            SettingType.CYCLE -> true
        }
        val icon = if (enabled) def.icon else XMaterial.GRAY_DYE
        val item = icon.parseItem() ?: return ItemStack(org.bukkit.Material.BARRIER)
        val meta = item.itemMeta ?: return item

        if (def.model != null) {
            meta.itemModel = NamespacedKey.fromString(def.model)
        }

        val currentValue = settings.get(def.id)
        val currentLabel = def.options.firstOrNull { it.first == currentValue }?.second ?: currentValue
        val displayStatus = if (def.type == SettingType.CYCLE) {
            "<yellow>[$currentLabel]"
        } else {
            val statusColor = if (enabled) "<green>" else "<red>"
            "${statusColor}[${if (enabled) "已开启" else "已关闭"}]"
        }

        meta.displayName("<bold>${def.name} $displayStatus".toComponent())

        val lore = mutableListOf<String>()
        val descLines = def.desc.lines()
        if (descLines.size > 1) {
            lore += loreSection("说明")
            descLines.forEach { lore += "<!i><gray>$it" }
        } else {
            lore += loreInfo("说明", "<gray>${descLines.firstOrNull() ?: ""}")
        }
        lore += loreSpace()
        if (def.type == SettingType.CYCLE) {
            lore += loreSection("选项")
            def.options.forEach { (value, label) ->
                val color = if (value == currentValue) "<green>" else "<gray>"
                lore += loreDash("$color$label")
            }
            lore += loreSpace()
            lore += loreInfo("当前值", "<green>$currentLabel")
        } else {
            lore += "<!i> <yellow>点击 <green>${if (enabled) "关闭" else "开启"}"
        }
        lore += loreInfo("ID", "<dark_gray>${def.id}")

        meta.lore(lore.map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private fun playClick(uuid: String) {
        if (ProfileManager.getSharedData(uuid).settings.getBoolean("soundEnabled")) {
            val p = Bukkit.getPlayer(java.util.UUID.fromString(uuid)) ?: return
            p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        }
    }

    private data class SettingItem(val def: SettingDef)
}

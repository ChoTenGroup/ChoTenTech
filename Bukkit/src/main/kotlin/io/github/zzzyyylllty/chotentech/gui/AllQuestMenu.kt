package io.github.zzzyyylllty.chotentech.gui

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.addon.AddonTrack.Companion.trackQuest
import ink.ptms.chemdah.core.quest.meta.Meta
import io.github.zzzyyylllty.chotentech.chemdah.GuiItem
import io.github.zzzyyylllty.chotentech.chemdah.addon.ChoTenUI
import io.github.zzzyyylllty.chotentech.chemdah.addon.ChoTenUI.Companion.chotenui
import io.github.zzzyyylllty.chotentech.chemdah.build
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem

class AllQuestMenu {
    fun open(player: Player) {

        player.openMenu<PageableChest<Quest>>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>进行中的任务".toComponent())) {

            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            val profile = ChemdahAPI.playerProfile[player.name]
            val quests = profile?.getQuests() ?: emptyList()

            // 设置可用槽位（通过字符）
            slotsBy('@')

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<gray>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            // 设置元素列表
            elements { quests }

            // 生成每个元素对应的物品
            onGenerate(async = true) { player, element, index, slot ->
                val cui = element.template.chotenui()
                val profile = profile ?: run {
                    warning("玩家 ${player.name} 的 Chemdah 数据尚未加载完成")
                    player.sendComponent("<red>您的数据正在加载，请耐心等待...")
                    null
                }
                val item = ChoTenUI.resolveIcon(cui?.icon, "任务 ${element.template.id}")
                if (profile != null) {
                    val meta = item.itemMeta
                    meta.customName(("<!i><white>「<green>" + element.template.meta<Meta<String>>("name")?.source?.legacyToMiniMessage() + "<reset><!i>」").toComponent())
                    val lore = mutableListOf<String>()
                    lore += "<!i> "
                    cui?.npcID?.let {
                        lore += "<!i><white><b>|<reset> <!i><aqua>任务来自 <gold>$it"
                        lore += "<!i> "
                    }
                    lore += "<!i><white><b>|<reset> <!i><aqua>进程"
                    element.tasks.forEach {
                        val name = it.meta<Meta<String>>("name")?.source?.legacyToMiniMessage()
                        lore += if (it.isCompleted(profile)) {
                            "<!i> <dark_gray>- <#88aacc>$name <#33ffaa>已完成"
                        } else {
                            val progress = it.objective.getProgress(profile, it)
                            val value = progress.value.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                            val target = progress.target.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                            "<!i> <dark_gray>- <#88aacc>$name <#ff3311>$value<#aaaabb>/$target"
                        }
                    }
                    lore += "<!i> "
                    cui?.descriptionIndex?.let {
                        lore += "<!i><white><b>|<reset> <!i><aqua>引子"
                        it.forEach {
                            lore += "<!i><#aacc88>$it"
                        }
                        lore += "<!i> "
                    }
                    cui?.description?.let {
                        lore += "<!i><white><b>|<reset> <!i><aqua>介绍"
                        it.forEach {
                            lore += "<!i><#ccaa88>$it"
                        }
                        lore += "<!i> "
                    }
                    cui?.descriptionReward?.let {
                        lore += "<!i><white><b>|<reset> <!i><aqua>奖励"
                        it.forEach {
                            lore += "<!i> <dark_gray>- <#88aacc>$it"
                        }
                        lore += "<!i> "
                    }
                    lore += "<!i><white><b>|<reset> <i>${providerText(cui)}"
                    lore += "<!i><yellow>点击 <green>打开详细菜单"
                    lore += "<!i><yellow>右键 <green>追踪任务"
                    lore += "<!i> "
                    meta.lore(lore.toComponent())
                    cui?.model?.let { meta.itemModel = NamespacedKey.fromString(it) }
                    cui?.cmd?.let { meta.setCustomModelData(it.toInt()) }
                    item.setItemMeta(meta)
                }
                item
            }

            // 元素点击事件
            onClick { event, element ->
                event.isCancelled = true
                val bukkitPlayer = event.clicker
                if (event.clickEvent().isRightClick) {
                    // 追踪/取消追踪任务,用音效提示
                    val tracking = profile?.trackQuest
                    val willTrack = tracking !== element.template
                    profile?.trackQuest = if (willTrack) element.template else null
                    bukkitPlayer.playSound(
                        bukkitPlayer.location,
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        1f,
                        if (willTrack) 1.7f else 0.7f
                    )
                } else {
                    bukkitPlayer.playSound(bukkitPlayer.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    QuestMenu().open(bukkitPlayer, element)
                }
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            // 设置上一页按钮
            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<green>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            // 设置下一页按钮
            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<green>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }
}


fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

fun providerText(cui: ChoTenUI?): String {
    val typeText = when (cui?.type) {
        "OPTIONAL" -> "可选"
        "CHAIN" -> "连环"
        "CRITICAL" -> "关键"
        "SPECIAL" -> "特殊"
        else -> "公有"
    }
    return when (cui?.provider?.uppercase()) {
        "ZERO" -> "<dark_blue>由 ZERO 提供的${typeText}任务"
        "XINGCHEN" -> "<aqua>由 星辰闪烁 提供的${typeText}任务"
        "FANXING" -> "<dark_aqua>由 可爱的小狗拔牙 提供的${typeText}任务"
        "ACHU", "THEACHU" -> "<light_purple>由 TheAchu 提供的${typeText}任务"
        "CEDRIC" -> "<gold>由 Parte 提供的${typeText}任务"
        null -> "<gray>原生${typeText}任务"
        else -> "<dark_blue>由 ${cui.provider} 提供的${typeText}任务"
    }
}
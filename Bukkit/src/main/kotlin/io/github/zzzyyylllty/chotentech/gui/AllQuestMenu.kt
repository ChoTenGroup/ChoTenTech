package io.github.zzzyyylllty.chotentech.gui

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.meta.Meta
import io.github.zzzyyylllty.chotentech.chemdah.GuiItem
import io.github.zzzyyylllty.chotentech.chemdah.addon.ChoTenUI.Companion.chotenui
import io.github.zzzyyylllty.chotentech.chemdah.build
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

import kotlin.collections.toList

class AllQuestMenu {
    fun open(player: Player) {

        player.openMenu<PageableChest<Quest>>("&f进行中的任务".colored()) {

            rows(6)

            map(
                "#########",
                "#########",
                "#########",
                "#########",
                "#########",
                "---B-C---"
            )

            set('-', GuiItem("background", "BLACK_STAINED_GLASS_PANE", " ").build())

            val profile = ChemdahAPI.playerProfile[player.name]

            // 设置可用槽位（通过字符）
            slotsBy('#')

            // 设置元素列表
            elements { profile?.getQuests() ?: emptyList() }

            // 生成每个元素对应的物品
            onGenerate(async = true) { player, element, index, slot ->
                val cui = element.template.chotenui()
                val profile = profile ?: run {
                    warning("玩家 ${player.name} 的 Chemdah 数据尚未加载完成")
                    player.sendComponent("<red>您的数据正在加载，请耐心等待...")
                    null
                }
                val item = cui?.icon?.let { XMaterial.valueOf(it) }?.parseItem() ?: ItemStack(Material.PAPER)
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
                        val progress = it.objective.getProgress(profile, it)
                        val value = progress.value.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                        val target = progress.target.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                        lore += "<!i> <dark_gray>- <#88aacc>$name <#83ff66>$value<#aaaabb>/$target"
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
                    lore += "<!i> <yellow>点击 <green>打开详细菜单"
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
                QuestMenu().open(bukkitPlayer, element)

            }

            // 设置上一页按钮
            setPreviousPage(48) { page, hasPreviousPage ->
                GuiItem("previouspage", "ARROW", "<dark_aqua><b>切换上一页").build()
            }

            // 设置下一页按钮
            setNextPage(50) { page, hasNextPage ->
                GuiItem("nextpage", "ARROW", "<dark_aqua><b>切换下一页").build()
            }
        }
    }
}


fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}
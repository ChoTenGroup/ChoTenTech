package io.github.zzzyyylllty.chotentech.gui

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.meta.Meta
import io.github.zzzyyylllty.chotentech.chemdah.GuiItem
import io.github.zzzyyylllty.chotentech.chemdah.addon.ChoTenUI.Companion.chotenui
import io.github.zzzyyylllty.chotentech.chemdah.build
import io.github.zzzyyylllty.chotentech.event.SubmitItemEvent
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.toComponent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

import kotlin.collections.toList
import kotlin.collections.toMutableList
import kotlin.math.round
import kotlin.math.roundToInt

class QuestMenu {
    fun open(player: Player, quest: Quest) {

        val questName = quest.template.meta<Meta<String>>("name")?.source

        player.openMenu<PageableChest<Task>>("&f任务: $questName".colored()) {

            rows(6)

            map(
                "#########",
                "#########",
                "#########",
                "#########",
                "#########",
                "<--B-C---"
            )

            set('-', GuiItem("background", "BLACK_STAINED_GLASS_PANE", " ").build())

            val profile = ChemdahAPI.playerProfile[player.name]

            // 设置可用槽位（通过字符）
            slotsBy('#')

            // 设置元素列表
            elements { quest.tasks.toList() }

            // 生成每个元素对应的物品
            onGenerate(async = true) { player, element, index, slot ->
                val cui = quest.template.chotenui()
                val profile = profile ?: run {
                    warning("玩家 ${player.name} 的 Chemdah 数据尚未加载完成")
                    player.sendComponent("<red>您的数据正在加载，请耐心等待...")
                    player.closeInventory()
                    null
                }
                val item = cui?.icon?.let { XMaterial.valueOf(it) }?.parseItem() ?: ItemStack(Material.PAPER)
                if (profile != null) {
                    val element = quest.getTask(element.id) ?: return@onGenerate ItemStack(Material.BARRIER)
                    val progress = element.objective.getProgress(profile, element)
                    val value = progress.value.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                    val target = progress.target.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                    val meta = item.itemMeta
                    meta.customName(("<!i><white>「<gray>" + element.meta<Meta<String>>("name")?.source?.legacyToMiniMessage() + "<reset><white><!i>」").toComponent())
                    val lore = mutableListOf<String>()
                    lore += " "
                    lore += "<!i><gray> 进度 <#99FC65>${progress.percent.roundToInt()}%"
                    lore += " "
                    lore += "<!i><gray> 当前任务进度 <#66CCFF>$value<gray>/<#FFCC66>$target"
                    lore += " "
                    if (element.objective.name == "choten submit") {
                        lore += "<!i> <dark_gray>- <yellow>点击 <green>提交任务物品"
                    }
                    meta.lore(lore.toComponent())
                    item.setItemMeta(meta)
                }
                item
            }

            // 元素点击事件
            onClick { event, element ->
                event.isCancelled = true
                val bukkitPlayer = event.clicker

                if (element.objective.name == "choten submit") {
                    SubmitItemEvent(player, quest, element).call()
                    resetElementsCache()
                    QuestMenu().open(bukkitPlayer, quest)
                }

            }

            onClick(45) {
                player.closeInventory()
                AllQuestMenu().open(player)
            }

            // 设置下一页按钮
            setNextPage(50) { page, hasNextPage ->
                GuiItem("nextpage", "ARROW", "切换下一页").build()
            }

            // 设置上一页按钮
            setPreviousPage(48) { page, hasPreviousPage ->
                GuiItem("previouspage", "ARROW", "切换上一页").build()
            }


            set(45) {
                GuiItem("back", "BARRIER", "<red><b>返回").build()
            }
        }
    }
}
package io.github.zzzyyylllty.chotentech.gui

import ink.ptms.chemdah.api.ChemdahAPI.chemdahProfile
import ink.ptms.chemdah.api.ChemdahAPI.isChemdahProfileLoaded
import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.meta.Meta
import io.github.zzzyyylllty.chotentech.chemdah.GuiItem
import io.github.zzzyyylllty.chotentech.chemdah.addon.ChoTenUI.Companion.chotenui
import io.github.zzzyyylllty.chotentech.chemdah.build
import io.github.zzzyyylllty.chotentech.event.SubmitItemEvent
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

import kotlin.collections.toList
import kotlin.math.roundToInt

class QuestMenu {
    fun open(player: Player, quest: Quest) {

        val questName = quest.template.meta<Meta<String>>("name")?.source

        player.openMenu<PageableChest<Task>>("<shift:-16><image:kangelicon:quests><shift:-256>&f任务: $questName".colored()) {

            rows(6)

            map(
                "         ",
                " ####### ",
                " ####### ",
                " ####### ",
                "         ",
                "<  B-C   "
            )

            set('-', GuiItem("background", "BLACK_STAINED_GLASS_PANE", " ").build())

            val profile = if (player.isChemdahProfileLoaded) player.chemdahProfile else null

            // 设置可用槽位（通过字符）
            slotsBy('#')

            handLocked(false)

            // 设置元素列表
            elements { quest.tasks.toList() }


            // 生成每个元素对应的物品
            onGenerate(async = true) { player, element, index, slot ->
                val profile = profile ?: run {
                    warning("玩家 ${player.name} 的 Chemdah 数据尚未加载完成")
                    player.sendComponent("<red>您的数据正在加载，请耐心等待...")
                    player.closeInventory()
                    return@onGenerate ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                }
                if (profile.questMap[quest.template.id] == null && index == 0) {
                    devLog("questMap: ${profile.questMap}")
                    player.sendComponent("<red>当前任务已不再进行,自动返回上个菜单...")
                    player.closeInventory()
                    AllQuestMenu().open(player)
                    return@onGenerate ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                } else {
                    val cui = quest.template.chotenui()
                    val item = cui?.icon?.let { XMaterial.valueOf(it) }?.parseItem() ?: ItemStack(Material.PAPER)
                    val element = quest.getTask(element.id) ?: return@onGenerate ItemStack(Material.BARRIER)
                    val progress = element.objective.getProgress(profile, element)
                    val value = progress.value.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                    val target = progress.target.toString().toDouble().roundTo(1).toString().removeSuffix(".0")
                    val meta = item.itemMeta
                    meta.customName(("<!i><white>「<gray>" + element.meta<Meta<String>>("name")?.source?.legacyToMiniMessage() + "<reset><white><!i>」").toComponent())
                    val lore = mutableListOf<String>()
                    lore += " "
                    lore += "<!i><gray> 进度 <#99FC65>${(progress.percent * 100.0).roundToInt()}%"
                    lore += " "
                    lore += "<!i><gray> 当前任务进度 <#66CCFF>$value<gray>/<#FFCC66>$target"
                    lore += " "
                    if (element.objective.name == "choten submit") {
                        lore += "<!i> <dark_gray>- <yellow>点击 <green>提交任务物品"
                    }
                    meta.lore(lore.toComponent())
                    item.setItemMeta(meta)
                    item
                }
            }

            // 元素点击事件
            onClick() { event, element ->
//                event.isCancelled = true
                val bukkitPlayer = event.clicker
                bukkitPlayer.playSound(bukkitPlayer.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

                if (element.objective.name == "choten submit" && event.cursorItem == null) {
                    SubmitItemEvent(player, quest, element, event.cursorItem).call()
                    event.isCancelled = true
                    resetElementsCache()
                    QuestMenu().open(bukkitPlayer, quest)
                } else {
                    event.isCancelled = true
                }

            }

            onClick(45) {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                player.closeInventory()
                AllQuestMenu().open(player)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
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
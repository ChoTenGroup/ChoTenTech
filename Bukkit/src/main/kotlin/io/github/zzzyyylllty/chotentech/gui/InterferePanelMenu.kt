package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.function.MetaManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreSpace

/**
 * 干涉事务面板 —— 由 draft/interferes.conf (菜单插件配置) 重写为 TabooLib PageableChest 菜单。
 * 展示全部剧情事务条目，进度存于玩家 meta:
 *   "{idef}"        -> 0 未解锁 / 1 进行中 / 2 已完成
 *   "{idef}result"  -> 已完成时的结果描述文本
 * 点击进行中的事务打开 InterfereManager 实例详情；返回按钮回到 MainMenu。
 */
class InterferePanelMenu {

    /** 剧情事务条目(对应 conf generator elements) */
    data class Entry(
        val name: String,
        val idef: String,
        val idefresult: String,
        val lore: String
    )

    private val entries = listOf(
        Entry(
            "权力斗争", "interfere_power", "result_power",
            "M.E.G. 内部其实也不是铁板一块，\n目前深陷权力斗争的困境。\n<#B46572>改组<#A9B4C3>，<#5FA8A3>保守<#A9B4C3>，<#EFDF8F>维新<#A9B4C3>三派为了争权夺利大打出手。\n<#6E7A8C>我至死也未能见到照耀我祖国的曙光。"
        ),
        Entry(
            "毛瑟兵变", "interfere_army", "result_army",
            "毛瑟因不满 M.E.G. 的制度打算脱离一部分军队。\n<#6E7A8C>痛苦面前，没有英雄。"
        ),
        Entry(
            "办公室紧急状态", "interfere_office", "result_office",
            "废弃办公室最近来了一批不速之客，\n<#6E7A8C>我们争取解放的道路不得已停下了脚步。"
        ),
        Entry(
            "宜居地带冲突", "interfere_skirmish", "result_skirmish",
            "对于那些盘踞了许久的叛军，\n是时候驱赶他们，还宜居地带一个自由了。\n<#6E7A8C>天日昭昭，天日昭昭！"
        ),
        Entry(
            "我不是药神", "interfere_medicinal", "result_medicinal",
            "他们只是想要活着，有什么错?\n<#6E7A8C>病是一种罪过，上帝也无法赦免。\n<#6E7A8C>穷是一种病态，药神也无法医治。"
        ),
        Entry(
            "奇爱博士", "interfere_stopworrying", "result_stopworrying",
            "这样的装置是不可能起到有效的威慑作用的...\n<#6E7A8C>这个世界只是一台手摇风琴，只能由天主亲自弹奏。\n<#6E7A8C>而我们所有人都只能跟着风箱拉出的音符起舞。"
        ),
        Entry(
            "贸易集团通商", "interfere_trade", "result_trade",
            "贸易是我们的主要组成部分...\n<#6E7A8C>流沙之上，大厦必倾。"
        ),
        Entry(
            "煤炭监狱", "interfere_coal", "result_coal",
            "贸易是我们的主要组成部分...\n<#6E7A8C>那乌黑的双手，能否捧起一滩清泉？"
        )
    )

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()

        player.openMenu<PageableChest<Entry>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:interferes><shift:-176><#84D1CB><font:minecraft:fusionpixel10>干涉事务".toComponent())
        ) {
            rows(6)

            map(
                "XXXAAA<>",
                "AAAAAAAA",
                "A      A",
                "A      A",
                "A      A",
                "AAAAAAAA"
            )

            // X: 返回主菜单(对应 conf 的 menu open "menu")
            set('X', guiItem(XMaterial.STRUCTURE_VOID, "<#F2F5FA>返回", model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                MainMenu().open(clicker)
            }

            slotsBy('A')

            elements { entries }

            onGenerate { _, entry, _, _ ->
                buildEntryItem(uuid, entry)
            }

            onClick { event, entry ->
                val clicker = event.clicker
                // 对应 conf:进行中(1)才允许打开详情,否则提示
                val state = getProgressState(uuid, entry.idef)
                if (state == 1) {
                    clicker.playSound(clicker.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f)
                    when (entry.idef) {
                        // 权力斗争有专属面板(支持率/派系介绍)
                        "interfere_power" -> InterferePowerMenu().open(clicker)
                        else -> {
                            if (!InterfereMenu().openDetail(clicker, entry.idef)) {
                                clicker.sendComponent("<#E08A98>目前没有进行这个干涉事务!")
                            }
                        }
                    }
                } else {
                    clicker.playSound(clicker.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f)
                    clicker.sendComponent("<#E08A98>目前没有进行这个干涉事务!")
                }
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(6) { _, page ->
                guiItem(XMaterial.CYAN_STAINED_GLASS_PANE, "<#5FA8A3><bold>${page}", model = "kangelitem:previous_no")
            }

            setNextPage(7) { _, page ->
                guiItem(XMaterial.LIME_STAINED_GLASS_PANE, "<#5FA8A3><bold>${page}", model = "kangelitem:next_no")
            }
        }
    }

    /** 读取进度状态:0 未解锁 / 1 进行中 / 2 已完成 */
    private fun getProgressState(uuid: String, idef: String): Int =
        MetaManager.getIndependent(uuid, idef)?.value?.toIntOrNull() ?: 0

    /** 读取已完成时的结果文本,未指定时显示灰色"未指定" */
    private fun getResultText(uuid: String, entry: Entry): String =
        MetaManager.getIndependent(uuid, entry.idefresult)?.value?.takeIf { it.isNotBlank() } ?: "<#6E7A8C>未指定"

    /** 输出物品:书本,模型按进度切换 kangelicon:{idef}_{state}(对应 conf 的 item_model 表达式) */
    private fun buildEntryItem(uuid: String, entry: Entry): ItemStack {
        val item = XMaterial.BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        val state = getProgressState(uuid, entry.idef)
        NamespacedKey.fromString("kangelicon:${entry.idef}_$state")?.let { meta.itemModel = it }
        meta.displayName("<white>剧情事务: <yellow>${entry.name}".toComponent())

        val lore = mutableListOf<String>()
        lore += loreSpace()
        entry.lore.lines().forEach { lore += "<!i>${it.legacyToMiniMessage()}" }
        lore += loreSpace()
        when (state) {
            0 -> lore += "<!i><#E08A98>未解锁"
            1 -> lore += "<!i><#EFDF8F>进行中"
            2 -> lore += "<!i><#8FD4A6>已完成 <#6E7A8C>- 结果为 <#EFDF8F>${getResultText(uuid, entry)}"
        }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

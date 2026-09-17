package io.github.zzzyyylllty.chotentech.gui

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
import io.github.zzzyyylllty.chotentech.function.MetaManager
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSpace

/**
 * 权力斗争面板 —— 由 draft/interfere_power.conf (菜单插件配置) 重写为 TabooLib Chest 菜单。
 * 展示 M.E.G. 三大派系(改组/保守/维新)的势力介绍与支持率。
 * 得票数据存于玩家 meta: power_reg / power_con / power_ref(各派票数,缺省 100)。
 * 支持率 = 票数 / 三派总票数; 票堆模型按支持率与中位数比较取绿/黄/红。
 * 返回按钮回到 InterferePanelMenu。
 */
class InterferePowerMenu {

    /** 单个派系的展示数据 */
    private data class Faction(
        val name: String,
        val nameColor: String,
        val metaKey: String,
        val rep: String
    )

    private val factions = listOf(
        Faction("改组派", "<#B46572>", "power_reg", "ZERO"),
        Faction("保守派", "<#5FA8A3>", "power_con", "毛瑟"),
        Faction("维新派", "<#EFDF8F>", "power_ref", "波风")
    )

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()
        val stats = computeStats(uuid)

        player.openMenu<Chest>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:interfere_power><shift:-176><#84D1CB><font:minecraft:fusionpixel10>权力斗争".toComponent())
        ) {
            rows(6)

            map(
                "XXX     U",
                "         ",
                "AAABBBCCC",
                "AAABBBCCC",
                "A1AB2BC3C",
                "AAABBBCCC"
            )

            // X: 返回干涉事务面板(对应 conf 的 menu open interferes)
            set('X', guiItem(XMaterial.STRUCTURE_VOID, "<#F2F5FA>返回", model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f)
                InterferePanelMenu().open(clicker)
            }

            // U: 总票数汇总
            set('U', buildTotalItem(stats)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            // 三个派系的介绍面板(3x3 区域)与支持率图标
            set('A', buildIntroItem(factions[0])) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
            set('B', buildIntroItem(factions[1])) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
            set('C', buildIntroItem(factions[2])) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
            set('1', buildSupportItem(uuid, stats, factions[0]))
            set('2', buildSupportItem(uuid, stats, factions[1]))
            set('3', buildSupportItem(uuid, stats, factions[2]))
        }
    }

    private class Stats(
        val votes: Map<String, Long>,
        val total: Long,
        val percents: Map<String, Double>,
        val piles: Map<String, Int>,
        val med: Double
    )

    /** 读取三派票数并计算支持率/票堆数量/中位数(对应 conf pre_open 的模板逻辑) */
    private fun computeStats(uuid: String): Stats {
        val votes = factions.associate {
            it.metaKey to (MetaManager.getIndependent(uuid, it.metaKey)?.value?.toLongOrNull() ?: 100L)
        }
        val total = votes.values.sum().coerceAtLeast(1)
        val percents = votes.mapValues { (_, v) -> v * 100.0 / total }
        val piles = votes.mapValues { (_, v) -> (v * 100 / total / 10.0).toInt() }   // 支持率每 10% 一堆
        val med = percents.values.sorted()[1]   // 三值中位数
        return Stats(votes, total, percents, piles, med)
    }

    /** 票堆颜色:等于中位数黄色,高于绿色,低于红色(对应 conf 的 item_model 表达式) */
    private fun pileColor(percent: Double, med: Double): String = when {
        percent == med -> "yellow"
        percent > med -> "green"
        else -> "red"
    }

    /** 派系介绍物品(3x3 大图标区域,统一模型) */
    private fun buildIntroItem(faction: Faction): ItemStack {
        val item = XMaterial.STRUCTURE_VOID.parseItem() ?: ItemStack(Material.STRUCTURE_VOID)
        val meta = item.itemMeta ?: return item
        NamespacedKey.fromString(MODEL)?.let { meta.itemModel = it }
        meta.displayName("${faction.nameColor}<b>${faction.name}势力".toComponent())

        val lore = mutableListOf<String>()
        lore += loreInfo("代表人", "<#F2F5FA>${faction.rep}")
        lore += loreSpace()
        faction.intro.lines().forEach { lore += "<!i><#A9B4C3>$it" }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 支持率物品:票堆模型按颜色+堆数变化,标题显示百分比,lore 显示得票数 */
    private fun buildSupportItem(uuid: String, stats: Stats, faction: Faction): ItemStack {
        val item = XMaterial.STRUCTURE_VOID.parseItem() ?: ItemStack(Material.STRUCTURE_VOID)
        val meta = item.itemMeta ?: return item

        val percent = stats.percents.getValue(faction.metaKey)
        val color = pileColor(percent, stats.med)
        val pile = stats.piles.getValue(faction.metaKey)
        NamespacedKey.fromString("kangelicon:${color}_pile_$pile")?.let { meta.itemModel = it }

        meta.displayName(
            "${faction.nameColor}<b>${faction.name}<#F2F5FA>支持率: <#EFDF8F>${round2(percent)}<#6E7A8C>%".toComponent()
        )

        val lore = mutableListOf<String>()
        lore += loreSpace()
        lore += loreInfo("得票数", "<#EFDF8F>${stats.votes.getValue(faction.metaKey)} <#6E7A8C>/ 总票数${stats.total}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 总票数汇总(对应 U 槽位,便于直接查看总量) */
    private fun buildTotalItem(stats: Stats): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#84D1CB><b>总票数",
        listOf(
            loreSpace(),
            loreInfo("总票数", "<#EFDF8F>${stats.total}"),
            loreInfo("改组派", "<#B46572>${stats.votes.getValue("power_reg")}"),
            loreInfo("保守派", "<#5FA8A3>${stats.votes.getValue("power_con")}"),
            loreInfo("维新派", "<#EFDF8F>${stats.votes.getValue("power_ref")}")
        ),
        model = MODEL
    )

    private fun round2(v: Double): String = "%.2f".format(v).trimEnd('0').trimEnd('.')

    companion object {
        /** 通用隐形 GUI 物品模型(对应 conf 中 structure void + model 10000) */
        private const val MODEL = "kangelitem:void"

        /** 三个派系的势力介绍文本(对应 conf 中 A/B/C 的 lore) */
        private val Faction.intro: String
            get() = when (metaKey) {
                "power_reg" -> "<#B46572>改组派<#A9B4C3>由当年在与<#E08A98> U.E.C. <#A9B4C3>的战争中\n投靠<#E08A98> U.E.C. <#A9B4C3>的前官员组成，\n签订和平协议后因此这些人员回到了<#DFBD7C> M.E.G. <#A9B4C3>工作。\n该派系成员将<#E08A98> U.E.C. <#A9B4C3>在 Level 76 设立的无限下水道政府作为榜样，\n坚称与<#E08A98> U.E.C. <#A9B4C3>的真正合作会为<#DFBD7C> M.E.G. <#A9B4C3>和流浪者带来最大的繁荣，\n——哪怕代价是屈服于他们。\n他们会反对任何对<#E08A98> U.E.C. <#A9B4C3>的合作产生阻碍的举动。"
                "power_con" -> "<#5FA8A3>保守派<#A9B4C3>主要由在<#DFBD7C> M.E.G. <#A9B4C3>输掉与\n<#E08A98>U.E.C. <#A9B4C3>的战争后的投降的一些成员组成。\n这些人的麻烦相对来说轻很多，因为他们古板又腐败，\n只要允许他们攫取利益，让金钱源源不断流入他们口袋的话，\n也许该派系大概就会满足现状。"
                else -> "<#EFDF8F>维新派<#A9B4C3>由监督者的手下直接领导，是一个近期成立的派系。\n大体上由充满希望且拥护<#DFBD7C> M.E.G. <#A9B4C3>的年轻职员组成，\n他们正在力图以革新让<#DFBD7C> M.E.G. <#A9B4C3>再次伟大。\n维新派基本上对<#DFBD7C> M.E.G. <#A9B4C3>表示支持。\n然而他们的热情，以及...比较令人担心的...\n难掩的公开与<#E08A98> U.E.C. <#A9B4C3>为敌的态度可能会在日后成为大问题。"
            }
    }
}

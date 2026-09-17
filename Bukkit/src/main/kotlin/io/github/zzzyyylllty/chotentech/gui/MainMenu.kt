package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.ClickType
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import java.time.LocalTime

/**
 * 主菜单 —— 由 draft/menu.conf (菜单插件配置) 重写为 TabooLib Chest 菜单。
 * 不启用虚拟化，直接复用各功能界面菜单类替代原 conf 的控制台指令跳转。
 */
class MainMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return

        player.openMenu<Chest>(buildTitle(player)) {
            rows(6)

            map(
                "AAAAA SSS",
                "AAAAA CCC",
                "DDDEEEFFF",
                "DDDEEEFFF",
                "GGGHHHIII",
                "GGGHHHIII"
            )

            // A: 个人信息面板
            set('A', buildInfoItem(player, data)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                OrganizationMenu().open(clicker)
            }

            // S: 设置
            set('S', buildSettingsItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickEvent().isRightClick) {
                    RankSelectionMenu().open(clicker)
                } else {
                    SettingsMenu().open(clicker)
                }
            }

            // C: 玩家社区(无交互)
            set('C', buildCommunityItem())

            // D: 层级数据库
            set('D', buildDatabaseItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickEvent().isRightClick) {
                    BaseCodexMenu().open(clicker)
                } else {
                    LevelBrowseMenu().open(clicker)
                }
            }

            // E: 阈界商城(测试阶段不开放)
            set('E', buildShopItem())

            // F: 基地服务
            // conf 中右击动作被注释，但 lore 仍提示右击打开投保菜单，按 lore 实现
            set('F', buildBaseServiceItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickType != ClickType.CLICK) return@set
                when (clickEvent().click.name) {
                    "SHIFT_LEFT" -> WarehouseMenu().open(clicker)
                    "RIGHT", "SHIFT_RIGHT" -> InsuranceMenu().open(clicker)
                    else -> healInBase(clicker)
                }
            }

            // G: 任务面板
            set('G', buildQuestItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                AllQuestMenu().open(clicker)
            }

            // H: 全球市场(测试阶段不开放)
            set('H', buildMarketItem())

            // I: 干涉事务面板
            set('I', buildInterfereItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                InterferePanelMenu().open(clicker)
            }
        }
    }

    /** 标题含按时段问候语，对应 conf 的 template 表达式 */
    private fun buildTitle(player: Player) = mmJsonUtil.serialize(
        "<#F2F5FA><shift:-16><image:kangelicon:menu><shift:-176><#EFDF8F><font:minecraft:fusionpixel10>${greeting()}, <#84D1CB>${player.name}".toComponent()
    )

    private fun greeting(): String {
        val h = LocalTime.now().hour
        return when {
            h >= 20 -> "晚上好"
            h >= 18 -> "傍晚好"
            h >= 13 -> "下午好"
            h == 12 -> "中午好"
            h >= 8 -> "上午好"
            h >= 5 -> "早上好"
            else -> "凌晨好"
        }
    }

    /** A: 十年不晚 —— 玩家基本信息(数据直接取自 PlayerData，替代原 PAPI 占位符) */
    private fun buildInfoItem(player: Player, data: PlayerData): ItemStack {
        val item = XMaterial.STRUCTURE_VOID.parseItem() ?: ItemStack(Material.STRUCTURE_VOID)
        val meta = item.itemMeta ?: return item
        NamespacedKey.fromString(MODEL)?.let { meta.itemModel = it }
        meta.displayName("<#84D1CB><b>十年不晚".toComponent())

        val mainLevel = runCatching { PlayerDataManager(player).getLevel("main") }.getOrNull() ?: 0L
        val base = data.subTag?.let { BaseManager.baseMap[it] }

        val lore = mutableListOf<String>()
        lore += "<!i><#84D1CB>阈限天际 v4.1 主题更新"
        lore += loreSpace()
        lore += loreSection("基本信息")
        lore += loreInfo("流浪者名称", "<#EFDF8F>${player.name}")
        lore += loreInfo("流浪者编号", "<#EFDF8F>${data.cid.id}")
        lore += loreInfo("数据库等级", "<#F2F5FA>$mainLevel")
        lore += loreInfo("所在基地名", "<#F2F5FA>${base?.name ?: "没有组织"} <#6E7A8C>${data.mainTag ?: "无组织"}-${data.subTag ?: "无基地"}")
        lore += loreSpace()
        lore += loreSection("钱包")
        lore += loreInfo("积分", "<#FFFF00>${data.money} <#F2F5FA>| <#00EC66>贸易点: <#66FFCC>${data.point}")
        lore += loreSpace()
        lore += loreSection("信誉")
        lore += loreInfo("主要组织", "<#FFFF00>${data.reputation.getOrDefault("MEG", 0L)} <#6E7A8C>/ <#66FFCC>${data.reputation.getOrDefault("BNTG", 0L)} <#6E7A8C>/ <#FF0000>${data.reputation.getOrDefault("UEC", 0L)}")
        lore += loreSpace()
        lore += "<!i> <#EFDF8F>左击 <#8FD4A6>打开超级组织数据板"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildSettingsItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#999999><b>设置",
        listOf(
            "<!i><#A9B4C3>调整游戏设置。",
            loreSpace(),
            "<!i> <#EFDF8F>左击 <#8FD4A6>打开设置面板",
            "<!i> <#EFDF8F>右击 <#8FD4A6>打开称号菜单"
        ),
        model = MODEL
    )

    private fun buildCommunityItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#0000FF><b>玩家社区",
        listOf(
            "<!i><#A9B4C3>加入企鹅交流渠道，",
            "<!i><#A9B4C3>反馈BUG，提出建议，",
            "<!i><#A9B4C3>并可获得官方地图以及礼包码等福利。",
            loreSpace(),
            "<!i> <#AABBCC>外部交流群 <#EFDF8F>108-1307-133",
            "<!i> <#CC8866>天际通知群 <#EFDF8F>108-4550-821"
        ),
        model = MODEL
    )

    private fun buildDatabaseItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#AAAACC><b>层级数据库",
        listOf(
            "<!i><#A9B4C3>在这里，可以查看已探索的层级和基地。",
            loreSpace(),
            "<!i> <#EFDF8F>左击 <#8FD4A6>打开层级数据库",
            "<!i> <#EFDF8F>右击 <#8FD4A6>打开基地数据库"
        ),
        model = MODEL
    )

    private fun buildShopItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#FFAA88><b>阈界商城",
        listOf(
            "<!i><#A9B4C3>在这里，可以购买史上最超值的道具。",
            loreSpace(),
            "<!i> <#E08A98>测试阶段不开放商城。"
        ),
        model = MODEL
    )

    private fun buildBaseServiceItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#FFDD88><b>基地服务",
        listOf(
            "<!i><#A9B4C3>使用基地内的医疗服务，或者保险服务。",
            loreSpace(),
            "<!i> <#EFDF8F>左击 <#8FD4A6>使用医疗服务 <#6E7A8C>(免费)",
            "<!i> <#EFDF8F>SHIFT+左击 <#8FD4A6>打开基地仓库",
            "<!i> <#EFDF8F>右击 <#8FD4A6>打开投保菜单"
        ),
        model = MODEL
    )

    private fun buildQuestItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#66CCDD><b>任务面板",
        listOf(
            "<!i><#A9B4C3>查看进行中的任务。",
            loreSpace(),
            "<!i> <#EFDF8F>点击 <#8FD4A6>打开任务面板"
        ),
        model = MODEL
    )

    private fun buildMarketItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#FFAA88><b>全球市场",
        listOf(
            "<!i><#A9B4C3>购买其他流浪者上架的物品",
            loreSpace(),
            "<!i> <#E08A98>测试阶段不开放全球市场。"
        ),
        model = MODEL
    )

    private fun buildInterfereItem(): ItemStack = guiItem(
        XMaterial.STRUCTURE_VOID,
        "<#66FF33><b>干涉事务",
        listOf(
            "<!i><#A9B4C3>查看进行中的剧情任务。",
            loreSpace(),
            "<!i> <#EFDF8F>点击 <#8FD4A6>打开干涉事务面板"
        ),
        model = MODEL
    )

    /** F 左击:在基地内回满血，野外拒绝(对应 conf 中对 currentbaseminor == 野外 的检查) */
    private fun healInBase(player: Player) {
        val inBase = playerRegions[player.uniqueId.toString()]?.any { it.startsWith("base-") } == true
        if (!inBase) {
            player.sendComponent("<#E08A98>你不在基地内，无法使用这个服务！")
            return
        }
        player.health = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.sendComponent("<#8FD4A6>成功使用了医疗服务。")
    }

    companion object {
        /** 通用隐形 GUI 物品模型(对应 conf 中 structure void + model 10000) */
        private const val MODEL = "kangelitem:void"
    }
}

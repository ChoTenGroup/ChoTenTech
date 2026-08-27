package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.data.QuetiapineDataBridge
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.math.roundToInt

class ProfileInfoGUI(private val target: Player) {

    fun open(player: Player) {
        val data = target.getData() ?: run {
            player.sendMessage("<#E08A98>无法获取 ${target.name} 的数据".toComponent())
            return
        }

        player.openMenu<Chest>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>${target.name} 的档案信息".toComponent())
        ) {
            rows(6)

            map(
                "CCC      ",
                "         ",
                " ####### ",
                " LLLLLLL ",
                " LLLLLLL ",
                "         "
            )

            // Background
            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }
            set('L', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            // === 3 return icons (top-left) ===
            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>菜单", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            // === Info items (row 2, slots 19-25) ===
            set(19, buildCoreStats(data))
            set(20, buildStatus(data))
            set(21, buildCombatAttrs(target))
            set(22, buildElementalDefense(target))
            set(23, buildMainLevel(target))
            set(24, buildStatsPart1(data))
            set(25, buildStatsPart2(data))

            // === Level icons (rows 4-5, L slots) ===
            val levelSlots = listOf(28, 29, 30, 31 ,32,33,34,37, 38, 39, 40, 41, 42, 43)
            var slotIdx = 0
            for ((id, template) in levels) {
                if (slotIdx >= levelSlots.size) break
                set(levelSlots[slotIdx++], buildLevelIcon(target, id, template))
            }

            onClick(lock = true) { event ->
                event.isCancelled = true
            }
        }
    }

    // ==================== Item builders ====================

    private fun buildCoreStats(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.IRON_CHESTPLATE, "<#84D1CB><bold>基础属性", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()
        lore += loreInfo("理智", "<#F2F5FA>${"%.1f".format(data.sanity)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.effectiveMaxSanity)}")
        lore += loreInfo("压力", "<#F2F5FA>${"%.1f".format(data.stress)}")
        lore += loreInfo("阴暗", "<#F2F5FA>${"%.1f".format(data.darkness)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.effectiveMaxDarkness)}")
        lore += loreInfo("体力", "<#F2F5FA>${"%.1f".format(data.stamina)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.maxStamina)}")
        lore += loreInfo("手部体力", "<#F2F5FA>${"%.1f".format(data.handStamina)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.maxHandStamina)}")
        lore += loreInfo("饥饿", "<#F2F5FA>${"%.1f".format(data.customHunger)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.maxCustomHunger)}")
        lore += loreInfo("饱和度", "<#F2F5FA>${"%.1f".format(data.customSaturation)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.maxCustomSaturation)}")
        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildStatus(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.SHIELD, "<#E08A98><bold>状态", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()
        lore += loreInfo("外伤", "<#F2F5FA>${"%.1f".format(data.externalInjury)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.maxExternalInjury)}")
        lore += loreInfo("外伤恢复", "<#F2F5FA>${"%.1f".format(data.externalInjuryRegen)}/tick")
        lore += loreInfo("内伤", "<#F2F5FA>${"%.1f".format(data.internalInjury)} <#A9B4C3>/ <#F2F5FA>${"%.1f".format(data.maxInternalInjury)}")
        lore += loreInfo("内伤恢复", "<#F2F5FA>${"%.1f".format(data.internalInjuryRegen)}/tick")
        lore += loreSpace()
        lore += loreSection("营养")
        lore += loreDash("<#F2F5FA>谷物: <#8FD4A6>${"%.1f".format(data.grain)}")
        lore += loreDash("<#F2F5FA>维生素: <#8FD4A6>${"%.1f".format(data.vitamin)}")
        lore += loreDash("<#F2F5FA>蛋白质: <#8FD4A6>${"%.1f".format(data.protein)}")
        lore += loreDash("<#F2F5FA>脂肪: <#8FD4A6>${"%.1f".format(data.fat)}")
        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildCombatAttrs(player: Player): ItemStack {
        val item = guiItem(XMaterial.DIAMOND_SWORD, "<#E08A98><bold>战斗属性", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()

        fun attr(id: String, name: String, unit: String = "", scale: Double = 1.0) {
            val v = AttributeManager.getAttributeValue(player, id) * scale
            lore += loreInfo(name, "<#F2F5FA>${"%.2f".format(v)}$unit")
        }

        attr("defense", "综合防御")
        attr("crit_chance", "暴击率", "%", 100.0)
        attr("crit_damage", "暴击伤害", "%", 100.0)
        attr("lifesteal", "生命偷取", "%", 100.0)
        attr("dodge_chance", "闪避率", "%", 100.0)
        attr("block_chance", "格挡概率", "%", 100.0)
        attr("block_defense", "格挡防御")
        attr("all_damage_reduction", "全伤害减免", "%", 100.0)
        lore += loreSpace()
        attr("physical_damage", "物理伤害")
        attr("ranged_damage", "远程伤害")
        attr("magic_damage", "魔法伤害")
        attr("fire_attack_chance", "火焰攻击几率", "%", 100.0)
        lore += loreSpace()
        attr("stamina_regen_multiplier", "体力回复倍率", "x")
        attr("health_regen_multiplier", "生命回复倍率", "x")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildElementalDefense(player: Player): ItemStack {
        val item = guiItem(XMaterial.ENCHANTED_BOOK, "<#EFDF8F><bold>元素防御", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()

        fun def(id: String, name: String, icon: String) {
            val v = AttributeManager.getAttributeValue(player, id)
            lore += loreInfo(name, "$icon <#F2F5FA>${"%.2f".format(v)}")
        }

        def("physical_defense", "物理防御", "<#c9c9c9>◆")
        def("burn_defense", "灼烧防御", "<#cf946e>◆")
        def("frost_defense", "霜寒防御", "<#bad9e8>◆")
        def("shock_defense", "电击防御", "<#cfcf6e>◆")
        def("psychic_defense", "精神防御", "<#d9bae8>◆")
        def("erosion_defense", "侵蚀防御", "<#bae8ba>◆")
        def("decay_defense", "衰退防御", "<#a7a7a7>◆")
        lore += loreSpace()
        lore += loreInfo("防御韧性", "<#F2F5FA>${"%.2f".format(AttributeManager.getAttributeValue(player, "defense_toughness"))}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 主等级 — 当前活跃等级 */
    private fun buildMainLevel(player: Player): ItemStack {
        val item = guiItem(XMaterial.EXPERIENCE_BOTTLE, "<#8FD4A6><bold>主等级", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()

        val data = player.getData()
        val activeId = data?.activeLevelStr
        if (activeId != null && levels.containsKey(activeId)) {
            val template = levels[activeId]!!
            val pm = PlayerDataManager(player)
            val level = QuetiapineDataBridge.getLevel(player, activeId) ?: 0
            val exp = QuetiapineDataBridge.getExp(player, activeId)
            val displayName = template.getDefaultName()
            val levelDisplay = template.level.getCurrentLevelDisplay(level, activeId)
            val nextExp = pm.getNextLevelExp(activeId)
            val expStr = if (nextExp != null && nextExp > 0) {
                "${"%.0f".format(exp)}/${"%.0f".format(nextExp)}"
            } else {
                "${"%.0f".format(exp)}"
            }
            lore += loreInfo(displayName, "<#F2F5FA>$levelDisplay")
            lore += loreInfo("经验", "<#F2F5FA>$expStr")
        } else {
            lore += loreInfo("状态", "<#A9B4C3>无活跃等级")
        }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 单个等级图标 — 用于右侧 L 槽位 */
    private fun buildLevelIcon(player: Player, id: String, template: io.github.zzzyyylllty.chotentech.data.LevelTemplate): ItemStack {
        val level = QuetiapineDataBridge.getLevel(player, id) ?: 0
        val exp = QuetiapineDataBridge.getExp(player, id)
        val displayName = template.getDefaultName()
        val levelDisplay = template.level.getCurrentLevelDisplay(level, id)
        val pm = PlayerDataManager(player)
        val nextExp = pm.getNextLevelExp(id)
        val expStr = if (nextExp != null && nextExp > 0) {
            "${"%.0f".format(exp)}/${"%.0f".format(nextExp)}"
        } else {
            "${"%.0f".format(exp)}"
        }

        val lore = listOf(
            loreInfo(displayName, "<#F2F5FA>$levelDisplay"),
            loreInfo("经验", "<#F2F5FA>$expStr")
        )
        val mat = template.gui?.material?.let { matName ->
            try { XMaterial.valueOf(matName) } catch (_: Exception) { null }
        } ?: XMaterial.EXPERIENCE_BOTTLE
        val model = template.gui?.model
        return guiItem(mat, "<#8FD4A6>$displayName", lore, model = model)
    }

    /** 统计信息① — 时间 & 探索 & 战斗统计 */
    private fun buildStatsPart1(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.CLOCK, "<#EFDF8F><bold>统计信息 I", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()

        val shared = ProfileManager.getSharedData(data.uuid)
        val gameTimeStr = GameTimeManager.formatOneLine(data.gameTimeHours)
        val totalSec = data.playTime / 1000
        val phours = totalSec / 3600
        val pmins = (totalSec % 3600) / 60
        val sharedSec = shared.totalPlayTime / 1000
        val sHours = sharedSec / 3600
        val sMins = (sharedSec % 3600) / 60

        lore += loreInfo("游戏时间", "<#F2F5FA>$gameTimeStr")
        lore += loreInfo("游玩时长", "<#F2F5FA>${phours}时 ${pmins}分<#6E7A8C> | 总计 <#F2F5FA>${sHours}时 ${sMins}分")
        lore += loreSpace()
        lore += loreInfo("已探索层级", "<#F2F5FA>${data.exploreredLevels.size}")
        lore += loreInfo("已发现基地", "<#F2F5FA>${data.discoveredBases.size}")
        lore += loreInfo("层级切出", "<#F2F5FA>${data.totalLevelExits}<#6E7A8C> | 总计 <#F2F5FA>${shared.totalLevelExits}")
        lore += loreInfo("死亡次数", "<#F2F5FA>${data.totalDeaths}<#6E7A8C> | 总计 <#F2F5FA>${shared.totalDeaths}")
        lore += loreInfo("击杀实体", "<#F2F5FA>${data.totalMobKills}<#6E7A8C> | 总计 <#F2F5FA>${shared.totalMobKills}")
        lore += loreInfo("击杀玩家", "<#F2F5FA>${data.totalPlayerKills}<#6E7A8C> | 总计 <#F2F5FA>${shared.totalPlayerKills}")
        lore += loreInfo("加入次数", "<#F2F5FA>${shared.joinCount}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 统计信息② — 玩家信息 & 经济 & PVP */
    private fun buildStatsPart2(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.GOLD_NUGGET, "<#EFDF8F><bold>统计信息 II", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()
        lore += loreInfo("名称", "<#F2F5FA>${data.playerName}")
        lore += loreInfo("玩家档案", "<#F2F5FA>${data.profileName} <#A9B4C3>(ID: ${data.profileId})")
        lore += loreInfo("CID", "<#F2F5FA>${data.cid.id}")
        lore += loreSpace()
        lore += loreInfo("积分", "<#F2F5FA>${data.money}")
        lore += loreInfo("贸易点", "<#F2F5FA>${data.point}")
        lore += loreSpace()
        lore += loreInfo("PVP", "${if (data.pvpEnabled) "<#8FD4A6>开启" else "<#E08A98>关闭"}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

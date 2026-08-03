package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.quetiapine.data.QuetiapineDataBridge
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
            player.sendMessage("<red>无法获取 ${target.name} 的数据".toComponent())
            return
        }

        player.openMenu<Chest>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${target.name} 的档案信息".toComponent())
        ) {
            rows(6)

            map(
                "CCC      ",
                "         ",
                " ####### ",
                "         ",
                " LLLLLLL ",
                " LLLLLLL "
            )

            // Background
            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }
            set('L', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            // === 3 return icons (top-left) ===
            set(0, guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<!i> <yellow>点击 <green>打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            set(1, guiItem(XMaterial.BOOK, "<gold><bold>玩家档案选择", listOf("<!i> <yellow>点击 <green>打开档案选择界面"))) {
                clicker.closeInventory()
                ProfileSelectionGUI().open(clicker)
            }

            set(2, guiItem(XMaterial.BARRIER, "<red><bold>关闭", listOf("<!i> <yellow>点击 <red>关闭界面"))) {
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
            val levelSlots = listOf(37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 49, 50, 51, 52)
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
        val item = guiItem(XMaterial.IRON_CHESTPLATE, "<aqua><bold>基础属性", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()
        lore += loreInfo("理智", "<white>${"%.1f".format(data.sanity)} <gray>/ <white>${"%.1f".format(data.effectiveMaxSanity)}")
        lore += loreInfo("压力", "<white>${"%.1f".format(data.stress)}")
        lore += loreInfo("阴暗", "<white>${"%.1f".format(data.darkness)} <gray>/ <white>${"%.1f".format(data.effectiveMaxDarkness)}")
        lore += loreInfo("体力", "<white>${"%.1f".format(data.stamina)} <gray>/ <white>${"%.1f".format(data.maxStamina)}")
        lore += loreInfo("手部体力", "<white>${"%.1f".format(data.handStamina)} <gray>/ <white>${"%.1f".format(data.maxHandStamina)}")
        lore += loreInfo("饥饿", "<white>${"%.1f".format(data.customHunger)} <gray>/ <white>${"%.1f".format(data.maxCustomHunger)}")
        lore += loreInfo("饱和度", "<white>${"%.1f".format(data.customSaturation)} <gray>/ <white>${"%.1f".format(data.maxCustomSaturation)}")
        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildStatus(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.SHIELD, "<red><bold>状态", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()
        lore += loreInfo("外伤", "<white>${"%.1f".format(data.externalInjury)} <gray>/ <white>${"%.1f".format(data.maxExternalInjury)}")
        lore += loreInfo("外伤恢复", "<white>${"%.1f".format(data.externalInjuryRegen)}/tick")
        lore += loreInfo("内伤", "<white>${"%.1f".format(data.internalInjury)} <gray>/ <white>${"%.1f".format(data.maxInternalInjury)}")
        lore += loreInfo("内伤恢复", "<white>${"%.1f".format(data.internalInjuryRegen)}/tick")
        lore += loreSpace()
        lore += loreSection("营养")
        lore += loreDash("<white>谷物: <green>${"%.1f".format(data.grain)}")
        lore += loreDash("<white>维生素: <green>${"%.1f".format(data.vitamin)}")
        lore += loreDash("<white>蛋白质: <green>${"%.1f".format(data.protein)}")
        lore += loreDash("<white>脂肪: <green>${"%.1f".format(data.fat)}")
        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildCombatAttrs(player: Player): ItemStack {
        val item = guiItem(XMaterial.DIAMOND_SWORD, "<red><bold>战斗属性", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()

        fun attr(id: String, name: String, unit: String = "", scale: Double = 1.0) {
            val v = AttributeManager.getAttributeValue(player, id) * scale
            lore += loreInfo(name, "<white>${"%.2f".format(v)}$unit")
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
        val item = guiItem(XMaterial.ENCHANTED_BOOK, "<yellow><bold>元素防御", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()

        fun def(id: String, name: String, icon: String) {
            val v = AttributeManager.getAttributeValue(player, id)
            lore += loreInfo(name, "$icon <white>${"%.2f".format(v)}")
        }

        def("physical_defense", "物理防御", "<#aaaaaa>◆")
        def("burn_defense", "灼烧防御", "<#ff6600>◆")
        def("frost_defense", "霜寒防御", "<#66ccff>◆")
        def("shock_defense", "电击防御", "<#ffff00>◆")
        def("psychic_defense", "精神防御", "<#cc66ff>◆")
        def("erosion_defense", "侵蚀防御", "<#66ff66>◆")
        def("decay_defense", "衰退防御", "<#888888>◆")
        lore += loreSpace()
        lore += loreInfo("防御韧性", "<white>${"%.2f".format(AttributeManager.getAttributeValue(player, "defense_toughness"))}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 主等级 — 当前活跃等级 */
    private fun buildMainLevel(player: Player): ItemStack {
        val item = guiItem(XMaterial.EXPERIENCE_BOTTLE, "<green><bold>主等级", emptyList())
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
            lore += loreInfo(displayName, "<white>$levelDisplay")
            lore += loreInfo("经验", "<white>$expStr")
        } else {
            lore += loreInfo("状态", "<gray>无活跃等级")
        }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 单个等级图标 — 用于右侧 L 槽位 */
    private fun buildLevelIcon(player: Player, id: String, template: io.github.zzzyyylllty.chotentech.quetiapine.data.LevelTemplate): ItemStack {
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
            loreInfo(displayName, "<white>$levelDisplay"),
            loreInfo("经验", "<white>$expStr")
        )
        val mat = template.gui?.material?.let { matName ->
            try { XMaterial.valueOf(matName) } catch (_: Exception) { null }
        } ?: XMaterial.EXPERIENCE_BOTTLE
        val model = template.gui?.model
        return guiItem(mat, "<green>$displayName", lore, model = model)
    }

    /** 统计信息① — 时间 & 探索 & 战斗统计 */
    private fun buildStatsPart1(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.CLOCK, "<yellow><bold>统计信息 I", emptyList())
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

        lore += loreInfo("游戏时间", "<white>$gameTimeStr")
        lore += loreInfo("游玩时长", "<white>${phours}时 ${pmins}分<dark_gray> | 总计 <white>${sHours}时 ${sMins}分")
        lore += loreSpace()
        lore += loreInfo("已探索层级", "<white>${data.exploreredLevels.size}")
        lore += loreInfo("已发现基地", "<white>${data.discoveredBases.size}")
        lore += loreInfo("层级切出", "<white>${data.totalLevelExits}<dark_gray> | 总计 <white>${shared.totalLevelExits}")
        lore += loreInfo("死亡次数", "<white>${data.totalDeaths}<dark_gray> | 总计 <white>${shared.totalDeaths}")
        lore += loreInfo("击杀实体", "<white>${data.totalMobKills}<dark_gray> | 总计 <white>${shared.totalMobKills}")
        lore += loreInfo("击杀玩家", "<white>${data.totalPlayerKills}<dark_gray> | 总计 <white>${shared.totalPlayerKills}")
        lore += loreInfo("加入次数", "<white>${shared.joinCount}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    /** 统计信息② — 玩家信息 & 经济 & PVP */
    private fun buildStatsPart2(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = guiItem(XMaterial.GOLD_NUGGET, "<yellow><bold>统计信息 II", emptyList())
        val meta = item.itemMeta ?: return item
        val lore = mutableListOf<String>()
        lore += loreInfo("名称", "<white>${data.playerName}")
        lore += loreInfo("玩家档案", "<white>${data.profileName} <gray>(ID: ${data.profileId})")
        lore += loreInfo("CID", "<white>${data.cid.id}")
        lore += loreSpace()
        lore += loreInfo("积分", "<white>${data.money}")
        lore += loreInfo("贸易点", "<white>${data.point}")
        lore += loreSpace()
        lore += loreInfo("PVP", "${if (data.pvpEnabled) "<green>开启" else "<red>关闭"}")

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}

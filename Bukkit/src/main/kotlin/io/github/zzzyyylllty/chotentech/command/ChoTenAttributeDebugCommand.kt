package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.attribute.api.ChoTenAttributeAPI
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager.getAttributeDisplayName
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.platform.util.asLangText

@CommandHeader(
    name = "chotenattribute-debug",
    aliases = ["attributedebug", "atbdebug"],
    permission = "chotenattribute.command.debug",
    description = "属性调试指令",
    permissionDefault = PermissionDefault.OP,
    newParser = true
)
object ChoTenAttributeDebugCommand {

    @CommandBody
    val main = mainCommand { createModernHelper() }

    // ==================== 属性分类定义 ====================

    private val CATEGORY_NAMES = mapOf(
        "basic" to "基础属性",
        "combat" to "战斗",
        "defense" to "防御",
        "block" to "格挡",
        "ranged" to "远程",
        "magic" to "魔法",
        "fire" to "火焰",
        "element" to "元素伤害",
        "body" to "身体状态",
        "hunger" to "饥饿系统",
        "regen" to "恢复",
        "other" to "其他"
    )

    private val CATEGORY_IDS: Map<String, Set<String>> by lazy {
        val map = mutableMapOf<String, MutableSet<String>>()
        CATEGORY_NAMES.keys.forEach { map[it] = mutableSetOf() }

        fun add(category: String, vararg ids: String) {
            ids.forEach { map[category]?.add(it) }
        }

        // 基础属性（拥有 vanillaName 的原版属性）
        add("basic",
            "max_health", "max_absorption", "movement_speed", "attack_damage",
            "attack_speed", "armor", "armor_toughness", "attack_knockback",
            "knockback_resistance", "luck", "fall_damage_multiplier", "burning_time",
            "explosion_knockback_resistance", "mining_efficiency", "movement_efficiency",
            "oxygen_bonus", "player_block_break_speed", "player_block_interaction_range",
            "player_entity_interaction_range", "player_submerged_mining_speed",
            "player_mining_speed", "gravity", "safe_fall_distance", "scale",
            "step_height", "spawn_reinforcements"
        )

        // 战斗
        add("combat",
            "crit_chance", "crit_damage", "lifesteal", "dodge_chance", "defense",
            "defense_toughness", "attack_damage"
        )

        // 防御
        add("defense",
            "armor", "armor_toughness", "all_damage_reduction", "physical_damage_reduction"
        )

        // 格挡
        add("block",
            "block_chance", "block_defense", "block_defense_percent"
        )

        // 远程
        add("ranged",
            "ranged_damage", "ranged_extra_damage",
            "ranged_damage_reduction", "bow_extra_damage", "crossbow_extra_damage",
            "bow_damage_reduction", "crossbow_damage_reduction"
        )

        // 魔法
        add("magic",
            "magic_attack_damage", "magic_damage", "magic_extra_damage",
            "magic_damage_reduction"
        )

        // 火焰
        add("fire",
            "fire_attack_damage", "fire_attack_chance", "fire_extra_damage",
            "fire_damage_reduction"
        )

        // 元素伤害类型（7种 × 3属性）
        add("element",
            "physical_defense", "physical_extra_damage", "physical_damage",
            "burn_defense", "burn_extra_damage", "burn_damage",
            "frost_defense", "frost_extra_damage", "frost_damage",
            "shock_defense", "shock_extra_damage", "shock_damage",
            "psychic_defense", "psychic_extra_damage", "psychic_damage",
            "erosion_defense", "erosion_extra_damage", "erosion_damage",
            "decay_defense", "decay_extra_damage", "decay_damage"
        )

        // 身体状态
        add("body",
            "max_sanity", "sanity_regeneration", "max_darkness",
            "toughness_head_max", "toughness_body_max", "toughness_stomach_max",
            "toughness_larm_max", "toughness_rarm_max", "toughness_lleg_max", "toughness_rleg_max", "water_max"
        )

        // 饥饿系统
        add("hunger",
            "max_custom_hunger", "max_custom_saturation"
        )

        // 恢复
        add("regen",
            "stamina_regen_multiplier", "health_regen_multiplier"
        )

        map
    }

    /** 获取属性所属分类 */
    private fun getCategory(attributeId: String): String {
        for ((cat, ids) in CATEGORY_IDS) {
            if (attributeId in ids) return cat
        }
        return "other"
    }

    /** 获取所有可用的分类 key */
    private val allCategoryKeys: List<String> get() = CATEGORY_NAMES.keys.toList()

    // ==================== 指令：按分类展示 ====================

    @CommandBody
    val show = subCommand {
        execute<Player> { player, _, _ ->
            showGrouped(player, player, null)
        }
        dynamic("category") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                allCategoryKeys + listOf("all")
            }
            execute<Player> { player, context, _ ->
                showGrouped(player, player, context["category"])
            }
        }
    }

    @CommandBody
    val showplayer = subCommand {
        player("player") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("player").castSafely<Player>()!!
                showGrouped(sender, target, null)
            }
        }
    }

    // ==================== 指令：列出所有分类 ====================

    @CommandBody
    val categories = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent(sender.asLangText("Command_Debug_Categories_Header"))
            val allAttrs = AttributeManager.attributeRegistry.keys
            allCategoryKeys.forEach { catKey ->
                val catName = CATEGORY_NAMES[catKey] ?: catKey
                val ids = CATEGORY_IDS[catKey] ?: emptySet()
                val count = ids.count { it in allAttrs }
                sender.sendComponent(sender.asLangText("Command_Debug_Categories_Item", catName, "§f/$catKey", count.toString()))
            }
            // "all" 特殊项
            sender.sendComponent(sender.asLangText("Command_Debug_Categories_Item", "全部", "§f/all", allAttrs.size.toString()))
        }
    }

    // ==================== 工具 ====================

    /** 按分类展示属性，category 为 null 或 "all" 时显示全部分类 */
    private fun showGrouped(sender: CommandSender, target: Player, category: String?) {
        val all: Map<String, Double> = try {
            ChoTenAttributeAPI.instance.getAllAttributes(target)
        } catch (_: Exception) {
            emptyMap()
        }
        if (all.isEmpty()) {
            sender.sendComponent(target.asLangText("Command_Debug_Show_EmptyAll"))
            return
        }

        if (category == null || category == "all") {
            // 全部分类
            val grouped = all.entries.groupBy { getCategory(it.key) }.mapValues { entry -> entry.value.associate { it.key to it.value } }
            for (catKey in allCategoryKeys) {
                val entries = grouped[catKey] ?: continue
                val catName = CATEGORY_NAMES[catKey] ?: catKey
                sender.sendComponent(sender.asLangText("Command_Debug_Show_Category_Header", catName, entries.size.toString()))
                entries.toSortedMap().forEach { id, value ->
                    val displayName = getAttributeDisplayName(sender, id)
                    sender.sendComponent("<#A9B4C3>  ${displayName}<#A9B4C3>: <#F2F5FA>$value</#F2F5FA>")
                }
            }
        } else if (category in CATEGORY_NAMES) {
            // 指定分类
            val catName = CATEGORY_NAMES[category] ?: category
            val ids = CATEGORY_IDS[category] ?: emptySet()
            val entries = all.filter { entry -> entry.key in ids }.toMap()
            if (entries.isEmpty()) {
                sender.sendComponent(sender.asLangText("Command_Debug_Show_Empty"))
                return
            }
            sender.sendComponent(sender.asLangText("Command_Debug_Show_Category_Header", catName, entries.size.toString()))
            entries.toSortedMap().forEach { (id, value) ->
                val displayName = getAttributeDisplayName(sender, id)
                sender.sendComponent("<#A9B4C3>  ${displayName}<#A9B4C3>: <#F2F5FA>$value</#F2F5FA>")
            }
        } else {
            sender.sendComponent(sender.asLangText("Command_Debug_Show_Empty"))
        }
    }

    /** 获取玩家所有属性值 */
    private fun getAllAttributes(player: Player): Map<String, Double> {
        return try {
            ChoTenAttributeAPI.instance.getAllAttributes(player)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

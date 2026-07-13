package io.github.zzzyyylllty.chotentech.quetiapine.data.load

import io.github.zzzyyylllty.chotentech.quetiapine.logger.infoS
import io.github.zzzyyylllty.chotentech.quetiapine.logger.warningS
import io.github.zzzyyylllty.chotentech.quetiapine.util.devLog
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.module.nms.getItemTag
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PickaxeLevelManager {

    /** itemId -> mining level */
    private val pickaxeLevels = ConcurrentHashMap<String, Int>()

    fun load() {
        pickaxeLevels.clear()
        val file = File(getDataFolder(), "pickaxe_levels.yml")
        if (!file.exists()) {
            infoS("Created default pickaxe_levels.yml")
            releaseResourceFile("pickaxe_levels.yml")
        }
        val map = multiExtensionLoader(file)
        if (map == null) {
            warningS("Failed to load pickaxe_levels.yml")
            return
        }
        for ((key, value) in map) {
            val level = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            if (level != null && level > 0) {
                pickaxeLevels[key] = level
                devLog("Pickaxe level: $key = $level")
            } else {
                warningS("Invalid pickaxe level for $key: $value")
            }
        }
    }

    /** Get the mining level of a player's held item */
    fun getHeldItemLevel(player: Player): Int {
        val item = player.inventory.itemInMainHand
        if (item.type.isAir) return 0
        return getItemLevel(item)
    }

    /** Infer mining level from item/material name tier */
    private fun inferTier(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.contains("netherite") || lower.contains("mythril") || lower.contains("ultra_alloy") -> 4
            lower.contains("diamond") || lower.contains("ruby") || lower.contains("emerald") || lower.contains("black_diamond") -> 3
            lower.contains("iron") || lower.contains("steel") || lower.contains("sapphire") -> 2
            lower.contains("stone") || lower.contains("gold") || lower.contains("wood") || lower.contains("flint") || lower.contains("bronze") || lower.contains("basic_alloy") || lower.contains("copper") -> 1
            else -> 0
        }
    }

    /** Get the mining level of a specific ItemStack */
    fun getItemLevel(item: ItemStack): Int {
        val sertralineId = item.getItemTag()["sertraline_id"]?.asString()
        if (sertralineId != null) {
            // Exact match first
            pickaxeLevels[sertralineId]?.let { return it }
            // Longest suffix match
            val bestSuffix = pickaxeLevels.entries
                .filter { sertralineId.endsWith(it.key) }
                .maxByOrNull { it.key.length }
            if (bestSuffix != null) return bestSuffix.value
            // Infer from sertraline_id prefix (e.g. "iron_mattock" -> iron -> 2)
            inferTier(sertralineId).let { if (it > 0) return it }
        }
        // Fallback for vanilla tools (no sertraline_id) — check by material name
        return inferTier(item.type.name)
    }

    /** Get the display name of a pickaxe level for messages */
    fun getLevelDisplay(level: Int): String {
        return when {
            level <= 1 -> "<gray>等级 ${level}"
            level == 2 -> "<green>等级 ${level}"
            level == 3 -> "<aqua>等级 ${level}"
            level == 4 -> "<#FFAA00>等级 ${level}"
            level >= 5 -> "<red>等级 ${level}"
            else -> "等级 $level"
        }
    }
}

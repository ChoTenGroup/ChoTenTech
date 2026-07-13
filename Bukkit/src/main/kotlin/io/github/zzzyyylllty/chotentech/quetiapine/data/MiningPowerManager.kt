package io.github.zzzyyylllty.chotentech.quetiapine.data

import io.github.zzzyyylllty.chotentech.quetiapine.data.load.multiExtensionLoader
import io.github.zzzyyylllty.chotentech.quetiapine.logger.infoS
import io.github.zzzyyylllty.chotentech.quetiapine.logger.warningS
import io.github.zzzyyylllty.chotentech.quetiapine.util.devLog
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.module.nms.getItemTag
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MiningPowerManager {

    /** suffix pattern -> category -> power */
    private val toolPowers = ConcurrentHashMap<String, Map<BlockCategory, Double>>()

    /** block location -> accumulated damage */
    val blockDamage = ConcurrentHashMap<Location, Double>()

    private const val DEFAULT_POWER = 1.0

    fun load() {
        toolPowers.clear()
        blockDamage.clear()
        val file = File(getDataFolder(), "mining_tools.yml")
        if (!file.exists()) {
            infoS("Created default mining_tools.yml")
            releaseResourceFile("mining_tools.yml")
        }
        val map = multiExtensionLoader(file)
        if (map == null) {
            warningS("Failed to load mining_tools.yml")
            return
        }

        for ((pattern, value) in map) {
            if (value !is Map<*, *>) {
                devLog("Skipping non-map entry: $pattern")
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val catMap = value as Map<String, Any?>
            val parsed = mutableMapOf<BlockCategory, Double>()
            for ((catStr, powerVal) in catMap) {
                val cat = BlockCategory.fromString(catStr)
                if (cat == null) {
                    warningS("Unknown block category '$catStr' in mining_tools.yml for '$pattern'")
                    continue
                }
                val power = when (powerVal) {
                    is Number -> powerVal.toDouble()
                    is String -> powerVal.toDoubleOrNull()
                    else -> null
                }
                if (power != null && power > 0) {
                    parsed[cat] = power
                } else {
                    warningS("Invalid power value for $pattern/$catStr: $powerVal")
                }
            }
            if (parsed.isNotEmpty()) {
                toolPowers[pattern] = parsed
                devLog("Tool power: $pattern -> $parsed")
            }
        }
    }

    /** 获取玩家手持工具对指定方块类型的挖掘伤害 */
    fun getToolDamage(player: Player, category: BlockCategory): Double {
        val item = player.inventory.itemInMainHand
        if (item.type.isAir) return DEFAULT_POWER
        return getItemDamage(item, category)
    }

    /** 获取物品对指定方块类型的挖掘伤害 */
    fun getItemDamage(item: ItemStack, category: BlockCategory): Double {
        // 优先读取 sertraline_id 进行匹配
        val sertralineId = item.getItemTag()["sertraline_id"]?.asString()
        if (sertralineId != null) {
            toolPowers[sertralineId]?.let { return it[category] ?: DEFAULT_POWER }
            val bestSuffix = toolPowers.entries
                .filter { sertralineId.endsWith(it.key) }
                .maxByOrNull { it.key.length }
            if (bestSuffix != null) {
                return bestSuffix.value[category] ?: DEFAULT_POWER
            }
        }

        // 回退：原版 material 名匹配
        val name = item.type.name
        for ((pattern, powers) in toolPowers) {
            if (name.equals(pattern, ignoreCase = true)) {
                return powers[category] ?: DEFAULT_POWER
            }
        }
        val bestSuffix = toolPowers.entries
            .filter { name.endsWith(it.key, ignoreCase = true) }
            .maxByOrNull { it.key.length }
        if (bestSuffix != null) {
            return bestSuffix.value[category] ?: DEFAULT_POWER
        }

        return DEFAULT_POWER
    }

    /** 应用挖掘伤害，返回累计总伤害 */
    fun applyDamage(location: Location, damage: Double): Double {
        val total = blockDamage.merge(location, damage, Double::plus)!!
        if (total.isInfinite() || total.isNaN()) {
            blockDamage[location] = 0.0
            return 0.0
        }
        return total
    }

    /** 获取挖掘进度 0.0 ~ 1.0 */
    fun getProgress(location: Location, strength: Double): Float {
        if (strength <= 0) return 1f
        val dmg = blockDamage[location] ?: 0.0
        return (dmg / strength).coerceIn(0.0, 1.0).toFloat()
    }

    /** 清除方块挖掘进度 */
    fun resetDamage(location: Location) {
        blockDamage.remove(location)
    }
}

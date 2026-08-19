package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import kotlin.math.floor
import org.bukkit.entity.Player
import java.util.UUID

/**
 * 营养系统管理器
 *
 * 四种营养值：grain / vitamin / protein / fat
 * 每 10 秒衰减 0.1，最大 100。
 * 死亡扣除：fat 不参与；其他 <10 补到 10，≥10 损失 (value - 10) / 2。
 *
 * 营养值 → 属性修饰符映射（均通过 AttributeManager.addModifier 实现）：
 *
 *   grain > 50 → +(grain-50)*0.1% attack_damage (PERCENT)
 *   grain < 10 → -25% attack_damage              (PERCENT)
 *   vitamin > 50 → +(vitamin-50) stamina_max       (ADD)
 *   vitamin < 10 → -25% stamina_regen_multiplier   (PERCENT)
 *   protein > 50 → +(protein-50) max_health        (ADD)
 *   protein < 10 → -25% health_regen_multiplier    (PERCENT)
 *   fat > 50 → -(fat-50)*0.2% movement_speed       (PERCENT)
 */
object NutritionManager {

    // 固定 UUID 命名空间，用于清除旧修饰符
    private val UUID_GRAIN_ABOVE = UUID.nameUUIDFromBytes("nutrition:grain:above50".toByteArray())
    private val UUID_GRAIN_BELOW = UUID.nameUUIDFromBytes("nutrition:grain:below10".toByteArray())
    private val UUID_VITAMIN_ABOVE = UUID.nameUUIDFromBytes("nutrition:vitamin:above50".toByteArray())
    private val UUID_VITAMIN_BELOW = UUID.nameUUIDFromBytes("nutrition:vitamin:below10".toByteArray())
    private val UUID_PROTEIN_ABOVE = UUID.nameUUIDFromBytes("nutrition:protein:above50".toByteArray())
    private val UUID_PROTEIN_BELOW = UUID.nameUUIDFromBytes("nutrition:protein:below10".toByteArray())
    private val UUID_FAT_ABOVE = UUID.nameUUIDFromBytes("nutrition:fat:above50".toByteArray())

    private val allNutritionUuids = setOf(
        UUID_GRAIN_ABOVE, UUID_GRAIN_BELOW,
        UUID_VITAMIN_ABOVE, UUID_VITAMIN_BELOW,
        UUID_PROTEIN_ABOVE, UUID_PROTEIN_BELOW,
        UUID_FAT_ABOVE
    )

    /**
     * 清除所有营养修饰符并重新计算
     *
     * 性能优化：直接操作 modifiers 列表，只做一次 DB 写入 + 一次属性重算
     */
    fun sync(player: Player) {
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return

        // 1. 读取持久化数据，直接操作 modifiers 列表
        val attrData = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = attrData.modifiers.toMutableList()

        // 2. 移除旧的营养修饰符
        mods.removeAll { it.uuid in allNutritionUuids }

        // 3. 计算并添加新的
        // —— grain ——
        if (data.grain > 50) {
            val pts = data.grain - 50
            mods += AttributeModifier(
                uuid = UUID_GRAIN_ABOVE,
                attributeId = "attack_damage",
                value = pts * 0.001,   // +0.1% per point → (1.0 + percent)
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        } else if (data.grain < 10) {
            mods += AttributeModifier(
                uuid = UUID_GRAIN_BELOW,
                attributeId = "attack_damage",
                value = -0.25,         // -25% → (1.0 + (-0.25)) = 0.75
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        }

        // —— vitamin ——
        if (data.vitamin > 50) {
            val pts = data.vitamin - 50
            mods += AttributeModifier(
                uuid = UUID_VITAMIN_ABOVE,
                attributeId = "stamina_max",
                value = pts,           // +1 per point (ADD)
                type = ModifierType.ADD,
                source = ModifierSource.OTHER
            )
        } else if (data.vitamin < 10) {
            mods += AttributeModifier(
                uuid = UUID_VITAMIN_BELOW,
                attributeId = "stamina_regen_multiplier",
                value = -0.25,         // -25% → (1.0 + (-0.25)) = 0.75
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        }

        // —— protein ——
        if (data.protein > 50) {
            val pts = data.protein - 50
            mods += AttributeModifier(
                uuid = UUID_PROTEIN_ABOVE,
                attributeId = "max_health",
                value = floor(pts * 0.1),     // +0.1 per point, 取整避免频繁改最大血量 (ADD)
                type = ModifierType.ADD,
                source = ModifierSource.OTHER
            )
        } else if (data.protein < 10) {
            mods += AttributeModifier(
                uuid = UUID_PROTEIN_BELOW,
                attributeId = "health_regen_multiplier",
                value = -0.25,         // -25% → (1.0 + (-0.25)) = 0.75
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        }

        // —— fat ——
        if (data.fat > 50) {
            val pts = data.fat - 50
            mods += AttributeModifier(
                uuid = UUID_FAT_ABOVE,
                attributeId = "movement_speed",
                value = pts * -0.002,  // -0.2% per point → (1.0 + (-0.002*pts))
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        }

        // 4. 一次性写入并重算:营养值未变化时修饰符列表相等,跳过 DB 写(每 10 秒×N 玩家的周期同步不再频繁连接)
        if (mods != attrData.modifiers) {
            attrData.modifiers = mods
            AttributeManager.savePlayerData(attrData)
        }
        AttributeManager.updatePlayer(player)
    }

    /**
     * 死亡扣除：fat 不参与
     *   < 20 → 补到 20
     *   ≥ 20 → 损失 (value - 20) / 2
     */
    fun applyDeathDeduction(player: Player) {
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return
        data.grain = deathDeduct(data.grain)
        data.vitamin = deathDeduct(data.vitamin)
        data.protein = deathDeduct(data.protein)
        // fat 不参与
        sync(player)
    }

    private fun deathDeduct(value: Double): Double {
        return if (value < 20) 20.0 else 20.0 + (value - 20.0) / 2.0
    }
}

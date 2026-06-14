package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageCategory
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeHandler
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.LivingEntity

/**
 * 普通伤害（NORMAL）基类
 * 计算逻辑：通用防御 + 对应属性防御
 *   damage *= (1 - defense/(defense+denominator))
 *   damage *= (1 - specific/(specific+specificDenominator))
 */
abstract class NormalDamageHandler(override val type: DamageType) : DamageTypeHandler {

    init {
        require(type.category == DamageCategory.NORMAL) { "NormalDamageHandler requires NORMAL category" }
    }

    override fun compute(baseDamage: Double, victim: LivingEntity): Double {
        var d = baseDamage.coerceAtLeast(0.0)
        if (d <= 0) return 0.0

        val rdt = DamageTypeRegistry.get(type.id) ?: return d
        if (!rdt.enabled) return d

        val denominator = DamageTypeRegistry.defenseDenominator
        val specificDenominator = DamageTypeRegistry.specificDenominator

        // 通用防御
        val generalDefense = AttributeManager.getAttributeValue(victim, "defense")
        d *= 1.0 - if (generalDefense > 0) generalDefense / (generalDefense + denominator) else 0.0

        // 属性防御
        val specificDefense = AttributeManager.getAttributeValue(victim, rdt.defenseAttribute)
        d *= 1.0 - if (specificDefense > 0) specificDefense / (specificDefense + specificDenominator) else 0.0

        // 全伤害减免
        val allReduction = AttributeManager.getAttributeValue(victim, "all_damage_reduction")
        if (allReduction > 0) d *= 1.0 - allReduction.coerceAtMost(1.0)

        return d.coerceAtLeast(0.0)
    }
}

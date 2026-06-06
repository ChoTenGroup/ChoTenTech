package io.github.zzzyyylllty.chotentech.attribute.damage.handlers

import io.github.zzzyyylllty.chotentech.attribute.damage.DamageCategory
import io.github.zzzyyylllty.chotentech.attribute.damage.DamageType
import io.github.zzzyyylllty.chotentech.attribute.damage.DamageTypeHandler
import io.github.zzzyyylllty.chotentech.attribute.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.attribute.manager.AttributeManager
import org.bukkit.entity.LivingEntity

/**
 * 特殊伤害（SPECIAL）基类
 * 计算逻辑：仅对应属性防御
 *   damage *= (1 - specific/(specific+specificDenominator))
 */
abstract class SpecialDamageHandler(override val type: DamageType) : DamageTypeHandler {

    init {
        require(type.category == DamageCategory.SPECIAL) { "SpecialDamageHandler requires SPECIAL category" }
    }

    override fun compute(baseDamage: Double, victim: LivingEntity): Double {
        var d = baseDamage.coerceAtLeast(0.0)
        if (d <= 0) return 0.0

        val rdt = DamageTypeRegistry.get(type.id) ?: return d
        if (!rdt.enabled) return d

        val specificDenominator = DamageTypeRegistry.specificDenominator

        // 属性防御
        val specificDefense = AttributeManager.getAttributeValue(victim, rdt.defenseAttribute)
        d *= 1.0 - if (specificDefense > 0) specificDefense / (specificDefense + specificDenominator) else 0.0

        // 全伤害减免
        val allReduction = AttributeManager.getAttributeValue(victim, "all_damage_reduction")
        if (allReduction > 0) d *= 1.0 - allReduction.coerceAtMost(1.0)

        return d.coerceAtLeast(0.0)
    }
}

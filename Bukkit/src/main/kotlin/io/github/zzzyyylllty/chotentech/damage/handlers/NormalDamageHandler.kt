package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.damage.DamageCategory
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeHandler
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * 普通伤害（NORMAL）基类
 * 计算逻辑：防御因子 × 易伤系数 → 属性防御 → 全伤害减免
 *   防御因子 = d / (d + 2*def + tough*d/20)
 *   易伤系数 = (extRatio*intRatio)*0.5 + (1 - tough/(tough+20))*0.5 + 0.5
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

        val specificDenominator = DamageTypeRegistry.specificDenominator
        val defense = AttributeManager.getAttributeValue(victim, "defense").coerceAtLeast(0.0)
        val toughness = AttributeManager.getAttributeValue(victim, "defense_toughness").coerceAtLeast(0.0)

        // 防御因子 = 伤害 / (伤害 + 4*防御 + 韧性*伤害/20)
        val defenseFactor = baseDamage / (baseDamage + 4.0 * defense + toughness * baseDamage / 20.0)

        // 伤势易伤系数
        val externalInjuryRatio: Double
        val internalInjuryRatio: Double
        if (victim is Player) {
            val data = ChoTenTech.playerDataMap[victim.uniqueId.toString()]
            if (data != null) {
                externalInjuryRatio = (1.0 - data.externalInjury / data.maxExternalInjury).coerceIn(0.0, 1.0)
                internalInjuryRatio = (1.0 - data.internalInjury / data.maxInternalInjury).coerceIn(0.0, 1.0)
            } else {
                externalInjuryRatio = 0.0
                internalInjuryRatio = 0.0
            }
        } else {
            externalInjuryRatio = 0.0
            internalInjuryRatio = 0.0
        }
        val A = (externalInjuryRatio * internalInjuryRatio) * 0.5
        val B = (1.0 - toughness / (toughness + 20.0)) * 0.5
        val vulnerability = A + B + 0.5

        d = baseDamage * defenseFactor * vulnerability

        // 属性防御
        val specificDefense = AttributeManager.getAttributeValue(victim, rdt.defenseAttribute)
        d *= 1.0 - if (specificDefense > 0) specificDefense / (specificDefense + specificDenominator) else 0.0

        // 全伤害减免
        val allReduction = AttributeManager.getAttributeValue(victim, "all_damage_reduction")
        if (allReduction > 0) d *= 1.0 - allReduction.coerceAtMost(1.0)

        return d.coerceAtLeast(0.0)
    }
}

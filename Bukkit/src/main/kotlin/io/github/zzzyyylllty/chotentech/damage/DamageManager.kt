package io.github.zzzyyylllty.chotentech.damage

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * 伤害系统门面
 * 所有方法委托给 DamageTypeRegistry 和对应的处理器
 */
object DamageManager {

    /**
     * 计算防御减免后的伤害
     */
    fun calculateDamage(baseDamage: Double, type: DamageType, victim: LivingEntity): Double {
        return DamageTypeRegistry.computeDamage(baseDamage, type, victim)
    }

    /**
     * 应用伤害类型效果
     */
    fun applyEffects(victim: Player, type: DamageType, finalDamage: Double, baseDamage: Double) {
        DamageTypeRegistry.applyEffects(victim, type, finalDamage, baseDamage)
    }
}

package io.github.zzzyyylllty.chotentech.attribute.damage.handlers

import io.github.zzzyyylllty.chotentech.attribute.damage.DamageType
import io.github.zzzyyylllty.chotentech.attribute.damage.DamageTypeRegistry
import org.bukkit.entity.Player
import kotlin.math.max

/**
 * 衰退伤害
 * 效果：百分比扣血
 *   扣血 = player.health * (finalDamage / maxHealth)，上限 decayMaxPercent * 当前血量
 *   例：80/200 血，受到 30 → 扣 30% → 56/200
 */
class DecayHandler : SpecialDamageHandler(DamageType.DECAY) {

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (finalDamage <= 0) return
        val maxHealth = player.maxHealth
        val percent = (finalDamage / maxHealth).coerceAtMost(DamageTypeRegistry.decayMaxPercent)
        val healthLoss = max(player.health * percent, 1.0)
        player.damage(healthLoss)
    }
}

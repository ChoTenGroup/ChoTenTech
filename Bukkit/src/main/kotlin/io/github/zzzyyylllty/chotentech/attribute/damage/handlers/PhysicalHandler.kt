package io.github.zzzyyylllty.chotentech.attribute.damage.handlers

import io.github.zzzyyylllty.chotentech.attribute.damage.DamageType
import org.bukkit.entity.Player

/**
 * 物理伤害
 * 纯伤害，无附加效果
 */
class PhysicalHandler : NormalDamageHandler(DamageType.PHYSICAL) {
    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {}
}

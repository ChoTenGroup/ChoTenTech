package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PAIN_RELIEF
import org.bukkit.entity.Player

/**
 * 侵蚀伤害
 * 效果：
 *   扣血 = finalDamage
 *   扣精神 = finalDamage * (sanityConversionRatio / 100)
 */
class ErosionHandler : SpecialDamageHandler(DamageType.EROSION) {

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (finalDamage <= 0) return
        val helper = ChoTenDataHelper(player)

        // 止痛效果：减少精神损失
        val painReliefLevel = helper.getStatusEffectLevel(PAIN_RELIEF)
        val sanityReduction = (painReliefLevel * 0.4).coerceIn(0.0, 1.0)
        val sanityLoss = finalDamage * (DamageTypeRegistry.sanityConversionRatio / 100.0)
        helper.removeSanity(sanityLoss * (1.0 - sanityReduction))
        player.damage(finalDamage)
    }
}

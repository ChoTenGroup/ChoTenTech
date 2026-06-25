package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PAIN_RELIEF
import org.bukkit.entity.Player

/**
 * 精神伤害
 * 效果：
 *   扣血 = finalDamage * (100% - sanity%)
 *   扣精神 = finalDamage * (sanityConversionRatio / 100)
 */
class PsychicHandler : SpecialDamageHandler(DamageType.PSYCHIC) {

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (finalDamage <= 0) return
        val helper = ChoTenDataHelper(player)
        val sanity = helper.data.sanity
        val maxSanity = helper.data.effectiveMaxSanity

        val sanityRatio = if (maxSanity > 0) (sanity / maxSanity).coerceIn(0.0, 1.0) else 0.0
        val healthDamage = finalDamage * (1.0 - sanityRatio)
        if (healthDamage > 0) player.damage(healthDamage)

        val sanityLoss = finalDamage * (DamageTypeRegistry.sanityConversionRatio / 100.0)

        // 止痛效果：减少精神损失
        val painReliefLevel = helper.getStatusEffectLevel(PAIN_RELIEF)
        val sanityReduction = (painReliefLevel * 0.4).coerceIn(0.0, 1.0)
        helper.removeSanity(sanityLoss * (1.0 - sanityReduction))
    }
}

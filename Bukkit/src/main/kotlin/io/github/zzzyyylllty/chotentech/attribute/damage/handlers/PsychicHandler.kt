package io.github.zzzyyylllty.chotentech.attribute.damage.handlers

import io.github.zzzyyylllty.chotentech.attribute.damage.DamageType
import io.github.zzzyyylllty.chotentech.attribute.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
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
        helper.removeSanity(sanityLoss)
    }
}

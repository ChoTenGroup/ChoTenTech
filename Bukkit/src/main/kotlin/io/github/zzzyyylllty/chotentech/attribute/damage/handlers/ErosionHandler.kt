package io.github.zzzyyylllty.chotentech.attribute.damage.handlers

import io.github.zzzyyylllty.chotentech.attribute.damage.DamageType
import io.github.zzzyyylllty.chotentech.attribute.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
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
        helper.removeSanity(finalDamage * (DamageTypeRegistry.sanityConversionRatio / 100.0))
        player.damage(finalDamage)
    }
}

package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import net.kyori.adventure.audience.Audience
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

/**
 * 电击伤害
 * 效果：概率禁锢（缓慢 7 级）
 *   概率 = shockBaseChance + baseDamage * shockDamageChanceRatio
 *   时长 = [shockMinDuration, shockMaxDuration] 随伤害递增
 */
class ShockHandler : NormalDamageHandler(DamageType.SHOCK) {

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (baseDamage <= 0) return

        val baseChance = DamageTypeRegistry.shockBaseChance
        val damageRatio = DamageTypeRegistry.shockDamageChanceRatio
        val minDur = DamageTypeRegistry.shockMinDuration
        val maxDur = DamageTypeRegistry.shockMaxDuration

        val chance = baseChance + baseDamage * damageRatio
        if (Random.nextDouble() < chance) {
            val duration = (minDur + (finalDamage / 10).toInt()).coerceIn(minDur, maxDur)
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration * 20, 6))
            try {
                (player as Audience).sendActionBar(
                    mmUtil.deserialize("<yellow>⚡ 你被电击禁锢了！".legacyToMiniMessage())
                )
            } catch (_: Exception) {}
        }
    }
}

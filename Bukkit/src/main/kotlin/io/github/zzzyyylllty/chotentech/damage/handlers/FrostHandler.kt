package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * 霜寒伤害
 * 效果：累计伤害窗口，根据阈值给予缓慢+虚弱
 */
class FrostHandler : NormalDamageHandler(DamageType.FROST) {

    private val frostAccumulation = mutableMapOf<String, Pair<Double, Long>>()

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (baseDamage <= 0) return

        val uuid = player.uniqueId.toString()
        val now = System.currentTimeMillis()
        val windowMs = DamageTypeRegistry.frostWindowTicks * 50

        val (accumulated, timestamp) = frostAccumulation[uuid] ?: (0.0 to now)
        val newAccumulated = if (now - timestamp > windowMs) baseDamage else accumulated + baseDamage
        frostAccumulation[uuid] = newAccumulated to now

        val (medium, high, extreme) = DamageTypeRegistry.frostThresholds
        val baseDuration = 60

        when {
            newAccumulated >= extreme -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, baseDuration * 3, 3))
                player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, baseDuration * 3, 2))
                frostAccumulation[uuid] = 0.0 to now
            }
            newAccumulated >= high -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, baseDuration * 2, 2))
                player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, baseDuration * 2, 1))
                frostAccumulation[uuid] = 0.0 to now
            }
            newAccumulated >= medium -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, baseDuration, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, baseDuration, 0))
                frostAccumulation[uuid] = 0.0 to now
            }
        }
    }

    fun reset(player: Player) {
        frostAccumulation.remove(player.uniqueId.toString())
    }
}

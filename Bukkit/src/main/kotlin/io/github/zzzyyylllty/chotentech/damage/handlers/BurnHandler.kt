package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit

/**
 * 灼烧伤害
 * 效果：持续 DoT（每秒 finalDamage/4，持续 4 秒）
 */
class BurnHandler : NormalDamageHandler(DamageType.BURN) {

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (finalDamage <= 0) return
        val dotPerTick = finalDamage / 4.0
        var ticks = 0
        val task = submit(async = false, period = 20L, delay = 20L) {
            if (!player.isOnline || ticks >= 4) return@submit
            player.damage(dotPerTick)
            ticks++
        }
        submit(async = false, delay = 80L) { task.cancel() }
    }
}

package io.github.zzzyyylllty.chotentech.damage.handlers

import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap

/**
 * 灼烧伤害
 * 效果：持续 DoT（每秒 finalDamage/4，持续 4 秒）
 */
class BurnHandler : NormalDamageHandler(DamageType.BURN) {

    private val activeTasks = ConcurrentHashMap<String, PlatformExecutor.PlatformTask>()

    override fun apply(player: Player, finalDamage: Double, baseDamage: Double) {
        if (finalDamage <= 0) return

        // Cancel any existing DoT for this player
        activeTasks.remove(player.uniqueId.toString())?.cancel()

        val dotPerTick = finalDamage / 4.0
        var ticks = 0
        val task = submit(async = false, period = 20L, delay = 20L) {
            if (!player.isOnline || player.isDead || ticks >= 4) {
                activeTasks.remove(player.uniqueId.toString())
                return@submit
            }
            player.damage(dotPerTick)
            ticks++
        }
        activeTasks[player.uniqueId.toString()] = task
    }

    /** Cancel all active DoT tasks (called on shutdown) */
    fun cancelAll() {
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
    }

    /** Cancel DoT for a specific player (called on quit) */
    fun cancel(player: Player) {
        activeTasks.remove(player.uniqueId.toString())?.cancel()
    }
}

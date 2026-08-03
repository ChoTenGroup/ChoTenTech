package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 平滑血量动画管理器
 *
 * 维护一个"显示血量"，每 tick 以指数平滑方式向玩家真实血量靠拢。
 * 速度由玩家设置 hpAnimationSpeed 控制：
 *   instant — 立即同步（因子 1.0）
 *   fast   — ~1s 追平（因子 0.14）
 *   normal — ~2s 追平（因子 0.12，默认）
 *   slow   — ~3s 追平（因子 0.075）
 */
object HealthAnimationManager {

    private var tickTask: PlatformExecutor.PlatformTask? = null

    /** 玩家 UUID -> 当前显示血量 */
    private val displayHealth = ConcurrentHashMap<UUID, Double>()

    /** 获取玩家的显示血量（动画值） */
    fun getDisplayHealth(player: Player): Double {
        return displayHealth.getOrDefault(player.uniqueId, player.health)
    }

    /** 立即将某玩家的显示血量同步到真实值（如重生时） */
    fun sync(player: Player) {
        displayHealth[player.uniqueId] = player.health
    }

    /** 根据玩家设置获取每 tick 插值因子 */
    private fun getLerpFactor(player: Player): Double {
        val setting = ProfileManager.getSharedData(player.uniqueId.toString()).settings.get("hpAnimationSpeed")
        return when (setting) {
            "instant" -> 1.0
            "fast"    -> 0.14
            "slow"    -> 0.075
            else      -> 0.12 // normal / fallback
        }
    }

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val target = player.health
            // Initialize entry on first sight, otherwise getOrDefault would
            // always return the real health and the animation would never start
            displayHealth.putIfAbsent(uuid, target)
            val current = displayHealth[uuid] ?: target
            val gap = target - current
            // Snap to real health when close, avoids the exponential smoothing
            // lingering near the target (sticky feel)
            if (kotlin.math.abs(gap) <= 0.5) {
                displayHealth[uuid] = target
                continue
            }
            val factor = getLerpFactor(player)
            if (factor >= 1.0) {
                displayHealth[uuid] = target
            } else {
                displayHealth[uuid] = current + gap * factor
            }
        }
    }

    @Awake(LifeCycle.ACTIVE)
    fun startTask() {
        tickTask = submitAsync(period = 1L) { tick() }
        devLog("[HealthAnimation] 血量动画任务已启动（每 tick 更新）")
    }

    @Awake(LifeCycle.DISABLE)
    fun stopTask() {
        tickTask?.cancel()
        tickTask = null
        displayHealth.clear()
    }
}

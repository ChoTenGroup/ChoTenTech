package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.feature.gun.GunSettings
import io.github.zzzyyylllty.chotentech.feature.gun.GunStateManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 手部体力（`PlayerData.handStamina`）的**唯一消耗/恢复入口**。
 *
 * ### 为什么单独一层
 * 手部体力以前散落在三处：出枪扣 `draw-cost`（[io.github.zzzyyylllty.chotentech.feature.gun.GunAim]）、
 * 瞄准每 tick 扣、开火扣 `shot-cost`，而恢复在 `listener/onFoodTimer.kt` 里是**无条件每 tick +1.0**
 * （= 20 点/秒），于是扣和回各写各的、谁也管不了谁：
 * 一次换弹读条扣掉的体力，下一 tick 就被自然恢复补回来，消耗完全没存在感。
 *
 * 现在统一成一条规则：
 * - **消耗**一律走 [spend]，同时记下「最后一次消耗的时间」；
 * - **恢复**由 [tickRegen] 每 tick 调一次：距最后一次消耗满 `stamina.regen-delay-seconds`
 *   （默认 5 秒）之后，每秒回复「上限的 `stamina.regen-percent-per-second`」（默认 5%），
 *   再乘上属性 `hand_stamina_regen_multiplier`。
 *
 * ### 属性（`AttributeManager` 内置，可被物品 / Buff / 命令改）
 * | 属性 id | 作用 |
 * |---|---|
 * | `hand_stamina_max` | 手部体力**上限**（[PlayerData.maxHandStamina] 读它） |
 * | `hand_stamina_regen_multiplier` | 回复速度倍率（1.0 = 100%，0 = 不回复） |
 *
 * 例：上限 100、倍率 1.0 → 5 秒后每秒回 5 点；把倍率改成 2.0 就是每秒 10 点。
 */
object HandStamina {

    /** 最后一次消耗的墙钟时间（毫秒）；没有消耗记录就不在表里。 */
    private val lastSpendMs = ConcurrentHashMap<UUID, Long>()

    /** 上一次结算恢复的时间（毫秒）：恢复按**真实经过时间**算，不依赖调用频率。 */
    private val lastRegenMs = ConcurrentHashMap<UUID, Long>()

    // ==================== 无限体力区 ====================

    /**
     * 这个玩家当前是否处于「**手部体力无限**」的状态。
     *
     * 以下三种情况手部体力直接维持满值、不消耗：
     * - **基地区域**（WorldGuard 的 `base-` 前缀区域）：基地是完整安全屋；
     * - **安全世界**（`config.yml` 的 `safe-worlds`）；
     * - **创造 / 观察者模式**：调试和建设时不该被体力卡住
     *   （原来的体力 tick 会跳过创造/观察者，导致这两个模式下只扣不回，一进基地也补不满）。
     */
    fun isUnlimited(player: Player): Boolean {
        return try {
            if (player.gameMode == org.bukkit.GameMode.CREATIVE ||
                player.gameMode == org.bukkit.GameMode.SPECTATOR
            ) {
                return true
            }
            if (io.github.zzzyyylllty.chotentech.isSafeWorld(player)) return true
            inBaseRegion(player)
        } catch (_: Exception) {
            false
        }
    }

    /** 基地判断与 `onFoodTimer` 同源：`playerRegions` 里的 `base-` 前缀区域。 */
    private fun inBaseRegion(player: Player): Boolean =
        io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions[player.uniqueId.toString()]
            ?.any { it.startsWith("base-") } == true

    /** 无限体力状态下把手部体力补满（每 tick 调用无副作用：满了就 return）。 */
    fun refillIfUnlimited(player: Player): Boolean {
        if (!isUnlimited(player)) return false
        val data = player.getData() ?: return false
        val max = max(data)
        if (data.handStamina < max) data.handStamina = max
        // 无限体力不参与「5 秒静默」计时，清掉记录避免离开基地后还残留一段等待
        lastSpendMs.remove(player.uniqueId)
        lastRegenMs.remove(player.uniqueId)
        return true
    }

    // ==================== 上限 / 速率 ====================

    /** 手部体力上限（属性 `hand_stamina_max`，未注册 / 未设置时回落到 [PlayerData] 默认）。 */
    fun max(data: PlayerData): Double = data.maxHandStamina.coerceAtLeast(0.0)

    /** 每秒回复多少「点」：上限 × 每秒百分比 × 属性倍率。 */
    fun regenPerSecond(player: Player, data: PlayerData): Double {
        val percent = if (GunStateManager.shouldPauseHandStaminaRegen(player)) {
            // 持枪且 `regen-pause-when-armed: true`：按「持枪恢复速度」算（默认 0 = 完全不回）
            GunSettings.stamina.regenArmedPercentPerSecond
        } else {
            GunSettings.stamina.regenPercentPerSecond
        }
        if (percent <= 0.0) return 0.0
        return max(data) * (percent / 100.0) * regenMultiplier(player)
    }

    /** 属性 `hand_stamina_regen_multiplier`（未注册时按 1.0 处理，避免「属性没配 = 不回复」）。 */
    fun regenMultiplier(player: Player): Double {
        val value = try {
            AttributeManager.getAttributeValue(player, ATTR_REGEN_MULTIPLIER)
        } catch (_: Exception) {
            0.0
        }
        return when {
            !value.isFinite() -> 1.0
            value <= 0.0 -> if (attributeRegistered()) 0.0 else 1.0
            else -> value
        }
    }

    /** 距最后一次消耗多少毫秒（没有记录返回 -1）。 */
    fun sinceLastSpendMs(player: Player): Long {
        val at = lastSpendMs[player.uniqueId] ?: return -1L
        return System.currentTimeMillis() - at
    }

    /** 还要等多久才开始回复（秒）；`<= 0` 表示已经在回复。 */
    fun secondsUntilRegen(player: Player): Double {
        val delayMs = regenDelayMs()
        if (delayMs <= 0L) return 0.0
        val since = sinceLastSpendMs(player)
        if (since < 0L) return 0.0
        return ((delayMs - since).coerceAtLeast(0L)) / 1000.0
    }

    /** 回复延迟（毫秒）：`stamina.regen-delay-seconds`。 */
    fun regenDelayMs(): Long =
        (GunSettings.stamina.regenDelaySeconds.coerceAtLeast(0.0) * 1000.0).toLong()

    // ==================== 消耗 ====================

    /**
     * 扣除手部体力并记录「最后一次消耗时间」。
     *
     * **无限体力区（基地 / 安全世界 / 创造观察者）里直接不扣**：手部体力恒为满值。
     *
     * @return 实际扣掉的量（体力不够时只扣到 0）
     */
    fun spend(player: Player, data: PlayerData, amount: Double): Double {
        if (!amount.isFinite() || amount <= 0.0) return 0.0
        if (isUnlimited(player)) {
            val max = max(data)
            if (data.handStamina < max) data.handStamina = max
            return 0.0
        }
        val before = data.handStamina
        data.handStamina = (before - amount).coerceAtLeast(0.0)
        val spent = before - data.handStamina
        if (spent > 0.0) lastSpendMs[player.uniqueId] = System.currentTimeMillis()
        return spent
    }

    /** 便捷入口：自己取一次 `PlayerData`。 */
    fun spend(player: Player, amount: Double): Double {
        val data = player.getData() ?: return 0.0
        return spend(player, data, amount)
    }

    /** 直接设置数值（管理员命令等）；**降低**数值也算一次消耗，会重置回复延迟。 */
    fun set(player: Player, data: PlayerData, value: Double) {
        val clamped = value.coerceIn(0.0, max(data))
        if (clamped < data.handStamina) lastSpendMs[player.uniqueId] = System.currentTimeMillis()
        data.handStamina = clamped
    }

    // ==================== 恢复 ====================

    /**
     * 结算一次自然恢复（由 `listener/onFoodTimer.kt` 的体力 tick 驱动，**每 20 tick 一次**）。
     *
     * 规则：**距最后一次消耗满 `regen-delay-seconds`（默认 5 秒）之后**，
     * 按「上限 × 每秒百分比 × 属性倍率」回复。
     *
     * 实现按**真实经过时间**折算，而不是假设「一次调用 = 一 tick」：
     * 调用频率变了（现在是每秒一次）也不会把速率算错。
     */
    fun tickRegen(player: Player, data: PlayerData) {
        val max = max(data)
        if (max <= 0.0) return
        // 基地 / 安全世界 / 创造观察者：手部体力恒满
        if (isUnlimited(player)) {
            if (data.handStamina < max) data.handStamina = max
            return
        }
        if (data.handStamina >= max) {
            data.handStamina = max
            return
        }
        // 5 秒静默期：最后一次消耗之后先不回，避免「扣完立刻被自然恢复抹平」
        val since = sinceLastSpendMs(player)
        if (since >= 0L && since < regenDelayMs()) return

        val now = System.currentTimeMillis()
        val last = lastRegenMs[player.uniqueId]
        if (last == null) {
            lastRegenMs[player.uniqueId] = now
            return
        }
        // 卡服/长 tick 时不要一次回一大截，最多按 5 秒结算
        val elapsedMs = (now - last).coerceIn(0L, 5000L)
        if (elapsedMs <= 0L) return
        lastRegenMs[player.uniqueId] = now

        val perSecond = regenPerSecond(player, data)
        if (perSecond <= 0.0) return
        data.handStamina = (data.handStamina + perSecond * elapsedMs / 1000.0).coerceAtMost(max)
    }

    // ==================== 生命周期 ====================

    /** 下线 / 切档时清掉记录。 */
    fun forget(player: Player) {
        lastSpendMs.remove(player.uniqueId)
        lastRegenMs.remove(player.uniqueId)
    }

    fun clear() {
        lastSpendMs.clear()
        lastRegenMs.clear()
    }

    // ==================== 内部 ====================

    private const val ATTR_REGEN_MULTIPLIER = "hand_stamina_regen_multiplier"

    /** 属性有没有被注册进 `AttributeManager`（没注册时 `getAttributeValue` 会返回 0，不能当成「0 倍」）。 */
    private fun attributeRegistered(): Boolean = try {
        AttributeManager.attributeExists(ATTR_REGEN_MULTIPLIER)
    } catch (_: Throwable) {
        false
    }
}

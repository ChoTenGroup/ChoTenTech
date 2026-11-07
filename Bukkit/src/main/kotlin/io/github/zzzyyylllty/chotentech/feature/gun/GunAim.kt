package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlotGroup
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 出枪（draw）与瞄准（ADS）。
 *
 * ### 出枪
 * 切换到枪械物品后需要 `usage.draw` 秒才能操作（[isReady] 为 false 期间不能开火/瞄准）。
 * 成功开始会扣 `stamina.draw-cost` 手部体力，体力不足直接拒绝。
 *
 * ### 瞄准（参考 WeaponMechanics，**按住**而非切换）
 * - 右键按下 → [pressAds]，进入瞄准；客户端按住右键期间会持续发「使用物品」包，
 *   每次都刷新 [PlayerGunState.adsHoldUntilMs]，读表超时（松手）后由
 *   [tickHold] 自动退出。这样不需要额外的松手包，也不会出现「松手还瞄着」。
 * - 进入瞄准后：
 *   1) **真实 FOV 缩放** —— [GunZoom] 改写 Abilities 包的 `fovModifier`，
 *      倍率取 `gun.usage.zoom`（未配置时回落到 `GunSettings.scope.zoom`）
 *   2) 移速乘 `GunSettings.scope.moveMultiplier`，用 `MOVEMENT_SPEED` 属性修饰符实现，
 *      不再直接改 `walkSpeed`（1.21.2 起该值已不再影响玩家移速，改了只会污染视野公式）
 *   3) 模型切到 `aiming`（[GunModel]）
 *   4) 散布/后坐由 `GunSettings` 收窄
 * - 瞄准期间每 tick 持续消耗手部体力（[GunStateManager] 的 tick 循环）。
 */
object GunAim {

    /**
     * 瞄准减速修饰符的 **key**。
     *
     * ### 为什么必须用 key 而不是名字
     * `AttributeModifier(String name, ...)` 是 1.21 起废弃的构造器，它内部会
     * `this(UUID.randomUUID(), name, ...)`，最终 key 是**那个随机 UUID 的字符串**，
     * 而 `getName()` 返回的也是这个 UUID 串 —— 也就是说名字根本没被保留。
     * 于是老写法里「按名字过滤再移除」永远匹配不到任何东西：
     * 每次进入瞄准 / 每秒 [refreshWalkSpeed] 都会**再挂一个 -45% 的乘法减速**，
     * 叠几次移速直接归零 —— 这就是「瞄准一下之后就再也走不动了」。
     */
    private val MOVE_MODIFIER_KEY = org.bukkit.NamespacedKey("chotentech", "gun_ads_slow")

    /** 老版本遗留修饰符的识别：名字是 UUID 字符串（废弃构造器生成的形态）。 */
    private val LEGACY_UUID_NAME = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** 未配置时的默认缩放倍数。 */
    private const val DEFAULT_ZOOM = 2.5

    /** 未配置时的默认瞄准移速系数。 */
    private const val DEFAULT_MOVE_MULTIPLIER = 0.55

    /** 右键保持的宽限时间（毫秒）。客户端按住右键时约每 200ms 重发一次使用包。 */
    private const val ADS_HOLD_GRACE_MS = 600L

    /** 记录「进入瞄准前」的属性修饰符是否已存在，避免重复添加。 */
    private val moveModifierApplied = ConcurrentHashMap<UUID, Boolean>()

    // ==================== 出枪 ====================

    /** 体力不足提示的间隔（毫秒）：tick 兜底路径会反复尝试，提示不能跟着刷屏。 */
    private val lastFailNoticeAt = ConcurrentHashMap<UUID, Long>()

    /**
     * 开始出枪（切枪 / 首次持枪时调用）。
     *
     * @return true = 已开始（或已在出枪），false = 手部体力不足被拒绝
     */
    fun beginDraw(player: Player): Boolean {
        val gun = GunRegistry.gunOf(player.inventory.itemInMainHand) ?: return false
        return beginDraw(player, gun)
    }

    /**
     * 以**指定枪械**开始出枪。
     *
     * 切栏用的是这一版：`PlayerItemHeldEvent` 触发时玩家手上还是**旧物品**，
     * 必须由调用方把即将切到的那把枪传进来，才能在不切换物品的前提下预先扣体力、
     * 判定「够不够出枪」，进而决定要不要取消这次切栏。
     *
     * @param notify true = 体力不足时提示（切栏路径传 true，tick 兜底路径传 false 靠节流）
     * @return true = 已开始（或已在出枪），false = 手部体力不足被拒绝
     */
    fun beginDraw(player: Player, gun: GunData, notify: Boolean = true): Boolean {
        val state = GunStateManager.state(player)

        val cost = GunSettings.stamina.drawCost
        val data = player.getData()
        if (data != null) {
            if (data.handStamina + 1e-6 < cost) {
                state.drawing = false
                state.drawTicks = 0
                if (notify) notifyDrawBlocked(player, cost)
                return false
            }
            // 统一走 HandStamina.spend：扣体力的同时也刷新「最后一次消耗时间」，
            // 5 秒静默期从这个时刻开始算（见 data/HandStamina.kt）。
            io.github.zzzyyylllty.chotentech.data.HandStamina.spend(player, data, cost)
        }

        state.drawing = true
        state.drawApplied = true
        state.drawTicks = (gun.usage.draw * 20.0).toInt().coerceAtLeast(1)
        // 换枪后瞄准状态不继承
        setAds(player, false)
        return true
    }

    /** 体力不足提示（1.5 秒内最多一条，避免 tick 兜底路径每 tick 刷一遍）。 */
    private fun notifyDrawBlocked(player: Player, cost: Double) {
        val now = System.currentTimeMillis()
        val last = lastFailNoticeAt[player.uniqueId] ?: 0L
        if (now - last < 1500L) return
        lastFailNoticeAt[player.uniqueId] = now
        sendActionBar(player, "<#E08A98>手部体力不足（需要 ${fmt(cost)}），无法出枪")
    }

    /** 供切栏监听调用：体力不足就提示（受 [notifyDrawBlocked] 同一个节流约束）。 */
    fun notifyDrawBlockedFor(player: Player) {
        notifyDrawBlocked(player, GunSettings.stamina.drawCost)
    }

    /**
     * **预检**：不扣体力、不改状态，只回答「现在切到这把枪能不能出枪」。
     *
     * 切栏事件必须能在**动手之前**回答这个问题，否则会先切过去再取消，客户端会闪一下。
     */
    fun canDraw(player: Player): Boolean {
        val cost = GunSettings.stamina.drawCost
        if (cost <= 0.0) return true
        val data = player.getData() ?: return true
        return data.handStamina + 1e-6 >= cost
    }

    /** 出枪是否完成（`drawTicks <= 0` 才能开火 / 瞄准）。 */
    fun isReady(player: Player): Boolean = GunStateManager.state(player).drawTicks <= 0

    /** 当前是否处于瞄准。 */
    fun isAds(player: Player): Boolean = GunStateManager.existing(player)?.ads == true

    // ==================== 瞄准：按住右键 ====================

    /**
     * 右键**按下**（客户端按住期间会不断重复调用，用来续期）。
     *
     * 已在瞄准状态时只续期，不重复播提示 / 不重复改属性。
     */
    fun pressAds(player: Player) {
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held) ?: return
        val state = GunStateManager.state(player)
        if (state.drawTicks > 0) {
            sendActionBar(player, "<#E08A98>正在出枪，无法瞄准")
            return
        }
        val now = System.currentTimeMillis()
        state.adsHoldUntilMs = now + ADS_HOLD_GRACE_MS
        if (state.ads) return
        enterAds(player, gun)
    }

    /** 显式松开右键（能拿到松手信号时用；拿不到就靠 [tickHold] 超时）。 */
    fun releaseAds(player: Player) {
        val state = GunStateManager.existing(player) ?: return
        if (!state.ads) return
        state.adsHoldUntilMs = 0L
        setAds(player, false)
    }

    /**
     * 每 tick 检查按住状态：超时未续期 / 手空了 / 换枪 → 退出瞄准。
     */
    fun tickHold(player: Player) {
        val state = GunStateManager.existing(player) ?: return
        if (!state.ads) return
        if (System.currentTimeMillis() > state.adsHoldUntilMs) {
            setAds(player, false)
            return
        }
        // 手持已经不是这把枪（被换走 / 丢出）时立刻退出
        val gun = GunRegistry.gunOf(player.inventory.itemInMainHand)
        if (gun == null) setAds(player, false)
    }

    private fun enterAds(player: Player, gun: GunData) {
        if (GunStateManager.existing(player)?.ads == true) return
        // 统一走 setAds：瞄准的全部副作用（移速 / FOV / 模型）只有一处实现，
        // 避免这里和 setAds 各写一份、以后只改了一边。
        setAds(player, true)
        val zoomText = fmt(zoomOf(gun))
        sendActionBar(player, "<#8FD4A6>进入瞄准 <#A9B4C3>| <#EFDF8F>${zoomText}x")
    }

    /** 这把枪的缩放倍数：`gun.usage.zoom`（YAML 可选），缺省 [DEFAULT_ZOOM]。 */
    fun zoomOf(gun: GunData): Double {
        val raw = gun.usage.zoom
        return if (raw.isFinite() && raw > 1.0) raw.coerceAtMost(20.0) else DEFAULT_ZOOM
    }

    /**
     * 设置瞄准状态（含全部副作用），幂等。
     */
    fun setAds(player: Player, ads: Boolean) {
        val state = GunStateManager.existing(player)
        if (state == null) {
            // 状态表里没有这个玩家：只做外部还原，不新建状态
            if (!ads) {
                applyMoveSpeed(player, false)
                GunZoom.clear(player)
                GunModel.forget(player)
            }
            return
        }
        if (state.ads == ads) return
        state.ads = ads
        state.adsTicks = 0
        if (!ads) {
            ThermalManager.clear(player)
            state.adsHoldUntilMs = 0L
        }
        applyMoveSpeed(player, ads)
        if (ads) {
            val gun = GunRegistry.gunOf(player.inventory.itemInMainHand)
            if (GunSettings.scope.enabled) GunZoom.setZoom(player, gun?.let { zoomOf(it) } ?: DEFAULT_ZOOM)
            GunModel.applyModel(player, GunModel.State.AIMING)
        } else {
            GunZoom.clear(player)
            GunModel.applyModel(player, GunModel.State.GENERAL)
        }
    }

    /**
     * 复位（切枪 / 死亡 / 退出世界 / 关服时调用）：退出瞄准、还原移速与视野、还原常态模型，
     * 并把 HUD 那一行清掉。
     */
    fun reset(player: Player) {
        ThermalManager.clear(player)
        GunStateManager.existing(player)?.let {
            it.ads = false
            it.adsTicks = 0
            it.adsHoldUntilMs = 0L
        }
        applyMoveSpeed(player, false)
        GunZoom.clear(player)
        GunModel.forget(player)
        GunNameplate.forget(player)
        // 不手持枪械就不该继续显示「耐久 / 体力」那一行：ActionBar 不会自己消失
        GunHud.clear(player)
        lastFailNoticeAt.remove(player.uniqueId)
    }

    // ==================== 移速 ====================

    /**
     * 瞄准时通过 `MOVEMENT_SPEED` 的**乘法修饰符**降速，退出时按 key 移除。
     *
     * 为什么不用 `Player#setWalkSpeed`：1.21.2 起 Abilities 包里的 walkingSpeed 已经被
     * `fovModifier` 取代，改 walkSpeed 既不会改移速，还会干扰视野公式。
     *
     * ### 三条硬约束（都是踩过的坑）
     * 1. 修饰符必须用 [MOVE_MODIFIER_KEY] 建，**不能**用废弃的 `AttributeModifier(String, ...)`
     *    （它的 key 是随机 UUID，按名字删不掉 → 减速每秒叠加一次 → 玩家卡死走不动）；
     * 2. 幂等：已经挂着就不再添加，`refreshWalkSpeed` 每秒调用也不会叠；
     * 3. 顺手清理历史遗留（UUID 命名的同数值乘法修饰符），老玩家不用重登也能恢复。
     */
    private fun applyMoveSpeed(player: Player, ads: Boolean) {
        val attribute = try {
            player.getAttribute(Attribute.MOVEMENT_SPEED)
        } catch (_: Exception) {
            null
        } ?: return

        try {
            val multiplier = effectiveMoveMultiplier()
            val amount = multiplier - 1.0

            // 先清（含历史遗留），再按需添加 —— 顺序很重要，绝不能只加不清
            removeOurModifiers(attribute, amount)

            if (!ads) {
                moveModifierApplied.remove(player.uniqueId)
                return
            }
            if (multiplier >= 0.999) return
            // 幂等：同一份减速只挂一个
            if (attribute.modifiers.any { it.key == MOVE_MODIFIER_KEY }) {
                moveModifierApplied[player.uniqueId] = true
                return
            }
            attribute.addModifier(
                AttributeModifier(MOVE_MODIFIER_KEY, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1),
            )
            moveModifierApplied[player.uniqueId] = true
        } catch (_: Exception) {
            // 属性写入失败不影响射击
        }
    }

    /**
     * 移除本系统的移速修饰符：[MOVE_MODIFIER_KEY] 的，以及**历史遗留**的同数值乘法修饰符。
     *
     * 遗留形态：名字是 UUID 字符串、`MULTIPLY_SCALAR_1`、数值正好等于 `multiplier - 1.0`。
     * 三个条件同时满足才动，不会误删护甲 / 其它插件的修饰符。
     */
    private fun removeOurModifiers(attribute: org.bukkit.attribute.AttributeInstance, amount: Double) {
        runCatching { attribute.removeModifier(MOVE_MODIFIER_KEY) }
        for (modifier in attribute.modifiers.toList()) {
            val ours = modifier.key == MOVE_MODIFIER_KEY ||
                (
                    modifier.operation == AttributeModifier.Operation.MULTIPLY_SCALAR_1 &&
                        kotlin.math.abs(modifier.amount - amount) < 1e-6 &&
                        LEGACY_UUID_NAME.matches(modifier.name)
                    )
            if (!ours) continue
            runCatching { attribute.removeModifier(modifier) }
        }
    }

    /** 当前挂在本系统上的减速修饰符数量（诊断用；正常只该是 0 或 1）。 */
    fun adsModifierCount(player: Player): Int {
        val attribute = try {
            player.getAttribute(Attribute.MOVEMENT_SPEED)
        } catch (_: Exception) {
            null
        } ?: return -1
        val amount = effectiveMoveMultiplier() - 1.0
        return attribute.modifiers.count {
            it.key == MOVE_MODIFIER_KEY ||
                (
                    it.operation == AttributeModifier.Operation.MULTIPLY_SCALAR_1 &&
                        kotlin.math.abs(it.amount - amount) < 1e-6 &&
                        LEGACY_UUID_NAME.matches(it.name)
                    )
        }
    }

    private fun effectiveMoveMultiplier(): Double =
        GunSettings.scope.moveMultiplier.takeIf { it in 0.05..1.0 } ?: DEFAULT_MOVE_MULTIPLIER

    /** 状态无关的周期性刷新（护甲变化的移速修正由护甲模块自己处理，这里只保证 ADS 系数在位）。 */
    fun refreshWalkSpeed(player: Player) {
        val ads = GunStateManager.existing(player)?.ads == true
        if (ads) {
            applyMoveSpeed(player, true)
            // 移速属性可能被护甲 / 外部插件改动过，FOV 公式里的 movementSpeed 必须同步补偿，
            // 否则缩放倍数会跟着漂（属性一变，同一个 fovModifier 就不等于原定倍数了）。
            GunZoom.refresh(player)
        } else {
            applyMoveSpeed(player, false)
        }
    }

    // ==================== 文本 ====================

    private fun sendActionBar(player: Player, message: String) {
        try {
            GunPrompt.show(player, message)
        } catch (_: Exception) {
        }
    }

    private fun fmt(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}

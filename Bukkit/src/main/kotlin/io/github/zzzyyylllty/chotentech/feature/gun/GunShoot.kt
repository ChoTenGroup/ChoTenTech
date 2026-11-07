package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.damage.DamageManager
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.util.Vector
import taboolib.platform.BukkitPlugin
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * 开火（P1 的核心闭环，P3 补齐手感与磨损）。
 *
 * ### 闸门顺序（与实施计划 §5 一致，P3 有调整）
 * 0. **P3：耐久归零 → 纯近战分支**（[brokenMelee]），直接 return，不再走任何枪械闸门
 * 1. 手持不是枪 → 直接返回（静默）
 * 2. 举枪未完成 → 返回
 * 2.5 读条中（换弹 / 拉栓 / 检视 / 压弹）→ 返回（`GunAction.isBusy`）
 * 2.6 强制停火（严重过热）→ 返回
 * 3. 卡壳 / 热量上限锁定 / 热量严重档 → 提示并返回
 * 4. 射速闸门：`shotInterval = round(1200 / (fireRate * heatRateMult))`（P3：越热越慢）
 * 5. 手部体力不足 → 提示并返回
 * 6. 取弹：膛内优先，否则弹匣末位；都没有 → 空仓音效 + 返回
 * 7. 方向 + 圆锥散布 / 后坐 / 热量 / 磨损 / 卡壳 roll / 生成子弹 / 音效粒子 / 计时
 *
 * ### 与「计划」的差异（详见交付说明）
 * - `ammo.pellets`（P6）：一次开火生成 N 颗独立散布的弹丸，但只消耗一发弹药，
 *   后坐、热量、磨损与卡壳判定也都只结算一次。
 * - 后坐没有写到玩家视角上：这里只累加 `state.recoilVelP/Y`，
 *   由 [GunStateManager] 的 tick 循环统一施加（避免事件线程与 tick 线程抢视角）。
 * - P3 起水平后坐**不再随机翻向**：`shakePattern` 的符号即方向（正=右、负=左）。
 */
enum class ShotResult { FIRED, RATE_LIMITED, BLOCKED }

object GunShoot {

    enum class TriggerSignal { PRESS, RELEASE }

    /** 「上次开火被哪道闸门拒了」的日志节流（uuid → 上次输出的毫秒时间戳）。 */
    private val lastDenyLogAt = java.util.concurrent.ConcurrentHashMap<UUID, Long>()

    /** 射速限制提示的节流（uuid → 上次提示的毫秒时间戳）；AUTO 模式每 tick 都可能撞到这道闸门。 */
    private val lastRateNoticeAt = java.util.concurrent.ConcurrentHashMap<UUID, Long>()

    /**
     * 记录「这次开火被拒」的原因并返回 `BLOCKED`。
     *
     * 所有闸门都走这里：原因同时写进 [PlayerGunState.lastShotResult]（`/ctt gun state` 会打印）
     * 和一条节流后的服务端日志。「只能打一发」「按了没反应」这类问题，
     * 以前全靠猜是卡壳、体力、射速还是热量，现在一句日志就够了。
     */
    private fun deny(player: Player, code: String): ShotResult {
        recordResult(player, code)
        val now = System.currentTimeMillis()
        val last = lastDenyLogAt[player.uniqueId] ?: 0L
        if (now - last >= 500L) {
            lastDenyLogAt[player.uniqueId] = now
            devLog("[Gun] 开火被拒 player=${player.name} reason=$code")
        }
        return ShotResult.BLOCKED
    }

    /** 只登记「上次开火结果」，不打日志（射速闸门每 tick 都可能触发，不能刷屏）。 */
    private fun recordResult(player: Player, text: String) {
        val state = GunStateManager.existing(player) ?: return
        state.lastShotResult = text
        state.lastShotResultTick = try {
            player.world.fullTime
        } catch (_: Exception) {
            0L
        }
    }

    /** 射速闸门的间隔（tick）：`1200 / (fireRate × 热量档位系数)`。调试输出也用它，避免两处算法漂移。 */
    fun shotIntervalTicks(gun: GunData, heatTier: Int): Long =
        (1200.0 / (gun.attributes.fireRate.coerceAtLeast(1.0) * GunSettings.heatRateMultiplier(heatTier)))
            .roundToLong().coerceAtLeast(1L)

    /**
     * 连发序号（`shotIndex`）归零的窗口（毫秒）。
     *
     * 取「配置的 `recoil.reset-seconds`」与「两倍射速间隔」中的**较大者**：
     * 射速慢的枪（例如 `fireRate: 65` ≈ 0.92 秒一发）如果还按 0.35 秒重置，
     * 那么两发之间必然超时，`shotIndex` 永远停在 0 ——
     * `recoil: {1: 1, 5: 5, 10: 2}` 这种曲线就永远只用到第 1 个关键帧，
     * 后坐手感是「平的」，看起来像曲线没生效。
     */
    fun effectiveResetMs(gun: GunData?): Long {
        val configured = (GunSettings.recoil.resetSeconds * 1000.0).toLong().coerceAtLeast(50L)
        if (gun == null) return configured
        val intervalMs = shotIntervalTicks(gun, 0) * 50L
        return maxOf(configured, intervalMs * 2L)
    }

    /** 上一次开火被拒的原因（调试命令用）。 */
    fun lastResultText(player: Player): String =
        GunStateManager.existing(player)?.lastShotResult?.ifEmpty { "无" } ?: "无"

    /** 最后输入时间仅用于断线保护；正常松开由 packetevents 立即停止。 */
    private const val AUTO_TRIGGER_TIMEOUT_MS = 1000L

    /** Bukkit 单击入口；真正持续按压由 packetevents 单独续期。 */
    fun onTrigger(player: Player, held: ItemStack) = handleTrigger(player, held, renewAuto = false)

    /** 数据包入口：PRESS 保持 AUTO，RELEASE 无条件停止。 */
    fun onPacketTrigger(player: Player, signal: TriggerSignal) {        if (signal == TriggerSignal.RELEASE) {
            // Packet input owns only AUTO hold state; it must not cancel a BURST started by Bukkit input.
            GunStateManager.existing(player)?.let {
                it.autoFiring = false
                it.triggerUntilMs = 0L
            }
            return
        }
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held) ?: return
        val fireMode = GunInstance.read(held)?.fireMode ?: 0
        if (GunAction.fireModeName(gun, fireMode) != "AUTO") return
        handleTrigger(player, held, renewAuto = true)
    }

    private fun handleTrigger(player: Player, held: ItemStack, renewAuto: Boolean) {
        val gun = GunRegistry.gunOf(held) ?: return
        val gunState = GunInstance.readEnsured(held, gun) ?: return
        val state = GunStateManager.state(player)
        if (gunState.durability <= 0.0) {
            resetTrigger(player)
            tryShoot(player, held)
            return
        }
        when (GunAction.fireModeName(gun, gunState.fireMode)) {
            "BURST" -> {
                state.autoFiring = false
                state.triggerUntilMs = 0L
                if (state.burstRemaining <= 0) state.burstRemaining = gun.burst.coerceAtLeast(1)
                tickFire(player, held, gun)
            }
            "AUTO" -> {
                val now = System.currentTimeMillis()
                if (renewAuto) {
                    state.autoFiring = true
                    state.triggerUntilMs = now + AUTO_TRIGGER_TIMEOUT_MS
                }
                state.burstRemaining = 0
                // AUTO is advanced only by tickFire; packet repeats renew the held trigger.
                if (!renewAuto) tryShoot(player, held)
            }
            else -> {
                resetTrigger(player)
                if (!renewAuto) tryShoot(player, held)
            }
        }
    }

    /** 每 tick 推进点射和自动射击，由 GunStateManager 在主线程调用。 */
    fun tickFire(player: Player, held: ItemStack, gun: GunData) {
        val state = GunStateManager.state(player)
        val now = System.currentTimeMillis()
        if (state.autoFiring && now > state.triggerUntilMs) {
            state.autoFiring = false
            state.triggerUntilMs = 0L
        }
        if (state.burstRemaining <= 0 && !state.autoFiring) return
        if (GunAction.isBusy(player) || !GunAim.isReady(player)) {
            resetTrigger(player)
            return
        }
        val mode = GunAction.fireModeName(gun, GunInstance.read(held)?.fireMode ?: 0)
        if ((state.burstRemaining > 0 && mode != "BURST") || (state.autoFiring && mode != "AUTO")) {
            resetTrigger(player)
            return
        }
        val result = tryShoot(player, held)
        if (state.burstRemaining > 0) {
            when (result) {
                ShotResult.FIRED -> state.burstRemaining--
                ShotResult.RATE_LIMITED -> Unit // 使用实际开火闸门的返回值，不重复推算热量射速。
                ShotResult.BLOCKED -> state.burstRemaining = 0
            }
        }
        // AUTO：一旦被闸门挡住（空仓 / 过热 / 体力不足 / 卡壳），就松开扳机。
        // 否则按住左键时 tickFire 每 tick 都会再试一次，那些提示会一条接一条地弹
        // （节流也压不住），而玩家其实什么也做不了 —— 需要重新按下才有意义。
        if (state.autoFiring && result == ShotResult.BLOCKED) {
            resetTrigger(player)
        }
    }

    /** 切枪、切模式、近战或读条时终止所有持续射击。 */
    fun resetTrigger(player: Player) {
        GunStateManager.existing(player)?.let {
            it.burstRemaining = 0
            it.autoFiring = false
            it.triggerUntilMs = 0L
        }
    }

    /**
     * 尝试开火。
     *
     * @param player 射手
     * @param held 射手主手物品（由监听器传入，减少一次背包读取）
     */
    fun tryShoot(player: Player, held: ItemStack): ShotResult {
        val baseGun = GunRegistry.gunOf(held) ?: return deny(player, "not-gun")
        // 以下所有计算统一使用“基础属性 + 配件加法”的快照；弹道也会保存这份快照。
        val gun = baseGun.copy(attributes = AddonManager.effectiveAttributes(baseGun, held))
        val state = GunStateManager.state(player)

        // 耐久归零 → 纯近战分支（用户拍板的设计：枪坏了就是一根钝器）。
        // 必须放在所有枪械闸门之前：损坏的枪既不能开火，也不该被「热量/卡壳」拦住左键。
        val gunState = GunInstance.readEnsured(held, baseGun, gun.attributes) ?: return deny(player, "nbt-missing")
        if (gunState.durability <= 0.0) {
            brokenMelee(player)
            return ShotResult.BLOCKED
        }

        // 出枪读条没走完 → 不能开火。必须给一句反馈：
        // 这是「有时候能打、有时候打不出来」最常见的原因（切枪后前 0.5 秒），
        // 之前这里静默返回，玩家只会觉得左键时灵时不灵。
        if (!GunAim.isReady(player)) {
            sendActionBar(player, "<#DFBD7C>正在出枪，稍等")
            return deny(player, "draw")
        }

        // 换弹/拉栓/检视/压弹等读条期间禁止开火（塔科夫手感：读条中枪不能打）
        if (GunAction.isBusy(player)) return deny(player, "busy")

        val nowMs = System.currentTimeMillis()
        // 严重过热强制停火（P1 就有的字段，P3 接线：`heat.severe-stop-ms` 毫秒）
        if (state.forcedStopUntil > nowMs) return deny(player, "forced-stop")

        // 卡壳闸门
        if (gunState.jam != 0) {
            if (gunState.jam >= 2) {
                sendActionBar(player, "<#E08A98>严重故障 <#A9B4C3>| <#EFDF8F>需要排障（按 F 打开菜单）")
            } else {
                sendActionBar(player, "<#E08A98>膛线卡住了 <#A9B4C3>| <#EFDF8F>右键拉栓")
            }
            return deny(player, "jam")
        }

        val heatCapacity = if (gun.heatCapacity > 0.0) gun.heatCapacity else GunSettings.heat.capacity
        val heatPerShot = if (gun.heatPerShot > 0.0) gun.heatPerShot else GunSettings.heat.perShot
        val currentHeat = GunStateManager.currentHeat(player, gun, gunState)
        val effectiveGunState = gunState.copy(heat = currentHeat)
        val unlockHeat = heatCapacity * GunSettings.heat.unlockRatio

        // 热量上限锁定：触顶后必须降到 `capacity * unlock-ratio` 以下才解锁
        if (state.heatLocked) {
            if (currentHeat < unlockHeat) {
                state.heatLocked = false
            } else {
                sendActionBar(
                    player,
                    "<#E08A98>热量上限锁定 <#A9B4C3>| <#EFDF8F>${percent(currentHeat, heatCapacity)}%" +
                        " <#A9B4C3>冷却至 <#EFDF8F>${(GunSettings.heat.unlockRatio * 100).roundToInt()}%" +
                        "<#A9B4C3> 解锁"
                )
                return deny(player, "heat-lock")
            }
        }
        if (currentHeat >= heatCapacity - 1e-6) {
            state.heatLocked = true
            sendActionBar(
                player,
                "<#E08A98>热量上限锁定 <#A9B4C3>| <#EFDF8F>${percent(currentHeat, heatCapacity)}%" +
                    " <#A9B4C3>冷却至 <#EFDF8F>${(GunSettings.heat.unlockRatio * 100).roundToInt()}%" +
                    "<#A9B4C3> 解锁"
            )
            return deny(player, "heat-full")
        }

        val heatTier = GunSettings.heatTier(currentHeat)
        if (heatTier >= 3) {
            // 正常情况下严重档已经被强制停火接管；这里是双保险
            sendActionBar(player, "<#E08A98>枪管严重过热，需要冷却")
            return deny(player, "heat-tier")
        }

        // 射速闸门（P3：热量档位越低越快，越高越慢）
        //
        // ⚠️ 用**墙钟**（毫秒）而不是 `world.fullTime`：服务器 `/tick freeze` 或某些 fork 会让
        // 世界 tick 完全不前进，用世界 tick 做冷却的闸门就永远解不开 —— 玩家看到的正是
        // 「提示射速限制，等多久都打不出去，切一下枪才能再打一发」。
        val fullTime = player.world.fullTime
        val interval = shotIntervalTicks(gun, heatTier)
        val intervalMs = interval * 50L
        if (state.lastShotMs > 0L && nowMs - state.lastShotMs < intervalMs) {
            // 这是最容易被误当成「枪坏了 / 只能打一发」的一道闸门：之前是完全静默的。
            // 记下原因，并且最多每秒提示一次（`fireRate: 65` 就是 0.92 秒一发，玩家连点几乎全被拒）。
            recordResult(player, "rate(${nowMs - state.lastShotMs}/${intervalMs}ms)")
            val lastNotice = lastRateNoticeAt[player.uniqueId] ?: 0L
            if (nowMs - lastNotice >= 1000L) {
                lastRateNoticeAt[player.uniqueId] = nowMs
                sendActionBar(
                    player,
                    "<#DFBD7C>射速限制 <#A9B4C3>| <#EFDF8F>${fmt1(interval / 20.0)}s 一发" +
                        " <#6E7A8C>(fireRate=${fmt1(gun.attributes.fireRate)})",
                )
            }
            return ShotResult.RATE_LIMITED
        }

        // 手部体力闸门
        val data = player.getData()
        val staminaCost = GunSettings.stamina.shotCost *
            (if (state.ads) GunSettings.stamina.adsMultiplier else 1.0)
        if (data != null && data.handStamina + 1e-6 < staminaCost) {
            sendActionBar(player, "<#E08A98>手部体力不足（需要 ${fmt(staminaCost)}）")
            return deny(player, "stamina(${fmt1(data.handStamina)})")
        }

        // 膛内弹种必须由上膛路径明确保存，禁止用弹匣或注册表猜测。
        val fromChamber = gunState.chamber > 0
        val ammoId = if (fromChamber) gunState.chamberAmmoId else gunState.magAmmo.lastOrNull()
        if (ammoId == null) {
            playSound(player, GunSettings.sounds.empty, 0.8f, 1.4f)
            sendActionBar(
                player,
                if (fromChamber) "<#E08A98>膛内弹种缺失，请重新装填" else "<#E08A98>没有子弹了",
            )
            return deny(player, "empty")
        }
        val ammo = GunRegistry.ammo(ammoId) ?: run {
            devLog("[Gun] 找不到子弹配置: $ammoId，已取消开火")
            sendActionBar(player, "<#E08A98>弹药数据失效，未消耗子弹")
            return deny(player, "ammo-config")
        }
        if (!ammo.caliber.equals(gun.caliber, ignoreCase = true)) {
            devLog("[Gun] 枪械与弹药口径不一致: gun=${gun.caliber}, ammo=$ammoId/${ammo.caliber}")
            sendActionBar(player, "<#E08A98>弹药口径不匹配，未消耗子弹")
            return deny(player, "caliber")
        }

        // 先在内存中完成全部计算，最后一次写入并回读确认，避免扣弹成功但后续开火失败。
        var working = if (fromChamber) {
            effectiveGunState.copy(chamber = 0, chamberAmmoId = null)
        } else {
            val remaining = effectiveGunState.magAmmo.toMutableList()
            remaining.removeAt(remaining.lastIndex)
            effectiveGunState.copy(magAmmo = remaining)
        }

        // 耐久比（本发开火前）：散布 / 后坐 / 卡壳率 / 弹速都吃它
        val durabilityRatio = durabilityRatio(gun, working.durability)

        // 方向 + 散布（P3：耐久削弱 + 连发增长 + ADS/scope）
        val spreadDegrees = computeSpread(
            player, gun, ammo, state.ads, heatTier, working.durability, state.shotIndex,
            AddonManager.zoom(held),
        )
        val baseDirection = player.eyeLocation.direction

        // 热量
        val newHeat = (working.heat + heatPerShot).coerceIn(0.0, heatCapacity)
        working = working.copy(heat = newHeat)

        // 耐久磨损（P3）：穿甲越高的子弹越费枪
        val wearAmount = GunSettings.wear.perShot * (1.0 + ammo.penetration / 100.0 * 0.5)
        val newDurability = (working.durability - wearAmount).coerceAtLeast(0.0)
        working = working.copy(
            durability = newDurability,
            worn = (working.worn + 0.001).coerceIn(0.0, 1.0),
        )

        // 卡壳 roll（P3：命中后 5% 概率升级为严重故障 jam=2）
        val jamChance = computeJamChance(gun, ammo, working.durability, heatTier)
        var jammedFatal = false
        if (Random.nextDouble() * 100.0 < jamChance) {
            jammedFatal = Random.nextDouble() < SEVERE_JAM_RATIO
            working = working.copy(jam = if (jammedFatal) 2 else 1)
        }

        // 一次性落盘并回读校验；失败时不生成子弹、不扣体力，也不施加后坐。
        if (!GunInstance.write(held, working)) {
            sendActionBar(player, "<#E08A98>枪械状态写入失败，本次开火已取消")
            return deny(player, "write")
        }
        try {
            player.inventory.setItemInMainHand(held)
        } catch (_: Exception) {
        }
        // 耐久刚变过：把新值写进物品名称（不必等下一个 HUD 周期）
        GunNameplate.refreshHeld(player, held, gun, working)
        // 手部体力：统一走 HandStamina.spend（顺带刷新「最后一次消耗时间」，5 秒后才开始自然回复）
        data?.let { io.github.zzzyyylllty.chotentech.data.HandStamina.spend(player, it, staminaCost) }
        applyRecoil(gun, ammo, state, durabilityRatio)
        GunStateManager.noteHeat(player, newHeat)

        // 生成子弹：每颗弹丸重新做一次圆锥采样；伤害仍是 AmmoData.damage/颗，不除以弹丸数。
        // 弹药消耗、后坐、热量、磨损与卡壳均已在上方按“一次开火”结算，不能放进这个循环。
        val speedMultiplier = bulletSpeedMultiplier(durabilityRatio)
        var origin = player.eyeLocation.clone().add(baseDirection.clone().multiply(0.6))
        repeat(ammo.pellets.coerceAtLeast(1)) {
            val direction = spreadDirection(baseDirection, spreadDegrees)
            origin = player.eyeLocation.clone().add(direction.clone().multiply(0.6))
            GunBallistics.spawn(player, origin, direction, ammo, gun, 0.0, speedMultiplier)
        }

        // 音效 / 枪口粒子：suppressor > 0 的最小值即消音倍率，无配件为 1。
        val suppressor = AddonManager.suppressor(held).coerceIn(0.0, 1.0)
        playSound(player, GunSettings.sounds.fire, suppressor.toFloat(), 1.0f)
        try {
            player.world.spawnParticle(Particle.SMOKE, origin, if (suppressor < 1.0) 1 else 3, 0.05, 0.05, 0.05)
            if (suppressor >= 1.0) {
                player.world.spawnParticle(Particle.FLAME, origin, 1, 0.02, 0.02, 0.02)
            }
        } catch (_: Exception) {
        }

        if (jammedFatal) {
            sendActionBar(player, "<#E08A98>严重故障！ <#A9B4C3>| <#EFDF8F>需要排障（按 F 打开菜单）")
        } else if (working.jam == 1) {
            sendActionBar(player, "<#E08A98>卡壳！ <#A9B4C3>| <#EFDF8F>右键拉栓")
        } else if (newDurability <= 0.0) {
            sendActionBar(player, "<#E08A98>枪械已损坏 <#A9B4C3>| <#EFDF8F>只能当钝器使用（左键近战）")
        }

        // 计时（墙钟优先：世界 tick 可能被 /tick freeze 冻住，不能拿来当冷却）
        state.lastShotTick = fullTime
        state.lastShotAt = fullTime
        state.lastShotMs = System.currentTimeMillis()
        state.shotIndex++
        recordResult(player, "fired")
        return ShotResult.FIRED
    }

    // ==================== 损坏近战（P3） ====================

    /**
     * 耐久归零后的「纯近战」分支。
     *
     * - 目标：**视线锥内、距离 [GunSettings.brokenMelee] 的 `range` 格以内、最近的一个** [LivingEntity]
     *   （排除自己 / 创造观察者 / 无敌实体；用 `hasLineOfSight` 挡墙）
     * - 伤害：固定 [GunSettings.brokenMelee]`.damage`（默认 3.0），**不吃** `attack_damage` / 暴击 / 增伤，
     *   但仍走 [DamageManager.calculateDamage] 的通用减免
     * - 冷却：`cooldown-ticks`（默认 20 tick），冷却中左键只提示「枪托还在冷却」
     * - 反馈：`entity.player.attack.crit` 音效 + [Particle.SWEEP_ATTACK]
     *
     * ### 为什么要打 `chotentech_gun_hit` metadata
     * `target.damage(...)` 会再次触发 `AttackListener.onEntityDamageByEntity`，
     * 那边会按近战类型再算一遍增伤 / 暴击 / 护甲，等于把固定 3 点打两遍。
     * 所以这里沿用 [GunDamage.applyHit] 的做法：施加伤害前后挂/摘
     * [GunDamage.HIT_METADATA]（`try/finally` 保证一定摘掉）。
     */
    fun brokenMelee(player: Player) {
        val state = GunStateManager.state(player)
        val now = player.world.fullTime
        // 冷却同样用墙钟：世界 tick 被冻住时用 tick 做冷却会永远解不开。
        val nowMs = System.currentTimeMillis()
        // 同一 tick 内可能既收到 PlayerInteractEvent 又收到 EntityDamageByEntityEvent：
        // 已经在本 tick 命中过就静默返回，不重复结算也不刷「冷却」提示。
        if (state.meleeLastTick == now && now > 0L) return

        val cooldown = GunSettings.brokenMelee.cooldownTicks.coerceAtLeast(0)
        val cooldownMs = cooldown * 50L
        if (cooldownMs > 0L && state.meleeLastMs > 0L && nowMs - state.meleeLastMs < cooldownMs) {
            val left = cooldownMs - (nowMs - state.meleeLastMs)
            sendActionBar(player, "<#E08A98>枪托还在冷却 <#A9B4C3>| <#EFDF8F>${fmt1(left / 1000.0)}s")
            return
        }

        val range = GunSettings.brokenMelee.range.coerceAtLeast(0.5)
        val target = meleeTarget(player, range)
        if (target == null) {
            sendActionBar(player, "<#DFBD7C>枪械已损坏 <#A9B4C3>| <#EFDF8F>挥空（${fmt(range)} 格内没有目标）")
            return
        }

        // 固定伤害：只走通用减免，不吃 attack_damage / 暴击 / 增伤
        val raw = GunSettings.brokenMelee.damage.coerceAtLeast(0.0) *
            (1.0 - io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager.armPenalties(player).melee)
        val damage = try {
            DamageManager.calculateDamage(raw, DamageType.PHYSICAL, target)
        } catch (_: Exception) {
            raw
        }

        if (!damage.isFinite() || damage <= 0.0) {
            sendActionBar(player, "<#DFBD7C>这一下没有造成伤害")
            return
        }

        // 冷却绑定“成功找到目标并发起挥击”，不绑定生命值变化。
        // 目标无敌帧或其它插件取消事件时，也不能让玩家每 tick 重复触发 damage()。
        state.meleeLastTick = now
        state.meleeLastMs = nowMs
        val plugin = try {
            BukkitPlugin.getInstance()
        } catch (_: Throwable) {
            null
        }
        val applied = try {
            if (plugin != null) {
                target.setMetadata(GunDamage.HIT_METADATA, FixedMetadataValue(plugin, true))
                    target.setMetadata(GunDamage.MELEE_METADATA, FixedMetadataValue(plugin, true))
            }
            target.damage(damage, player)
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                if (plugin != null) {
                    target.removeMetadata(GunDamage.HIT_METADATA, plugin)
                    target.removeMetadata(GunDamage.MELEE_METADATA, plugin)
                }
            } catch (_: Exception) {
            }
        }

        if (!applied) {
            sendActionBar(player, "<#DFBD7C>这一下没有造成伤害")
            return
        }

        // 音效 + 粒子
        try {
            player.world.playSound(target.location, "entity.player.attack.crit", 1.0f, 1.0f)
        } catch (_: Exception) {
        }
        try {
            val center = target.location.clone().add(0.0, target.height / 2.0, 0.0)
            player.world.spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0.0, 0.0, 0.0)
        } catch (_: Exception) {
        }
        sendActionBar(player, "<#DFBD7C>枪托猛击 <#A9B4C3>| <#EFDF8F>${fmt(damage)} 伤害")
    }

    /**
     * 视线锥内最近的可击打目标。
     *
     * 判定顺序：存活实体 → 排除自己 / 旁观者 → 在 [range] 内 → 与视线夹角 ≤ ~35° → 有视线。
     * 用「命中点在夹角内」而不是严格的射线求交，是为了让近战在近距离不会因为瞄偏几像素就落空。
     */
    private fun meleeTarget(player: Player, range: Double): LivingEntity? {
        val eye = player.eyeLocation
        val direction = eye.direction.clone().normalize()
        val nearby = try {
            player.getNearbyEntities(range, range, range)
        } catch (_: Exception) {
            emptyList<org.bukkit.entity.Entity>()
        }
        var best: LivingEntity? = null
        var bestDistance = Double.MAX_VALUE
        for (entity in nearby) {
            if (entity !is LivingEntity) continue
            if (entity == player || entity.isDead) continue
            if (entity is Player && entity.gameMode == GameMode.SPECTATOR) continue
            val target = entity
            val center = target.location.clone().add(0.0, target.height / 2.0, 0.0)
            val toTarget = center.toVector().subtract(eye.toVector())
            val distance = toTarget.length()
            if (distance <= 1e-4 || distance > range) continue
            val dot = toTarget.multiply(1.0 / distance).dot(direction)
            if (dot < MELEE_CONE_DOT) continue
            val visible = try {
                player.hasLineOfSight(target)
            } catch (_: Exception) {
                true
            }
            if (!visible) continue
            if (distance < bestDistance) {
                bestDistance = distance
                best = target
            }
        }
        return best
    }

    // ==================== 散布 ====================

    /**
     * 最终散布（度）——兼容 P1 的旧签名（不带耐久 / 连发增长）。
     *
     * 退化调用等价于「满耐久 + 第 1 发」，仅用于外部只读展示。
     */
    fun computeSpread(
        player: Player,
        gun: GunData,
        ammo: AmmoData,
        ads: Boolean,
        heatTier: Int,
    ): Double = computeSpread(player, gun, ammo, ads, heatTier, gun.attributes.durability, 0)

    /**
     * 最终散布（度），P3 完整版。
     *
     * ```
     * accuracy        = clamp(gun.accuracy + ammo.accuracy, 0..100)
     * spreadBase      = (100 - accuracy) * k / 100
     * barrelMult      = 1 - min(0.25, barrelLength * barrel-per-meter)
     * durabilitySpread= 1 + (1 - durabilityRatio) * 0.6      // 耐久见底散布 ×1.6
     * burstSpread     = min(1.5, 1 + shotIndex * 0.03)       // shotIndex = 已经打出的发数（停火 0.35s 归零）
     * situational     = 潜行 / 滞空 / 移动 / 受伤 各自倍率之积
     * heatSpread      = 热量档位系数（1.0 / 1.15 / 1.35 / 1.70）
     * adsMult         = scope.enabled && ADS ? spread.ads : 1.0
     * 结果            = spreadBase * barrelMult * durabilitySpread * burstSpread * situational
     *                 * heatSpread * adsMult        → clamp(0..45)
     * ```
     *
     * @param durability 当前耐久（用于耐久削弱）
     * @param shotIndex 本次开火**之前**已经打出的发数（0 表示这是第一发）
     */
    fun computeSpread(
        player: Player,
        gun: GunData,
        ammo: AmmoData,
        ads: Boolean,
        heatTier: Int,
        durability: Double,
        shotIndex: Int,
    ): Double = computeSpread(player, gun, ammo, ads, heatTier, durability, shotIndex, 1.0)

    /** P4：ADS 时额外按瞄具倍率收窄，旧签名保持 zoom=1。 */
    fun computeSpread(
        player: Player,
        gun: GunData,
        ammo: AmmoData,
        ads: Boolean,
        heatTier: Int,
        durability: Double,
        shotIndex: Int,
        zoom: Double,
    ): Double {
        val cfg = GunSettings.spread
        val accuracy = ((gun.attributes.accuracy + ammo.accuracy) * (1.0 - io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager.armPenalties(player).accuracy)).coerceIn(0.0, 100.0)
        val spreadBase = (100.0 - accuracy) * cfg.k / 100.0
        val barrelMult = 1.0 - minOf(0.25, gun.attributes.barrelLength * cfg.barrelPerMeter)

        var situational = 1.0
        if (player.isSneaking) situational *= cfg.sneaking
        if (!player.isOnGround) situational *= cfg.air
        if (isMoving(player)) situational *= cfg.moving
        if (isHurt(player)) situational *= cfg.hurt

        val durabilitySpread = 1.0 + (1.0 - durabilityRatio(gun, durability)) * 0.6
        val burstSpread = (1.0 + shotIndex.coerceAtLeast(0) * 0.03).coerceAtMost(1.5)
        // ADS 收窄由 scope.enabled 控制（P3 只读开关，不做 FOV / 热成像）
        val adsMult = if (ads && GunSettings.scope.enabled) {
            cfg.ads / zoom.coerceIn(1.0, 8.0)
        } else 1.0

        val total = spreadBase * barrelMult * durabilitySpread * burstSpread *
            situational * GunSettings.heatSpreadMultiplier(heatTier) * adsMult
        return total.coerceIn(0.0, 45.0)
    }

    /** 耐久比（0..1）；`attributes.durability <= 0` 时视为满耐久（没有耐久概念的枪不该被扣散布）。 */
    fun durabilityRatio(gun: GunData, durability: Double): Double {
        val max = gun.attributes.durability
        if (max <= 0.0) return 1.0
        return (durability / max).coerceIn(0.0, 1.0)
    }

    /** 耐久对弹速的系数：`0.85 + 0.15 * ratio`（满耐久 1.0，见底 0.85）。 */
    fun bulletSpeedMultiplier(durabilityRatio: Double): Double =
        0.85 + 0.15 * durabilityRatio.coerceIn(0.0, 1.0)

    /** 水平速度阈值判断「移动中」（客户端 velocity 在无输入时会衰减到 0，阈值取 0.05）。 */
    private fun isMoving(player: Player): Boolean {
        val velocity = player.velocity
        return abs(velocity.x) > 0.05 || abs(velocity.z) > 0.05
    }

    /**
     * 「刚受伤」：原版受伤后 `noDamageTicks` 会从 20 递减，> 0 即最近 1 秒内被打过。
     * 再叠加「血量不满」过滤，避免自然回血期间被误判。
     */
    private fun isHurt(player: Player): Boolean =
        player.noDamageTicks > 0 && player.health < player.maxHealth - 0.5

    /**
     * 把单位方向向量散到一个球冠内（圆锥均匀采样）。
     */
    private fun spreadDirection(direction: Vector, degrees: Double): Vector {
        val forward = direction.clone().normalize()
        if (degrees <= 1e-4) return forward

        val tan = tan(Math.toRadians(degrees.coerceIn(0.0, 45.0)))
        val theta = Random.nextDouble() * Math.PI * 2.0
        val radius = sqrt(Random.nextDouble()) * tan

        val up = if (abs(forward.y) > 0.99) Vector(1.0, 0.0, 0.0) else Vector(0.0, 1.0, 0.0)
        val right = forward.clone().crossProduct(up).normalize()
        val realUp = right.clone().crossProduct(forward).normalize()

        val result = forward.clone()
            .add(right.multiply(cos(theta) * radius))
            .add(realUp.multiply(sin(theta) * radius))
        return if (result.lengthSquared() < 1e-9) forward else result.normalize()
    }

    // ==================== 后坐 ====================

    /**
     * 后坐曲线采样（P3 核心）：**关键帧线性插值**。
     *
     * - 键按发数升序排列；
     * - `shotIndex` 正好落在某个键上 → 取该键的值；
     * - 落在两键之间 → 线性插值（`v0 + (v1 - v0) * (n - k0) / (k1 - k0)`）；
     * - 小于首键 → 取首键值；大于末键 → 取末键值；
     * - 空曲线 → 返回 1.0（中性系数）。
     *
     * 约定：`recoilPattern` 的键从 1 开始（第 1 发），`shakePattern` 的键从 0 开始
     * （0 = 未开火时的基准），调用方按各自语义传入 `shotIndex` 即可，本函数不做 ±1 修正。
     */
    fun samplePattern(pattern: Map<Int, Double>, shotIndex: Int): Double {
        if (pattern.isEmpty()) return 1.0
        val keys = pattern.keys.sorted()
        val first = keys.first()
        val last = keys.last()
        if (shotIndex <= first) return pattern[first] ?: 1.0
        if (shotIndex >= last) return pattern[last] ?: 1.0
        // 找到夹住 shotIndex 的相邻两键
        for (index in 0 until keys.size - 1) {
            val low = keys[index]
            val high = keys[index + 1]
            if (shotIndex == low) return pattern[low] ?: 1.0
            if (shotIndex in low..high) {
                val vLow = pattern[low] ?: return 1.0
                val vHigh = pattern[high] ?: return vLow
                if (high == low) return vLow
                val t = (shotIndex - low).toDouble() / (high - low).toDouble()
                return vLow + (vHigh - vLow) * t
            }
        }
        return pattern[last] ?: 1.0
    }

    /**
     * 把这一发的后坐冲量累加到玩家状态上（视角由 [GunStateManager] 的 tick 循环施加）。
     *
     * ```
     * n       = 本次开火前的 shotIndex + 1（第几发，1 起）
     * kickP   = recoilV * samplePattern(recoilPattern, n)
     *           * (ammo.recoil/100) * speed * 0.1 * (ADS?0.7:1) * (1 + (1-耐久比)*0.3)
     * kickYaw = recoilH * samplePattern(shakePattern, shotIndex)
     *           * (ammo.recoil/100) * speed * 0.1 * (ADS?0.7:1) * (1 + (1-耐久比)*0.3)
     * ```
     * `kickYaw` 的符号即方向（**正 = 右、负 = 左**，`shakePattern` 里直接写负数就是往左飘）。
     * 累加后对 `recoilPitch/Yaw` 与两个速度都做 `±maxAccumulation` 夹取。
     */
    private fun applyRecoil(
        gun: GunData,
        ammo: AmmoData,
        state: PlayerGunState,
        durabilityRatio: Double,
    ) {
        val cfg = GunSettings.recoil
        val index = state.shotIndex + 1
        val patternV = samplePattern(gun.recoilPattern, index)
        // shakePattern 从第 0 发开始计，直接原样用 shotIndex；空曲线 = 没有水平后坐
        val patternY = if (gun.shakePattern.isEmpty()) 0.0 else samplePattern(gun.shakePattern, state.shotIndex)
        val ammoMult = (ammo.recoil / 100.0).coerceIn(0.0, 5.0)
        val adsMult = if (state.ads) 0.7 else 1.0
        // P3：枪越旧后坐越大（最多 +30%）
        val wearMult = 1.0 + (1.0 - durabilityRatio.coerceIn(0.0, 1.0)) * 0.3
        val scale = cfg.speed * 0.1 * ammoMult * adsMult * wearMult

        state.recoilVelP += gun.attributes.recoilV * patternV * scale
        state.recoilVelY += gun.attributes.recoilH * patternY * scale

        // 兜底：速度与累积偏移都不允许无限大
        val cap = cfg.maxAccumulation.coerceAtLeast(0.0)
        state.recoilVelP = state.recoilVelP.coerceIn(-cap, cap)
        state.recoilVelY = state.recoilVelY.coerceIn(-cap, cap)
        state.recoilPitch = state.recoilPitch.coerceIn(-cap, cap)
        state.recoilYaw = state.recoilYaw.coerceIn(-cap, cap)
    }

    // ==================== 卡壳 ====================

    /**
     * 卡壳率：
     * ```
     * jamChance = gun.jamChance
     *           * (1 + (1 - durability/maxDurability) * 2)   // 耐久磨损
     *           * (ammo.stuckChance/100 * 0.5 + 0.5)         // 子弹质量
     *           * heatJamMultiplier(tier)                    // 热量档位
     * ```
     * 单位是百分比（`Random.nextDouble() * 100 < jamChance` 判定）。
     * 判定命中后由 [tryShoot] 再 roll 一次 [SEVERE_JAM_RATIO]（5%）决定是 `jam = 1` 还是 `jam = 2`。
     */
    fun computeJamChance(gun: GunData, ammo: AmmoData, durability: Double, heatTier: Int): Double {
        val wearFactor = 1.0 + (1.0 - durabilityRatio(gun, durability)) * 2.0
        val ammoFactor = (ammo.stuckChance / 100.0 * 0.5 + 0.5).coerceAtLeast(0.0)
        return (gun.jamChance * wearFactor * ammoFactor * GunSettings.heatJamMultiplier(heatTier))
            .coerceIn(0.0, 100.0)
    }

    // ==================== 常量 ====================

    /** 卡壳命中后升级为「严重故障（jam=2）」的概率。 */
    private const val SEVERE_JAM_RATIO = 0.05

    /** 近战视线锥的余弦阈值（`cos(35°) ≈ 0.819`）。 */
    private const val MELEE_CONE_DOT = 0.82

    // ==================== 工具 ====================

    private fun playSound(player: Player, key: String, volume: Float, pitch: Float) {
        try {
            player.world.playSound(player.location, key, volume, pitch)
        } catch (_: Exception) {
        }
    }

    private fun sendActionBar(player: Player, message: String) {
        try {
            GunPrompt.show(player, message)
        } catch (_: Exception) {
        }
    }

    private fun percent(value: Double, capacity: Double): Int {
        if (capacity <= 0.0) return 0
        return ((value / capacity) * 100.0).roundToInt().coerceIn(0, 999)
    }

    private fun fmt(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    private fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)
}

package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * 单个玩家的枪械运行时状态（只在内存里，不落盘）。
 *
 * - [drawing] / [drawTicks]：举枪进度，`drawTicks <= 0` 才算举枪完成
 * - [ads] / [adsTicks]：瞄准状态与进入 ADS 后的 tick 数
 * - [lastShotTick]：上一发的世界 tick（射速闸门用）
 * - [shotIndex]：连续射击序号（后坐/抖动曲线用，停火 `reset-seconds` 后归零）
 * - [lastShotAt]：上一发的世界 tick（判断是否停火 + P3 的「延迟回中」）
 * - [recoilPitch] / [recoilYaw]：**当前尚未回中的后坐偏移**（度，正 pitch = 视角上抬）
 * - [recoilVelP] / [recoilVelY]：后坐速度（度/tick）
 * - [jammedUntil]：卡壳结束时间戳（毫秒，0=没卡）
 * - [forcedStopUntil]：强制停火截止时间戳（毫秒，例如严重过热）
 * - [heatLocked]：**P3** 热量触顶后的锁定闩：`heat >= capacity` 置位，
 *   冷却到 `capacity * unlock-ratio` 以下才复位（不是单纯比较，否则降到 99% 就能继续打）
 * - [meleeLastTick]：**P3** 损坏近战上一次命中的世界 tick（冷却闸门；0=从未命中）
 */
/** 每 tick 只读的手持枪识别快照；ItemStack 仅供读取，不能在缓存副本上另写 NBT。 */
data class HeldGunSnapshot(
    val item: ItemStack,
    val gun: GunData,
)

data class PlayerGunState(
    var drawing: Boolean = false,
    var drawTicks: Int = 0,
    /** 本次持枪是否已经走过一次出枪读条（防止 tick 与切栏事件各扣一次体力）。 */
    var drawApplied: Boolean = false,
    var ads: Boolean = false,
    var adsTicks: Int = 0,
    /** 按住右键的续期截止时间戳（毫秒）；超时视为松手，自动退出瞄准。 */
    var adsHoldUntilMs: Long = 0L,
    var lastShotTick: Long = 0L,
    /**
     * 上一发成功的**墙钟时间**（毫秒）。
     *
     * 射速、停火重置、后坐延迟回中、模型 SHOOT 残留全都用这个，**不用 `world.fullTime`**：
     * 服务器 `/tick freeze`（或某些 fork / 冻结世界的插件）会让 `fullTime` 完全不走，
     * 用世界 tick 做冷却的闸门就永远解不开 —— 表现正是「只能打一发，切枪才能再打一发」。
     */
    var lastShotMs: Long = 0L,
    var shotIndex: Int = 0,
    var lastShotAt: Long = 0L,
    var recoilPitch: Double = 0.0,
    var recoilYaw: Double = 0.0,
    var recoilVelP: Double = 0.0,
    var recoilVelY: Double = 0.0,
    var jammedUntil: Long = 0L,
    var forcedStopUntil: Long = 0L,
    var heatLocked: Boolean = false,
    var meleeLastTick: Long = 0L,
    /** 枪托近战上次命中的**墙钟时间**（毫秒）；理由同 [lastShotMs]。 */
    var meleeLastMs: Long = 0L,
    /** 点射剩余尝试发数；每发仍走完整射击闸门。 */
    var burstRemaining: Int = 0,
    /** 自动射击只由持续的左键信号续期，信号消失后短暂停止。 */
    var triggerUntilMs: Long = 0L,
    var autoFiring: Boolean = false,
    /**
     * 上一次开火被拒的原因（`draw / busy / forced-stop / jam / heat-lock / heat-tier / rate / stamina /
     * ammo / caliber / write`，成功时清空为 `fired`）。
     *
     * 「只能打一发」「按了没反应」这类问题，靠它一眼定位是哪道闸门 ——
     * 之前所有闸门都是静默 return，只能靠猜。
     */
    var lastShotResult: String = "",
    /** 上一次开火被拒时的世界 tick（用来判断「现在是不是还在冷却」）。 */
    var lastShotResultTick: Long = 0L,
)

/**
 * 枪械全局 1 tick 循环 + 玩家状态表。
 *
 * 这个类是**唯一**允许主线程驱动枪械运行时的地方：
 * 后坐回中、举枪/ADS 计时、射击序号归零、热量冷却、曳光与弹道推进、HUD 刷新
 * 全都挂在 [submit] 的 `period = 1, async = false` 任务里，所有 Bukkit API 调用都在主线程。
 *
 * ### 热量为什么需要内存镜像
 * 需求要求「每 tick `heat -= coolPerTick`，但只在跨整数/跨档位时落盘」。
 * 如果每 tick 都从 NBT 重新读，未落盘的亚整数进度会丢，热量会一直卡住不降；
 * 所以这里用 [heatMirror] 保存「已冷却到但还没落盘」的热量，
 * 每隔 `1.0 / coolPerTick` tick 才真正写一次物品（热量落在整数边界）。
 */
object GunStateManager {

    private val states = ConcurrentHashMap<UUID, PlayerGunState>()

    /** 每 tick 的手持识别缓存；非枪械直接移除键，避免体力/HUD 再调用 Sertraline getId。 */
    private val heldGunCache = ConcurrentHashMap<UUID, HeldGunSnapshot>()

    /** Current held weapon identity, independent of whether a heat mirror exists. */
    private data class HeldGunIdentity(val gunId: String, val instanceId: String)
    private val heldGunIdentity = ConcurrentHashMap<UUID, HeldGunIdentity>()

    /** 热量镜像绑定物品实例；同槽换成同 ID 的另一把枪也不能继承。 */
    private data class HeatMirror(val gunId: String, val instanceId: String, val heat: Double)

    /** 内存里的热量镜像（uuid → 已冷却但可能尚未落盘的热量）。 */
    private val heatMirror = ConcurrentHashMap<UUID, HeatMirror>()

    /**
     * 上一次观测到的热量档位（uuid → 0..3）。
     *
     * P3 的「档位跳变提示 / 严重档强制停火」需要一个 before/after 对比，
     * 而热量在两个地方变化（开火 [noteHeat] 与冷却 [tickHeat]），
     * 所以档位镜像集中放在这里，两条路径都调 [applyHeatSnapshot]。
     */
    private val heatTierMirror = ConcurrentHashMap<UUID, Int>()

    private var task: PlatformExecutor.PlatformTask? = null

    private var tickCounter: Long = 0L

    /** HUD 刷新间隔（tick）。 */
    private const val HUD_INTERVAL = 5L

    /**
     * 单 tick 允许施加的最大后坐视角变化（度）。
     *
     * 正常一发的 kick 远小于这个值（`recoilV 10 × 系数 1 × speed 0.35 × 0.1 ≈ 0.35°/tick`），
     * 这个上限只是防「状态被改坏 / 切枪残留」时一帧把镜头甩出去。
     */
    private const val MAX_STEP_DEGREES = 4.0

    // ==================== 生命周期 ====================

    /** 启动 1 tick 循环（重复调用是安全的）。 */
    fun start() {
        if (task != null) return
        task = submit(period = 1, async = false) { tick() }
    }

    /** 停止循环并清空所有玩家状态（关服 / 卸载插件时调用）。 */
    fun stop() {
        task?.cancel()
        task = null
        // 清表前逐玩家还原后坐与 ADS，避免卸载后视角永久停在后坐位置。
        try {
            for (player in Bukkit.getOnlinePlayers()) {
                val state = states[player.uniqueId]
                try {
                    if (state != null) applyViewDelta(player, -state.recoilYaw, state.recoilPitch)
                    GunAim.reset(player)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        states.clear()
        heldGunCache.clear()
        heldGunIdentity.clear()
        heatMirror.clear()
        heatTierMirror.clear()
        ThermalManager.clearAll()
        // 读条动作（换弹 / 压弹 / 检视……）也必须一起停，否则关服时还在往物品写 NBT
        GunAction.cancelAll()
        GunDialogSessions.clear()
    }

    /** 取（必要时创建）玩家状态。 */
    fun state(player: Player): PlayerGunState =
        states.getOrPut(player.uniqueId) { PlayerGunState() }

    /** 只取已存在的状态（不存在返回 null，用于不想创建状态的只读路径）。 */
    fun existing(player: Player): PlayerGunState? = states[player.uniqueId]

    /**
     * 清空玩家状态（切枪 / 死亡 / 退出 / 换世界）。
     *
     * 会先把手部残留的后坐偏移「反向应用」回视角，避免人走枪留、视角停在天上。
     */
    fun clear(player: Player) {
        // 读条动作（换弹 / 压弹 / 卸弹匣……）不能跨状态清理继续结算：
        // 切枪 / 死亡 / 退出 / 换世界都必须把它掐掉，否则会往已经换掉的物品上写 NBT。
        GunAction.cancel(player)
        // 「我们自己的对话框开着」的登记也属于「这次持枪期间」，跟着状态一起作废。
        GunDialogSessions.forget(player)
        GunShoot.resetTrigger(player)
        ThermalManager.clear(player)
        val state = states.remove(player.uniqueId)
        heldGunCache.remove(player.uniqueId)
        heldGunIdentity.remove(player.uniqueId)
        heatMirror.remove(player.uniqueId)
        heatTierMirror.remove(player.uniqueId)
        try {
            if (player.isOnline) {
                if (state != null) {
                    applyViewDelta(player, -state.recoilYaw, state.recoilPitch)
                }
                GunAim.reset(player)
            }
        } catch (_: Exception) {
        }
    }

    /** 清除全部玩家的状态（重载 / 关服用）。 */
    fun clearAll() {
        for (uuid in ArrayList(states.keys)) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null) clear(player) else states.remove(uuid)
        }
        states.clear()
        heldGunCache.clear()
        heldGunIdentity.clear()
        heatMirror.clear()
        heatTierMirror.clear()
        ThermalManager.clearAll()
    }

    // ==================== 对外查询 ====================

    /** HUD 与开火后热量同步共用的只读手持快照；尚未经过首轮 tick 时返回 null。 */
    fun heldGunSnapshot(player: Player): HeldGunSnapshot? = heldGunCache[player.uniqueId]

    /**
     * 手持枪械且 `stamina.regen-pause-when-armed: true` 时，手部体力**暂停自然恢复**。
     *
     * 由 `listener/onFoodTimer.kt` 每 tick 调用。
     */
    fun shouldPauseHandStaminaRegen(player: Player): Boolean {
        if (!GunSettings.stamina.regPause) return false
        return heldGunCache[player.uniqueId] != null
    }

    /**
     * 通知「物品上的热量刚刚被外部改动」（[GunShoot] 每发都会写一次），
     * 让内存镜像跟上，避免下一次冷却把刚打出去的热量又降回去。
     *
     * P3 追加：这里同时做档位跳变处理（严重档强制停火 / 档位提示 / 热量上限锁定闩）。
     */
    /** Returns the freshest heat value, including sub-integer cooling not yet persisted to NBT. */
    fun currentHeat(player: Player, gun: GunData, state: GunState): Double {
        val mirror = heatMirror[player.uniqueId]
        return if (mirror != null && mirror.gunId == gun.id && mirror.instanceId == state.instanceId) {
            mirror.heat
        } else {
            state.heat
        }
    }

    fun noteHeat(player: Player, heat: Double) {
        val uuid = player.uniqueId
        val gun = heldGunSnapshot(player)?.gun
        val instanceId = GunInstance.read(player.inventory.itemInMainHand)?.instanceId.orEmpty()
        if (heat <= 0.0 || gun == null || instanceId.isEmpty()) {
            heatMirror.remove(uuid)
        } else {
            val mirror = heatMirror[uuid]
            if (mirror?.gunId != gun.id || mirror.instanceId != instanceId) {
                val state = state(player)
                state.forcedStopUntil = 0L
                state.heatLocked = false
                heatTierMirror.remove(uuid)
            }
            heatMirror[uuid] = HeatMirror(gun.id, instanceId, heat)
        }
        val capacity = if (gun != null && gun.heatCapacity > 0.0) {
            gun.heatCapacity
        } else {
            GunSettings.heat.capacity
        }
        applyHeatSnapshot(player, state(player), heat, capacity)
    }

    /**
     * 热量快照的统一处理（P3）：档位跳变提示 + 严重档强制停火 + 上限锁定闩。
     *
     * - `heat >= capacity` → [PlayerGunState.heatLocked] 置位；
     *   只有降到 `capacity * unlock-ratio` 以下才复位（见 [GunShoot] 的开火闸门）。
     * - 档位**升高**：ActionBar `热量：过热` + 枪口冒烟；升到 3 档时额外
     *   `forcedStopUntil = now + severe-stop-ms`（毫秒，与 P1 字段单位一致）+ 提示。
     * - 档位**降到安全档**：提示「枪管已冷却」。
     *
     * 单位约定：[PlayerGunState.forcedStopUntil] 是 `System.currentTimeMillis()` 基准的**毫秒**时间戳，
     * 所以 `severe-stop-ms` 直接按毫秒相加，不做 tick 换算。
     */
    private fun applyHeatSnapshot(player: Player, state: PlayerGunState, heat: Double, capacity: Double) {
        val cap = capacity.coerceAtLeast(1.0)
        // 上限锁定闩
        if (heat >= cap - 1e-6) {
            state.heatLocked = true
        } else if (heat < cap * GunSettings.heat.unlockRatio) {
            state.heatLocked = false
        }

        val tierBefore = heatTierMirror[player.uniqueId]
        val tierAfter = GunSettings.heatTier(heat)
        if (tierBefore == null) {
            // 首次观测（刚上线 / 刚切到一把已经在发热的枪）：只记录，不弹提示、不触发强制停火，
            // 避免玩家一进服就被「热量：过热」刷一脸。
            heatTierMirror[player.uniqueId] = tierAfter
            return
        }
        if (tierAfter == tierBefore) return
        heatTierMirror[player.uniqueId] = tierAfter

        if (tierAfter > tierBefore) {
            if (tierAfter >= 3) {
                val stopMs = GunSettings.heat.severeStopMs.coerceAtLeast(0L)
                state.forcedStopUntil = System.currentTimeMillis() + stopMs
                sendActionBar(
                    player,
                    "<#E08A98>枪管严重过热，强制停火 <#A9B4C3>| <#EFDF8F>冷却 " +
                        "${fmt1(stopMs / 1000.0)}s"
                )
                smokeAtMuzzle(player, 10)
            } else {
                sendActionBar(player, "<#DFBD7C>热量：${GunSettings.heatTierText(tierAfter)}")
                smokeAtMuzzle(player, 3)
            }
        } else if (tierAfter == 0) {
            sendActionBar(player, "<#8FD4A6>枪管已冷却")
        }
    }

    /** 枪口冒烟（档位升高时的视觉反馈；拿不到枪口就用眼睛位置兜底）。 */
    private fun smokeAtMuzzle(player: Player, count: Int) {
        if (count <= 0) return
        try {
            val origin = player.eyeLocation.clone().add(player.eyeLocation.direction.clone().multiply(0.6))
            player.world.spawnParticle(Particle.SMOKE, origin, count, 0.06, 0.06, 0.06)
        } catch (_: Exception) {
        }
    }

    // ==================== 1 tick 循环 ====================

    private fun tick() {
        tickCounter++

        // 弹道 / 曳光（独立于玩家状态，先推进）
        GunBallistics.tick()

        val updateHud = tickCounter % HUD_INTERVAL == 0L

        for (player in Bukkit.getOnlinePlayers()) {
            try {
                val held = player.inventory.itemInMainHand
                val gun = GunRegistry.gunOf(held)
                if (gun == null) {
                    heldGunCache.remove(player.uniqueId)
                    heldGunIdentity.remove(player.uniqueId)
                } else {
                    heldGunCache[player.uniqueId] = HeldGunSnapshot(held, gun)
                    val instanceId = GunInstance.readEnsured(held, gun)?.instanceId.orEmpty()
                    val identity = HeldGunIdentity(gun.id, instanceId)
                    val previous = heldGunIdentity.put(player.uniqueId, identity)
                    if (previous != identity) {
                        if (previous != null) {
                            GunShoot.resetTrigger(player)
                            heatMirror.remove(player.uniqueId)
                            heatTierMirror.remove(player.uniqueId)
                            state(player).forcedStopUntil = 0L
                            state(player).heatLocked = false
                        }
                    }
                }
                // ADS 与护甲移速共用 GunAim 的唯一写入口；每秒刷新以跟随穿脱和耐久变化。
                if (tickCounter % 20L == 0L) {
                    GunAim.refreshWalkSpeed(player)
                    // 基地 / 安全世界 / 创造观察者里手部体力恒满：每秒兜一次底，
                    // 不依赖 1 秒一次的体力 tick（它也跳过创造/观察者），进基地立刻显示满值。
                    io.github.zzzyyylllty.chotentech.data.HandStamina.refillIfUnlimited(player)
                }
                val state = if (gun != null) state(player) else states[player.uniqueId] ?: continue

                // 1) 后坐回中并施加到视角
                updateRecoil(player, state)

                // 2) 出枪 / 瞄准计时
                if (state.drawTicks > 0) {
                    state.drawTicks--
                    if (state.drawTicks <= 0) state.drawing = false
                } else if (!state.drawing && !state.drawApplied && gun != null) {
                    // 首次持枪（上线 / 登录 / 物品被插件直接塞到手上）也要走一遍出枪读条，
                    // 否则「出枪时间」只在切栏时生效，登录后可以立刻开火。
                    if (GunAim.beginDraw(player)) {
                        state.drawApplied = true
                    }
                }
                if (state.adsTicks < Int.MAX_VALUE) state.adsTicks++
                // 按住右键才算瞄准：松手后超时自动退出（客户端不再续期）
                if (state.ads) GunAim.tickHold(player)
                ThermalManager.tick(player, state, held, gun != null)

                // 3) 停火一段时间后射击序号归零（后坐曲线重新开始）
                //    用墙钟：世界 tick 可能被 `/tick freeze` 冻住，用 tick 判「停火」会永远不归零。
                //    窗口还要 ≥ 两倍射速间隔，否则射速慢的枪每发都算「重新开始」，
                //    recoil/shake 的多关键帧曲线永远只用到第一帧（手感是平的）。
                if (state.shotIndex > 0) {
                    val resetMs = GunShoot.effectiveResetMs(gun)
                    val since = System.currentTimeMillis() - state.lastShotMs
                    if (state.lastShotMs > 0L && since > resetMs) {
                        state.shotIndex = 0
                    }
                }

                // 4) 热量冷却（只在跨整数 / 跨档位时落盘）
                if (gun != null && !held.type.isAir) {
                    tickHeat(player, gun, held, state)
                    tickHoldStamina(player, state)
                    // 5) 多状态模型（瞄准 / 开火 / 卡壳 / 前方被挡收枪）：内部只在状态变化时写物品
                    GunModel.tick(player, GunInstance.read(held))
                } else {
                    heatMirror.remove(player.uniqueId)
                    heatTierMirror.remove(player.uniqueId)
                    GunModel.forget(player)
                }
                // 先完成枪械实例与热量校验，再推进持续射击，避免同槽换枪后多打一 tick。
                if (gun != null) GunShoot.tickFire(player, held, gun) else GunShoot.resetTrigger(player)

                // 5) HUD
                if (updateHud) {
                    GunHud.update(player)
                    // 耐久写进物品名称：按 HUD 间隔兜底刷新（内部做变更检测，没变就不写物品）
                    if (gun != null) {
                        val instance = GunInstance.read(held)
                        if (instance != null) GunNameplate.refreshHeld(player, held, gun, instance)
                    }
                }
            } catch (_: Exception) {
                // 单个玩家出错不能打断整个循环
            }
        }
    }

    // ==================== 后坐 ====================

    /**
     * 后坐回中。
     *
     * 每 tick：
     * ```
     * velP  *= (1 - damping)
     * pitch += velP
     * pitch *= (1 - recovery)          // ADS 时用 recovery-ads；「延迟回中」窗口内跳过这一步
     * ```
     * 然后把 `pitch/yaw` 的**增量**施加到玩家视角（`setRotation(yaw + dYaw, pitch - dPitch)`）。
     * 用增量而不是绝对值，是为了让「玩家自己转过的视角」不被每 tick 覆盖回去；
     * 增量之和在完全回中时为 0，所以准星最终会回到开火前的落点。
     *
     * ### P3：延迟回中（`recoil.recovery-delay`）
     * 最后一发之后的 `recovery-delay` 秒内**只衰减速度、不回中**（塔科夫 / COD 手感：
     * 枪先「顶住」一下再落回来）。判定用世界 tick 与 `lastShotAt` 的差值，
     * 停火超过 `recoil.reset-seconds` 时 `shotIndex` 已归零，此时整段后坐会自然回中。
     */
    private fun updateRecoil(player: Player, state: PlayerGunState) {
        val oldPitch = state.recoilPitch
        val oldYaw = state.recoilYaw
        val idle = abs(oldPitch) < 1e-4 && abs(oldYaw) < 1e-4 &&
            abs(state.recoilVelP) < 1e-4 && abs(state.recoilVelY) < 1e-4
        if (idle) return

        val damping = GunSettings.recoil.damping.coerceIn(0.0, 0.99)
        val recovery = (if (state.ads) GunSettings.recoil.recoveryAds else GunSettings.recoil.recovery)
            .coerceIn(0.0, 0.99)

        state.recoilVelP *= (1.0 - damping)
        state.recoilVelY *= (1.0 - damping)
        state.recoilPitch += state.recoilVelP
        state.recoilYaw += state.recoilVelY

        val cap = GunSettings.recoil.maxAccumulation.coerceAtLeast(0.0)
        state.recoilPitch = state.recoilPitch.coerceIn(-cap, cap)
        state.recoilYaw = state.recoilYaw.coerceIn(-cap, cap)

        if (!inRecoveryDelay(player, state)) {
            state.recoilPitch *= (1.0 - recovery)
            state.recoilYaw *= (1.0 - recovery)
        }

        val dPitch = (state.recoilPitch - oldPitch)
        val dYaw = (state.recoilYaw - oldYaw)
        if (abs(dPitch) < 0.001 && abs(dYaw) < 0.001) return
        // 单 tick 的最大施加量：状态被外部改坏（切枪 / 重连 / 数值异常）时也不会一帧甩镜头
        val stepPitch = dPitch.coerceIn(-MAX_STEP_DEGREES, MAX_STEP_DEGREES)
        val stepYaw = dYaw.coerceIn(-MAX_STEP_DEGREES, MAX_STEP_DEGREES)
        val actual = applyViewDelta(player, stepYaw, -stepPitch)
        // 只记录服务端真正施加的增量；pitch 撞到 ±90° 时被截掉的部分不回记。
        state.recoilYaw = oldYaw + actual.first
        state.recoilPitch = oldPitch - actual.second
    }

    /** 是否还处在「最后一发之后不许回中」的延迟窗口内（墙钟，理由同 [lastShotMs]）。 */
    private fun inRecoveryDelay(player: Player, state: PlayerGunState): Boolean {
        val delayMs = (GunSettings.recoil.recoveryDelay * 1000.0).toLong()
        if (delayMs <= 0L) return false
        if (state.shotIndex <= 0) return false
        if (state.lastShotMs <= 0L) return false
        val since = System.currentTimeMillis() - state.lastShotMs
        return since in 0 until delayMs
    }

    /**
     * 把视角偏移增量施加到玩家（主线程）。
     *
     * `dPitch` 传的是「pitch 增量」：正数 = 视角下压。开火时后坐让 `recoilPitch` 增大，
     * 所以要传 `-dPitch`（视角上抬）。异常一律吞掉（旧版本 / 特殊载具上 setRotation 可能失败）。
     *
     * ### 为什么**不再回读** `player.location`
     * 旧实现在 `setRotation` 之后再读一次位置、用「读回的差值」当实际施加量。
     * 但玩家的 rotation 是**客户端权威**的：两次读之间如果客户端发了新的朝向包，
     * 读回来的就是「客户端自己转了多少」，被误算成我们施加的量，然后写回 `recoilPitch` ——
     * 于是后坐偏移被反复拉成负数 / 正数，镜头每 tick 在两点之间来回弹（玩家说的「视角一直抽动」）。
     *
     * 现在只按「我们算出来的目标值」结算：目标 = 之前的服务端值 + 增量（pitch 夹到 ±90），
     * 实际施加量 = 目标 − 之前的服务端值。全程不碰客户端的值，状态和施加量永远一致。
     */
    private fun applyViewDelta(player: Player, dYaw: Double, dPitch: Double): Pair<Double, Double> {
        return try {
            val before = player.location
            val oldYaw = before.yaw
            val oldPitch = before.pitch
            val yaw = (oldYaw + dYaw).toFloat()
            val pitch = (oldPitch + dPitch).toFloat().coerceIn(-90.0f, 90.0f)
            player.setRotation(yaw, pitch)
            // 被 ±90° 夹掉的部分不算「已施加」，其余按请求量结算
            val appliedPitch = (pitch - oldPitch).toDouble()
            val appliedYaw = ((yaw - oldYaw + 540.0f) % 360.0f - 180.0f).toDouble()
            appliedYaw to appliedPitch
        } catch (_: Throwable) {
            0.0 to 0.0
        }
    }

    // ==================== 热量 ====================

    private fun tickHeat(player: Player, gun: GunData, held: ItemStack, state: PlayerGunState) {
        val uuid = player.uniqueId
        val capacity = if (gun.heatCapacity > 0.0) gun.heatCapacity else GunSettings.heat.capacity
        val mirror = heatMirror[uuid]
        val storedState = GunInstance.readEnsured(held, gun) ?: return
        val sameInstance = mirror?.gunId == gun.id && mirror.instanceId == storedState.instanceId
        val externalHeatChanged = mirror != null && kotlin.math.abs(storedState.heat - mirror.heat) > 1e-6
        val current = if (sameInstance && !externalHeatChanged) {
            mirror.heat
        } else {
            // 同槽、同 ID 的不同物品也通过 instanceId 隔离，不能带入旧枪热量和扳机状态。
            if (mirror != null) {
                state.forcedStopUntil = 0L
                state.heatLocked = false
                heatTierMirror.remove(uuid)
                GunShoot.resetTrigger(player)
            }
            storedState.heat
        }
        if (current <= 0.0) {
            heatMirror[uuid] = HeatMirror(gun.id, storedState.instanceId, 0.0)
            // 冷到位：复位锁定闩与档位镜像，避免「档位停在 0 但闩还锁着」
            applyHeatSnapshot(player, state, 0.0, capacity)
            return
        }
        val gunCool = gun.heatCoolPerTick
        val cool = if (gunCool > 0.0) gunCool else GunSettings.heat.coolPerTick
        if (cool <= 0.0) return

        val tierBefore = GunSettings.heatTier(current)
        val next = (current - cool).coerceAtLeast(0.0)
        heatMirror[uuid] = HeatMirror(gun.id, storedState.instanceId, next)

        // P3：档位跳变（降温方向）/ 锁定闩复位都在这里处理
        applyHeatSnapshot(player, state, next, capacity)

        val crossInteger = floor(next) != floor(current)
        val crossTier = GunSettings.heatTier(next) != tierBefore
        if (!crossInteger && !crossTier) return

        val stored = GunInstance.read(held) ?: return
        if (!GunInstance.write(held, stored.copy(heat = next))) {
            // 下 tick 从物品真实热量重建，避免内存镜像与 NBT 持续分叉。
            heatMirror.remove(uuid)
            return
        }
        try {
            player.inventory.setItemInMainHand(held)
        } catch (_: Exception) {
        }
    }

    // ==================== 手部体力（瞄准持续消耗） ====================

    private fun tickHoldStamina(player: Player, state: PlayerGunState) {
        if (!state.ads) return
        val perTick = GunSettings.stamina.holdCostPerSecond * GunSettings.stamina.adsMultiplier / 20.0 *
            (1.0 + io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager.armPenalties(player).aim)
        if (perTick <= 0.0) return
        val data = player.getData() ?: return
        if (data.handStamina <= 0.0) {
            // 体力耗尽自动退出瞄准
            GunAim.setAds(player, false)
            sendActionBar(player, "<#E08A98>手部体力耗尽，已退出瞄准")
            return
        }
        // 统一走 HandStamina.spend：瞄准期间每 tick 都在消耗，所以「最后一次消耗」会一直往后推，
        // 松手后自然要等满 stamina.regen-delay-seconds（默认 5 秒）才开始回体力。
        io.github.zzzyyylllty.chotentech.data.HandStamina.spend(player, data, perTick)
    }

    // ==================== 文本 ====================

    private fun sendActionBar(player: Player, message: String) {
        try {
            GunPrompt.show(player, message)
        } catch (_: Exception) {
        }
    }

    /** 秒数格式化（一位小数，固定 `Locale.US` 避免部分地区用逗号当小数点）。 */
    private fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)
}

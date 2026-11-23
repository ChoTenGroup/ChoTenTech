package io.github.zzzyyylllty.chotentech.feature.gun

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 瞄准时的**真实 FOV 缩放**（参考 WeaponMechanics 的做法）。
 *
 * ### 原理
 * 客户端的视野角由 `Abilities` 包里的 `fovModifier` 字段参与计算：
 * ```
 * fovMultiplier = (movementSpeed / fovModifier + 1) / 2      // movementSpeed 通常 0.1
 * ```
 * 因此想放大 `zoom` 倍（FOV 变成原来的 `1/zoom`），需要：
 * ```
 * fovModifier = movementSpeed / (2 / zoom - 1)
 * ```
 * `zoom = 2` 时分母为 0，此时发 `Infinity` —— 客户端理解这个值（与原版 `WeaponMechanics#getScope`
 * 的 `1.0 / (20.0 / level - 10.0)` 完全等价，`zoom = 4 → -0.2`，`zoom = 10 → -0.125`）。
 * 默认值 `0` 会被客户端当作「不缩放」。
 *
 * ### 两条写入路径
 * 1. [setZoom] / [refresh]：主动构造一个 Abilities 包发给客户端（立即生效）。
 * 2. [onPacketSend]：拦截**服务端自己**发出的 Abilities 包（切创造、开关飞行、改移速……），
 *    把 `fovModifier` 重新写成当前缩放值，避免瞄准中途被服务端包打回原状。
 *
 * 本类只改 `fovModifier`，**不动** `walkingSpeed` / 移速属性，因此缩放与移速解耦。
 */
object GunZoom : PacketListener {

    /** 缩放倍数上限，防止 `zoom` 配得过大导致画面完全黑屏。 */
    private const val MAX_ZOOM = 20.0

    /**
     * 上次下发的 `fovModifier`（uuid → 值）。
     *
     * 用来做「值没变就不发包」的去重：`refresh` 每秒都会调一次，而每发一个 Abilities 包
     * 客户端都会重算视野，数值在临界点上来回抖时会变成肉眼可见的「视角抽动」。
     */
    private val lastSent = ConcurrentHashMap<UUID, Float>()

    /** 判定「值没变」的阈值。 */
    private const val FOV_EPSILON = 0.01f

    /** 1.0（含）以下视为「不缩放」。 */
    private const val NO_ZOOM = 1.0001

    /** uuid → 当前缩放倍数（>1 才生效）。 */
    private val zooming = ConcurrentHashMap<UUID, Double>()

    /**
     * uuid → 最近一次在主线程读到的 `MOVEMENT_SPEED`（FOV 公式的输入）。
     *
     * [onPacketSend] 跑在 netty 线程，**不能**在那里调 `Bukkit.getPlayer` / 读属性：
     * 属性随时可能被其它插件并发改写，跨线程读取会抛异常（又被空 catch 吞掉，表现为
     * 「这个包没生效、缩放被打回原状」）。改为主线程算好缓存、包线程只做算术。
     */
    private val cachedSpeed = ConcurrentHashMap<UUID, Double>()

    private var registeredListener: PacketListenerCommon? = null

    /** packetevents 是否可用；不可用时缩放降级为「不缩放」，其余枪械功能不受影响。 */
    private var available = false

    // ==================== 生命周期 ====================

    fun register() {
        if (registeredListener != null) return
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            devLog("[Gun] 未检测到 packetevents，瞄准缩放（FOV）不可用")
            return
        }
        try {
            registeredListener = this.asAbstract(PacketListenerPriority.NORMAL)
            PacketEvents.getAPI().eventManager.registerListener(registeredListener!!)
            available = true
        } catch (e: Throwable) {
            devLog("[Gun] 注册 FOV 缩放监听失败: ${e.message}")
            available = false
        }
    }

    fun shutdown() {
        registeredListener?.let {
            try {
                PacketEvents.getAPI().eventManager.unregisterListener(it)
            } catch (_: Throwable) {
            }
        }
        registeredListener = null
        available = false
        // 退出前把所有人的视野还原，避免关服时客户端停在被放大的状态
        for (uuid in ArrayList(zooming.keys)) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            zooming.remove(uuid)
            send(player, 0f)
        }
    }

    fun isAvailable(): Boolean = available

    /** 当前缩放倍数（1.0 = 未瞄准）。 */
    fun zoomOf(player: Player): Double = zooming[player.uniqueId] ?: 1.0

    fun isZooming(player: Player): Boolean = zoomOf(player) > NO_ZOOM

    // ==================== 写入 ====================

    /**
     * 设置缩放倍数并立即下发到客户端。
     *
     * @param zoom <= 1.0 等同于 [clear]
     */
    fun setZoom(player: Player, zoom: Double) {
        if (zoom <= NO_ZOOM) {
            clear(player)
            return
        }
        val clamped = zoom.coerceAtMost(MAX_ZOOM)
        zooming[player.uniqueId] = clamped
        send(player, modifierFor(player, clamped))
    }

    /** 退出缩放（把 `fovModifier` 写回 0 = 原版视野）。 */
    fun clear(player: Player) {
        cachedSpeed.remove(player.uniqueId)
        if (zooming.remove(player.uniqueId) == null) return
        // 退出必须强制发一次 0，不能被「值没变」的去重挡掉
        lastSent.remove(player.uniqueId)
        send(player, 0f)
        lastSent.remove(player.uniqueId)
    }

    /**
     * 按当前状态重新下发一次。
     *
     * 用在「移速属性 / 护甲 / 状态被外部改动」之后：FOV 公式里 movementSpeed 变过，
     * 补偿系数必须一起重算，否则缩放倍数会跟着漂。
     */
    fun refresh(player: Player) {
        val zoom = zooming[player.uniqueId] ?: return
        send(player, modifierFor(player, zoom))
    }

    // ==================== 计算 ====================

    /**
     * `zoom` → 客户端 `fovModifier`。
     *
     * `2 / zoom - 1 == 0`（即 `zoom == 2`）时返回 `Infinity`：客户端在这种极端值下等效于
     * 「除数无穷大」，正好对应 2 倍视野压缩。
     */
    private fun modifierFor(player: Player, zoom: Double): Float {
        val movementSpeed = try {
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.value ?: 0.1
        } catch (_: Throwable) {
            0.1
        }
        val speed = if (movementSpeed > 1e-6) movementSpeed else 0.1
        // 主线程顺手把移速缓存下来，供包线程使用（见 [cachedSpeed]）。
        cachedSpeed[player.uniqueId] = speed
        return modifierOf(speed, zoom)
    }

    /** 纯算术：`zoom` → `fovModifier`。包线程只允许调这个。 */
    private fun modifierOf(movementSpeed: Double, zoom: Double): Float {
        val speed = if (movementSpeed > 1e-6) movementSpeed else 0.1
        val divisor = 2.0 / zoom - 1.0
        val modifier = if (divisor == 0.0) Double.POSITIVE_INFINITY else speed / divisor
        return modifier.toFloat()
    }

    // ==================== 下发 ====================

    /** 构造一个完整的 Abilities 包发出去（其余字段与玩家当前状态一致）。 */
    private fun send(player: Player, fovModifier: Float) {
        if (!available) return
        // 值没变就不发包：`refresh` 每秒都会调一次，而 Abilities 包每发一次客户端都会重算视野，
        // 数值在临界点抖动时会表现成「视角/视野一直抽动」。
        val previous = lastSent[player.uniqueId]
        if (previous != null && abs(previous - fovModifier) <= FOV_EPSILON) return
        try {
            val wrapper = WrapperPlayServerPlayerAbilities(
                player.isInvulnerable,
                player.isFlying,
                player.allowFlight,
                player.gameMode == GameMode.CREATIVE,
                player.flySpeed,
                fovModifier,
            )
            PacketEvents.getAPI().playerManager.sendPacket(player, wrapper)
            lastSent[player.uniqueId] = fovModifier
        } catch (e: Throwable) {
            devLog("[Gun] 下发 FOV 失败: ${e.message}")
        }
    }

    // ==================== 拦截服务端 Abilities 包 ====================

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.PLAYER_ABILITIES) return
        val uuid = event.user.uuid ?: return
        val zoom = zooming[uuid] ?: return
        // 包线程：只用缓存值做算术，不碰 Bukkit / 属性 / 玩家对象。
        val speed = cachedSpeed[uuid] ?: return
        try {
            val wrapper = WrapperPlayServerPlayerAbilities(event)
            wrapper.fovModifier = modifierOf(speed, zoom)
            wrapper.write()
        } catch (_: Throwable) {
        }
    }
}

package io.github.zzzyyylllty.chotentech.feature.gun

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 左键按住/松开数据包入口。回调只采集信号，所有物品操作统一切回主线程。 */
object GunPacketInput : PacketListener {
    private var registered = false
    private var registeredListener: PacketListenerCommon? = null

    /**
     * 每 50ms（≈1 tick）允许转发的信号数上限。
     *
     * `ANIMATION` 是客户端可控的高频包（挥臂动画，原版里随手一动就发），
     * 而 [dispatch] 每次都要往主线程排一个任务。没有任何限流时，恶意客户端
     * 每 tick 发几百个包就等于往主线程塞几百个任务 —— 既是主线程 DoS，
     * 也可能被用来抢射速（[GunShoot.onPacketTrigger] 的冷却判断在任务里才做）。
     *
     * 正常玩家人手点击上限约 20 次/秒（= 1 次/tick），连点器也就 15~20/tick，
     * 这里给 8 是「正常操作碰不到、异常洪泛立刻被削」的折中。
     *
     * ⚠️ 时间基准用 [System.nanoTime] 而**不是** `Bukkit.getCurrentTick()`：
     * `onPacketReceive` 跑在 packetevents 的网络线程上，`getCurrentTick()` 并非
     * 线程安全 API，跨线程读可能拿到不一致的值。nanoTime 单调且与线程无关。
     */
    private const val SIGNAL_WINDOW_NANOS = 50_000_000L
    private const val SIGNALS_PER_WINDOW = 8

    /** uuid → 窗口起点（纳秒）。 */
    private val windowStart = ConcurrentHashMap<UUID, Long>()

    /** uuid → 本窗口已转发信号数。 */
    private val windowUsed = ConcurrentHashMap<UUID, Int>()

    /** 记录被丢弃的信号数，便于排查「是不是限流太紧」。 */
    private val dropped = ConcurrentHashMap<UUID, Long>()

    fun droppedCount(player: Player): Long = dropped[player.uniqueId] ?: 0L

    fun isRegistered(): Boolean = registered

    fun register() {
        if (registered) return
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            // 以前这里是静默 return：packetevents 是软依赖，比本插件晚加载（或首次重载后才启用）
            // 时 AUTO / BURST 会退化成单发，玩家和服主完全查不到原因。重载路径会再调一次。
            io.github.zzzyyylllty.chotentech.util.devLog(
                "[Gun] packetevents 未安装/未加载，左键按住连发（AUTO/BURST）不可用",
            )
            return
        }
        registeredListener = this.asAbstract(PacketListenerPriority.NORMAL)
        PacketEvents.getAPI().eventManager.registerListener(registeredListener!!)
        registered = true
    }

    fun shutdown() {
        registeredListener?.let { PacketEvents.getAPI().eventManager.unregisterListener(it) }
        registeredListener = null
        registered = false
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        val uuid = event.user.uuid ?: return
        val player = Bukkit.getPlayer(uuid) ?: return
        when (event.packetType) {
            PacketType.Play.Client.PLAYER_DIGGING -> {
                // 用枚举名称而非编译期枚举常量，兼容 PacketEvents 2.10~2.14 的命名差异。
                when (WrapperPlayClientPlayerDigging(event).action.toString().uppercase()) {
                    "START_DESTROY_BLOCK", "START_DIGGING" ->
                        dispatch(player, GunShoot.TriggerSignal.PRESS)
                    "ABORT_DESTROY_BLOCK", "CANCEL_DIGGING", "RELEASE_USE_ITEM", "STOP_DESTROY_BLOCK",
                    "FINISH_DIGGING", "DROP_ITEM", "DROP_ITEM_STACK", "SWAP_ITEM_WITH_OFFHAND" ->
                        dispatch(player, GunShoot.TriggerSignal.RELEASE)
                    else -> Unit
                }
            }
            PacketType.Play.Client.ANIMATION -> dispatch(player, GunShoot.TriggerSignal.PRESS)
            PacketType.Play.Client.HELD_ITEM_CHANGE -> dispatch(player, GunShoot.TriggerSignal.RELEASE)
            else -> Unit
        }
    }

    private fun dispatch(player: Player, signal: GunShoot.TriggerSignal) {
        if (!consumeBudget(player)) return
        submit(async = false) {
            if (player.isOnline) GunShoot.onPacketTrigger(player, signal)
        }
    }

    /**
     * 该玩家本窗口是否还有转发额度。
     *
     * 滑动窗口按玩家惰性推进：只有真的发包时才读写自己的计数，
     * 不发包的玩家零开销，也不需要额外定时任务。
     */
    private fun consumeBudget(player: Player): Boolean {
        val uuid = player.uniqueId
        val now = System.nanoTime()
        val start = windowStart[uuid]
        if (start == null || now - start >= SIGNAL_WINDOW_NANOS) {
            windowStart[uuid] = now
            windowUsed[uuid] = 1
            return true
        }
        val used = windowUsed[uuid] ?: 0
        if (used >= SIGNALS_PER_WINDOW) {
            dropped.merge(uuid, 1L, Long::plus)
            return false
        }
        windowUsed[uuid] = used + 1
        return true
    }

    /**
     * 清理单个玩家的限流状态（退出/重载时调用，避免 UUID 表无限增长）。
     */
    fun forget(player: Player) {
        val uuid = player.uniqueId
        windowStart.remove(uuid)
        windowUsed.remove(uuid)
        dropped.remove(uuid)
    }

    fun forgetAll() {
        windowStart.clear()
        windowUsed.clear()
        dropped.clear()
    }
}

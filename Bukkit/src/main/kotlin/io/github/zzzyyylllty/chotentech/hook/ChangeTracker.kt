package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * PAPI 变化追踪：记录玩家各项数据的变动幅度，用于显示上下箭头提示。
 *
 * 状态模型（每玩家每数据）:
 *   record   最近一次记录的变动值；5s 内的反方向变动直接相加，同方向仅更大变动才重新记录
 *   ratio    记录超过 6s 后开始向 record 靠拢（每 2s 增加 record 的 20%，不超过 |record|）
 * 占位符返回 record + ratio，按绝对值分档显示 1-6 个箭头；ratio 追上 record 后清空。
 */
object ChangeTracker {

    private val TRACKED_KEYS = listOf("health", "food", "sanity", "internalinjury", "externalinjury")

    private class TrackState(
        var record: Double = 0.0,
        var ratio: Double = 0.0,
        var lastChange: Long = 0L,
        var lastRatioMove: Long = 0L,
        var lastValue: Double = 0.0
    )

    private val states = ConcurrentHashMap<UUID, MutableMap<String, TrackState>>()

    private var tickTask: PlatformExecutor.PlatformTask? = null

    private fun getValue(player: Player, key: String): Double = when (key) {
        "health" -> player.health
        "food" -> getPercentHunger(player)
        "sanity" -> player.getData()?.sanity ?: 0.0
        "internalinjury" -> player.getData()?.internalInjury ?: 0.0
        "externalinjury" -> player.getData()?.externalInjury ?: 0.0
        else -> Double.NaN
    }

    /** 占位符入口：返回上下箭头提示（无变化返回空字符串） */
    fun getArrows(uuid: UUID, key: String): String {
        val st = states[uuid]?.get(key) ?: return ""
        if (st.record == 0.0) return ""
        val v = st.record + st.ratio
        val count = arrowCount(v)
        if (count == 0) return ""
        val up = v > 0
        val arrow = if (up) {
            config.getString("change-arrows.up", "▲") ?: "▲"
        } else {
            config.getString("change-arrows.down", "▼") ?: "▼"
        }
        val color = if (up) {
            config.getString("change-arrows.up-color", "<#c6ecc6>") ?: "<#c6ecc6>"
        } else {
            config.getString("change-arrows.down-color", "<#ecc6c6>") ?: "<#ecc6c6>"
        }
        return color + arrow.repeat(count)
    }

    private fun arrowCount(v: Double): Int {
        val a = abs(v)
        return when {
            a > 32 -> 6
            a > 16 -> 5
            a > 8 -> 4
            a > 4 -> 3
            a > 2 -> 2
            a > 0 -> 1
            else -> 0
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val online = Bukkit.getOnlinePlayers()
        // 清理已下线玩家的状态
        val onlineUuids = online.map { it.uniqueId }.toSet()
        states.keys.removeIf { it !in onlineUuids }
        for (player in online) {
            val map = states.computeIfAbsent(player.uniqueId) { mutableMapOf() }
            for (key in TRACKED_KEYS) {
                val value = getValue(player, key)
                if (value.isNaN()) continue
                val st = map.computeIfAbsent(key) { TrackState(lastValue = value) }
                val diff = value - st.lastValue
                if (abs(diff) >= 0.05) {
                    recordChange(st, diff, now)
                }
                st.lastValue = value
                // ratio 向 record 靠拢：记录超过 6s 后，每 2s 增加 record 的 20%
                if (st.record != 0.0 && now - st.lastChange > 6000 && now - st.lastRatioMove >= 2000) {
                    st.lastRatioMove = now
                    st.ratio += st.record * 0.2
                    if (abs(st.ratio) >= abs(st.record)) {
                        st.record = 0.0
                        st.ratio = 0.0
                    }
                }
            }
        }
    }

    private fun recordChange(st: TrackState, change: Double, now: Long) {
        // 无记录或记录超过 5s：直接作为新记录
        if (st.record == 0.0 || now - st.lastChange > 5000) {
            st.record = change
            st.ratio = 0.0
            st.lastChange = now
            st.lastRatioMove = now
            return
        }
        // 5s 内：反方向直接相加；同方向仅更大的变动才重新记录
        if ((change > 0) != (st.record > 0)) {
            st.record += change
        } else if (abs(change) > abs(st.record)) {
            st.record = change
        } else {
            return
        }
        st.ratio = 0.0
        st.lastChange = now
        st.lastRatioMove = now
    }

    @Awake(LifeCycle.ACTIVE)
    fun startTask() {
        tickTask = submit(period = 20L) { tick() }
        devLog("[ChangeTracker] 变化追踪任务已启动（每 20 tick 更新）")
    }

    @Awake(LifeCycle.DISABLE)
    fun stopTask() {
        tickTask?.cancel()
        tickTask = null
        states.clear()
    }
}

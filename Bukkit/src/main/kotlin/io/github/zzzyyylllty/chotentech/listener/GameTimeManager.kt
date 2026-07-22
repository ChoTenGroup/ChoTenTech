package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor

object GameTimeManager {

    private const val HOUR_MS = 60_000L  // 1 game hour = 60 real seconds
    private const val HOURS_PER_YEAR = 8640   // 12 months × 30 days × 24 hours
    private const val HOURS_PER_MONTH = 720   // 30 days × 24 hours
    private const val HOURS_PER_DAY = 24

    private var task: PlatformExecutor.PlatformTask? = null
    private var accumulator = 0L

    fun start() {
        stop()
        accumulator = 0L
        task = submit(period = 20L) { tick() }
        devLog("GameTimeManager: started (60 real seconds = 1 game hour)")
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /** Called every second (20 ticks). Accumulates real time and advances game time. */
    private fun tick() {
        accumulator += 1000L  // ~1 real second per tick
        if (accumulator < HOUR_MS) return

        val hours = accumulator / HOUR_MS
        accumulator %= HOUR_MS

        for (player in Bukkit.getOnlinePlayers()) {
            val data = player.getData() ?: continue
            data.gameTimeHours += hours
        }
    }

    // ==================== Date calculation ====================

    /** Compute game year (base: 2150) */
    fun getYear(hours: Long): Int = 2150 + (hours / HOURS_PER_YEAR).toInt()

    /** Compute game month (1-based) */
    fun getMonth(hours: Long): Int = ((hours % HOURS_PER_YEAR) / HOURS_PER_MONTH).toInt() + 1

    /** Compute game day (1-based) */
    fun getDay(hours: Long): Int = ((hours % HOURS_PER_MONTH) / HOURS_PER_DAY).toInt() + 1

    /** Compute game hour (0-23) */
    fun getHour(hours: Long): Int = (hours % HOURS_PER_DAY).toInt()

    /** Format: YYYY.MM.DD HH:00  e.g. "2160.01.01 13:00" */
    fun formatOneLine(hours: Long): String {
        val y = getYear(hours)
        val m = getMonth(hours)
        val d = getDay(hours)
        val h24 = getHour(hours)
        return "${y}.${m.toString().padStart(2, '0')}.${d.toString().padStart(2, '0')} ${h24.toString().padStart(2, '0')}:00"
    }
}

package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor

private var sanityTask: PlatformExecutor.PlatformTask? = null

/** 解析 min 字段：若以 % 结尾则为百分比，否则为绝对值 */
private fun resolveSanityMin(minStr: String, maxSanity: Double): Double {
    return if (minStr.endsWith("%")) {
        val pct = minStr.removeSuffix("%").toDoubleOrNull() ?: 40.0
        (maxSanity * pct / 100.0).coerceAtMost(maxSanity)
    } else {
        (minStr.toDoubleOrNull() ?: 40.0).coerceAtMost(maxSanity)
    }
}

/** 每秒执行：安全层级立即恢复，普通层级按 regen 缓慢恢复 */
@Awake(LifeCycle.ACTIVE)
fun sanityTimer() {
    sanityTask = submit(period = 20) {
        for (player in Bukkit.getOnlinePlayers()) {
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            val cfg = data.activeLevel?.sanity ?: continue
            if (cfg.regen <= 0.0 && !cfg.safe) continue

            val min = resolveSanityMin(cfg.min, data.effectiveMaxSanity)
            if (data.sanity >= min) continue

            if (cfg.safe) {
                data.sanity = min
            } else {
                data.sanity = (data.sanity + cfg.regen).coerceAtMost(min)
            }
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelSanityTimer() {
    sanityTask?.cancel()
}

package io.github.zzzyyylllty.chotentech.feature.gun

import kotlin.math.abs

/**
 * 热量镜像的纯计算部分（无 Bukkit 依赖，便于单测）。
 *
 * ### 背景
 * [GunStateManager] 用一个内存镜像保存「尚未落盘的亚整数冷却」，只在跨越整数档位时才把
 * 热量写回物品 NBT。原先「热量是否被外部改动」的判定拿**镜像里的最新值**和 NBT 比，
 * 于是每个没有跨整数的冷却 tick 都会被误判成外部改动：
 *
 * ```
 * 开火 → NBT=5.00、镜像=5.00
 * tick1: 5.00→4.94，跨整数 → 落盘，镜像=4.94
 * tick2: 4.94→4.88，不跨整数 → 不落盘，镜像=4.88、NBT 仍 4.94
 * tick3: |4.94-4.88| > 1e-6 → 判定「被外部改过」→ 丢弃镜像并清扳机
 * ```
 *
 * 该分支还会清 `heatLocked` / `forcedStopUntil` 并调用 `GunShoot.resetTrigger`
 * （同时清 `autoFiring` 与 `burstRemaining`），最终表现为：
 * **AUTO / BURST 打不出第二发，热量永远停在某个整数下方不再归零**。
 *
 * 修法：镜像额外记录 [HEAT_EPSILON] 意义上的「已落盘值」，只拿它和 NBT 比。
 */

/** 判定热量是否相等的容差（NBT 双精度往返后的最小可比差异）。 */
const val HEAT_EPSILON: Double = 1e-6

/**
 * NBT 里的热量是否已经不是「我们上次写进去的那个值」。
 *
 * @param nbtHeat 物品 NBT 里的真实热量
 * @param persistedHeat 上一次**成功写入 NBT** 的热量（不是镜像里的冷却进度）
 */
fun heatChangedExternally(nbtHeat: Double, persistedHeat: Double): Boolean =
    abs(nbtHeat - persistedHeat) > HEAT_EPSILON

/**
 * 冷却一 tick 后是否需要落盘。
 *
 * 跨越整数档位（显示与档位判定都按 `floor` 取整）或跨越热量档位时落盘，
 * 其余 tick 只更新内存镜像 —— 这正是「不落盘但不能被当成外部改动」的前提。
 *
 * 另外强制「至少每隔 [minIntervalTicks] tick 落一次盘」：`coolPerTick >= 1` 这种
 * 「一秒凉透」的配置会**每 tick 都跨整数**，退化成每 tick 一次 NBT 写 + 主线程包。
 *
 * @param ticksSincePersist 距离上次落盘过了多少 tick
 * @param minIntervalTicks 相邻两次落盘的最小间隔（按 coolPerTick 算出来）
 */
fun heatNeedsPersist(
    currentHeat: Double,
    nextHeat: Double,
    tierBefore: Int,
    tierAfter: Int,
    ticksSincePersist: Int = Int.MAX_VALUE,
    minIntervalTicks: Int = 1,
): Boolean {
    if (ticksSincePersist < minIntervalTicks.coerceAtLeast(1)) return false
    return kotlin.math.floor(nextHeat) != kotlin.math.floor(currentHeat) || tierAfter != tierBefore
}

/**
 * 按冷却速度推出「最少隔多少 tick 落一次盘」。
 *
 * `coolPerTick <= 0.1` 时按整数档位落盘就够（约 10 tick 一次），
 * 冷却更快时把间隔拉到至少 1/5 秒，避免高频写 NBT。
 */
fun heatPersistInterval(coolPerTick: Double): Int {
    if (!coolPerTick.isFinite() || coolPerTick <= 0.0) return 1
    val byRate = kotlin.math.ceil(0.2 / coolPerTick).toInt()
    return byRate.coerceIn(1, 10)
}
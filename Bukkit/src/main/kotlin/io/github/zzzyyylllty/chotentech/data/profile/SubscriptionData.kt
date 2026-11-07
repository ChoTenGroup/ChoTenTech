package io.github.zzzyyylllty.chotentech.data.profile

/**
 * 订阅状态。存放于 [SharedData]，同账号所有档案共享。
 *
 * 时间字段全部为 epoch 毫秒。
 *
 * - [expireTime] 到期时间。到期后订阅仍保留在数据中（菜单显示「已过期」可续费），但不再产出。
 * - [instantGranted] 「立即获得」奖励是否已发放过（仅首次购买发放一次，续费只延长时间）。
 * - [lastDailyLogin] 最近一次领取每日登录奖励的自然日（按服务器时区判定，0 表示从未领取）。
 * - [sixHourWindowStart] / [sixHourRemaining] 6 小时加成池：滚动窗口，窗口开始满 6h 后回满。
 * - [weekWindowStart] / [weekRemaining] 每周加成池：滚动窗口，窗口开始满 7 天后回满。
 */
data class SubscriptionData(
    var subscriptionId: String = "",
    var expireTime: Long = 0L,
    var firstPurchaseTime: Long = 0L,
    var instantGranted: Boolean = false,
    var lastDailyLogin: Long = 0L,
    var sixHourWindowStart: Long = 0L,
    var sixHourRemaining: Long = 0L,
    var weekWindowStart: Long = 0L,
    var weekRemaining: Long = 0L,
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = expireTime > now
}

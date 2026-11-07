package io.github.zzzyyylllty.chotentech.function.subscription

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.profile.SharedData
import io.github.zzzyyylllty.chotentech.data.profile.SubscriptionData
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 订阅系统。
 *
 * 配置：`plugins/ChoTenTech/subscriptions/` 下的 yml，每个文件一个订阅（文件名即 id），
 * 也兼容「一个文件内多段、段名即 id」的写法。
 *
 * 数据：订阅状态与 6h / 周加成池存放于 [SharedData]（同账号所有档案共享）；
 * Token 余额存放于玩家档案的共享字段（CurrencyManager 的 ltoken / ptoken）。
 *
 * 加成池（timed）语义：订阅本身不发 6h / 周 固定奖励。订阅生效期间，
 * 其他途径发放的 Token 走 [addLTokenWithBonus]，由每个生效订阅各自贡献 bonus = min(amount, 6h余量, 周余量)，
 * 实发 amount + bonus，两个池各扣 bonus。
 */
object SubscriptionManager {

    const val SIX_HOURS_MS = 6L * 60L * 60L * 1000L
    const val ONE_WEEK_MS = 7L * 24L * 60L * 60L * 1000L

    private val defs = LinkedHashMap<String, SubscriptionDef>()

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 购买结果 */
    enum class PurchaseResult { SUCCESS, UNKNOWN_SUBSCRIPTION, UNKNOWN_DURATION, NOT_ENOUGH_TAPE }

    // ------------------------------------------------------------------
    // 配置加载
    // ------------------------------------------------------------------

    fun loadAll() {
        defs.clear()
        val dir = File(getDataFolder(), "subscriptions")
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".yml", ignoreCase = true) } ?: emptyArray()
        for (file in files) {
            val config = try {
                YamlConfiguration.loadConfiguration(file)
            } catch (e: Exception) {
                devLog("[Subscription] 解析失败 ${file.name}: ${e.message}")
                continue
            }
            val fileId = file.nameWithoutExtension
            if (looksLikeSingleSubscription(config)) {
                SubscriptionConfig.parse(fileId, config)?.let { defs[it.id] = it }
            } else {
                for (key in config.getKeys(false)) {
                    val section = config.getConfigurationSection(key) ?: continue
                    SubscriptionConfig.parse(key, section)?.let { defs[it.id] = it }
                }
            }
        }
        devLog("[Subscription] 已加载 ${defs.size} 个订阅: ${defs.keys.joinToString(", ")}")
    }

    /** 文件顶层直接是订阅字段时，视为「一个文件 = 一个订阅」，id 取文件名 */
    private fun looksLikeSingleSubscription(config: YamlConfiguration): Boolean =
        config.isConfigurationSection("pricing") ||
            config.contains("instant") ||
            config.contains("daily-login") ||
            config.contains("6h") ||
            config.contains("week") ||
            config.contains("color")

    fun isLoaded(): Boolean = defs.isNotEmpty()

    fun all(): List<SubscriptionDef> = defs.values.toList()

    fun get(id: String): SubscriptionDef? = defs[id]

    fun ids(): List<String> = defs.keys.toList()

    // ------------------------------------------------------------------
    // 数据访问
    // ------------------------------------------------------------------

    fun shared(uuid: String): SharedData = ProfileManager.getSharedData(uuid)

    fun getData(uuid: String, id: String): SubscriptionData? = shared(uuid).subscriptions[id]

    /** 该玩家已拥有（含已过期）的订阅，按配置顺序 */
    fun owned(uuid: String): List<Pair<SubscriptionDef, SubscriptionData>> {
        val subscriptions = shared(uuid).subscriptions
        return defs.values.mapNotNull { def -> subscriptions[def.id]?.let { def to it } }
    }

    /** 该玩家当前生效中的订阅 */
    fun active(uuid: String): List<Pair<SubscriptionDef, SubscriptionData>> {
        val now = System.currentTimeMillis()
        return owned(uuid).filter { it.second.isActive(now) }
    }

    // ------------------------------------------------------------------
    // 购买 / 发放 / 移除
    // ------------------------------------------------------------------

    /** 用 tape 购买指定时长（pricing 的 key，如 "30d"）。 */
    fun purchase(player: Player, id: String, pricingKey: String): PurchaseResult {
        val def = defs[id] ?: return PurchaseResult.UNKNOWN_SUBSCRIPTION
        val pricing = def.pricing.find { it.key == pricingKey } ?: return PurchaseResult.UNKNOWN_DURATION
        if (!CurrencyManager.removeBalance(player, "tape", pricing.price)) return PurchaseResult.NOT_ENOUGH_TAPE
        grant(player, def, pricing.durationMs)
        return PurchaseResult.SUCCESS
    }

    /** 直接发放/续费指定时长（不扣费，供管理员命令使用）。时长为毫秒。 */
    fun grant(player: Player, id: String, durationMs: Long): Boolean {
        val def = defs[id] ?: return false
        grant(player, def, maxOf(durationMs, 0L))
        return true
    }

    private fun grant(player: Player, def: SubscriptionDef, durationMs: Long) {
        val uuid = player.uniqueId.toString()
        val shared = shared(uuid)
        val now = System.currentTimeMillis()
        val data = shared.subscriptions[def.id]
            ?: SubscriptionData(subscriptionId = def.id).also { shared.subscriptions[def.id] = it }

        val wasActive = data.isActive(now)
        // 生效中续费：在到期时间上叠加；已过期（或首次）：从本次购买时间重新起算
        data.expireTime = if (wasActive) data.expireTime + durationMs else now + durationMs
        val firstPurchase = data.firstPurchaseTime <= 0L
        if (firstPurchase) data.firstPurchaseTime = now

        // 「立即获得」仅首次购买发放一次
        val grantingInstant = !data.instantGranted
        if (grantingInstant) {
            data.instantGranted = true
            for (grant in def.instant) CurrencyManager.addBalance(player, grant.currencyType, grant.amount)
        }

        if (!wasActive) refillPools(data, def, now)
        ProfileManager.saveSharedData(uuid)

        val action = if (wasActive) "续费成功" else "订阅成功"
        player.sendComponent(
            "<#8FD4A6>$action <${def.color}>${def.displayName} <#A9B4C3>| <#F2F5FA>到期时间: <#EFDF8F>" +
                formatTime(data.expireTime)
        )
        if (grantingInstant && def.instant.isNotEmpty()) {
            player.sendComponent(
                "<#8FD4A6>立即获得: " + def.instant.joinToString(" <#6E7A8C>+ ") { it.shortText }
            )
        }
    }

    /** 移除订阅（管理员）。返回是否存在。 */
    fun removeSubscription(player: Player, id: String): Boolean {
        val uuid = player.uniqueId.toString()
        val shared = shared(uuid)
        if (shared.subscriptions.remove(id) == null) return false
        ProfileManager.saveSharedData(uuid)
        return true
    }

    // ------------------------------------------------------------------
    // 加成池
    // ------------------------------------------------------------------

    private fun refillPools(data: SubscriptionData, def: SubscriptionDef, now: Long) {
        if (def.sixHourBonus > 0L) {
            data.sixHourWindowStart = now
            data.sixHourRemaining = def.sixHourBonus
        }
        if (def.weekBonus > 0L) {
            data.weekWindowStart = now
            data.weekRemaining = def.weekBonus
        }
    }

    /** 滚动窗口：窗口开始满 6h / 7 天后回满 */
    private fun refreshPools(data: SubscriptionData, def: SubscriptionDef, now: Long) {
        if (def.sixHourBonus > 0L && (data.sixHourWindowStart <= 0L || now - data.sixHourWindowStart >= SIX_HOURS_MS)) {
            data.sixHourWindowStart = now
            data.sixHourRemaining = def.sixHourBonus
        }
        if (def.weekBonus > 0L && (data.weekWindowStart <= 0L || now - data.weekWindowStart >= ONE_WEEK_MS)) {
            data.weekWindowStart = now
            data.weekRemaining = def.weekBonus
        }
    }

    /** 只读：当前 6h 池余量（不修改数据） */
    fun sixHourRemaining(def: SubscriptionDef, data: SubscriptionData, now: Long = System.currentTimeMillis()): Long {
        if (data.sixHourWindowStart <= 0L) return def.sixHourBonus
        if (now - data.sixHourWindowStart >= SIX_HOURS_MS) return def.sixHourBonus
        return data.sixHourRemaining.coerceIn(0L, def.sixHourBonus)
    }

    /** 只读：当前周池余量（不修改数据） */
    fun weekRemaining(def: SubscriptionDef, data: SubscriptionData, now: Long = System.currentTimeMillis()): Long {
        if (data.weekWindowStart <= 0L) return def.weekBonus
        if (now - data.weekWindowStart >= ONE_WEEK_MS) return def.weekBonus
        return data.weekRemaining.coerceIn(0L, def.weekBonus)
    }

    /** 只读：6h 池下次回满时间 */
    fun sixHourResetAt(def: SubscriptionDef, data: SubscriptionData, now: Long = System.currentTimeMillis()): Long {
        val start = data.sixHourWindowStart
        if (start <= 0L) return now + SIX_HOURS_MS
        val reset = start + SIX_HOURS_MS
        return if (reset <= now) now + SIX_HOURS_MS else reset
    }

    /** 只读：周池下次回满时间 */
    fun weekResetAt(def: SubscriptionDef, data: SubscriptionData, now: Long = System.currentTimeMillis()): Long {
        val start = data.weekWindowStart
        if (start <= 0L) return now + ONE_WEEK_MS
        val reset = start + ONE_WEEK_MS
        return if (reset <= now) now + ONE_WEEK_MS else reset
    }

    // ------------------------------------------------------------------
    // Token 入账
    // ------------------------------------------------------------------

    /**
     * 通用加成入账：发放 [amount] 份 Token 时，所有生效中且加成池货币与 [paid] 匹配的订阅各自贡献 bonus。
     *
     * `bonus = min(amount, 6h池余量, 周池余量)`；实发 `amount + Σbonus`；每个订阅的两个池各扣自己的 bonus。
     *
     * @return 实际入账总额（含 bonus）
     */
    fun addTokenWithBonus(player: Player, amount: Long, paid: Boolean): Long {
        val clamped = maxOf(amount, 0L)
        if (clamped == 0L) return 0L

        val uuid = player.uniqueId.toString()
        val shared = shared(uuid)
        val now = System.currentTimeMillis()
        var bonusTotal = 0L

        for ((id, data) in shared.subscriptions) {
            val def = defs[id] ?: continue
            if (!data.isActive(now)) continue
            if (def.timedPaid != paid) continue
            if (def.sixHourBonus <= 0L && def.weekBonus <= 0L) continue
            refreshPools(data, def, now)
            // 未配置的池视为不设限（不参与取最小值，也不被扣减）
            val sixAvail = if (def.sixHourBonus > 0L) data.sixHourRemaining else Long.MAX_VALUE
            val weekAvail = if (def.weekBonus > 0L) data.weekRemaining else Long.MAX_VALUE
            val bonus = minOf(clamped, sixAvail, weekAvail)
            if (bonus <= 0L) continue
            if (def.sixHourBonus > 0L) data.sixHourRemaining -= bonus
            if (def.weekBonus > 0L) data.weekRemaining -= bonus
            bonusTotal += bonus
        }

        val total = clamped + bonusTotal
        CurrencyManager.addBalance(player, if (paid) CurrencyManager.PTOKEN else CurrencyManager.LTOKEN, total)
        if (bonusTotal > 0L) ProfileManager.saveSharedData(uuid)
        return total
    }

    /** 免费 Token 入账（老玩家常用的入口，含订阅加成池翻倍）。返回实发总额。 */
    fun addLTokenWithBonus(player: Player, amount: Long): Long = addTokenWithBonus(player, amount, false)

    /** 付费 Token 入账（仅 `timed: P` 的订阅会加成）。返回实发总额。 */
    fun addPTokenWithBonus(player: Player, amount: Long): Long = addTokenWithBonus(player, amount, true)

    /** 不带任何加成的 Token 入账。 */
    fun addToken(player: Player, type: String, amount: Long) = CurrencyManager.addBalance(player, type, amount)

    // ------------------------------------------------------------------
    // 每日登录奖励
    // ------------------------------------------------------------------

    /**
     * 每日登录奖励：登录时对每个生效中且当天（服务器自然日 00:00 起）未领取的订阅自动发放。
     * 应在登录流程完成、玩家数据就绪后调用。
     */
    fun onDailyLogin(player: Player) {
        val uuid = player.uniqueId.toString()
        val shared = try {
            shared(uuid)
        } catch (e: Exception) {
            devLog("[Subscription] 每日登录奖励跳过 ${player.name}: ${e.message}")
            return
        }
        if (shared.subscriptions.isEmpty()) return

        val now = System.currentTimeMillis()
        val dayStart = startOfDay(now)
        var granted = false

        for ((id, data) in shared.subscriptions) {
            val def = defs[id] ?: continue
            if (!data.isActive(now)) continue
            if (def.dailyLogin.isEmpty()) continue
            if (data.lastDailyLogin >= dayStart) continue

            data.lastDailyLogin = now
            for (grant in def.dailyLogin) CurrencyManager.addBalance(player, grant.currencyType, grant.amount)
            granted = true
            player.sendComponent(
                "<${def.color}>[${def.displayName}] <#8FD4A6>每日登录奖励: " +
                    def.dailyLogin.joinToString(" <#6E7A8C>+ ") { it.shortText }
            )
        }

        if (granted) ProfileManager.saveSharedData(uuid)
        if (granted) player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f)
    }

    // ------------------------------------------------------------------
    // 展示工具
    // ------------------------------------------------------------------

    fun startOfDay(now: Long = System.currentTimeMillis()): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun formatTime(epochMs: Long): String =
        if (epochMs <= 0L) "—"
        else Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

    /** 毫秒 -> 3天4小时 / 2小时30分 / 45秒 */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0秒"
        val days = ms / 86_400_000L
        val hours = (ms % 86_400_000L) / 3_600_000L
        val minutes = (ms % 3_600_000L) / 60_000L
        val seconds = (ms % 60_000L) / 1000L
        val text = buildString {
            if (days > 0L) append("${days}天")
            if (hours > 0L) append("${hours}小时")
            if (days == 0L && minutes > 0L) append("${minutes}分")
            if (days == 0L && hours == 0L && minutes == 0L && seconds > 0L) append("${seconds}秒")
        }
        return text.ifEmpty { "0秒" }
    }

    /** 剩余时间文案；已过期返回「已过期」 */
    fun remainText(data: SubscriptionData, now: Long = System.currentTimeMillis()): String {
        val remain = data.expireTime - now
        return if (remain <= 0L) "已过期" else formatDuration(remain)
    }
}

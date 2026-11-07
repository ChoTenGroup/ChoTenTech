package io.github.zzzyyylllty.chotentech.function.subscription

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.shop.ShopManager
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial

/** 十六进制颜色（形如 #DC96FF）转 MiniMessage 颜色标签：<#DC96FF> */
fun String.toColorTag(): String = if (startsWith("#")) "<$this>" else "<#$this>"

/** 规范化颜色：统一带 # 前缀 */
fun normalizeHexColor(hex: String?, fallback: String): String {
    val raw = hex?.trim().orEmpty()
    if (raw.isEmpty()) return fallback
    return if (raw.startsWith("#")) raw else "#$raw"
}

/**
 * 一次 Token 发放。
 * [paid] = true 表示 PToken（付费）；false 表示 LToken（免费）。
 */
data class TokenGrant(val amount: Long, val paid: Boolean) {

    val currencyType: String get() = if (paid) CurrencyManager.PTOKEN else CurrencyManager.LTOKEN

    /** 形如 <#8FD4A6>10000L / <#FFD166>50000P */
    val shortText: String
        get() = "${if (paid) "<#FFD166>" else "<#8FD4A6>"}$amount${if (paid) "P" else "L"}"

    /** 形如 10000 免费Token */
    fun fullText(): String = "$amount ${CurrencyManager.displayName(currencyType)}"
}

/** 一个可购买时长。[key] 形如 "30d"；[durationMs] 由 [ShopManager.parseDuration] 换算；[price] 单位为 tape。 */
data class SubscriptionPricing(val key: String, val durationMs: Long, val price: Long) {

    /** 形如 30天 */
    val durationText: String get() = SubscriptionManager.formatDuration(durationMs)
}

/**
 * 订阅定义（一个 subscriptions 目录下的 yml 条目）。
 *
 * - [instant] 立即获得，仅首次购买发放一次
 * - [dailyLogin] 每日登录奖励，自动发放
 * - [sixHourBonus] / [weekBonus] 加成池容量；生效期间其他途径获得的 Token 会按池余量翻倍（见 SubscriptionManager.addLTokenWithBonus）
 * - [timedPaid] 加成池货币是否为 PToken（配置 timed: P）；默认 L 表示翻倍免费 Token 收入
 */
data class SubscriptionDef(
    val id: String,
    val displayName: String,
    val color: String,
    val colorDark: String,
    val icon: XMaterial,
    val iconFallback: Material,
    val instant: List<TokenGrant>,
    val dailyLogin: List<TokenGrant>,
    val sixHourBonus: Long,
    val weekBonus: Long,
    val timedPaid: Boolean,
    val pricing: List<SubscriptionPricing>,
) {
    val colorTag: String get() = color.toColorTag()
    val colorDarkTag: String get() = colorDark.toColorTag()

    /** 渐变标签：<gradient:#DC96FF:#AC06eF> */
    val gradientTag: String get() = "<gradient:$color:$colorDark>"

    /** 加成池货币类型（ltoken / ptoken） */
    val timedCurrencyType: String
        get() = if (timedPaid) CurrencyManager.PTOKEN else CurrencyManager.LTOKEN
}

object SubscriptionConfig {

    const val DEFAULT_COLOR = "#DC96FF"
    const val DEFAULT_COLOR_DARK = "#AC06EF"

    /** 解析一个订阅段。返回 null 表示该段没有任何有效内容。 */
    fun parse(id: String, section: ConfigurationSection): SubscriptionDef? {
        val instant = parseGrants(section.get("instant"))
        val daily = parseGrants(section.get("daily-login") ?: section.get("dailyLogin") ?: section.get("daily"))
        val sixHour = section.getLong("6h", section.getLong("six-hour", 0L)).coerceAtLeast(0L)
        val week = section.getLong("week", section.getLong("weekly", 0L)).coerceAtLeast(0L)
        val pricing = parsePricing(section.getConfigurationSection("pricing"))

        if (instant.isEmpty() && daily.isEmpty() && sixHour <= 0L && week <= 0L && pricing.isEmpty()) return null

        val iconName = section.getString("icon")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "AMETHYST_SHARD"
        val icon = runCatching { XMaterial.valueOf(iconName) }.getOrElse { XMaterial.AMETHYST_SHARD }
        val iconFallback = runCatching { Material.valueOf(icon.name) }.getOrElse { Material.PAPER }

        return SubscriptionDef(
            id = id,
            displayName = section.getString("display-name")?.takeIf { it.isNotBlank() } ?: id,
            color = normalizeHexColor(section.getString("color"), DEFAULT_COLOR),
            colorDark = normalizeHexColor(section.getString("color-dark") ?: section.getString("colorDark"), DEFAULT_COLOR_DARK),
            icon = icon,
            iconFallback = iconFallback,
            instant = instant,
            dailyLogin = daily,
            sixHourBonus = sixHour,
            weekBonus = week,
            timedPaid = section.getString("timed", "L")?.trim()?.uppercase()?.startsWith("P") == true,
            pricing = pricing,
        )
    }

    private fun parsePricing(section: ConfigurationSection?): List<SubscriptionPricing> {
        if (section == null) return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val price = section.getLong(key, 0L)
            val durationMs = ShopManager.parseDuration(key)
            if (durationMs <= 0L) return@mapNotNull null
            SubscriptionPricing(key = key, durationMs = durationMs, price = maxOf(price, 0L))
        }
    }

    /**
     * 解析奖励。支持：
     * - 标量：`instant: 50000P` / `instant: 50000`（无后缀按免费 Token 处理）
     * - 列表：`daily-login: [10000L, 10000P]`
     */
    fun parseGrants(raw: Any?): List<TokenGrant> = when (raw) {
        null -> emptyList()
        is List<*> -> raw.mapNotNull { parseGrant(it) }
        else -> listOfNotNull(parseGrant(raw))
    }

    /** "50000P" / "10000L" / 50000 -> TokenGrant */
    fun parseGrant(raw: Any?): TokenGrant? {
        when (raw) {
            null -> return null
            is Number -> {
                val amount = raw.toLong()
                return if (amount > 0L) TokenGrant(amount, false) else null
            }
            is String -> {
                val text = raw.trim()
                if (text.isEmpty()) return null
                val suffix = text.last()
                val paid = suffix == 'P' || suffix == 'p'
                val free = suffix == 'L' || suffix == 'l'
                val amount = if (paid || free) text.dropLast(1).trim().toLongOrNull() else text.toLongOrNull()
                return if (amount != null && amount > 0L) TokenGrant(amount, paid) else null
            }
            else -> return null
        }
    }
}

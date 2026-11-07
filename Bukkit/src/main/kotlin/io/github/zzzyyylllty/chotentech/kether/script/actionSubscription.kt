package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.profile.SubscriptionData
import io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionDef
import io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionManager
import io.github.zzzyyylllty.chotentech.gui.SubscriptionDurationMenu
import io.github.zzzyyylllty.chotentech.gui.SubscriptionMenu
import io.github.zzzyyylllty.chotentech.shop.ShopManager
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.library.kether.QuestReader
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionNow
import taboolib.module.kether.actionTake
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser
import java.util.concurrent.CompletableFuture

/**
 * 订阅（Subscription）的 Kether 动作。
 *
 * ```
 * subscription open|menu                 # 打开订阅中心菜单
 * subscription list|ids                  # 返回已拥有（含已过期）的订阅 id 列表
 * subscription <id> info|status          # 返回该订阅的文字状态（默认动作）
 * subscription <id> active               # 是否生效中（布尔）
 * subscription <id> remain               # 剩余毫秒（未拥有或已过期返回 0）
 * subscription <id> expire|time          # 到期时间戳（毫秒，未拥有返回 0）
 * subscription <id> pool|bonus [6h|week] # 加成池剩余（不写时取两种池的较小值）
 * subscription <id> give <时长>          # 管理员式发放，不扣磁带；时长支持 30d/12h/45m/3600s 或毫秒数
 * subscription <id> buy [时长档]         # 用磁带购买（默认第一个时长档），返回是否成功
 * subscription <id> take|remove          # 删除该订阅（不退款），返回是否删除
 * subscription <id> open                 # 打开该订阅的时长选择菜单
 * ```
 *
 * 动作名大小写不敏感；`<id>` 可以写字面量，也可以写 `&变量`。
 * 例：`subscription amethyst buy 30d`、`subscription amethyst active`、`subscription list`。
 *
 * 注意：kether 里裸词是字面量，变量要写 `&变量名`（帧变量）或 chemdah 的 `var 变量名`。
 */
@KetherParser(["subscription", "subs", "sub"], shared = true)
fun actionSubscription() = scriptParser {
    val head = peekToken(it)
    if (head != null && head in GLOBAL_ACTIONS) {
        it.nextToken()
        when (head) {
            "open", "menu" -> actionNow {
                val player = requirePlayer(script().sender)
                SubscriptionMenu().open(player)
                null
            }

            else -> actionNow {
                val player = requirePlayer(script().sender)
                SubscriptionManager.owned(player.uniqueId.toString()).map { it.first.id }
            }
        }
    } else {
        val id = it.nextParsedAction()
        val peeked = peekToken(it)
        val action = when {
            peeked == null -> "info"
            peeked in SUBSCRIPTION_ACTIONS -> {
                it.nextToken()
                peeked
            }

            else -> error("Unknown subscription action: $peeked (available: ${SUBSCRIPTION_ACTIONS.joinToString(", ")})")
        }
        val value = if (peekToken(it) == null) null else it.nextParsedAction()
        actionTake {
            val sender = script().sender
            run(id).thenCompose { idValue ->
                val idText = idValue?.toString()?.trim().orEmpty()
                if (value == null) {
                    CompletableFuture.completedFuture<Any?>(handleSubscription(sender, idText, action, null))
                } else {
                    run(value).thenApply { raw -> handleSubscription(sender, idText, action, raw?.toString()) }
                }
            }
        }
    }
}

private val GLOBAL_ACTIONS = setOf("open", "menu", "list", "ids")

private val SUBSCRIPTION_ACTIONS = setOf(
    "info", "status", "active", "remain", "expire", "time",
    "pool", "bonus", "give", "grant", "buy", "purchase", "take", "remove", "open", "menu"
)

/** 窥视下一个 token（小写、不消费），没有 token 时返回 null。 */
private fun peekToken(reader: QuestReader): String? {
    if (!reader.hasNext()) return null
    reader.mark()
    val raw = try {
        reader.nextToken()
    } catch (ex: Throwable) {
        null
    } finally {
        reader.reset()
    }
    return raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}

private fun requirePlayer(sender: ProxyCommandSender?): Player =
    sender?.castSafely<Player>() ?: error("Player required")

private fun handleSubscription(sender: ProxyCommandSender?, id: String, action: String, value: String?): Any? {
    val player = requirePlayer(sender)
    if (id.isEmpty()) error("subscription: 缺少订阅 id")
    val uuid = player.uniqueId.toString()
    val now = System.currentTimeMillis()
    val def = SubscriptionManager.get(id)
        ?: error("Unknown subscription: $id (available: ${SubscriptionManager.ids().joinToString(", ")})")
    val data = SubscriptionManager.getData(uuid, id)
    return when (action) {
        "info", "status" -> describe(def, data, now)
        "active" -> data?.isActive(now) == true
        "remain" -> data?.let { maxOf(it.expireTime - now, 0L) } ?: 0L
        "expire", "time" -> data?.expireTime ?: 0L
        "pool", "bonus" -> poolRemaining(def, data, value, now)
        "give", "grant" -> SubscriptionManager.grant(player, id, parseDurationText(value))
        "buy", "purchase" -> {
            val key = value?.trim()?.takeIf { it.isNotEmpty() }
                ?: def.pricing.firstOrNull()?.key
                ?: error("subscription $id: 未配置 pricing，无法购买")
            SubscriptionManager.purchase(player, id, key) == SubscriptionManager.PurchaseResult.SUCCESS
        }

        "take", "remove" -> SubscriptionManager.removeSubscription(player, id)
        "open", "menu" -> {
            SubscriptionDurationMenu(def).open(player)
            true
        }

        else -> error("Unknown subscription action: $action")
    }
}

private fun describe(def: SubscriptionDef, data: SubscriptionData?, now: Long): String {
    if (data == null) return "${def.displayName} 未拥有"
    return if (data.isActive(now)) {
        "${def.displayName} 生效中（剩余 ${SubscriptionManager.formatDuration(data.expireTime - now)}）"
    } else {
        "${def.displayName} 已过期（${SubscriptionManager.formatTime(data.expireTime)} 到期）"
    }
}

private fun poolRemaining(def: SubscriptionDef, data: SubscriptionData?, value: String?, now: Long): Long {
    if (data == null || !data.isActive(now)) return 0L
    val six = if (def.sixHourBonus > 0L) SubscriptionManager.sixHourRemaining(def, data, now) else 0L
    val week = if (def.weekBonus > 0L) SubscriptionManager.weekRemaining(def, data, now) else 0L
    val target = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "min"
    return when (target) {
        "6h", "6", "six", "sixhour", "hour", "h" -> six
        "week", "weekly", "7d", "w" -> week
        "min", "all", "any" -> when {
            def.sixHourBonus > 0L && def.weekBonus > 0L -> minOf(six, week)
            def.sixHourBonus > 0L -> six
            def.weekBonus > 0L -> week
            else -> 0L
        }

        else -> error("Unknown pool type: $value (available: 6h, week)")
    }
}

/** 时长写法：`30d` / `12h` / `45m` / `3600s`，或直接写毫秒数。 */
private fun parseDurationText(raw: String?): Long {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) error("subscription give: 需要时长，例 30d / 12h / 45m / 3600s 或毫秒数")
    text.toLongOrNull()?.let { if (it > 0L) return it }
    val millis = ShopManager.parseDuration(text)
    if (millis <= 0L) error("subscription give: 无法解析时长 '$text'（支持 30d / 12h / 45m / 3600s 或毫秒数）")
    return millis
}

package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionManager
import org.bukkit.entity.Player
import taboolib.library.kether.ArgTypes
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionTake
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser

/**
 * Token（免费 LToken / 付费 PToken）的 Kether 动作。
 *
 * ```
 * token <类型> get                    # 余额；类型 token/total 时返回两种 Token 之和
 * token <类型> has <数量>             # 是否足够（布尔）；token 类型表示两种之和是否足够
 * token <类型> add <数量>             # 直接入账（不吃订阅加成），返回入账后的余额
 * token <类型> addbonus <数量>        # 带订阅加成池入账，返回实发总额（含加成）
 * token <类型> remove <数量>          # 扣除；token 类型按「先扣免费、再扣付费」；不足返回 false 且不扣
 * ```
 *
 * 类型写法：`ltoken`/`l`/`free`（免费）、`ptoken`/`p`/`paid`（付费）、`token`/`total`（两种合计）。
 * 例：`token ltoken addbonus 1000`、`token token remove 500`、`token ptoken get`。
 *
 * 注意：kether 里裸词是字面量，变量要写 `&变量名`（帧变量）或 chemdah 的 `var 变量名`。
 */
@KetherParser(["token", "tokens"], shared = true)
fun actionToken() = scriptParser {
    val type = it.nextParsedAction()
    val option = it.nextParsedAction()
    val value = try {
        it.mark()
        it.next(ArgTypes.LONG)
    } catch (ex: Throwable) {
        it.reset()
        null
    }
    actionTake {
        val sender = script().sender
        run(type).thenCompose { typeValue ->
            run(option).thenApply { optionValue ->
                val player = sender?.castSafely<Player>() ?: error("Player required")
                handleToken(player, typeValue.toString(), optionValue.toString().lowercase(), value)
            }
        }
    }
}

private const val TOKEN_ALL = "token"

/** 解析货币类型；无法识别时抛出错误。 */
private fun resolveTokenType(raw: String): String = when (raw.trim().lowercase()) {
    "l", "ltoken", "free", "lfree" -> CurrencyManager.LTOKEN
    "p", "ptoken", "paid" -> CurrencyManager.PTOKEN
    "token", "tokens", "total", "tokentotal" -> TOKEN_ALL
    else -> error("Unknown token type: $raw (available: ltoken, ptoken, token)")
}

private fun handleToken(player: Player, type: String, option: String, value: Long?): Any {
    val currency = resolveTokenType(type)
    return when (option.trim().lowercase()) {
        "get" ->
            if (currency == TOKEN_ALL) CurrencyManager.getTokenTotal(player)
            else CurrencyManager.getBalance(player, currency)

        "has" -> {
            val amount = value ?: error("token $type has <数量>")
            if (currency == TOKEN_ALL) CurrencyManager.hasToken(player, amount)
            else CurrencyManager.getBalance(player, currency) >= amount
        }

        "add" -> {
            val amount = value ?: error("token $type add <数量>")
            val target = if (currency == TOKEN_ALL) CurrencyManager.LTOKEN else currency
            CurrencyManager.addBalance(player, target, amount)
            CurrencyManager.getBalance(player, target)
        }

        // 带订阅加成池：只有 timed 与该货币匹配的生效中订阅会翻倍
        "addbonus", "bonus" -> {
            val amount = value ?: error("token $type addbonus <数量>")
            SubscriptionManager.addTokenWithBonus(player, amount, paid = currency == CurrencyManager.PTOKEN)
        }

        "remove", "take" -> {
            val amount = value ?: error("token $type remove <数量>")
            if (currency == TOKEN_ALL) CurrencyManager.removeToken(player, amount)
            else CurrencyManager.removeBalance(player, currency, amount)
        }

        else -> error("Unknown token action: $option (available: get, has, add, addbonus, remove)")
    }
}

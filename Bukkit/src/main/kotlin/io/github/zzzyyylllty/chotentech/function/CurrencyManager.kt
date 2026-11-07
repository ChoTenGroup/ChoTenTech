package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import org.bukkit.entity.Player

object CurrencyManager {

    val types = setOf("money", "point", "tape", "ltoken", "ptoken")

    val displayNames = mapOf(
        "money" to "MEG积分",
        "point" to "BNTG贸易点",
        "tape" to "磁带",
        "ltoken" to "免费Token",
        "ptoken" to "付费Token",
    )

    const val LTOKEN = "ltoken"
    const val PTOKEN = "ptoken"

    fun getBalance(player: Player, type: String): Long {
        val data = playerDataMap[player.uniqueId.toString()] ?: return 0L
        return when (type) {
            "point" -> data.point
            "tape" -> data.tape
            "ltoken" -> data.ltoken
            "ptoken" -> data.ptoken
            else -> data.money
        }
    }

    fun setBalance(player: Player, type: String, amount: Long) {
        val clamped = maxOf(amount, 0L)
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        when (type) {
            "money" -> data.money = clamped
            "point" -> data.point = clamped
            "tape" -> data.tape = clamped
            "ltoken" -> data.ltoken = clamped
            "ptoken" -> data.ptoken = clamped
        }
    }

    fun addBalance(player: Player, type: String, amount: Long) {
        val clamped = maxOf(amount, 0L)
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        when (type) {
            "money" -> data.money += clamped
            "point" -> data.point += clamped
            "tape" -> data.tape += clamped
            "ltoken" -> data.ltoken += clamped
            "ptoken" -> data.ptoken += clamped
        }
    }

    fun removeBalance(player: Player, type: String, amount: Long): Boolean {
        val clamped = maxOf(amount, 0L)
        val data = playerDataMap[player.uniqueId.toString()] ?: return false
        val current = getBalance(player, type)
        if (current < clamped) return false
        when (type) {
            "money" -> data.money = current - clamped
            "point" -> data.point = current - clamped
            "tape" -> data.tape = current - clamped
            "ltoken" -> data.ltoken = current - clamped
            "ptoken" -> data.ptoken = current - clamped
        }
        return true
    }

    fun hasBalance(player: Player, type: String, amount: Long): Boolean {
        return getBalance(player, type) >= amount
    }

    // ------------------------------------------------------------------
    // Token（LToken 免费 / PToken 付费）
    // ------------------------------------------------------------------

    /** LToken 与 PToken 的合计余额。 */
    fun getTokenTotal(player: Player): Long {
        val data = playerDataMap[player.uniqueId.toString()] ?: return 0L
        return data.ltoken + data.ptoken
    }

    fun hasToken(player: Player, amount: Long): Boolean = getTokenTotal(player) >= maxOf(amount, 0L)

    /**
     * 通用扣费：优先消耗 LToken（免费），不足部分再消耗 PToken（付费）。
     * 两者合计不足时返回 false，且不扣除任何余额。
     */
    fun removeToken(player: Player, amount: Long): Boolean {
        val clamped = maxOf(amount, 0L)
        if (clamped == 0L) return true
        val data = playerDataMap[player.uniqueId.toString()] ?: return false
        if (data.ltoken + data.ptoken < clamped) return false
        val fromFree = minOf(data.ltoken, clamped)
        val fromPaid = clamped - fromFree
        data.ltoken -= fromFree
        data.ptoken -= fromPaid
        return true
    }

    fun displayName(type: String): String = displayNames[type] ?: type

    fun format(player: Player, type: String): String {
        val balance = getBalance(player, type)
        return "${displayName(type)}: $balance"
    }
}

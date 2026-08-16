package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import org.bukkit.entity.Player

object CurrencyManager {

    val types = setOf("money", "point", "tape", "goldentape")

    val displayNames = mapOf(
        "money" to "MEG积分",
        "point" to "BNTG贸易点",
        "tape" to "磁带",
        "goldentape" to "金磁带",
    )

    fun getBalance(player: Player, type: String): Long {
        val data = playerDataMap[player.uniqueId.toString()] ?: return 0L
        return when (type) {
            "point" -> data.point
            "tape" -> data.tape
            "goldentape" -> data.goldentape
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
            "goldentape" -> data.goldentape = clamped
        }
    }

    fun addBalance(player: Player, type: String, amount: Long) {
        val clamped = maxOf(amount, 0L)
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        when (type) {
            "money" -> data.money += clamped
            "point" -> data.point += clamped
            "tape" -> data.tape += clamped
            "goldentape" -> data.goldentape += clamped
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
            "goldentape" -> data.goldentape = current - clamped
        }
        return true
    }

    fun hasBalance(player: Player, type: String, amount: Long): Boolean {
        return getBalance(player, type) >= amount
    }

    fun displayName(type: String): String = displayNames[type] ?: type

    fun format(player: Player, type: String): String {
        val balance = getBalance(player, type)
        return "${displayName(type)}: $balance"
    }
}

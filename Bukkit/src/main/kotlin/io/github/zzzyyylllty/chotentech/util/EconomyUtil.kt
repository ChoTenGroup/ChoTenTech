package io.github.zzzyyylllty.chotentech.util

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/** 插件自带经济（PlayerData.money）的封装，供保险/基地等系统使用，不再依赖 Vault。 */
object EconomyUtil {

    fun getBalance(player: OfflinePlayer): Long =
        (player as? Player)?.let { CurrencyManager.getBalance(it, "money") } ?: 0L

    fun has(player: OfflinePlayer, amount: Double): Boolean =
        getBalance(player) >= amount

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        (player as? Player)?.let { CurrencyManager.removeBalance(it, "money", amount.toLong()) } ?: false

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        val p = player as? Player ?: return false
        CurrencyManager.addBalance(p, "money", amount.toLong())
        return true
    }

    fun format(amount: Double): String = "${CurrencyManager.displayName("money")} ${amount.toLong()}"
}

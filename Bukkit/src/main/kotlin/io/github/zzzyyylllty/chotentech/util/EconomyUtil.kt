package io.github.zzzyyylllty.chotentech.util

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

object EconomyUtil {

    private var _economy: Economy? = null

    fun setup(): Boolean {
        if (_economy != null) return true
        val provider = Bukkit.getServicesManager().getRegistration(Economy::class.java)
        _economy = provider?.provider
        return _economy != null
    }

    fun getEconomy(): Economy? {
        if (_economy == null) setup()
        return _economy
    }

    fun has(player: OfflinePlayer, amount: Double): Boolean {
        return getEconomy()?.has(player, amount) ?: false
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val eco = getEconomy() ?: return false
        if (!eco.has(player, amount)) return false
        eco.withdrawPlayer(player, amount)
        return true
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        val eco = getEconomy() ?: return false
        eco.depositPlayer(player, amount)
        return true
    }

    fun format(amount: Double): String {
        return getEconomy()?.format(amount) ?: "$$amount"
    }
}

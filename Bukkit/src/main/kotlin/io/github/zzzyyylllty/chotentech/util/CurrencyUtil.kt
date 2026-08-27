package io.github.zzzyyylllty.chotentech.util

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.kether.evalKether
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration

object CurrencyUtil {

    data class CurrencyEntry(
        val id: String,
        val type: String,
        val displayName: String,
        val papi: String?,
        val actionsRemove: List<String>?,
        val actionsDeny: List<String>?,
    )

    private val currencies = linkedMapOf<String, CurrencyEntry>()

    fun load(config: Configuration) {
        currencies.clear()
        for (key in config.getKeys(false)) {
            try {
                val sec = config.getConfigurationSection(key) ?: continue
                val type = sec.getString("type")?.uppercase() ?: continue
                val displayName = sec.getString("display-name") ?: key
                val papi = sec.getString("papi")
                val actionsSec = sec.getConfigurationSection("actions")
                val actionsRemove = actionsSec?.getStringList("remove")
                val actionsDeny = actionsSec?.getStringList("deny")
                currencies[key] = CurrencyEntry(key, type, displayName, papi, actionsRemove, actionsDeny)
            } catch (e: Exception) {
                warning("加载货币 $key 失败: ${e.message}")
            }
        }
    }

    fun getCurrency(id: String): CurrencyEntry? = currencies[id]

    fun getBalance(player: Player, currencyId: String): Double {
        val entry = currencies[currencyId] ?: return 0.0
        return when (entry.type) {
            "PLUGIN" -> CurrencyManager.getBalance(player, entry.id).toDouble()
            "PAPI" -> {
                if (entry.papi != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    PlaceholderAPI.setPlaceholders(player, entry.papi).toDoubleOrNull() ?: 0.0
                } else 0.0
            }
            else -> 0.0
        }
    }

    fun hasBalance(player: Player, currencyId: String, amount: Double): Boolean {
        return getBalance(player, currencyId) >= amount
    }

    fun remove(player: Player, currencyId: String, amount: Double): Boolean {
        val entry = currencies[currencyId] ?: return false
        return when (entry.type) {
            "PLUGIN" -> CurrencyManager.removeBalance(player, entry.id, amount.toLong())
            "PAPI" -> {
                val scripts = entry.actionsRemove
                if (scripts.isNullOrEmpty()) return false
                try {
                    scripts.evalKether(player, mapOf(
                        "amount" to amount,
                        "money" to amount,
                        "needMoney" to amount,
                        "currentMoney" to getBalance(player, currencyId),
                        "displayName" to entry.displayName,
                        "currencyId" to currencyId,
                    )).get()
                    true
                } catch (e: Exception) {
                    warning("货币扣除失败 ($currencyId, $amount): ${e.message}")
                    false
                }
            }
            else -> false
        }
    }

    fun formatAmount(player: Player, currencyId: String, amount: Double): String {
        val entry = currencies[currencyId] ?: return "$amount"
        return "${entry.displayName} $amount"
    }
}

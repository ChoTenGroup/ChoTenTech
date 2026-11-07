package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import taboolib.platform.BukkitPlugin
import java.lang.String
import kotlin.Boolean

class PapiHookOriginal(plugin: BukkitPlugin) : PlaceholderExpansion() {
    private val plugin: BukkitPlugin

    init {
        this.plugin = plugin
    }

    override fun getAuthor(): kotlin.String {
        return "AkaCandy"
    }

    override fun getIdentifier(): kotlin.String {
        return "chotenapi"
    }

    override fun getVersion(): kotlin.String {
        return "1.0.0"
    }

    override fun persist(): Boolean {
        return true //
    }

    override fun onRequest(player: OfflinePlayer?, params: kotlin.String): kotlin.String? {
        if (params.equals("randomname", ignoreCase = true)) {
            val first = config.getStringList("names.first").random()
            val last = config.getStringList("names.last").random()
            return "$first $last"
        }
        return null //
    }
}
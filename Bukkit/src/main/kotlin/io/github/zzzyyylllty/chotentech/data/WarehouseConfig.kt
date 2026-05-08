package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import org.bukkit.entity.Player

object WarehouseConfig {

    data class Expansion(
        val permission: String,
        val slots: Int
    )

    private fun defaultSlots(): Int = config.getInt("warehouse.default-slots", 36)
    private fun defaultTitle(): String = config.getString("warehouse.title", "个人仓库") ?: "个人仓库"
    private fun maxTotalSlots(): Int = config.getInt("warehouse.max-total-slots", 54)

    private fun loadDefaultExpansions(): List<Expansion> {
        return loadExpansions("default")
    }

    private fun loadBaseExpansions(baseIdef: String): List<Expansion> {
        return loadExpansions(baseIdef)
    }

    private fun loadExpansions(sectionKey: String): List<Expansion> {
        val list = mutableListOf<Expansion>()
        val path = "warehouse.expansions.$sectionKey"
        val section = config.getConfigurationSection(path)
        if (section != null) {
            for (key in section.getKeys(false)) {
                val perm = section.getString("$key.permission") ?: continue
                val slots = section.getInt("$key.slots", 0)
                if (slots > 0) {
                    list.add(Expansion(perm, slots))
                }
            }
        }
        return list
    }

    private fun getBaseConfigInt(baseIdef: String, path: String, default: Int): Int {
        val basePath = "warehouse.base.$baseIdef.$path"
        return config.getInt(basePath, config.getInt("warehouse.$path", default))
    }

    private fun getBaseConfigString(baseIdef: String, path: String, default: String): String {
        val basePath = "warehouse.base.$baseIdef.$path"
        return config.getString(basePath) ?: config.getString("warehouse.$path") ?: default
    }

    fun getTitle(baseIdef: String): String {
        return getBaseConfigString(baseIdef, "title", defaultTitle())
    }

    fun getMaxTotalSlots(baseIdef: String): Int {
        return getBaseConfigInt(baseIdef, "max-total-slots", maxTotalSlots())
    }

    fun getMaxSlots(player: Player, baseIdef: String): Int {
        val base = getBaseConfigInt(baseIdef, "default-slots", defaultSlots())
        var slots = base

        // 全局扩展（适用于所有基地）
        for (expansion in loadDefaultExpansions()) {
            if (player.hasPermission(expansion.permission)) {
                slots += expansion.slots
            }
        }

        // 基地专属扩展
        for (expansion in loadBaseExpansions(baseIdef)) {
            if (player.hasPermission(expansion.permission)) {
                slots += expansion.slots
            }
        }

        return slots.coerceIn(base, getMaxTotalSlots(baseIdef))
    }

    fun getWarehouseSlots(player: Player, baseIdef: String): Int {
        return getMaxSlots(player, baseIdef)
    }
}

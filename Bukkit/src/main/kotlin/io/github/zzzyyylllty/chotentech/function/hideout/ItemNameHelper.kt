package io.github.zzzyyylllty.chotentech.function.hideout

import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import java.io.File

object ItemNameHelper {

    private val itemNames = linkedMapOf<String, String>()

    fun load() {
        itemNames.clear()
        val file = File(getDataFolder(), "itemoutputnames.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        for (key in config.getKeys(false)) {
            val name = config.getString(key) ?: continue
            itemNames[key] = name
        }
    }

    fun getName(id: String?): String? {
        if (id == null) return null
        val key = if (id.contains(":")) id.substringAfter(":") else id
        return itemNames[key]
    }

    fun getDisplayName(id: String?): String {
        return getName(id) ?: id ?: "?"
    }
}

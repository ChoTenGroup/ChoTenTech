package io.github.zzzyyylllty.chotentech.quetiapine.util.serialize

import com.google.gson.Gson
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.StringReader

fun parseToMap(data: String, format: String): Map<String, Any?>? {
    return try {
        @Suppress("UNCHECKED_CAST")
        when (format.lowercase()) {
            "yaml", "yml" -> yamlToMap(data)
            "json" -> Gson().fromJson(data, Map::class.java) as? Map<String, Any?>
            else -> throw IllegalArgumentException("Unsupported format: $format")
        }
    } catch (e: Exception) {
        println("Error parsing $format: ${e.message}")
        null
    }
}

private fun yamlToMap(data: String): Map<String, Any?> {
    val config = YamlConfiguration.loadConfiguration(StringReader(data))
    return configToMap(config)
}

@Suppress("UNCHECKED_CAST")
private fun configToMap(section: ConfigurationSection): Map<String, Any?> {
    val result = LinkedHashMap<String, Any?>()
    for (key in section.getKeys(false)) {
        val value = section[key]
        result[key] = when (value) {
            is ConfigurationSection -> configToMap(value)
            is List<*> -> value.map { item ->
                if (item is ConfigurationSection) configToMap(item) else item
            }
            else -> value
        }
    }
    return result
}

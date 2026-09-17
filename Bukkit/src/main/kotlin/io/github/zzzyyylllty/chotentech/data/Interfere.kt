package io.github.zzzyyylllty.chotentech.data

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File

enum class ResultLevel(val displayName: String, val displayColor: String) {
    FULL_SUCCESS("完胜", "<#6EA982>"),
    SUCCESS("成功", "<#8FD4A6>"),
    NEUTRAL("平手", "<#EFDF8F>"),
    FAIL("失败", "<#E08A98>"),
    FULL_FAIL("完败", "<#B46572>")
}

data class InterfereScore(
    val changes: Map<String, Long>
) {
    companion object {
        fun fromConfig(section: ConfigurationSection): InterfereScore {
            val changes = mutableMapOf<String, Long>()
            for (faction in section.getKeys(false)) {
                val value = section.getLong(faction)
                changes[faction] = value
            }
            return InterfereScore(changes)
        }
    }
}

data class InterfereConfig(
    val id: String,
    val name: String,
    val icon: String?,
    val itemModel: String?,
    val scores: Map<ResultLevel, InterfereScore>
) {
    companion object {
        fun fromConfig(id: String, section: ConfigurationSection): InterfereConfig {
            val name = section.getString("name") ?: id
            val icon = section.getString("icon")
            val itemModel = section.getString("item-model")
            val scores = mutableMapOf<ResultLevel, InterfereScore>()
            val scoreSection = section.getConfigurationSection("score") ?: error("Missing score section in interfere $id")
            for (key in scoreSection.getKeys(false)) {
                val level = runCatching { ResultLevel.valueOf(key.uppercase()) }.getOrNull()
                    ?: continue
                scoreSection.getConfigurationSection(key)?.let { sec ->
                    scores[level] = InterfereScore.fromConfig(sec)
                }
            }
            return InterfereConfig(id, name, icon, itemModel, scores)
        }
    }
}

private fun ensureInterferesDir() {
    val dir = File(getDataFolder(), "interferes")
    if (!dir.exists()) {
        releaseResourceFolder("interferes")
    }
}

fun loadInterfereFiles() {
    interfereConfigs.clear()
    ensureInterferesDir()
    val dir = File(getDataFolder(), "interferes")
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.name.endsWith(".yml")) {
            val conf = Configuration.loadFromFile(file)
            for (key in conf.getKeys(false)) {
                conf.getConfigurationSection(key)?.let { section ->
                    interfereConfigs[key] = InterfereConfig.fromConfig(key, section)
                }
            }
        }
    }
}

val interfereConfigs = linkedMapOf<String, InterfereConfig>()

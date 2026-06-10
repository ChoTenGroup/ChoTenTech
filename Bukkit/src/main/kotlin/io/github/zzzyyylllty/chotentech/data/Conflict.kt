package io.github.zzzyyylllty.chotentech.data

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File

enum class ResultLevel(val displayName: String, val displayColor: String) {
    FULL_SUCCESS("完胜", "<dark_green>"),
    SUCCESS("成功", "<green>"),
    NEUTRAL("平手", "<yellow>"),
    FAIL("失败", "<red>"),
    FULL_FAIL("完败", "<dark_red>")
}

data class ConflictScore(
    val changes: Map<String, Long>
) {
    companion object {
        fun fromConfig(section: ConfigurationSection): ConflictScore {
            val changes = mutableMapOf<String, Long>()
            for (faction in section.getKeys(false)) {
                val value = section.getLong(faction)
                changes[faction] = value
            }
            return ConflictScore(changes)
        }
    }
}

data class ConflictConfig(
    val id: String,
    val name: String,
    val scores: Map<ResultLevel, ConflictScore>
) {
    companion object {
        fun fromConfig(id: String, section: ConfigurationSection): ConflictConfig {
            val name = section.getString("name") ?: id
            val scores = mutableMapOf<ResultLevel, ConflictScore>()
            val scoreSection = section.getConfigurationSection("score") ?: error("Missing score section in conflict $id")
            for (key in scoreSection.getKeys(false)) {
                val level = runCatching { ResultLevel.valueOf(key.uppercase()) }.getOrNull()
                    ?: continue
                scoreSection.getConfigurationSection(key)?.let { sec ->
                    scores[level] = ConflictScore.fromConfig(sec)
                }
            }
            return ConflictConfig(id, name, scores)
        }
    }
}

private fun ensureConflictsDir() {
    val dir = File(getDataFolder(), "conflicts")
    if (!dir.exists()) {
        releaseResourceFolder("conflicts")
    }
}

fun loadConflictFiles() {
    conflictConfigs.clear()
    ensureConflictsDir()
    val dir = File(getDataFolder(), "conflicts")
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.name.endsWith(".yml")) {
            val conf = Configuration.loadFromFile(file)
            for (key in conf.getKeys(false)) {
                conf.getConfigurationSection(key)?.let { section ->
                    conflictConfigs[key] = ConflictConfig.fromConfig(key, section)
                }
            }
        }
    }
}

val conflictConfigs = linkedMapOf<String, ConflictConfig>()

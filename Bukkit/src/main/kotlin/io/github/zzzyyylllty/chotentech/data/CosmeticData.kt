package io.github.zzzyyylllty.chotentech.data

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File

enum class CosmeticSlot {
    AURA, FOOTSTEP, KILL_PARTICLE, KILL_MESSAGE
}

data class CosmeticConfig(
    val id: String,
    val slot: CosmeticSlot,
    val name: String,
    val permission: String?,
    val material: String,
    val model: String?,
    val particle: String?,
    val particleCount: Int,
    val particleSpeed: Double,
    val particleHeight: Double,
    val particleRadius: Double,
    val particlePattern: String?,
    val messageFormat: String?,
) {
    companion object {
        fun fromConfig(id: String, section: ConfigurationSection): CosmeticConfig {
            val slotName = section.getString("slot")?.uppercase() ?: "AURA"
            val slot = runCatching { CosmeticSlot.valueOf(slotName) }.getOrNull() ?: CosmeticSlot.AURA
            return CosmeticConfig(
                id = id,
                slot = slot,
                name = section.getString("name") ?: id,
                permission = section.getString("permission"),
                material = section.getString("material", "BARRIER") ?: "BARRIER",
                model = section.getString("model"),
                particle = section.getString("particle"),
                particleCount = section.getInt("particle_count", 3),
                particleSpeed = section.getDouble("particle_speed", 0.1),
                particleHeight = section.getDouble("particle_height", 0.5),
                particleRadius = section.getDouble("particle_radius", 0.8),
                particlePattern = section.getString("particle_pattern"),
                messageFormat = section.getString("message_format"),
            )
        }
    }
}

val cosmeticConfigs = linkedMapOf<String, CosmeticConfig>()

private fun ensureCosmeticsDir() {
    val dir = File(getDataFolder(), "cosmetics")
    if (!dir.exists()) {
        releaseResourceFolder("cosmetics")
    }
}

fun loadCosmeticFiles() {
    cosmeticConfigs.clear()
    ensureCosmeticsDir()
    val dir = File(getDataFolder(), "cosmetics")
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.name.endsWith(".yml")) {
            val conf = Configuration.loadFromFile(file)
            for (key in conf.getKeys(false)) {
                conf.getConfigurationSection(key)?.let { section ->
                    cosmeticConfigs[key] = CosmeticConfig.fromConfig(key, section)
                }
            }
        }
    }
}

fun getCosmeticsBySlot(slot: CosmeticSlot): List<CosmeticConfig> {
    return cosmeticConfigs.values.filter { it.slot == slot }
}

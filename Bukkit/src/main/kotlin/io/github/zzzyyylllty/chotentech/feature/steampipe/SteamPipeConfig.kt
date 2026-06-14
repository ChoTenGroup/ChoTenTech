package io.github.zzzyyylllty.chotentech.feature.steampipe

import org.bukkit.Material

data class SteamPipeLevelConfig(
    val level: String,
    val blocks: List<Material>,
    val centerRange: IntRange,
    val maxSearches: Int,
    val maxNativeUnits: Int,
    val warningSeconds: Double,
    val warningSecondsNative: Double,
    val eruptionSeconds: IntRange,
    val endSeconds: Double,
    val endSecondsNative: Double,
    val firstHitDamage: Double,
    val firstHitDamageNative: Double,
    val firstHitMinHp: Double,
    val secondHitDamage: Double,
    val secondHitDamageNative: Double
)

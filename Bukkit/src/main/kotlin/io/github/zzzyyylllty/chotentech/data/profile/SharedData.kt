package io.github.zzzyyylllty.chotentech.data.profile

import io.github.zzzyyylllty.chotentech.data.AchievementProgress

/**
 * Shared data that persists across all profiles for a player.
 * - tape, goldentape: premium currencies
 * - achievements: account-wide achievement progress
 * - cosmetics: account-wide cosmetic unlocks/equips
 */
data class SharedData(
    var activeProfileId: String = "1",
    var tape: Long = 0L,
    var goldentape: Long = 0L,
    var achievements: LinkedHashMap<String, AchievementProgress> = linkedMapOf(),
    var cosmetics: MutableMap<String, String> = mutableMapOf(),
    // Cross-profile statistics
    var totalPlayTime: Long = 0L,    // ms
    var joinCount: Int = 0,
    var totalDeaths: Int = 0,
    var totalLevelExits: Int = 0,
    var totalMobKills: Int = 0,
    var totalPlayerKills: Int = 0,
    var firstJoinTime: Long = 0L,   // epoch ms
    var lastJoinTime: Long = 0L,    // epoch ms
    var lastQuitTime: Long = 0L,    // epoch ms

    // Cross-profile meta flags (shared scope)
    var meta: MutableMap<String, MetaEntry> = mutableMapOf(),

    // Unlocked end IDs (profile completion achievements, shared across profiles)
    var unlockedEnds: MutableSet<String> = mutableSetOf(),

    // Active ranks shared across all profiles (type -> rankId)
    var activeRanks: MutableMap<String, String> = mutableMapOf(),
) {
    /** Fill in null collection fields from Gson deserialization */
    fun normalize(): SharedData {
        if (achievements == null) achievements = linkedMapOf()
        if (cosmetics == null) cosmetics = mutableMapOf()
        if (meta == null) meta = mutableMapOf()
        if (unlockedEnds == null) unlockedEnds = mutableSetOf()
        return this
    }
}

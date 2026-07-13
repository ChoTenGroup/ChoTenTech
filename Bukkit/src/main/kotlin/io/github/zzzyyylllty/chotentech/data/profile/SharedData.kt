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
    // Stats shared across all profiles
    var totalPlayTime: Long = 0L,    // ms
    var joinCount: Int = 0,
    var totalDeaths: Int = 0,
    var firstJoinTime: Long = 0L,   // epoch ms
    var lastJoinTime: Long = 0L,    // epoch ms
    var lastQuitTime: Long = 0L,    // epoch ms

    // Cross-profile meta flags (shared scope)
    var meta: MutableMap<String, MetaEntry> = mutableMapOf(),
)

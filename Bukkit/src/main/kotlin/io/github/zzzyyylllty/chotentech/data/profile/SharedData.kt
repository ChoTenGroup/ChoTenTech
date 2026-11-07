package io.github.zzzyyylllty.chotentech.data.profile

import io.github.zzzyyylllty.chotentech.data.AchievementProgress
import io.github.zzzyyylllty.chotentech.data.IDData

/**
 * Shared data that persists across all profiles for a player.
 * - tape, ltoken, ptoken: premium / universal currencies
 * - subscriptions: 订阅状态（含 6h / 周 加成池）
 * - achievements: account-wide achievement progress
 * - cosmetics: account-wide cosmetic unlocks/equips
 * - cid: shared cool ID across all profiles
 */
data class SharedData(
    var activeProfileId: String = "1",
    var tape: Long = 0L,
    var ltoken: Long = 0L,   // 免费Token
    var ptoken: Long = 0L,   // 付费Token
    var subscriptions: MutableMap<String, SubscriptionData> = mutableMapOf(),
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

    // 是否已完成教学任务(共享:任一 profile 完成即全局生效)
    var isCompletedTutorial: Boolean = false,

    // Cross-profile meta flags (shared scope)
    var meta: MutableMap<String, MetaEntry> = mutableMapOf(),

    // Unlocked end IDs (profile completion achievements, shared across profiles)
    var unlockedEnds: MutableSet<String> = mutableSetOf(),

    // Active ranks shared across all profiles (type -> rankId)
    var activeRanks: MutableMap<String, String> = mutableMapOf(),

    // Shared cool ID across all profiles
    var cid: IDData = IDData(0L, "NORMAL"),

    // Shared player settings across all profiles
    var settings: PlayerSettings = PlayerSettings(),

    // 好友列表（跨 profile 共享，UUID 字符串集合，存入数据库）
    var friends: MutableSet<String> = mutableSetOf(),
) {
    /** Fill in null collection fields from Gson deserialization */
    fun normalize(): SharedData {
        if (achievements == null) achievements = linkedMapOf()
        if (cosmetics == null) cosmetics = mutableMapOf()
        if (meta == null) meta = mutableMapOf()
        if (unlockedEnds == null) unlockedEnds = mutableSetOf()
        if (subscriptions == null) subscriptions = mutableMapOf()
        if (settings == null) settings = PlayerSettings()
        if (friends == null) friends = mutableSetOf()
        return this
    }
}

/** Player settings shared across all profiles */
data class PlayerSettings(
    val values: MutableMap<String, String> = DEFAULT_VALUES.toMutableMap()
) {
    companion object {
        val DEFAULT_VALUES: Map<String, String> = SettingRegistry.all.associate { it.id to it.defaultValue }
    }

    fun get(key: String): String = values[key] ?: DEFAULT_VALUES[key] ?: "unknown"
    fun getBoolean(key: String): Boolean = get(key).toBoolean()
}

package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerHudMap
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutPlayerData
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.Instant
import java.util.UUID
import kotlin.math.round

val defMaxDarkness by lazy { config.getDouble("default.max-darkness", 100.0) }
val defDarkness by lazy { config.getDouble("default.darkness", 15.0) }
val defMaxSanity by lazy { config.getDouble("default.max-sanity", 100.0) }
val defSanity by lazy { config.getDouble("default.sanity", 40.0) }
val defMaxStamina by lazy { config.getDouble("default.max-stamina", 100.0) }
val defStamina by lazy { config.getDouble("default.stamina", 100.0) }
val defMaxHandStamina by lazy { config.getDouble("default.max-hand-stamina", 100.0) }
val defHandStamina by lazy { config.getDouble("default.hand-stamina", 100.0) }
val defMaxCustomHunger by lazy { config.getDouble("default.max-custom-hunger", 100.0) }
val defCustomHunger by lazy { config.getDouble("default.custom-hunger", 100.0) }
val defMaxCustomSaturation by lazy { config.getDouble("default.max-custom-saturation", 100.0) }
val defCustomSaturation by lazy { config.getDouble("default.custom-saturation", 100.0) }
val defMaxExternalInjury by lazy { config.getDouble("default.max-external-injury", 100.0) }
val defExternalInjury by lazy { config.getDouble("default.external-injury", 100.0) }
val defMaxInternalInjury by lazy { config.getDouble("default.max-internal-injury", 100.0) }
val defInternalInjury by lazy { config.getDouble("default.internal-injury", 100.0) }
val defExternalInjuryRegen by lazy { config.getDouble("default.external-injury-regen", 0.0) }
val defInternalInjuryRegen by lazy { config.getDouble("default.internal-injury-regen", 0.0) }
val defSanityRegen by lazy { config.getDouble("default.sanity-regen", 0.0) }
val defNutritionGrain by lazy { config.getDouble("default.nutrition-grain", 50.0) }
val defNutritionVitamin by lazy { config.getDouble("default.nutrition-vitamin", 50.0) }
val defNutritionProtein by lazy { config.getDouble("default.nutrition-protein", 50.0) }
val defNutritionFat by lazy { config.getDouble("default.nutrition-fat", 50.0) }

data class PlayerData (
    val playerName: String,
    val uuid: String,
    var profileId: String = "1",
    var profileName: String = "Default",
    var cid: IDData,
    var noclipCooldownEnd: Long? = null,
    var noclipPoint: NoclipPoint? = null,
    var noclipTimeEnd: Long? = null,
    var noclipping: Boolean = false,
    var sanity: Double = 40.0,
    var stress: Double = 0.0,
    var maxSanity: Double = 100.0,
    var darkness: Double = 15.0,
    var maxDarkness: Double = 100.0,
    var mainTag: String? = null,
    var subTag: String? = null,
    var createTime: Long = System.currentTimeMillis(),
    var lastLoginTime: Long = System.currentTimeMillis(),
    var lastLogoutTime: Long = 0L,
    var editorMode: Boolean? = true,
    var discoveredBases: HashSet<String> = hashSetOf(),
    var exploreredLevels: HashSet<String> = hashSetOf(),
    var exploreredNoclips: LinkedHashMap<String ,HashSet<String>> = linkedMapOf(),
    var achievements: LinkedHashMap<String, AchievementProgress> = linkedMapOf(),
    // key: global 或 base idef (ALP/BET/...), value: slot -> serialized item
    var warehouseContents: LinkedHashMap<String, LinkedHashMap<Int, String>> = linkedMapOf(),
    // 安全箱 (塔科夫式)：slot -> serialized item，死亡不掉落
    var secureContainer: LinkedHashMap<Int, String> = linkedMapOf(),
    // 背包物品：slot -> serialized item（profile独立）
    var inventoryContents: MutableMap<Int, String> = mutableMapOf(),
    // 投保物品：uuid -> InsuredItem
    var insuredItems: LinkedHashMap<String, InsuredItem> = linkedMapOf(),
    // 死亡后返还的投保物品（序列化）
    var returnedInsuranceItems: MutableList<String> = mutableListOf(),
    // 死亡时保留在身上的物品（未掉落的40%）
    var deathKeepItems: MutableList<String> = mutableListOf(),
    var respawnPoint: RespawnPoint? = null,
    var warehouseBaseSlots: Int? = null,
    var hideoutData: MutableMap<String, HideoutPlayerData> = linkedMapOf(),
    var statusEffects: MutableList<PlayerStatusEffect> = mutableListOf(),
    // Shared fields — synced from SharedData by ProfileManager

    var stamina: Double = defStamina,
    var handStamina: Double = defHandStamina,
    var maxHunger: Double = 100.0,
    var maxSaturation: Double = 100.0,
    var customHunger: Double = defCustomHunger,
    var customSaturation: Double = defCustomSaturation,
    var consumption: Double = 0.0,
    var externalInjury: Double = defExternalInjury,
    var internalInjury: Double = defInternalInjury,
    // nutrition
    var grain: Double = defNutritionGrain,
    var vitamin: Double = defNutritionVitamin,
    var protein: Double = defNutritionProtein,
    var fat: Double = defNutritionFat,
    // NPC uuid/id -> affection value
    var affection: MutableMap<String, Long> = mutableMapOf(),
    // affection mark -> NPC ids that have consumed the mark (repeat guard)
    var affectionMarks: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    // faction id -> reputation value (e.g. MEG, BNTG, UEC)
    var reputation: MutableMap<String, Long> = mutableMapOf(),
    // 信誉记录（conflictId=null 表示杂项扣除途径），按时间先后追加
    var reputationRecords: MutableList<ReputationRecord> = mutableListOf(),
    // Shared field — cosmetic slot -> cosmetic id (synced from SharedData)
    var cosmetics: MutableMap<String, String> = mutableMapOf(),

    // chemdah 任务数据（persistentDataContainer JSON）
    var chemdahProfileData: String? = null,
    // chemdah 活跃任务数据（quest_id -> quest DataContainer JSON）
    var chemdahQuestData: String? = null,

    // pvp mode toggle
    var pvpEnabled: Boolean = false,

    // profile end/completion flags
    var ended: Boolean = false,
    var endedId: String? = null,

    // music replay tracking
    var lastMusicPlayTime: Long = 0L,
    var musicReplayDelay: Long = 0L,  // randomized 120000..300000 ms
    var musicPlayedCount: Int = 0,  // 当前层级音乐已播放次数(进入层级时重置)

    // currencies (use Long to avoid floating-point errors)
    var money: Long = 0L,
    var point: Long = 0L,
    // Shared fields — synced from SharedData by ProfileManager
    var tape: Long = 0L,
    var goldentape: Long = 0L,

    var playTime: Long = 0L,        // this profile's accumulated play time (ms)

    var unlockedNoclips: MutableSet<String> = mutableSetOf(),
    var unlockedShopItems: MutableSet<String> = mutableSetOf(),

    var savedBases: MutableList<String> = mutableListOf(),

    var activeLevelStr: String? = null,

    // Save point system
    var normalSave: String? = null,
    var baseSave: String? = null,
    var baseSaveSpawnTime: Long = 0L,

    // Last logout / profile-switch location — used to teleport when re-selecting this profile
    var profileLocation: RespawnPoint? = null,

    // Marshmallow counter for greasy marshmallows system
    var marshmallowCounter: Int = 0,

    // Chocolate counter for chocobytes system (decays to min 2, reset on death)
    var chocoCounter: Int = 0,

    // Quetiapine integration: per-profile key-value data (levels, exp, cooldowns, etc.)
    var quetiapineData: MutableMap<String, String> = mutableMapOf(),

    // Per-profile attribute modifiers — null means "not yet migrated" (will be seeded from shared attributes.db)
    var attributeModifiers: List<AttributeModifier>? = null,

    // In-game time tracking (60 real seconds = 1 game hour, base: 1950-01-01 00:00 = 0 hours)
    var gameTimeHours: Long = 0L,

    // Per-profile statistics
    var totalLevelExits: Int = 0,
    var totalDeaths: Int = 0,
    var totalMobKills: Int = 0,
    var totalPlayerKills: Int = 0,

    // Per-profile meta flags (independent scope)
    var meta: MutableMap<String, io.github.zzzyyylllty.chotentech.data.profile.MetaEntry> = mutableMapOf(),

    // Per-profile saved Bukkit potion effects (serialized)
    var potionEffects: MutableList<PotionEffectData> = mutableListOf(),

    @kotlin.jvm.Transient
    var sessionStart: Long = 0L     // set when profile is loaded, used to accumulate playTime
) {
    /** Fill in null collection fields that result from Gson deserializing old data */
    fun normalize(): PlayerData {
        if (discoveredBases == null) discoveredBases = hashSetOf()
        if (exploreredLevels == null) exploreredLevels = hashSetOf()
        if (exploreredNoclips == null) exploreredNoclips = linkedMapOf()
        if (achievements == null) achievements = linkedMapOf()
        if (warehouseContents == null) warehouseContents = linkedMapOf()
        if (secureContainer == null) secureContainer = linkedMapOf()
        if (inventoryContents == null) inventoryContents = mutableMapOf()
        if (insuredItems == null) insuredItems = linkedMapOf()
        if (returnedInsuranceItems == null) returnedInsuranceItems = mutableListOf()
        if (deathKeepItems == null) deathKeepItems = mutableListOf()
        if (hideoutData == null) hideoutData = linkedMapOf()
        if (statusEffects == null) statusEffects = mutableListOf()
        if (affection == null) affection = mutableMapOf()
        if (affectionMarks == null) affectionMarks = mutableMapOf()
        if (reputation == null) reputation = mutableMapOf()
        if (reputationRecords == null) reputationRecords = mutableListOf()
        if (cosmetics == null) cosmetics = mutableMapOf()
        if (unlockedNoclips == null) unlockedNoclips = mutableSetOf()
        if (unlockedShopItems == null) unlockedShopItems = mutableSetOf()
        if (savedBases == null) savedBases = mutableListOf()
        if (quetiapineData == null) quetiapineData = mutableMapOf()
        if (meta == null) meta = mutableMapOf()
        if (potionEffects == null) potionEffects = mutableListOf()
        return this
    }

    val activeLevel: Level?
        get() = levelsList[activeLevelStr]

    @kotlin.jvm.Transient
    private var _cachedUuid: UUID? = null

    // Effective max values — resolve from attribute system when player is online
    private fun attrMax(attrId: String, fallback: Double): Double {
        var puid = _cachedUuid
        if (puid == null) {
            puid = UUID.fromString(uuid)
            _cachedUuid = puid
        }
        val p = Bukkit.getPlayer(puid) ?: return fallback
        val v = AttributeManager.getAttributeValue(p, attrId)
        return if (v > 0.0) v else fallback
    }

    val effectiveMaxSanity: Double get() = attrMax("max_sanity", maxSanity)
    val sanityRegeneration: Double get() = attrMax("sanity_regeneration", defSanityRegen)
    val effectiveSanityRegeneration: Double get() = sanityRegeneration
    val effectiveMaxDarkness: Double get() = attrMax("max_darkness", maxDarkness)
    val maxExternalInjury: Double get() = attrMax("external_injury", defMaxExternalInjury)
    val maxInternalInjury: Double get() = attrMax("internal_injury", defMaxInternalInjury)
    val externalInjuryRegen: Double get() = attrMax("external_injury_regen", defExternalInjuryRegen)
    val internalInjuryRegen: Double get() = attrMax("internal_injury_regen", defInternalInjuryRegen)
    val maxStamina: Double get() = attrMax("stamina_max", defMaxStamina)
    val maxHandStamina: Double get() = attrMax("hand_stamina_max", defMaxHandStamina)
    val maxCustomHunger: Double get() = attrMax("max_custom_hunger", defMaxCustomHunger)
    val maxCustomSaturation: Double get() = attrMax("max_custom_saturation", defMaxCustomSaturation)

    fun getCooldownLeftLong(): Long? {
        return noclipCooldownEnd?.minus(Instant.now().toEpochMilli())
    }
    fun getTimeLeftLong(): Long? {
        return noclipTimeEnd?.minus(Instant.now().toEpochMilli())
    }
    fun setCooldownSec(sec: Double) {
        val now = Instant.now().toEpochMilli()
        val base = maxOf(noclipCooldownEnd ?: now, now)
        noclipCooldownEnd = base + round(sec * 1000).toLong()
    }
}

/** 单条信誉变化记录；conflictId=null 表示杂项扣除途径，level 为 ResultLevel.name 或 null */
data class ReputationRecord(
    val conflictId: String?,
    val faction: String,
    val level: String?,
    val change: Long,
    val time: Long
)

data class PlayerStatusEffect(
    val type: String,
    val level: Int,
    var remainingDuration: Long
)

/** Serializable representation of a Bukkit PotionEffect for per-profile storage */
data class PotionEffectData(
    val typeKey: String,       // e.g. "minecraft:speed"
    val duration: Int,         // ticks
    val amplifier: Int,
    val ambient: Boolean,
    val particles: Boolean,
    val icon: Boolean
) {
    fun toBukkit(): org.bukkit.potion.PotionEffect? {
        val key = org.bukkit.NamespacedKey.fromString(typeKey) ?: return null
        val type = org.bukkit.Registry.EFFECT.get(key) ?: return null
        return org.bukkit.potion.PotionEffect(type, duration, amplifier, ambient, particles, icon)
    }

    companion object {
        fun fromBukkit(effect: org.bukkit.potion.PotionEffect): PotionEffectData {
            return PotionEffectData(
                typeKey = effect.type.key.asString(),
                duration = effect.duration,
                amplifier = effect.amplifier,
                ambient = effect.isAmbient,
                particles = effect.hasParticles(),
                icon = effect.hasIcon()
            )
        }
    }
}

data class PlayerHUDData(
    val huds: LinkedHashMap<String, MutableList<SingleHUDData>> = linkedMapOf()
) {
    fun remove(key: String) {
        huds.remove(key)
    }
    fun add(key: String, content: String, seconds: Double) {
        update(key)
        val last = huds[key]?.lastOrNull()
        val hud = SingleHUDData(
            content,
            (last?.endTime ?: System.currentTimeMillis()) + round(seconds * 1000).toLong()
        )
        if (huds[key] == null) huds[key] = mutableListOf(hud) else huds[key]?.add(hud)
    }
    fun update(key: String) {
        val new = huds[key]?.sortedBy { (content, endTime) -> endTime } // 越小越前
        new?.filter { !it.isHUDExpired() }?.let { huds[key] = it.toMutableList() }
    }
    fun getCurrentHUD(key: String): SingleHUDData? {
        update(key)
        return huds[key]?.firstOrNull()
    }
    fun getCurrentHUDContent(key: String, defaultValue: String): String {
        return getCurrentHUD(key)?.content ?: defaultValue
    }
}

data class SingleHUDData (
    val content: String,
    val endTime: Long
) {
    fun isHUDExpired(): Boolean {
        return endTime < System.currentTimeMillis()
    }
}

class HUDManager(val player: Player) {

    fun getOrCreateHUDData(): PlayerHUDData {
        return playerHudMap.getOrPut(player.uniqueId.toString()) { PlayerHUDData() }
    }

    fun addHUD(key: String, content: String, seconds: Double) {
        getOrCreateHUDData().add(key, content, seconds)
    }
    fun removeHUD(key: String) {
        getOrCreateHUDData().remove(key)
    }

    fun getHUDContent(key: String, defaultValue: String): String {
        return getOrCreateHUDData().getCurrentHUDContent(key, defaultValue)
    }
}

data class RespawnPoint(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
) {
    fun toLocation(): Location? {
        return Bukkit.getWorld(world)?.let { Location(it, x, y, z, yaw, pitch) }
    }

    companion object {
        fun fromLocation(location: Location): RespawnPoint {
            return RespawnPoint(
                world = location.world?.name ?: "world",
                x = location.x,
                y = location.y,
                z = location.z,
                yaw = location.yaw,
                pitch = location.pitch
            )
        }
    }
}

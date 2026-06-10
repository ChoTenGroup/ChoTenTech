package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerHudMap
import io.github.zzzyyylllty.chotentech.attribute.manager.AttributeManager
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
val defMaxCustomHunger by lazy { config.getDouble("default.max-custom-hunger", 100.0) }
val defCustomHunger by lazy { config.getDouble("default.custom-hunger", 100.0) }
val defMaxCustomSaturation by lazy { config.getDouble("default.max-custom-saturation", 100.0) }
val defCustomSaturation by lazy { config.getDouble("default.custom-saturation", 100.0) }
val defMaxExternalInjury by lazy { config.getDouble("default.max-external-injury", 100.0) }
val defExternalInjury by lazy { config.getDouble("default.external-injury", 100.0) }
val defMaxInternalInjury by lazy { config.getDouble("default.max-internal-injury", 100.0) }
val defInternalInjury by lazy { config.getDouble("default.internal-injury", 100.0) }
val defExternalInjuryRegen by lazy { config.getDouble("default.external-injury-regen", 0.3) }
val defInternalInjuryRegen by lazy { config.getDouble("default.internal-injury-regen", 0.2) }
val defSanityRegen by lazy { config.getDouble("default.sanity-regen", 0.0) }

data class PlayerData (
    val playerName: String,
    val uuid: String,
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
    var editorMode: Boolean? = true,
    var discoveredBases: HashSet<String> = hashSetOf(),
    var exploreredLevels: HashSet<String> = hashSetOf(),
    var exploreredNoclips: LinkedHashMap<String ,HashSet<String>> = linkedMapOf(),
    var achievements: LinkedHashMap<String, AchievementProgress> = linkedMapOf(),
    // key: global 或 base idef (ALP/BET/...), value: slot -> serialized item
    var warehouseContents: LinkedHashMap<String, LinkedHashMap<Int, String>> = linkedMapOf(),
    // 安全箱 (塔科夫式)：slot -> serialized item，死亡不掉落
    var secureContainer: LinkedHashMap<Int, String> = linkedMapOf(),
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
    // type -> rankId (e.g. "prefix" -> "owner")
    var activeRanks: LinkedHashMap<String, String> = linkedMapOf(),

    var stamina: Double = defStamina,
    var maxStamina: Double = defMaxStamina,
    var customHunger: Double = defCustomHunger,
    var maxCustomHunger: Double = defMaxCustomHunger,
    var customSaturation: Double = defCustomSaturation,
    var maxCustomSaturation: Double = defMaxCustomSaturation,
    var consumption: Double = 0.0,
    var externalInjury: Double = defExternalInjury,
    var internalInjury: Double = defInternalInjury,
    var sanityRegeneration: Double = defSanityRegen,
    // NPC uuid/id -> affection value
    var affection: MutableMap<String, Long> = mutableMapOf(),
    // faction id -> reputation value (e.g. MEG, BNTG, UEC)
    var reputation: MutableMap<String, Long> = mutableMapOf(),
    // cosmetic slot -> cosmetic id (e.g. AURA -> flame_aura)
    var cosmetics: MutableMap<String, String> = mutableMapOf(),

    // pvp mode toggle
    var pvpEnabled: Boolean = false,

    // music replay tracking
    var lastMusicPlayTime: Long = 0L,
    var musicReplayDelay: Long = 0L,  // randomized 120000..300000 ms

    // currencies (use Long to avoid floating-point errors)
    var money: Long = 0L,
    var point: Long = 0L,
    var tape: Long = 0L,
    var goldentape: Long = 0L,

    var unlockedNoclips: MutableSet<String> = mutableSetOf(),

    @kotlin.jvm.Transient
    var activeLevelStr: String? = null
) {
    val activeLevel: Level?
        get() = levelsList[activeLevelStr]

    // Effective max values — resolve from attribute system when player is online
    private fun attrMax(attrId: String, fallback: Double): Double {
        val p = Bukkit.getPlayer(UUID.fromString(uuid)) ?: return fallback
        val v = AttributeManager.getAttributeValue(p, attrId)
        return if (v > 0.0) v else fallback
    }

    val effectiveMaxSanity: Double get() = attrMax("max_sanity", maxSanity)
    val effectiveSanityRegeneration: Double get() = attrMax("sanity_regeneration", sanityRegeneration)
    val effectiveMaxDarkness: Double get() = attrMax("max_darkness", maxDarkness)
    val maxExternalInjury: Double get() = attrMax("external_injury", defMaxExternalInjury)
    val maxInternalInjury: Double get() = attrMax("internal_injury", defMaxInternalInjury)

    fun getCooldownLeftLong(): Long? {
        return noclipCooldownEnd?.minus(Instant.now().toEpochMilli())
    }
    fun getTimeLeftLong(): Long? {
        return noclipTimeEnd?.minus(Instant.now().toEpochMilli())
    }
    fun setCooldownSec(sec: Double) {
        val now = Instant.now().toEpochMilli()
        val base = maxOf(noclipCooldownEnd ?: now, now)
        noclipCooldownEnd = maxOf((noclipTimeEnd ?: base), now) + round(sec * 1000).toLong()
    }
}

data class PlayerStatusEffect(
    val type: String,
    val level: Int,
    var remainingDuration: Long
)

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

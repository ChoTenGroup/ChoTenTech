package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerHudMap
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player
import java.time.Instant
import kotlin.math.round

val defMaxDarkness by lazy { config.getDouble("default.max-darkness", 100.0) }
val defDarkness by lazy { config.getDouble("default.darkness", 15.0) }
val defMaxSanity by lazy { config.getDouble("default.max-sanity", 100.0) }
val defSanity by lazy { config.getDouble("default.sanity", 40.0) }

@Serializable
data class PlayerData (
    val playerName: String,
    val uuid: String,
    var cid: IDData,
    var noclipCooldownEnd: Long? = null,
    var noclipPoint: @Contextual NoclipPoint? = null,
    var noclipTimeEnd: Long? = null,
    var noclipping: Boolean = false,
    var sanity: Double = 1.0,
    var maxSanity: Double = 1.0,
    var darkness: Double = 1.0,
    var maxDarkness: Double = 1.0,
    var mainTag: String? = null,
    var subTag: String? = null,
    var createTime: Long = System.currentTimeMillis(),
    var lastLoginTime: Long = System.currentTimeMillis(),
    var editorMode: Boolean? = true,

    @Transient
    var activeLevel: Level? = null
) {
    fun getCooldownLeftLong(): Long? {
        return noclipCooldownEnd?.minus(Instant.now().toEpochMilli())
    }
    fun getTimeLeftLong(): Long? {
        return noclipTimeEnd?.minus(Instant.now().toEpochMilli())
    }
    fun setCooldownSec(sec: Double) {
        val current = noclipCooldownEnd ?: Instant.now().toEpochMilli()
        noclipCooldownEnd =  (noclipTimeEnd ?: current) + round(sec * 1000).toLong()
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

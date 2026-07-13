package io.github.zzzyyylllty.chotentech.quetiapine.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import org.bukkit.entity.Player

/**
 * Bridge between Quetiapine's DataContainer-based storage and ChoTenTech's per-profile PlayerData.
 *
 * All Quetiapine level/exp/cooldown data is stored in PlayerData.quetiapineData,
 * making it per-profile instead of per-UUID.
 */
object QuetiapineDataBridge {

    /** Get the profile-scoped key-value map for this player */
    fun getDataMap(player: Player): MutableMap<String, String> {
        return playerDataMap[player.uniqueId.toString()]?.quetiapineData
            ?: mutableMapOf()
    }

    /** Get a string value from the player's profile data */
    fun get(player: Player, key: String): String? {
        return getDataMap(player)[key]
    }

    /** Set a value in the player's profile data */
    fun set(player: Player, key: String, value: Any) {
        getDataMap(player)[key] = value.toString()
    }

    /** Delete a key from the player's profile data */
    fun delete(player: Player, key: String) {
        getDataMap(player).remove(key)
    }

    /** Get all keys in the player's profile data */
    fun keys(player: Player): Set<String> {
        return getDataMap(player).keys
    }

    /** Initialize default levels for a player */
    fun initProfile(player: Player) {
        val map = getDataMap(player)
        for (level in levels.values) {
            val levelKey = "level.${level.id}"
            val expKey = "exp.${level.id}"
            if (map[levelKey] == null) {
                map[levelKey] = level.level.start.toString()
                map[expKey] = "0"  // Reset exp when level is missing (stale data from detached-map bug)
            }
            if (map[expKey] == null) {
                map[expKey] = "0"
            }
        }
    }

    /** Get level value as Long */
    fun getLevel(player: Player, levelName: String): Long? {
        return get(player, "level.$levelName")?.toLongOrNull()
    }

    /** Set level value */
    fun setLevel(player: Player, levelName: String, value: Long) {
        set(player, "level.$levelName", value)
    }

    /** Get exp value as Double */
    fun getExp(player: Player, levelName: String): Double {
        return get(player, "exp.$levelName")?.toDoubleOrNull() ?: 0.0
    }

    /** Set exp value */
    fun setExp(player: Player, levelName: String, value: Double) {
        set(player, "exp.$levelName", value)
    }
}

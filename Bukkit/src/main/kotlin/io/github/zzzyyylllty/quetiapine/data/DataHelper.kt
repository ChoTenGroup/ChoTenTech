package io.github.zzzyyylllty.quetiapine.data

import io.github.zzzyyylllty.quetiapine.util.CastHelper
import io.github.zzzyyylllty.quetiapine.util.toBooleanTolerance
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submitAsync
import java.time.Instant
import java.util.Date
import kotlin.math.round

/**
 * DataHelper -- now using per-profile QuetiapineDataBridge instead of DataContainer.
 * Level/exp defaults are initialized in QuetiapineDataBridge.initProfile().
 */
object DataHelper {
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        submitAsync {
            val player = event.player
            DataUtil.cleanupCooldown(player)
            QuetiapineDataBridge.initProfile(player)
        }
    }
}

@Suppress("UNUSED")
object DataUtil {

    fun getDataRaw(player: Player, dataID: String): String? {
        return QuetiapineDataBridge.get(player, dataID)
    }

    fun getDataSmart(player: Player, dataID: String): Any? {
        return QuetiapineDataBridge.get(player, dataID)?.let { CastHelper.smartCast(it) }
    }

    fun getDataAsInt(player: Player, dataID: String): Int? {
        return QuetiapineDataBridge.get(player, dataID)?.toIntOrNull()
    }

    fun getDataAsBoolean(player: Player, dataID: String): Boolean? {
        return QuetiapineDataBridge.get(player, dataID)?.toBooleanTolerance()
    }

    fun getDataAsDouble(player: Player, dataID: String): Double? {
        return QuetiapineDataBridge.get(player, dataID)?.toDoubleOrNull()
    }

    fun getDataAsLong(player: Player, dataID: String): Long? {
        return QuetiapineDataBridge.get(player, dataID)?.toLongOrNull()
    }

    fun removeData(player: Player, dataID: String) {
        QuetiapineDataBridge.delete(player, dataID)
    }

    fun setData(player: Player, dataID: String, dataValue: Any) {
        QuetiapineDataBridge.set(player, dataID, dataValue)
    }

    fun setDataIfNotExist(player: Player, dataID: String, dataValue: Any) {
        val map = QuetiapineDataBridge.getDataMap(player)
        if (map[dataID] == null) map[dataID] = dataValue.toString()
    }

    fun resetAllCooldown(player: Player) {
        val map = QuetiapineDataBridge.getDataMap(player)
        map.keys.filter { it.startsWith("cooldown.") }.forEach { map.remove(it) }
    }

    fun getAllCooldownRaw(player: Player): Map<String, String> {
        return QuetiapineDataBridge.getDataMap(player).filter { (key) -> key.startsWith("cooldown.") }
    }

    fun getAllDataRaw(player: Player): Map<String, String> {
        return QuetiapineDataBridge.getDataMap(player).toMap()
    }

    fun getAllCooldownLong(player: Player): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (element in QuetiapineDataBridge.getDataMap(player).filter { (key) -> key.startsWith("cooldown.") }) {
            element.value.toLongOrNull()?.let { map[element.key] = it }
        }
        return map
    }

    fun getAllCooldownDate(player: Player): Map<String, Date> {
        val map = mutableMapOf<String, Date>()
        for (element in QuetiapineDataBridge.getDataMap(player).filter { (key) -> key.startsWith("cooldown.") }) {
            element.value.toLongOrNull()?.let { map[element.key] = Date.from(Instant.ofEpochMilli(it)) }
        }
        return map
    }

    fun getCooldownRaw(player: Player, cooldownID: String): String? {
        return QuetiapineDataBridge.get(player, "cooldown.$cooldownID")
    }

    fun getCooldownLong(player: Player, cooldownID: String): Long? {
        return QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull()
    }

    fun getCooldownDate(player: Player, cooldownID: String): Date? {
        return QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull()?.let { Date.from(Instant.ofEpochMilli(it)) }
    }

    fun getCooldownLeftLong(player: Player, cooldownID: String): Long? {
        return QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull()?.let { it - Instant.now().toEpochMilli() }
    }

    fun getCooldownLeftDate(player: Player, cooldownID: String): Date? {
        return QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull()?.let { Date.from(Instant.ofEpochMilli(it - Instant.now().toEpochMilli())) }
    }

    fun setCooldown(player: Player, cooldownID: String, second: Double) {
        val current = Instant.now().toEpochMilli()
        QuetiapineDataBridge.set(player, "cooldown.$cooldownID", round(current + second * 1000).toLong())
    }

    fun setCooldownMill(player: Player, cooldownID: String, tick: Int) {
        val current = Instant.now().toEpochMilli()
        QuetiapineDataBridge.set(player, "cooldown.$cooldownID", current + tick)
    }

    fun extendCooldown(player: Player, cooldownID: String, second: Double) {
        val current = QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull() ?: Instant.now().toEpochMilli()
        QuetiapineDataBridge.set(player, "cooldown.$cooldownID", round(current + second * 1000).toLong())
    }

    fun extendCooldownMill(player: Player, cooldownID: String, tick: Int) {
        val current = QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull() ?: Instant.now().toEpochMilli()
        QuetiapineDataBridge.set(player, "cooldown.$cooldownID", current + tick)
    }

    fun reduceCooldown(player: Player, cooldownID: String, second: Double) {
        val current = QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull() ?: Instant.now().toEpochMilli()
        QuetiapineDataBridge.set(player, "cooldown.$cooldownID", round(current - second * 1000).toLong())
    }

    fun reduceCooldownTick(player: Player, cooldownID: String, tick: Int) {
        val current = QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull() ?: Instant.now().toEpochMilli()
        QuetiapineDataBridge.set(player, "cooldown.$cooldownID", current - tick)
    }

    fun resetCooldown(player: Player, cooldownID: String) {
        QuetiapineDataBridge.delete(player, "cooldown.$cooldownID")
    }

    fun isInCooldown(player: Player, cooldownID: String): Boolean {
        return (QuetiapineDataBridge.get(player, "cooldown.$cooldownID")?.toLongOrNull() ?: 0L) >= Instant.now().toEpochMilli()
    }

    fun cleanupCooldown(player: Player) {
        val map = QuetiapineDataBridge.getDataMap(player)
        map.filter { (key) -> key.startsWith("cooldown.") }.forEach { (key) ->
            if ((map[key]?.toLongOrNull() ?: 0L) <= Instant.now().toEpochMilli()) map.remove(key)
        }
    }

    fun resetAllData(player: Player) {
        QuetiapineDataBridge.getDataMap(player).clear()
    }

    fun savePlayerData(player: Player) {
        // No-op: profile data is saved by ProfileManager.saveAll()
    }
}

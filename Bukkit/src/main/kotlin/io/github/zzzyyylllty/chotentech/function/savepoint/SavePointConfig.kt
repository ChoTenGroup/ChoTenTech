package io.github.zzzyyylllty.chotentech.function.savepoint

import io.github.zzzyyylllty.chotentech.data.RespawnPoint
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.util.Vector
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.module.configuration.Configuration
import java.io.File

enum class SavePointType { NORMAL, BASE }

data class SavePoint(
    val id: String,
    val level: String,
    val world: String,
    val pos1: Vector,
    val pos2: Vector,
    val holoPos: Location?,  // null = auto-calculate from midpoint
    val name: String,
    val type: SavePointType,
    val base: String?,
    val respawnPos: RespawnPoint?,
) {
    /** Center point of the save zone */
    val center: Vector get() = pos1.clone().add(pos2).multiply(0.5)

    /** Default hologram position: center + 1 y */
    val defaultHoloPos: Location get() {
        val w = Bukkit.getWorld(world) ?: error("World $world not found")
        return Location(w, center.x, center.y + 1, center.z)
    }

    /** Hologram position to use (configured or default) */
    val effectiveHoloPos: Location get() = holoPos ?: defaultHoloPos

    /** Check if a location is within this save zone */
    fun contains(loc: Location): Boolean {
        if (loc.world?.name != world) return false
        val minX = minOf(pos1.x, pos2.x)
        val maxX = maxOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val maxY = maxOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxZ = maxOf(pos1.z, pos2.z)
        return loc.x >= minX && loc.x <= maxX
            && loc.y >= minY && loc.y <= maxY
            && loc.z >= minZ && loc.z <= maxZ
    }

    val isBase: Boolean get() = type == SavePointType.BASE
}

object SavePointConfig {

    private val savePoints = linkedMapOf<String, SavePoint>()

    fun load() {
        savePoints.clear()
        val file = File(getDataFolder(), "saves.yml")
        if (!file.exists()) {
            releaseResourceFolder("saves.yml")
        }
        val config = Configuration.loadFromFile(file)

        for (key in config.getKeys(false)) {
            try {
                val sec = config.getConfigurationSection(key) ?: continue
                val level = sec.getString("level") ?: continue
                val posStr = sec.getString("pos") ?: continue
                val parts = posStr.split(" ")
                if (parts.size < 7) continue

                val world = parts[0]
                val pos1 = Vector(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
                val pos2 = Vector(parts[4].toDouble(), parts[5].toDouble(), parts[6].toDouble())

                val holoPos: Location? = sec.getString("holoPos")?.let { hStr ->
                    val hp = hStr.split(" ")
                    if (hp.size >= 4) {
                        val hw = Bukkit.getWorld(hp[0])
                        if (hw != null) Location(hw, hp[1].toDouble(), hp[2].toDouble(), hp[3].toDouble()) else null
                    } else null
                }

                val respawnPos: RespawnPoint? = sec.getString("respawn")?.let { rStr ->
                    val rp = rStr.split(" ")
                    if (rp.size >= 4) RespawnPoint(rp[0], rp[1].toDouble(), rp[2].toDouble(), rp[3].toDouble(), 0f, 0f) else null
                }

                val type = try { SavePointType.valueOf(sec.getString("type") ?: "NORMAL") } catch (_: Exception) { SavePointType.NORMAL }

                savePoints[key] = SavePoint(
                    id = key,
                    level = level,
                    world = world,
                    pos1 = pos1,
                    pos2 = pos2,
                    holoPos = holoPos,
                    name = sec.getString("name") ?: key,
                    type = type,
                    base = sec.getString("base"),
                    respawnPos = respawnPos,
                )
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    fun getAll(): Collection<SavePoint> = savePoints.values
    fun get(id: String): SavePoint? = savePoints[id]
    fun getByLevel(level: String): List<SavePoint> = savePoints.values.filter { it.level == level }
    fun getByBase(base: String): SavePoint? = savePoints.values.firstOrNull { it.base == base }

    /** Find which save point a location is inside (if any) */
    fun findContaining(loc: Location): SavePoint? = savePoints.values.firstOrNull { it.contains(loc) }
}

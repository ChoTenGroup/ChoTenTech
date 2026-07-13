package io.github.zzzyyylllty.chotentech.feature.keydoor

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Configuration
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class DoorLocationKey(val world: String, val x: Int, val y: Int, val z: Int) {
    fun toLocation(): Location? {
        val w = Bukkit.getWorld(world) ?: return null
        return Location(w, x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
    }
}

data class DoorConfig(
    val id: String,
    val key: String,
    val locations: List<DoorLocationKey>
)

object KeyDoorManager {

    // Config
    private val doorById = ConcurrentHashMap<String, DoorConfig>()
    private val doorByLocation = ConcurrentHashMap<DoorLocationKey, DoorConfig>()

    // Tracking
    private val openDoors = ConcurrentHashMap<DoorLocationKey, Boolean>()

    // Tasks
    private var autoCloseRunning = false
    private var autoCloseTask: PlatformExecutor.PlatformTask? = null

    // ==================== Config ====================

    fun loadConfig() {
        doorById.clear()
        doorByLocation.clear()
        openDoors.clear()

        val dir = File(getDataFolder(), "keys")
        if (!dir.exists()) releaseResourceFolder("keys")

        val file = File(dir, "config.yml")
        if (!file.exists()) return

        val config = Configuration.loadFromFile(file)
        val doors = config.getConfigurationSection("doors") ?: return

        for (doorId in doors.getKeys(false)) {
            try {
                val sec = doors.getConfigurationSection(doorId) ?: continue
                val key = sec.getString("key") ?: continue
                val locs = sec.getMapList("locations").mapNotNull { map ->
                    val w = map["world"]?.toString() ?: return@mapNotNull null
                    val x = (map["x"] as? Number)?.toInt() ?: return@mapNotNull null
                    val y = (map["y"] as? Number)?.toInt() ?: return@mapNotNull null
                    val z = (map["z"] as? Number)?.toInt() ?: return@mapNotNull null
                    DoorLocationKey(w, x, y, z)
                }
                if (locs.isEmpty()) continue

                val dc = DoorConfig(id = doorId, key = key, locations = locs)
                doorById[doorId] = dc
                for (loc in locs) {
                    doorByLocation[loc] = dc
                }
            } catch (_: Exception) { /* skip */ }
        }
    }

    fun getDoorAt(loc: DoorLocationKey): DoorConfig? = doorByLocation[loc]

    /**
     * 开门并加入自动关闭追踪
     */
    fun openDoorAndTrack(loc: DoorLocationKey) {
        openDoor(loc)
        openDoors[loc] = true
    }

    // ==================== Door Control ====================

    fun tryOpenDoor(player: Player, doorLoc: DoorLocationKey): Boolean {
        val doorConfig = doorByLocation[doorLoc] ?: return false

        val item = player.inventory.itemInMainHand
        if (item.type.isAir || item.amount <= 0) return false

        // Check keys tag (async-friendly: we run this sync in listener, but the tag check is fast)
        val tag = try { item.getItemTag() } catch (_: Exception) { return false }
        val keys = getKeysFromTag(tag)

        if (doorConfig.key !in keys) {
            io.github.zzzyyylllty.chotentech.function.SkillManager.onDoorUnlockFailed(player)
            return false
        }

        // Open the door
        io.github.zzzyyylllty.chotentech.function.SkillManager.onDoorUnlocked(player)
        openDoor(doorLoc)
        openDoors[doorLoc] = true

        // Durability handling
        handleDurability(item, player)

        return true
    }

    fun openDoor(loc: DoorLocationKey) {
        val world = Bukkit.getWorld(loc.world) ?: return
        val block = world.getBlockAt(loc.x, loc.y, loc.z)
        if (block.type != Material.IRON_DOOR) return

        val door = block.blockData as? Door ?: return
        if (door.isOpen) return

        door.isOpen = true
        block.blockData = door

        // Also open the top half if it's a double door
        val topBlock = world.getBlockAt(loc.x, loc.y + 1, loc.z)
        if (topBlock.type == Material.IRON_DOOR) {
            val topDoor = topBlock.blockData as? Door
            if (topDoor != null && !topDoor.isOpen) {
                topDoor.isOpen = true
                topBlock.blockData = topDoor
            }
        }
    }

    fun closeDoor(loc: DoorLocationKey) {
        val world = Bukkit.getWorld(loc.world) ?: return
        val block = world.getBlockAt(loc.x, loc.y, loc.z)
        if (block.type != Material.IRON_DOOR) return

        val door = block.blockData as? Door ?: return
        if (!door.isOpen) return

        door.isOpen = false
        block.blockData = door

        // Also close the top half
        val topBlock = world.getBlockAt(loc.x, loc.y + 1, loc.z)
        if (topBlock.type == Material.IRON_DOOR) {
            val topDoor = topBlock.blockData as? Door
            if (topDoor != null && topDoor.isOpen) {
                topDoor.isOpen = false
                topBlock.blockData = topDoor
            }
        }

        openDoors.remove(loc)
    }

    // ==================== Auto-Close (every 400 ticks) ====================

    fun startAutoCloseTask() {
        if (autoCloseRunning) return
        autoCloseRunning = true
        autoCloseTask = submit(period = 400L, delay = 400L) {
            if (openDoors.isEmpty()) return@submit
            val iter = openDoors.entries.iterator()
            while (iter.hasNext()) {
                val (loc, _) = iter.next()
                val location = loc.toLocation() ?: run { iter.remove(); continue }

                // Door no longer open (redstone/other plugin) — clean up tracking
                val block = location.world?.getBlockAt(loc.x, loc.y, loc.z)
                if (block == null || block.type != Material.IRON_DOOR) { iter.remove(); continue }
                val door = block.blockData as? Door
                if (door == null || !door.isOpen) { iter.remove(); continue }

                val hasPlayerNearby = location.world?.getNearbyPlayers(location, 10.0)?.isNotEmpty() ?: false
                if (!hasPlayerNearby) {
                    closeDoor(loc)
                }
            }
        }
    }

    // ==================== Startup / Shutdown ====================

    fun closeAllDoors() {
        for (loc in doorByLocation.keys) {
            closeDoor(loc)
        }
        openDoors.clear()
    }

    fun onServerStart() {
        // Close any open doors left from previous server run
        for (loc in doorByLocation.keys) {
            closeDoor(loc)
        }
        openDoors.clear()
    }

    fun onDisable() {
        autoCloseRunning = false
        autoCloseTask?.cancel()
        autoCloseTask = null
        closeAllDoors()
    }

    // ==================== Key Tag Parsing ====================

    private fun getKeysFromTag(tag: ItemTag): Set<String> {
        val keys = mutableSetOf<String>()

        // Try as list
        val keysList = tag["keys"]?.asList()
        if (keysList != null) {
            for (item in keysList) {
                item.asString().let { keys.add(it) }
            }
            if (keys.isNotEmpty()) return keys
        }

        // Try as single string
        tag["keys"]?.asString()?.let { keys.add(it) }

        return keys
    }

    // ==================== Durability ====================

    private fun handleDurability(item: ItemStack, player: Player) {
        val tag = try { item.getItemTag() } catch (_: Exception) { return }
        val sData = tag["sertraline_data"]?.asCompound() ?: return
        val durability = sData["durability"]?.asDouble()?.toInt() ?: return
        if (durability <= 0) return

        val newDura = durability - 1
        if (newDura <= 0) {
            item.amount--
        } else {
            // Update tag with new durability
            val newTag = ItemTag()
            for (entry in tag.entries) {
                if (entry.key == "sertraline_data") {
                    val newSData = ItemTag()
                    for (sEntry in sData.entries) {
                        @Suppress("UNCHECKED_CAST")
                        newSData[sEntry.key] = if (sEntry.key == "durability") {
                            newDura
                        } else {
                            sEntry.value
                        }
                    }
                    newTag["sertraline_data"] = newSData
                } else {
                    newTag[entry.key] = entry.value
                }
            }
            item.setItemTag(newTag)
        }
        player.updateInventory()
    }

    // ==================== Helpers ====================

    /**
     * 从点击的方块获取铁门下半部分位置。
     * 如果点击的是上半部分，自动定位到下半部分。
     */
    fun getDoorBaseLocation(block: org.bukkit.block.Block): DoorLocationKey? {
        if (block.type != Material.IRON_DOOR) return null
        val door = block.blockData as? Door ?: return null
        val baseBlock = if (door.half == Bisected.Half.BOTTOM) block else block.getRelative(BlockFace.DOWN)
        val world = baseBlock.world.name
        return DoorLocationKey(world, baseBlock.x, baseBlock.y, baseBlock.z)
    }
}

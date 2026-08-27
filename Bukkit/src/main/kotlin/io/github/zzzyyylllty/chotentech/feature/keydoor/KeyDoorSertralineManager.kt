package io.github.zzzyyylllty.chotentech.feature.keydoor

import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Configuration
import taboolib.module.nms.getItemTag
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class SertralineKeyDoor(
    val configId: String,
    val name: String,
    val id: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val durabilityCost: Int
)

object KeyDoorSertralineManager {

    private val doorsByLocation = ConcurrentHashMap<DoorLocationKey, SertralineKeyDoor>()
    private val openDoors = ConcurrentHashMap<DoorLocationKey, Boolean>()
    private var autoCloseTask: PlatformExecutor.PlatformTask? = null

    // ==================== Config Loading ====================

    fun loadConfig() {
        doorsByLocation.clear()
        openDoors.clear()

        val dir = File(getDataFolder(), "keys")
        val file = File(dir, "sertraline.yml")
        if (!file.exists()) {
            stopAutoCloseTask()
            return
        }

        val config = Configuration.loadFromFile(file)
        for (key in config.getKeys(false)) {
            try {
                val sec = config.getConfigurationSection(key) ?: continue
                val name = sec.getString("name") ?: continue
                val posStr = sec.getString("pos") ?: continue
                val id = sec.getString("id") ?: continue

                val parts = posStr.split(" ")
                if (parts.size < 4) continue
                val world = parts[0]
                val x = parts[1].toIntOrNull() ?: continue
                val y = parts[2].toIntOrNull() ?: continue
                val z = parts[3].toIntOrNull() ?: continue

                val durabilityCost = sec.getInt("durability", 1).coerceAtLeast(1)

                doorsByLocation[DoorLocationKey(world, x, y, z)] = SertralineKeyDoor(
                    configId = key,
                    name = name,
                    id = id,
                    world = world,
                    x = x,
                    y = y,
                    z = z,
                    durabilityCost = durabilityCost
                )
            } catch (_: Exception) { /* skip invalid entry */ }
        }

        startAutoCloseTask()
    }

    private fun stopAutoCloseTask() {
        autoCloseTask?.cancel()
        autoCloseTask = null
    }

    // ==================== Auto Close ====================

    private fun startAutoCloseTask() {
        stopAutoCloseTask()
        autoCloseTask = submit(period = 400L, delay = 400L) {
            if (openDoors.isEmpty()) return@submit
            val iter = openDoors.entries.iterator()
            while (iter.hasNext()) {
                val (loc, _) = iter.next()
                val location = loc.toLocation() ?: run { iter.remove(); continue }

                val block = location.world?.getBlockAt(loc.x, loc.y, loc.z)
                if (block == null || block.type != Material.IRON_DOOR) { iter.remove(); continue }
                val door = block.blockData as? Door
                if (door == null || !door.isOpen) { iter.remove(); continue }

                val hasPlayerNearby = location.world?.getNearbyPlayers(location, 20.0)?.isNotEmpty() ?: false
                if (!hasPlayerNearby) {
                    closeDoor(loc)
                }
            }
        }
    }

    // ==================== Door Control ====================

    private fun openDoor(loc: DoorLocationKey) {
        val world = Bukkit.getWorld(loc.world) ?: return
        val block = world.getBlockAt(loc.x, loc.y, loc.z)
        if (block.type != Material.IRON_DOOR) return

        val door = block.blockData as? Door ?: return
        if (door.isOpen) return

        door.isOpen = true
        block.blockData = door

        // Also open the top half
        val topBlock = world.getBlockAt(loc.x, loc.y + 1, loc.z)
        if (topBlock.type == Material.IRON_DOOR) {
            val topDoor = topBlock.blockData as? Door
            if (topDoor != null && !topDoor.isOpen) {
                topDoor.isOpen = true
                topBlock.blockData = topDoor
            }
        }
    }

    private fun closeDoor(loc: DoorLocationKey) {
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

    // ==================== Listener ====================

    @SubscribeEvent
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.IRON_DOOR) return

        // Get door base position (bottom half)
        val doorData = block.blockData as? Door ?: return
        val baseBlock = if (doorData.half == Bisected.Half.BOTTOM) block else block.getRelative(BlockFace.DOWN)
        val doorLoc = DoorLocationKey(baseBlock.world.name, baseBlock.x, baseBlock.y, baseBlock.z)

        val doorConfig = doorsByLocation[doorLoc] ?: return

        // This door has a sertraline key config — handle the interaction
        event.isCancelled = true

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (item.type.isAir || item.amount <= 0) {
            sendActionBar(player, "<#E08A98>需要手持 <#F2F5FA>${doorConfig.name}</#F2F5FA> 钥匙")
            return
        }

        val sertralineId = Sertraline.api().getId(item)
        if (sertralineId != doorConfig.id) {
            sendActionBar(player, "<#E08A98>需要手持 <#F2F5FA>${doorConfig.name}</#F2F5FA> 钥匙")
            return
        }

        // Open the door
        openDoor(doorLoc)
        openDoors[doorLoc] = true

        // Deduct durability (best-effort, no early returns)
        try {
            val tag = item.getItemTag()
            if (tag != null) {
                val sData = tag["sertraline_data"]?.asCompound()
                if (sData != null) {
                    val dur = sData["durability"]?.asInt() ?: 0
                    val newDur = dur - doorConfig.durabilityCost
                    if (newDur <= 0) {
                        item.amount--
                    } else {
                        sData.put("durability", newDur)
                        tag.saveTo(item)
                        item.rebuildDisplay(player)
                    }
                    player.updateInventory()
                }
            }
        } catch (_: Exception) { /* durability deduction best-effort */ }

        // Play sound
        player.world.playSound(player.location, "minecraft:block.iron_door.open", 1.0f, 1.0f)
    }

    // ==================== Utility ====================

    private fun sendActionBar(player: Player, message: String) {
        try {
            (player as Audience).sendActionBar(mmUtil.deserialize(message.legacyToMiniMessage()))
        } catch (_: Exception) {}
    }

    fun getDoorBaseLocation(block: org.bukkit.block.Block): DoorLocationKey? {
        if (block.type != Material.IRON_DOOR) return null
        val door = block.blockData as? Door ?: return null
        val baseBlock = if (door.half == Bisected.Half.BOTTOM) block else block.getRelative(BlockFace.DOWN)
        return DoorLocationKey(baseBlock.world.name, baseBlock.x, baseBlock.y, baseBlock.z)
    }
}

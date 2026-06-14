package io.github.zzzyyylllty.chotentech.feature.signpass

import io.github.zzzyyylllty.chotentech.feature.keydoor.DoorLocationKey
import io.github.zzzyyylllty.chotentech.feature.keydoor.KeyDoorManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import java.util.Collections
import java.util.HashSet
import java.util.UUID
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ==================== Data Classes ====================

sealed class SignAction {
    abstract fun execute(player: Player)

    data class OpenDoor(val location: DoorLocationKey) : SignAction() {
        override fun execute(player: Player) {
            val world = Bukkit.getWorld(location.world) ?: return
            val block = world.getBlockAt(location.x, location.y, location.z)
            if (block.type.name.contains("DOOR")) {
                KeyDoorManager.openDoorAndTrack(location)
            }
        }
    }

    data class ConsoleCommand(val command: String) : SignAction() {
        override fun execute(player: Player) {
            val parsed = command.replace("{{player}}", player.name)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)
        }
    }
}

data class SignPasswordConfig(
    val location: LocationKey,
    val password: String,
    val actions: List<SignAction>
)

data class LocationKey(val world: String, val x: Int, val y: Int, val z: Int)

// ==================== Manager ====================

object SignPassManager {

    private val configs = ConcurrentHashMap<LocationKey, SignPasswordConfig>()
    private val waitingPlayers = Collections.synchronizedSet(HashSet<UUID>())

    fun loadConfig() {
        configs.clear()
        waitingPlayers.clear()

        val dir = File(getDataFolder(), "signpass")
        if (!dir.exists()) releaseResourceFolder("signpass")

        val file = File(dir, "config.yml")
        if (!file.exists()) return

        val config = Configuration.loadFromFile(file)
        val signs = config.getList("signs") ?: return

        for (obj in signs) {
            try {
                val map = obj as? Map<*, *> ?: continue
                val locMap = map["location"] as? Map<*, *> ?: continue
                val world = locMap["world"]?.toString() ?: continue
                val x = (locMap["x"] as? Number)?.toInt() ?: continue
                val y = (locMap["y"] as? Number)?.toInt() ?: continue
                val z = (locMap["z"] as? Number)?.toInt() ?: continue
                val password = map["password"]?.toString() ?: continue

                val actionsRaw = map["actions"] as? List<*> ?: emptyList<Any>()
                val actions = mutableListOf<SignAction>()
                for (actObj in actionsRaw) {
                    val actMap = actObj as? Map<*, *> ?: continue
                    val type = actMap["type"]?.toString() ?: continue
                    when (type) {
                        "door" -> {
                            val aw = actMap["world"]?.toString() ?: world
                            val ax = (actMap["x"] as? Number)?.toInt() ?: continue
                            val ay = (actMap["y"] as? Number)?.toInt() ?: continue
                            val az = (actMap["z"] as? Number)?.toInt() ?: continue
                            actions.add(SignAction.OpenDoor(DoorLocationKey(aw, ax, ay, az)))
                        }
                        "command" -> {
                            val cmd = actMap["command"]?.toString() ?: continue
                            actions.add(SignAction.ConsoleCommand(cmd))
                        }
                    }
                }

                val key = LocationKey(world, x, y, z)
                configs[key] = SignPasswordConfig(key, password, actions)
            } catch (_: Exception) { /* skip */ }
        }
    }

    fun getConfig(loc: LocationKey): SignPasswordConfig? = configs[loc]

    fun isWaiting(player: Player): Boolean = waitingPlayers.contains(player.uniqueId)

    fun setWaiting(player: Player) {
        waitingPlayers.add(player.uniqueId)
    }

    fun removeWaiting(player: Player) {
        waitingPlayers.remove(player.uniqueId)
    }

    fun isWaiting(uid: UUID): Boolean = waitingPlayers.contains(uid)

    fun removeWaiting(uid: UUID) {
        waitingPlayers.remove(uid)
    }

    fun executeActions(actions: List<SignAction>, player: Player) {
        for (action in actions) {
            action.execute(player)
        }
    }

    fun cleanup() {
        configs.clear()
        waitingPlayers.clear()
    }
}

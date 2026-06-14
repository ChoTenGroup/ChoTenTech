package io.github.zzzyyylllty.chotentech.function.area6

import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Area6Teleporter {

    private data class Waypoint(
        val id: Int,
        val x: Double,
        val y: Double,
        val z: Double
    ) {
        fun toLocation(world: World) = Location(world, x, y, z)
    }

    private val waypoints = listOf(
        Waypoint(1, 601056.44, 1.00, -16.41),
        Waypoint(2, 601604.0, 1.0, -17.0),
        Waypoint(3, 601055.84, 1.00, -156.62),
        Waypoint(4, 601600.0, 1.0, -157.0),
        Waypoint(5, 601059.0, 1.0, -297.0),
        Waypoint(6, 601604.0, 1.0, -289.0)
    )

    private const val WORLD_NAME = "Area6"
    private const val TRIGGER_RADIUS_SQ = 25.0 // 5^2
    private const val TASK_INTERVAL = 10L // ticks (0.5s)
    private const val PARTICLE_HEIGHT = 3.0
    private const val PARTICLE_STEP = 0.5

    // player UUID -> (target waypoint id -> source waypoint id)
    private val playerLastSource = ConcurrentHashMap<UUID, MutableMap<Int, Int>>()

    // player UUID -> waypoints that have been triggered and still in range
    private val playerTriggered = ConcurrentHashMap<UUID, MutableSet<Int>>()

    fun startTask() {
        submit(period = TASK_INTERVAL) {
            val world = Bukkit.getWorld(WORLD_NAME) ?: return@submit

            // find level-60 players in Area6
            val l60Players = world.players.filter { p ->
                val level = p.getPlayerLevel()
                level != null && (level.number == "60" || level.id == "level-60")
            }

            if (l60Players.isEmpty()) return@submit

            // emit white_ash particles at all waypoints (bottom to top)
            val steps = (PARTICLE_HEIGHT / PARTICLE_STEP).toInt()
            for (wp in waypoints) {
                val base = wp.toLocation(world)
                for (i in 0..steps) {
                    val loc = base.clone().add(0.0, i * PARTICLE_STEP, 0.0)
                    world.spawnParticle(Particle.WHITE_ASH, loc, 1, 0.0, 0.0, 0.0, 0.0)
                }
            }

            // process each level-60 player
            for (player in l60Players) {
                processPlayer(player, world)
            }
        }
    }

    private fun processPlayer(player: Player, world: World) {
        val pLoc = player.location
        val uuid = player.uniqueId
        val triggered = playerTriggered.getOrPut(uuid) { mutableSetOf() }

        // clean up triggered waypoints that player has left
        triggered.removeIf { wpId ->
            val wp = waypoints.firstOrNull { it.id == wpId } ?: return@removeIf true
            pLoc.distanceSquared(wp.toLocation(world)) > TRIGGER_RADIUS_SQ
        }

        // check proximity to each waypoint
        for (wp in waypoints) {
            if (wp.id in triggered) continue

            val wpLoc = wp.toLocation(world)
            if (pLoc.distanceSquared(wpLoc) > TRIGGER_RADIUS_SQ) continue

            // determine destination based on rules
            val destId = getDestination(uuid, wp.id)
            val destWp = waypoints.firstOrNull { it.id == destId } ?: continue

            val destLoc = destWp.toLocation(world).apply {
                yaw = player.location.yaw
                pitch = player.location.pitch
            }
            player.teleport(destLoc)

            // track: destination waypoint was reached from current waypoint
            playerLastSource.getOrPut(uuid) { mutableMapOf() }[destId] = wp.id

            // mark current waypoint as triggered (needs leave-range to retrigger)
            triggered.add(wp.id)
        }
    }

    /**
     * Teleportation rules:
     *  1 -> 3
     *  3 -> 2 if last arrival at 3 was from 1, else 3 -> 5
     *  5 -> 4 if last arrival at 5 was from 3, else random among {1,2,3,4,6}
     *  6 -> 1
     *  2, 4, or any other: random among the other 5 waypoints
     */
    private fun getDestination(uuid: UUID, currentWp: Int): Int {
        val sources = playerLastSource[uuid] ?: emptyMap()
        val allIds = waypoints.map { it.id }

        return when (currentWp) {
            1 -> 3
            6 -> 1
            3 -> {
                if (sources[3] == 1) 2 else 5
            }
            5 -> {
                if (sources[5] == 3) 4 else {
                    (allIds - 5).random()
                }
            }
            else -> (allIds - currentWp).random()
        }
    }

    // cleanup player data on quit
    @SubscribeEvent
    fun onPlayerQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        playerLastSource.remove(uuid)
        playerTriggered.remove(uuid)
    }
}

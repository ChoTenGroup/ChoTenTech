package io.github.zzzyyylllty.chotentech.worldgeneration.l2

import java.lang.Math.floorDiv
import java.lang.Math.floorMod
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class Level2Region(val corridorRadius: Int, val ceiling: Int) {
    PIPE(4, 5), DUST(3, 5), INDUSTRIAL(6, 8), CLAY(4, 6),
}

internal enum class Level2Space {
    SOLID, CORRIDOR, DOORWAY, ROOM, RAILWAY,
}

internal enum class Level2RoomUse { EMPTY, STORAGE, WORKSHOP, OFFICE, UTILITY }

internal data class Level2Column(
    val space: Level2Space,
    val region: Level2Region,
    val ceiling: Int,
    val core: Boolean,
    val roomUse: Level2RoomUse? = null,
)

/** All coordinates are world blocks. A chunk and its neighbours sample the same layout. */
internal class Level2Layout(private val seed: Long) {
    private val rooms = HashMap<Pair<Int, Int>, Room?>()

    fun column(x: Int, z: Int): Level2Column {
        if (minOf(abs(x), abs(z)) <= railwayRadius(x, z)) {
            return Level2Column(Level2Space.RAILWAY, Level2Region.INDUSTRIAL, 9, true)
        }

        val (primary, secondary, blend) = regionMix(x, z)
        val region = if (unit(x, z, 8) < blend) secondary else primary
        val widthVariation = 3.0 * (noise(x, z, 24, 10) - 0.5)
        val radius = (mix(primary.corridorRadius, secondary.corridorRadius, blend) + widthVariation)
            .roundToInt().coerceIn(2, 7)
        val roughness = if (primary == Level2Region.CLAY || secondary == Level2Region.CLAY) {
            val clayWeight = (if (primary == Level2Region.CLAY) 1 - blend else 0.0) +
                (if (secondary == Level2Region.CLAY) blend else 0.0)
            3.5 * clayWeight * (noise(x, z, 6, 11) - 0.5)
        } else 0.0
        val ceiling = (mix(primary.ceiling, secondary.ceiling, blend) + roughness).roundToInt().coerceIn(4, 9)

        if (corridor(x, z, radius)) {
            return Level2Column(Level2Space.CORRIDOR, region, ceiling, corridor(x, z, 1))
        }

        val cellX = floorDiv(x, GRID_SIZE)
        val cellZ = floorDiv(z, GRID_SIZE)
        val localX = floorMod(x, GRID_SIZE)
        val localZ = floorMod(z, GRID_SIZE)
        val room = roomAt(cellX, cellZ)
        val fromWest = localX < JUNCTION && roomAt(cellX - 1, cellZ)
            ?.let { it.exit == Side.EAST && localZ in it.doorway.top until it.doorway.bottom } == true
        val fromNorth = localZ < JUNCTION && roomAt(cellX, cellZ - 1)
            ?.let { it.exit == Side.SOUTH && localX in it.doorway.left until it.doorway.right } == true
        return when {
            fromWest || fromNorth || room?.doorway?.contains(localX, localZ) == true ->
                Level2Column(Level2Space.DOORWAY, region, 4, true)
            room == null -> Level2Column(Level2Space.SOLID, region, ceiling, false)
            room.main.contains(localX, localZ) || room.annex?.contains(localX, localZ) == true ->
                Level2Column(Level2Space.ROOM, region, ceiling + 2, false, room.use)
            else -> Level2Column(Level2Space.SOLID, region, ceiling, false)
        }
    }

    private fun railwayRadius(x: Int, z: Int): Int {
        val along = if (abs(x) <= abs(z)) z else x
        return 9 + ((MAX_RAILWAY_RADIUS - 9) * noise(along, 0, 64, 12)).roundToInt()
    }

    private fun corridor(x: Int, z: Int, radius: Int): Boolean {
        val nearX = abs(floorMod(x, GRID_SIZE) - JUNCTION) <= radius
        val nearZ = abs(floorMod(z, GRID_SIZE) - JUNCTION) <= radius
        if (!nearX && !nearZ) return false
        if (nearX && nearZ) return true
        return if (nearX) {
            edge(floorDiv(x, GRID_SIZE), floorDiv(z - JUNCTION, GRID_SIZE), vertical = true)
        } else {
            edge(floorDiv(x - JUNCTION, GRID_SIZE), floorDiv(z, GRID_SIZE), vertical = false)
        }
    }

    // Every junction has a route toward the origin. Additional edges make loops.
    private fun edge(x: Int, z: Int, vertical: Boolean): Boolean {
        if ((vertical && x == 0) || (!vertical && z == 0)) return true
        if (unit(x, z, if (vertical) 21 else 22) < 0.24) return true
        if (vertical) {
            val outerZ = if (z >= 0) z + 1 else z
            return unit(x, outerZ, 23) < 0.5
        }
        val outerX = if (x >= 0) x + 1 else x
        return unit(outerX, z, 23) >= 0.5
    }

    private fun roomAt(x: Int, z: Int): Room? {
        val key = x to z
        if (key !in rooms) rooms[key] = createRoom(x, z)
        return rooms[key]
    }

    private fun createRoom(x: Int, z: Int): Room? {
        val random = Random(hash(x, z, 30))
        if (random.nextInt(100) >= 48) return null
        val sides = buildList {
            if (edge(x, z, vertical = true)) add(Side.WEST)
            if (edge(x + 1, z, vertical = true)) add(Side.EAST)
            if (edge(x, z, vertical = false)) add(Side.NORTH)
            if (edge(x, z + 1, vertical = false)) add(Side.SOUTH)
        }
        if (sides.isEmpty()) return null

        val width = random.nextInt(12, 39)
        val depth = if (random.nextInt(3) == 0) width else random.nextInt(12, 39)
        // The railway straddles zero and takes some of the last cell on the negative side.
        // Leave a solid wall there except where the room's doorway joins the tunnel.
        val eastLimit = if (x == -1) GRID_SIZE - MAX_RAILWAY_RADIUS - 1 else 93
        val southLimit = if (z == -1) GRID_SIZE - MAX_RAILWAY_RADIUS - 1 else 93
        val left = random.nextInt(23, minOf(91, eastLimit + 1) - width)
        val top = random.nextInt(23, minOf(91, southLimit + 1) - depth)
        val main = Rect(left, top, left + width, top + depth)
        val midX = left + width / 2
        val midZ = top + depth / 2
        // Start inside the room and reach the center of an existing corridor.
        val exit = sides.random(random)
        val doorway = when (exit) {
            Side.WEST -> Rect(JUNCTION, midZ - 1, left + 2, midZ + 2)
            Side.EAST -> Rect(main.right - 2, midZ - 1, GRID_SIZE, midZ + 2)
            Side.NORTH -> Rect(midX - 1, JUNCTION, midX + 2, top + 2)
            Side.SOUTH -> Rect(midX - 1, main.bottom - 2, midX + 2, GRID_SIZE)
        }
        val annex = if (random.nextInt(7) == 0) {
            val north = top - 19
            val south = southLimit - main.bottom
            val reach = random.nextInt(8, minOf(22, maxOf(north, south)) + 1)
            val annexLeft = (midX - random.nextInt(3, 8)).coerceAtLeast(19)
            val annexRight = (main.right + random.nextInt(3, 11)).coerceAtMost(eastLimit)
            if (north >= south) Rect(annexLeft, top - reach, annexRight, top + 3)
            else Rect(annexLeft, main.bottom - 3, annexRight, main.bottom + reach)
        } else null
        val use = when (pick(x, z, 31, 100)) {
            in 0..35 -> Level2RoomUse.EMPTY
            in 36..55 -> Level2RoomUse.STORAGE
            in 56..72 -> Level2RoomUse.WORKSHOP
            in 73..86 -> Level2RoomUse.OFFICE
            else -> Level2RoomUse.UTILITY
        }
        return Room(main, annex, doorway, exit, use)
    }

    private fun regionMix(x: Int, z: Int): RegionMix {
        val warpedX = x + 30.0 * (noise(x, z, 53, 1) - 0.5)
        val warpedZ = z + 30.0 * (noise(x, z, 53, 2) - 0.5)
        val regionX = floor(warpedX / REGION_SIZE).toInt()
        val regionZ = floor(warpedZ / REGION_SIZE).toInt()
        var nearest = Double.POSITIVE_INFINITY
        var second = Double.POSITIVE_INFINITY
        var primary = Level2Region.PIPE
        var secondary = primary
        for (rx in regionX - 1..regionX + 1) for (rz in regionZ - 1..regionZ + 1) {
            val dx = warpedX - REGION_SIZE * (rx + 0.25 + 0.5 * unit(rx, rz, 3))
            val dz = warpedZ - REGION_SIZE * (rz + 0.25 + 0.5 * unit(rx, rz, 4))
            val distance = dx * dx + dz * dz
            if (distance < nearest) {
                second = nearest
                secondary = primary
                nearest = distance
                primary = regionType(rx, rz)
            } else if (distance < second) {
                second = distance
                secondary = regionType(rx, rz)
            }
        }
        // A distance gap of 20 blocks or less mixes the two palettes, including across chunks.
        val blend = 0.5 * smooth((1.0 - (sqrt(second) - sqrt(nearest)) / 20.0).coerceIn(0.0, 1.0))
        return RegionMix(primary, secondary, blend)
    }

    private fun regionType(x: Int, z: Int): Level2Region {
        val pair = (x xor z xor seed.toInt()) and 1
        val variant = if (unit(x, z, 5) < 0.5) 0 else 1
        return Level2Region.entries[pair * 2 + variant]
    }

    fun noise(x: Int, z: Int, span: Int, salt: Int): Double {
        val gx = floorDiv(x, span)
        val gz = floorDiv(z, span)
        val dx = smooth(floorMod(x, span).toDouble() / span)
        val dz = smooth(floorMod(z, span).toDouble() / span)
        return mix(
            mix(unit(gx, gz, salt), unit(gx + 1, gz, salt), dx),
            mix(unit(gx, gz + 1, salt), unit(gx + 1, gz + 1, salt), dx),
            dz,
        )
    }

    fun pick(x: Int, z: Int, salt: Int, bound: Int): Int = ((hash(x, z, salt) ushr 1) % bound).toInt()

    fun unit(x: Int, z: Int, salt: Int): Double = (hash(x, z, salt) ushr 11) / 9007199254740992.0

    private fun hash(x: Int, z: Int, salt: Int): Long {
        var value = seed xor (x * -7046029254386353131L) xor (z * -4658895280553007687L) xor
            (salt * -7723592293110705685L)
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    private fun smooth(value: Double): Double = value * value * (3.0 - 2.0 * value)

    private fun mix(a: Int, b: Int, weight: Double): Double = mix(a.toDouble(), b.toDouble(), weight)
    private fun mix(a: Double, b: Double, weight: Double): Double = a + (b - a) * weight

    private data class RegionMix(val primary: Level2Region, val secondary: Level2Region, val blend: Double)
    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, z: Int): Boolean = x in left until right && z in top until bottom
    }
    private enum class Side { WEST, EAST, NORTH, SOUTH }
    private data class Room(
        val main: Rect,
        val annex: Rect?,
        val doorway: Rect,
        val exit: Side,
        val use: Level2RoomUse,
    )

    private companion object {
        const val GRID_SIZE = 6 * 16
        const val JUNCTION = 8
        const val MAX_RAILWAY_RADIUS = 12
        const val REGION_SIZE = 10 * 16
    }
}

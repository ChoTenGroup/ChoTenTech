package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionLayout
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionType
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 走廊连通性模型（参考 `tinf/1corridors.kt`）。
 *
 * 1corridors 的思路：把区块看成迷宫格子，用「随机生长（frontier / Prim）」生成一棵生成树，
 * 每个区块记录 up/down/left/right 四个方向的开孔；四条外边界再各自保证至少一个开口。
 * 然后按开口数分配类型：
 *
 * - 开口数 == 1 → `CLOSED_ROOM`（尽端房）
 * - 开口数 >= 2 → 15% 概率是 `ROOM`，其余是 `CORRIDOR`
 *
 * 为了让无限世界可用，这里把世界切成 16×16 区块的「迷宫块」，每块用 `(seed, mx, mz)`
 * 派生独立随机种子生成生成树；块内一定全连通。
 *
 * **跨迷宫块的边界**不能各算各的，否则两边会不一致。因此边界上的开口一律用
 * 「全局边界 key」来决定（key 取坐标较大的一侧），两边算出来的结果必然相同；
 * 并且每条边界段再强制开一个口（同样由全局 key 决定），保证迷宫块之间一定连通。
 */
object CorridorMaze {

    /** 迷宫块的边长（区块）。 */
    const val MAZE_SIZE = 16

    /** 方向位。 */
    const val UP = 1      // -z（北）
    const val DOWN = 2    // +z（南）
    const val LEFT = 4    // -x（西）
    const val RIGHT = 8   // +x（东）

    /** 跨迷宫块边界的额外随机开口概率（%）。 */
    private const val OPEN_CHANCE = 35

    /** 开口数 >= 2 时成为房间的概率（%）。 */
    private const val ROOM_CHANCE = 15

    private const val SALT_MAZE = 2101L
    private const val SALT_EDGE_X = 2102L
    private const val SALT_EDGE_Z = 2103L
    private const val SALT_FORCE_X = 2104L
    private const val SALT_FORCE_Z = 2105L

    private const val MAX_CACHED = 512

    /** (dx, dz, bit)。 */
    private val DELTAS = listOf(
        intArrayOf(0, -1, UP),
        intArrayOf(0, 1, DOWN),
        intArrayOf(-1, 0, LEFT),
        intArrayOf(1, 0, RIGHT),
    )

    private data class MazeKey(val seed: Long, val mx: Int, val mz: Int)

    private class Maze(val mask: Array<IntArray>, val type: Array<IntArray>)

    private val cache = ConcurrentHashMap<MazeKey, Maze>()

    /** 该区块四个方向的开孔位掩码。 */
    fun openings(chunkX: Int, chunkZ: Int, seed: Long): Int {
        val maze = mazeOf(chunkX, chunkZ, seed)
        return maze.mask[Math.floorMod(chunkX, MAZE_SIZE)][Math.floorMod(chunkZ, MAZE_SIZE)]
    }

    /** 该区块的类型。 */
    fun type(chunkX: Int, chunkZ: Int, seed: Long): CorridorChunkType {
        val maze = mazeOf(chunkX, chunkZ, seed)
        return CorridorChunkType.entries[
            maze.type[Math.floorMod(chunkX, MAZE_SIZE)][Math.floorMod(chunkZ, MAZE_SIZE)]
        ]
    }

    fun isOpen(mask: Int, bit: Int): Boolean = (mask and bit) != 0

    private fun mazeOf(chunkX: Int, chunkZ: Int, seed: Long): Maze {
        val mx = Math.floorDiv(chunkX, MAZE_SIZE)
        val mz = Math.floorDiv(chunkZ, MAZE_SIZE)
        if (cache.size > MAX_CACHED) cache.clear()
        return cache.getOrPut(MazeKey(seed, mx, mz)) { build(seed, mx, mz) }
    }

    private fun build(seed: Long, mx: Int, mz: Int): Maze {
        val n = MAZE_SIZE
        val mask = Array(n) { IntArray(n) }
        val type = Array(n) { IntArray(n) }
        val visited = Array(n) { BooleanArray(n) }
        val rand = Random(mix(seed, SALT_MAZE, mx, mz))

        // ---- 1. 块内生成树：随机 frontier（与 1corridors 的算法一致），保证全连通 ----
        val startX = rand.nextInt(n)
        val startZ = rand.nextInt(n)
        visited[startX][startZ] = true

        // frontier 项：(cx, cz, bit, parentX, parentZ)
        val frontier = mutableListOf<IntArray>()

        fun pushNeighbours(cx: Int, cz: Int) {
            for (d in DELTAS) {
                val nx = cx + d[0]
                val nz = cz + d[1]
                if (nx !in 0 until n || nz !in 0 until n) continue
                if (visited[nx][nz]) continue
                frontier.add(intArrayOf(nx, nz, d[2], cx, cz))
            }
        }

        pushNeighbours(startX, startZ)
        while (frontier.isNotEmpty()) {
            val item = frontier.removeAt(rand.nextInt(frontier.size))
            val cx = item[0]
            val cz = item[1]
            if (visited[cx][cz]) continue

            val bit = item[2]
            val px = item[3]
            val pz = item[4]
            // bit 是「从父格指向子格」的方向：
            // 父格记录该方向（通向子格），子格记录相反方向（通回父格）。
            // 曾经两边都写 bit，导致左右/上下方向反了 —— 开口对不上、迷宫整体不连通。
            mask[px][pz] = mask[px][pz] or bit
            mask[cx][cz] = mask[cx][cz] or opposite(bit)
            visited[cx][cz] = true
            pushNeighbours(cx, cz)
        }

        // ---- 2. 跨迷宫块边界：用全局边界 key，保证两侧一致 ----
        for (i in 0 until n) {
            val wx = mx * n + i
            val wz = mz * n + i

            // 北边界（线 z = mz*n，key 取较高的 mz）
            if (edgeOpen(seed, wx, mz * n, SALT_EDGE_Z)) mask[i][0] = mask[i][0] or UP
            // 南边界（线 z = (mz+1)*n，key 取较高的 mz+1）
            if (edgeOpen(seed, wx, (mz + 1) * n, SALT_EDGE_Z)) mask[i][n - 1] = mask[i][n - 1] or DOWN
            // 西边界（线 x = mx*n，key 取较高的 mx）
            if (edgeOpen(seed, mx * n, wz, SALT_EDGE_X)) mask[0][i] = mask[0][i] or LEFT
            // 东边界（线 x = (mx+1)*n，key 取较高的 mx+1）
            if (edgeOpen(seed, (mx + 1) * n, wz, SALT_EDGE_X)) mask[n - 1][i] = mask[n - 1][i] or RIGHT
        }

        // ---- 3. 每条边界段强制开一个口，保证迷宫块之间一定连通 ----
        val iNorth = Math.floorMod(hash(seed, mx, mz, SALT_FORCE_Z), n)
        mask[iNorth][0] = mask[iNorth][0] or UP
        val iSouth = Math.floorMod(hash(seed, mx, mz + 1, SALT_FORCE_Z), n)
        mask[iSouth][n - 1] = mask[iSouth][n - 1] or DOWN
        val iWest = Math.floorMod(hash(seed, mx, mz, SALT_FORCE_X), n)
        mask[0][iWest] = mask[0][iWest] or LEFT
        val iEast = Math.floorMod(hash(seed, mx + 1, mz, SALT_FORCE_X), n)
        mask[n - 1][iEast] = mask[n - 1][iEast] or RIGHT

        // ---- 3.5 每个格子至少要有一条通到「非实心区域」的开口 ----
        // RegionLayout 里 WALLS / UNBREAKABLE 是实心填充区域，开口朝着它们等于没有出口，
        // 该格子就成了进不去的密闭房间。这里给这种格子改开一条朝向非实心邻居的边；
        // 改动同时写在两侧（迷宫表是整块共享的），所以交界仍然一致。
        // 只动迷宫块内部的边：跨块边界由第 2/3 步的边界逻辑负责。
        for (x in 0 until n) {
            for (z in 0 until n) {
                val worldX = mx * n + x
                val worldZ = mz * n + z
                if (hasRealExit(worldX, worldZ, mask[x][z], seed)) continue
                for (d in DELTAS) {
                    val nx = x + d[0]
                    val nz = z + d[1]
                    if (nx !in 0 until n || nz !in 0 until n) continue
                    if (isSolidRegion(seed, worldX + d[0], worldZ + d[1])) continue
                    mask[x][z] = mask[x][z] or d[2]
                    mask[nx][nz] = mask[nx][nz] or opposite(d[2])
                    break
                }
            }
        }

        // ---- 4. 分配类型（与 1corridors 一致） ----
        for (x in 0 until n) {
            for (z in 0 until n) {
                if (mask[x][z] == 0) mask[x][z] = UP          // 安全保护，理论上不会发生
                type[x][z] = when {
                    Integer.bitCount(mask[x][z]) == 1 -> CorridorChunkType.CLOSED_ROOM.ordinal
                    rand.nextDouble() < ROOM_CHANCE / 100.0 -> CorridorChunkType.ROOM.ordinal
                    else -> CorridorChunkType.CORRIDOR.ordinal
                }
            }
        }

        return Maze(mask, type)
    }

    private fun opposite(bit: Int): Int = when (bit) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        else -> LEFT
    }

    /** 这个格子的开口里，是否至少有一个朝向「非实心区域」。 */
    private fun hasRealExit(worldX: Int, worldZ: Int, mask: Int, seed: Long): Boolean {
        for (d in DELTAS) {
            if (!isOpen(mask, d[2])) continue
            if (!isSolidRegion(seed, worldX + d[0], worldZ + d[1])) return true
        }
        return false
    }

    /** WALLS / UNBREAKABLE 是实心填充区域。 */
    private fun isSolidRegion(seed: Long, chunkX: Int, chunkZ: Int): Boolean {
        val type = RegionLayout.typeAt(seed, chunkX, chunkZ)
        return type == RegionType.WALLS || type == RegionType.UNBREAKABLE
    }

    private fun edgeOpen(seed: Long, a: Int, b: Int, salt: Long): Boolean =
        hash(seed, a, b, salt) % 100 < OPEN_CHANCE

    private fun mix(seed: Long, salt: Long, a: Int, b: Int): Long {
        var h = seed xor salt
        h = h * 6364136223846793005L + a
        h = h * 6364136223846793005L + b
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return h
    }

    private fun hash(seed: Long, a: Int, b: Int, salt: Long): Int =
        (mix(seed, salt, a, b) and 0x7FFFFFFF).toInt()
}

/** 走廊区块类型（与 `tinf/1corridors.kt` 的 BlockType 对应）。 */
enum class CorridorChunkType {
    /** 开口 >= 2，普通走廊。 */
    CORRIDOR,

    /** 开口 >= 2 且抽中房间概率，开口更宽并带内部装饰。 */
    ROOM,

    /** 开口 == 1，尽端房。 */
    CLOSED_ROOM,
}

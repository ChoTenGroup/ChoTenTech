import kotlin.random.Random

enum class Dir { UP, DOWN, LEFT, RIGHT }
enum class BlockType { CORRIDOR, ROOM, CLOSED_ROOM }

fun main() {
    val chunkXMin = -4
    val chunkXMax =  4
    val chunkYMin = -4
    val chunkYMax =  4

    val numChunksX = chunkXMax - chunkXMin + 1
    val numChunksY = chunkYMax - chunkYMin + 1
    val totalChunks = numChunksX * numChunksY

    val rand = Random(12345)   // 固定种子，可复现

    data class Chunk(var up: Boolean = false,
                     var down: Boolean = false,
                     var left: Boolean = false,
                     var right: Boolean = false,
                     var visited: Boolean = false)

    val chunks = Array(numChunksX) { Array(numChunksY) { Chunk() } }

    fun toIndex(cx: Int, cy: Int) = (cx - chunkXMin) to (cy - chunkYMin)

    fun isValid(cx: Int, cy: Int) =
        cx in chunkXMin..chunkXMax && cy in chunkYMin..chunkYMax

    fun getNeighbors(cx: Int, cy: Int): List<Pair<Pair<Int, Int>, Dir>> {
        val deltas = listOf(
            (0 to -1) to Dir.UP,
            (0 to  1) to Dir.DOWN,
            (-1 to 0) to Dir.LEFT,
            (1  to 0) to Dir.RIGHT
        )
        return deltas.mapNotNull { (delta, dir) ->
            val (dx, dy) = delta
            val nx = cx + dx
            val ny = cy + dy
            if (isValid(nx, ny)) (nx to ny) to dir else null
        }
    }

    fun pickStart(): Pair<Int, Int> {
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (cx in chunkXMin..chunkXMax) {
            for (cy in chunkYMin..chunkYMax) {
                if (cx == chunkXMin || cx == chunkXMax || cy == chunkYMin || cy == chunkYMax) {
                    candidates.add(cx to cy)
                }
            }
        }
        return candidates.random(rand)
    }

    val (startX, startY) = pickStart()
    val (si, sj) = toIndex(startX, startY)
    chunks[si][sj].visited = true
    var visitedCount = 1

    data class FrontierItem(val x: Int, val y: Int, val dir: Dir, val parentX: Int, val parentY: Int)

    val frontier = mutableListOf<FrontierItem>()
    for ((coord, dir) in getNeighbors(startX, startY)) {
        val (nx, ny) = coord
        val (ni, nj) = toIndex(nx, ny)
        if (!chunks[ni][nj].visited) {
            frontier.add(FrontierItem(nx, ny, dir, startX, startY))
        }
    }

    while (visitedCount < totalChunks && frontier.isNotEmpty()) {
        val index = rand.nextInt(frontier.size)
        val item = frontier.removeAt(index)
        val (cx, cy, dir, px, py) = item
        val (ci, cj) = toIndex(cx, cy)
        if (chunks[ci][cj].visited) continue

        val (pi, pj) = toIndex(px, py)
        when (dir) {
            Dir.UP -> {
                chunks[pi][pj].up = true
                chunks[ci][cj].down = true
            }
            Dir.DOWN -> {
                chunks[pi][pj].down = true
                chunks[ci][cj].up = true
            }
            Dir.LEFT -> {
                chunks[pi][pj].left = true
                chunks[ci][cj].right = true
            }
            Dir.RIGHT -> {
                chunks[pi][pj].right = true
                chunks[ci][cj].left = true
            }
        }
        chunks[ci][cj].visited = true
        visitedCount++

        for ((coord, ndir) in getNeighbors(cx, cy)) {
            val (nx, ny) = coord
            val (ni, nj) = toIndex(nx, ny)
            if (!chunks[ni][nj].visited) {
                frontier.add(FrontierItem(nx, ny, ndir, cx, cy))
            }
        }
    }

    // ---------- 修改后的外围开口逻辑 ----------
    // 对四条边界分别处理，确保每条边界至少有一个开口，开口概率约1/3
    fun ensureBoundaryOpen(boundaryCoords: List<Pair<Int, Int>>, dir: Dir) {
        // 收集可开口的区块（已经存在的开口不重复添加）
        val candidates = boundaryCoords.filter { (cx, cy) ->
            val (i, j) = toIndex(cx, cy)
            val chunk = chunks[i][j]
            when (dir) {
                Dir.LEFT -> !chunk.left
                Dir.RIGHT -> !chunk.right
                Dir.UP -> !chunk.up
                Dir.DOWN -> !chunk.down
            }
        }
        if (candidates.isEmpty()) return // 已经有开口，无需额外添加

        // 按概率随机开口：概率 1/3 左右（这里设为0.3）
        val openProb = 0.3
        val toOpen = candidates.filter { rand.nextDouble() < openProb }
        // 如果随机后没有开口，则强制选第一个候选开口
        val finalList = if (toOpen.isNotEmpty()) toOpen else listOf(candidates.first())

        for ((cx, cy) in finalList) {
            val (i, j) = toIndex(cx, cy)
            val chunk = chunks[i][j]
            when (dir) {
                Dir.LEFT -> chunk.left = true
                Dir.RIGHT -> chunk.right = true
                Dir.UP -> chunk.up = true
                Dir.DOWN -> chunk.down = true
            }
        }
    }

    // 收集各边界的区块坐标
    val leftBoundary  = (chunkYMin..chunkYMax).map { chunkXMin to it }
    val rightBoundary = (chunkYMin..chunkYMax).map { chunkXMax to it }
    val topBoundary   = (chunkXMin..chunkXMax).map { it to chunkYMin }
    val bottomBoundary= (chunkXMin..chunkXMax).map { it to chunkYMax }

    ensureBoundaryOpen(leftBoundary, Dir.LEFT)
    ensureBoundaryOpen(rightBoundary, Dir.RIGHT)
    ensureBoundaryOpen(topBoundary, Dir.UP)
    ensureBoundaryOpen(bottomBoundary, Dir.DOWN)

    // 分配类型：开口数1 → 封闭房间，开口数≥2 → 15%普通房间，其余走廊
    data class ChunkType(val type: BlockType, val directions: Set<Dir>)

    val chunkTypes = Array(numChunksX) { x ->
        Array(numChunksY) { y ->
            val chunk = chunks[x][y]
            val dirs = mutableSetOf<Dir>()
            if (chunk.up) dirs.add(Dir.UP)
            if (chunk.down) dirs.add(Dir.DOWN)
            if (chunk.left) dirs.add(Dir.LEFT)
            if (chunk.right) dirs.add(Dir.RIGHT)
            if (dirs.isEmpty()) dirs.add(Dir.UP) // 安全保护

            val type = when (dirs.size) {
                1 -> BlockType.CLOSED_ROOM
                else -> if (rand.nextDouble() < 0.15) BlockType.ROOM else BlockType.CORRIDOR
            }
            ChunkType(type, dirs)
        }
    }

    fun printMap() {
        for (chunkRow in 0 until numChunksY) {
            for (innerRow in 0..2) {
                for (chunkCol in 0 until numChunksX) {
                    val (type, dirs) = chunkTypes[chunkCol][chunkRow]
                    val centerChar = when (type) {
                        BlockType.CORRIDOR -> "⬜"
                        BlockType.ROOM -> "🟦"
                        BlockType.CLOSED_ROOM -> "🟪"
                    }
                    val openChar = "⬜" // 开口统一白色
                    for (innerCol in 0..2) {
                        val ch = when {
                            innerRow == 1 && innerCol == 1 -> centerChar
                            innerRow == 0 && innerCol == 1 -> if (dirs.contains(Dir.UP)) openChar else "⬛"
                            innerRow == 2 && innerCol == 1 -> if (dirs.contains(Dir.DOWN)) openChar else "⬛"
                            innerRow == 1 && innerCol == 0 -> if (dirs.contains(Dir.LEFT)) openChar else "⬛"
                            innerRow == 1 && innerCol == 2 -> if (dirs.contains(Dir.RIGHT)) openChar else "⬛"
                            else -> "⬛"
                        }
                        print(ch)
                    }
                    if (chunkCol < numChunksX - 1) print(" ")
                }
                println()
            }
            if (chunkRow < numChunksY - 1) println()
        }
    }

    printMap()
}
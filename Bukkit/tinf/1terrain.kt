import kotlin.random.Random

enum class RegionType(val symbol: String) {
    UNBREAKABLE("🟥"),
    WALLS("⬛"),
    CORRIDORS("⬜"),
    AQYILA("🟦"),
    GILD("🟫"),
    GOTHIC("🟨"),
    OUROBOROS("🟪"),
    GARDEN("🟩"),
    FABLED("🟧")
}

fun main() {
    val width = 20
    val height = 20
    val grid = Array(width) { arrayOfNulls<RegionType>(height) }
    val rand = Random(12345)

    fun canPlace(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (x < 0 || y < 0 || x + w > width || y + h > height) return false
        for (i in x until x + w) for (j in y until y + h) if (grid[i][j] != null) return false
        return true
    }

    fun canPlaceReplace(x: Int, y: Int, w: Int, h: Int, replaceable: Set<RegionType>): Boolean {
        if (x < 0 || y < 0 || x + w > width || y + h > height) return false
        for (i in x until x + w) for (j in y until y + h) {
            val g = grid[i][j]
            if (g != null && g !in replaceable) return false
        }
        return true
    }

    fun place(x: Int, y: Int, w: Int, h: Int, type: RegionType) {
        for (i in x until x + w) for (j in y until y + h) grid[i][j] = type
    }

    fun getMaxSize(x: Int, y: Int): Pair<Int, Int> {
        var w = 0
        while (x + w < width && grid[x + w][y] == null) w++
        var h = 0
        while (y + h < height && grid[x][y + h] == null) h++
        return w to h
    }

    fun tryPlace(type: RegionType, sizes: List<Pair<Int, Int>>, attempts: Int) {
        repeat(attempts) {
            val (w, h) = sizes.random(rand)
            if (w > width || h > height) return@repeat
            val x = rand.nextInt(width - w + 1)
            val y = rand.nextInt(height - h + 1)
            if (canPlace(x, y, w, h)) place(x, y, w, h, type)
        }
    }

    fun tryPlaceGothic(attempts: Int) {
        val sizes = listOf(3 to 3, 4 to 4)
        repeat(attempts) {
            val (w, h) = sizes.random(rand)
            val x = rand.nextInt(width - w + 1)
            val y = rand.nextInt(height - h + 1)
            if (canPlaceReplace(x, y, w, h, setOf(RegionType.WALLS, RegionType.CORRIDORS))) {
                place(x, y, w, h, RegionType.GOTHIC)
            }
        }
    }

    fun shuffledPositions(): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until width) for (y in 0 until height) list.add(x to y)
        list.shuffle(rand)
        return list
    }

    fun placeOuroborosScan(targetCount: Int) {
        var placed = 0
        for ((x, y) in shuffledPositions()) {
            if (placed >= targetCount) break
            if (grid[x][y] != null) continue
            val r = rand.nextInt(10)
            val (w, h) = when {
                r < 4 -> 2 to 3
                r < 8 -> 2 to 4
                else -> 2 to 2
            }
            if (canPlace(x, y, w, h)) {
                place(x, y, w, h, RegionType.OUROBOROS)
                placed++
            }
        }
    }

    /**
     * GARDEN 扫描式放置。
     * targetCount 是主目标数量；密集扩展每个只在附近补 0~1 个，总上限 targetCount + 2。
     */
    fun placeGardenScan(targetCount: Int) {
        var placed = 0
        val hardLimit = targetCount + 2
        for ((x, y) in shuffledPositions()) {
            if (placed >= targetCount) break
            if (x + 2 > width || y + 2 > height) continue
            if (!canPlaceReplace(x, y, 2, 2, setOf(RegionType.WALLS))) continue
            place(x, y, 2, 2, RegionType.GARDEN)
            placed++
            // 密集扩展：仅 0~1 个
            repeat(rand.nextInt(0, 2)) {
                val nx = (x + rand.nextInt(-2, 3)).coerceIn(0, width - 2)
                val ny = (y + rand.nextInt(-2, 3)).coerceIn(0, height - 2)
                if (placed < hardLimit &&
                    canPlaceReplace(nx, ny, 2, 2, setOf(RegionType.WALLS))) {
                    place(nx, ny, 2, 2, RegionType.GARDEN)
                    placed++
                }
            }
        }
    }

    fun weightedFill() {
        val allTypes = listOf(
            RegionType.AQYILA to listOf(2 to 6, 6 to 2, 2 to 4, 4 to 2, 2 to 2),
            RegionType.GILD to listOf(8 to 4, 4 to 8, 2 to 4, 4 to 2, 4 to 4, 2 to 2),
            RegionType.GOTHIC to listOf(4 to 4, 3 to 3),
            RegionType.OUROBOROS to listOf(2 to 3, 2 to 4, 2 to 2),
            RegionType.GARDEN to listOf(2 to 2),
            RegionType.FABLED to listOf(2 to 2),
            RegionType.CORRIDORS to listOf(1 to 5, 5 to 1, 1 to 4, 4 to 1, 3 to 3, 2 to 3, 3 to 2, 1 to 3, 3 to 1, 2 to 2)
        )
        val weights = listOf(30, 24, 10, 8, 4, 6, 8)   // GARDEN 权重 8 → 4

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (grid[x][y] != null) continue
                val (maxW, maxH) = getMaxSize(x, y)
                val area = maxW * maxH
                val skipChance = when {
                    maxW == 1 && maxH == 1 -> 0.55
                    area <= 4 -> 0.30
                    area <= 9 -> 0.15
                    else -> 0.05
                }
                if (rand.nextDouble() < skipChance) continue

                var r = rand.nextInt(weights.sum())
                var chosen = 0
                for (i in weights.indices) {
                    if (r < weights[i]) { chosen = i; break }
                    r -= weights[i]
                }
                val order = listOf(chosen) + (allTypes.indices).filter { it != chosen }
                for (idx in order) {
                    val (type, sizes) = allTypes[idx]
                    val valid = sizes.filter { (w, h) -> w <= maxW && h <= maxH && canPlace(x, y, w, h) }
                    if (valid.isNotEmpty()) {
                        val (w, h) = valid.maxByOrNull { it.first * it.second }!!
                        place(x, y, w, h, type)
                        break
                    }
                }
            }
        }
    }

    // ---- 主放置顺序 ----
    tryPlace(RegionType.UNBREAKABLE, listOf(1 to 1, 1 to 1, 1 to 2, 2 to 1, 1 to 3, 3 to 1), 8)
    tryPlace(RegionType.WALLS, listOf(1 to 2, 2 to 1, 1 to 3, 3 to 1), 6)
    tryPlace(RegionType.CORRIDORS, listOf(1 to 3, 3 to 1, 1 to 4, 4 to 1, 1 to 5, 5 to 1, 2 to 2, 3 to 3, 2 to 3, 3 to 2), 15)

    // 1. AQYILA
    tryPlace(RegionType.AQYILA, listOf(2 to 2, 2 to 4, 4 to 2, 2 to 6, 6 to 2), 70)
    // 2. GILD
    tryPlace(RegionType.GILD, listOf(2 to 4, 4 to 2, 8 to 4, 4 to 8), 40)
    // 3. GOTHIC
    tryPlaceGothic(60)
    // 4. OUROBOROS
    placeOuroborosScan(10)
    // 5. GARDEN —— 目标 10 → 4
    placeGardenScan(4)
    // 6. FABLED
    tryPlace(RegionType.FABLED, listOf(2 to 2), 10)

    repeat(2) { weightedFill() }

    for (x in 0 until width) for (y in 0 until height) if (grid[x][y] == null) grid[x][y] = RegionType.WALLS

    for (y in 0 until height) {
        for (x in 0 until width) print(grid[x][y]!!.symbol)
        println()
    }
}
package io.github.zzzyyylllty.chotentech.worldgeneration.l1

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.fillLayer
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainAquila
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainCorridor
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainFabled
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainGarden
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainGild
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainGothic
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainOuroboros
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainUnbreakable
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainWalls
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator.ChunkData
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.random.Random

/**
 * GL1Terrain - Level1 地形区域调度器
 *
 * 不再自行铺设地表，而是先按 `tinf/1terrain.kt` 的随机布局算法求出
 * 当前区块属于哪一种区域（[RegionType]），再把生成工作交给该区域对应的
 * 走廊生成器（`GL1PlainAquila`、`GL1PlainGild` ……）。
 *
 * 布局算法完全确定：同一 `(worldSeed, 区块坐标)` 永远得到同一区域类型。
 */
class GL1Terrain(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    override fun generate(chunkData: ChunkData) {
        // 1. 判定本区块属于哪种区域
        val region = RegionLayout.typeAt(worldSeed, chunkX, chunkZ)

        // 2. 交给该区域对应的生成器（分阶段保护：异常会带完整堆栈进日志）。
        //    区域交界的门洞由生成器在砌自己那道交界墙时按 RegionGateway 的方案留出来，
        //    不再由外部二次开挖（避免多挖、两层门、合页方向错）。
        guard(region, "region-generator") { createGenerator(region).generate(chunkData) }

        // 3. 调试：回读 ChunkData 自身内容，判断「生成器写没写」与「写入落没落地」。
        //    回读剖面异常时会用内存缓冲重跑同一生成器做 A/B 对比，把断点定位到具体环节。
        L1GenDebug.report(chunkX, chunkZ, region, chunkData) { buffer ->
            createGenerator(region).generate(buffer)
        }

        // 4. 封顶兜底（定位问题期间先只报告不填充，避免掩盖「生成器少写了内容」）
        if (SEAL_FILL) guard(region, "seal") { sealChunk(chunkData) }
    }

    /**
     * 分阶段执行并记录失败原因。
     *
     * 区块生成里任何一步抛异常，Paper 都不会把「半个区块」当成错误提示出来，
     * 结果就是区块悄悄缺一块（本次现场：只剩 y≤-5 的深板岩混合层）。
     * 这里把每一步单独保护起来，并把**完整堆栈**打进日志，失败原因才看得见。
     *
     * 捕获 Throwable 是有意的：NoClassDefFoundError 之类的 LinkageError 同样会让
     * 区块半途而废，而它不属于 Exception。
     */
    private inline fun guard(region: RegionType, step: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // 日志本身也再包一层：兜底逻辑绝不能因为「日志不可用」而二次抛异常
            try {
                Bukkit.getLogger().log(
                    Level.WARNING,
                    "[ChoTenTech] level1 区块 ($chunkX, $chunkZ) 区域 $region 的 $step 阶段失败",
                    t
                )
            } catch (_: Throwable) {
                // 无服务器实例（离线工具 / 极早期启动阶段）时忽略
            }
        }
    }

    /**
     * 封顶兜底。
     *
     * 各区域生成器的可活动高度并不一致（Fabled y5、Corridor y6、Ouroboros y7、
     * Gothic y8、Aquila y9、Gild y12），谁的「结构最高层 +1 → y15 磨制凝灰岩覆盖」
     * 漏了，该区块顶部就会露出空洞。
     *
     * 这里统一兜底：
     *  - 补齐底层四层装饰 + y=-12 基岩、顶层四层装饰 + y=20 基岩；
     *  - 扫描出本区块已有的最高方块，把它以上到 y=15 全部填成磨制凝灰岩。
     *
     * 由于只写在「已有最高方块之上」，不会覆盖任何生成器已放置的内容。
     *
     * ⚠️ `GARDEN` 不铺顶层（它自己用玻璃 + 屏障封顶），否则会把花园的天窗盖掉。
     */
    private fun sealChunk(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        if (RegionLayout.typeAt(worldSeed, chunkX, chunkZ) != RegionType.GARDEN) {
            chunkData.fillWorldRoof()
        }

        var top = Int.MIN_VALUE
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in 15 downTo 0) {
                    if (chunkData.getType(x, y, z) != Material.AIR) {
                        if (y > top) top = y
                        break
                    }
                }
            }
        }

        val start = if (top == Int.MIN_VALUE) 0 else top + 1
        for (y in start..15) chunkData.fillLayer(y, Material.POLISHED_TUFF)
    }

    /**
     * 区域类型 → 生成器 的索引表。
     *
     * - `WALLS` 索引到 `GL1PlainWalls`（石头/深板岩混合）
     * - `UNBREAKABLE` 索引到 `GL1PlainUnbreakable`（同上，但额外掺 30% 基岩）
     * - `GARDEN` 索引到 `GL1PlainGarden`（丛林花园）
     */
    private fun createGenerator(region: RegionType): ChunkGen = when (region) {
        RegionType.UNBREAKABLE -> GL1PlainUnbreakable(chunkX, chunkZ, worldSeed)
        RegionType.WALLS -> GL1PlainWalls(chunkX, chunkZ, worldSeed)
        RegionType.CORRIDORS -> GL1PlainCorridor(chunkX, chunkZ, worldSeed)
        RegionType.AQYILA -> GL1PlainAquila(chunkX, chunkZ, worldSeed)
        RegionType.GILD -> GL1PlainGild(chunkX, chunkZ, worldSeed)
        RegionType.GOTHIC -> GL1PlainGothic(chunkX, chunkZ, worldSeed)
        RegionType.OUROBOROS -> GL1PlainOuroboros(chunkX, chunkZ, worldSeed)
        RegionType.GARDEN -> GL1PlainGarden(chunkX, chunkZ, worldSeed)
        RegionType.FABLED -> GL1PlainFabled(chunkX, chunkZ, worldSeed)
    }

    companion object {
        /**
         * 是否执行封顶兜底填充。
         *
         * 定位「部分区块只剩底部深板岩层」问题期间先设为 false：只由 [L1GenDebug] 报告，
         * 不做任何填充，避免把「生成器少写了内容」这件事掩盖掉。
         * 问题定位完改回 true 即可恢复兜底。
         */
        private val SEAL_FILL = false

        /**
         * 查询某个区块所属的区域类型（供调试 / 外部指令使用）。
         */
        fun regionTypeAt(chunkX: Int, chunkZ: Int, worldSeed: Long): RegionType =
            RegionLayout.typeAt(worldSeed, chunkX, chunkZ)
    }
}

/**
 * 区域类型。与 `tinf/1terrain.kt` 中的定义保持一致，`symbol` 用于布局预览。
 */
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

/**
 * 区域布局表。
 *
 * 移植自 `tinf/1terrain.kt`：在一个 [GRID_SIZE] × [GRID_SIZE] 的区块网格里
 * 按固定顺序随机放置各类区域，最后把剩余空格兜底为 [RegionType.WALLS]。
 *
 * 世界按 [GRID_SIZE] 区块切分为若干「元区块」（mega chunk）。每个元区块用
 * `(worldSeed, megaX, megaZ)` 派生各自的随机种子并独立布局，因此地形既能
 * 无限延伸，又对同一世界种子完全确定性。元区块 (0, 0) 的布局与 1terrain 的
 * 输出形式一致。
 */
object RegionLayout {

    /** 布局网格边长（单位：区块）。 */
    const val GRID_SIZE = 20

    /** 布局缓存上限，超出即整体清空（每个布局约 20×20 个引用，开销很小）。 */
    private const val MAX_CACHED_LAYOUTS = 1024

    private data class LayoutKey(val worldSeed: Long, val megaX: Int, val megaZ: Int)

    private val cache = ConcurrentHashMap<LayoutKey, Array<Array<RegionType>>>()

    /**
     * 查询指定区块所属的区域类型。
     */
    fun typeAt(worldSeed: Long, chunkX: Int, chunkZ: Int): RegionType {
        val megaX = Math.floorDiv(chunkX, GRID_SIZE)
        val megaZ = Math.floorDiv(chunkZ, GRID_SIZE)
        val layout = layoutFor(worldSeed, megaX, megaZ)
        return layout[Math.floorMod(chunkX, GRID_SIZE)][Math.floorMod(chunkZ, GRID_SIZE)]
    }

    /**
     * 获取（并缓存）指定元区块的布局表，索引方式为 `layout[x][z]`。
     */
    fun layoutFor(worldSeed: Long, megaX: Int, megaZ: Int): Array<Array<RegionType>> {
        if (cache.size > MAX_CACHED_LAYOUTS) cache.clear()
        return cache.getOrPut(LayoutKey(worldSeed, megaX, megaZ)) {
            build(worldSeed, megaX, megaZ)
        }
    }

    /**
     * 生成便于调试的符号布局预览，行序与 `tinf/1terrain.kt` 的打印结果一致。
     */
    fun preview(worldSeed: Long, megaX: Int = 0, megaZ: Int = 0): String {
        val layout = layoutFor(worldSeed, megaX, megaZ)
        return buildString {
            for (z in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) append(layout[x][z].symbol)
                append('\n')
            }
        }
    }

    // ==================== 布局算法（移植自 tinf/1terrain.kt） ====================

    private fun build(worldSeed: Long, megaX: Int, megaZ: Int): Array<Array<RegionType>> {
        val width = GRID_SIZE
        val height = GRID_SIZE
        val grid = Array(width) { arrayOfNulls<RegionType>(height) }
        val rand = Random(layoutSeed(worldSeed, megaX, megaZ))

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
         * 大花园段：**提前**占一块完整的地（4×4 起，越大越罕见）。
         *
         * 和其它区域不同，这一趟放在最前面：它要的是一整块干净的地，
         * 放到后面就会被切得七零八落、根本凑不出 4×4。
         * 出现概率很低（[BIG_GARDEN_CHANCE]），所以对整体布局影响很小。
         */
        fun placeBigGardens() {
            if (rand.nextInt(100) >= BIG_GARDEN_CHANCE) return

            // 先按权重抽尺寸：绝大多数是 4×4，越大的越罕见
            var total = BIG_GARDEN_SIZES.sumOf { it.second }
            var r = rand.nextInt(total)
            var picked = BIG_GARDEN_SIZES.first().first
            for ((size, weight) in BIG_GARDEN_SIZES) {
                r -= weight
                if (r < 0) {
                    picked = size
                    break
                }
            }
            val (w, h) = picked
            if (w > width || h > height) return

            // 这一段是空的（全 null），随便找几个落点都行
            repeat(20) {
                val x = rand.nextInt(width - w + 1)
                val y = rand.nextInt(height - h + 1)
                if (canPlace(x, y, w, h)) {
                    place(x, y, w, h, RegionType.GARDEN)
                    return
                }
            }
        }

        /**
         * GARDEN 扫描式放置。
         *
         * [wanted] 是**期望数量**，可以是小数：**开局就把这次放几个抽定**
         * （`toInt()` 个 + 以小数部分为概率再多一个），这样「整体概率下调 60%」这种缩放才落得准 ——
         * 放到循环里现抽不行：那时网格已经没多少空地，第二个经常放不下去，概率会掉一大截。
         * 不再做密集扩展。
         */
        fun placeGardenScan(wanted: Double) {
            val whole = wanted.toInt()
            val extra = if (rand.nextInt(100) < ((wanted - whole) * 100).toInt()) 1 else 0
            val count = whole + extra
            var placed = 0
            for ((x, y) in shuffledPositions()) {
                if (placed >= count) break
                if (x + 2 > width || y + 2 > height) continue
                if (!canPlaceReplace(x, y, 2, 2, setOf(RegionType.WALLS))) continue
                place(x, y, 2, 2, RegionType.GARDEN)
                placed++
            }
        }

        fun weightedFill() {
            val allTypes = listOf(
                RegionType.AQYILA to listOf(2 to 6, 6 to 2, 2 to 4, 4 to 2, 2 to 2),
                RegionType.GILD to listOf(8 to 4, 4 to 8, 2 to 4, 4 to 2, 4 to 4, 2 to 2),
                RegionType.GOTHIC to listOf(4 to 4, 3 to 3),
                RegionType.OUROBOROS to listOf(2 to 3, 2 to 4, 2 to 2),
                RegionType.GARDEN to listOf(2 to 2),   // 权重从 4 降到 2（花园整体 -60% 的一部分）
                RegionType.FABLED to listOf(2 to 2),
                RegionType.CORRIDORS to listOf(
                    1 to 5, 5 to 1, 1 to 4, 4 to 1, 3 to 3,
                    2 to 3, 3 to 2, 1 to 3, 3 to 1, 2 to 2
                )
            )
            val weights = listOf(30, 24, 10, 8, 2, 6, 8)   // GARDEN 权重 8 → 4 → 2

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
                        if (r < weights[i]) {
                            chosen = i
                            break
                        }
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

        // 0. 大花园段：提前占一块完整的地（概率很低，见 placeBigGardens）
        placeBigGardens()

        tryPlace(RegionType.UNBREAKABLE, listOf(1 to 1, 1 to 1, 1 to 2, 2 to 1, 1 to 3, 3 to 1), 8)
        tryPlace(RegionType.WALLS, listOf(1 to 2, 2 to 1, 1 to 3, 3 to 1), 6)
        tryPlace(
            RegionType.CORRIDORS,
            listOf(1 to 3, 3 to 1, 1 to 4, 4 to 1, 1 to 5, 5 to 1, 2 to 2, 3 to 3, 2 to 3, 3 to 2),
            15
        )

        // 1. AQYILA
        tryPlace(RegionType.AQYILA, listOf(2 to 2, 2 to 4, 4 to 2, 2 to 6, 6 to 2), 70)
        // 2. GILD
        tryPlace(RegionType.GILD, listOf(2 to 4, 4 to 2, 8 to 4, 4 to 8), 40)
        // 3. GOTHIC
        tryPlaceGothic(60)
        // 4. OUROBOROS
        placeOuroborosScan(10)
        // 5. GARDEN —— 原来是目标 4（外加密集扩展）；花园整体概率下调 60% → 期望 1.6
        placeGardenScan(1.6)
        // 6. FABLED
        tryPlace(RegionType.FABLED, listOf(2 to 2), 10)

        repeat(2) { weightedFill() }

        // 兜底：剩余空格全部视为 WALLS
        return Array(width) { x ->
            Array(height) { y -> grid[x][y] ?: RegionType.WALLS }
        }
    }

    /** 每个元区块（20×20 区块）里，出现「大花园段」的概率（%）。原来 8%，整体下调 60% → 3% */
    private const val BIG_GARDEN_CHANCE = 3

    /**
     * 大花园段的尺寸（区块数）与相对权重。
     * 绝大多数是 4×4，越大越罕见；8×8 差不多是百里挑一。
     */
    private val BIG_GARDEN_SIZES = listOf(
        (4 to 4) to 60,
        (5 to 5) to 15,
        (4 to 6) to 10,
        (6 to 4) to 10,
        (6 to 6) to 8,
        (8 to 6) to 3,
        (6 to 8) to 3,
        (8 to 8) to 1
    )

    /**
     * 由世界种子与元区块坐标派生布局随机种子。
     */
    private fun layoutSeed(worldSeed: Long, megaX: Int, megaZ: Int): Long {
        var h = worldSeed
        h = h * 6364136223846793005L + megaX
        h = h * 6364136223846793005L + megaZ
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return h
    }
}

package io.github.zzzyyylllty.chotentech.worldgeneration.l1

import io.github.zzzyyylllty.chotentech.worldgeneration.WorldGenDemo
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator.ChunkData
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

/**
 * L1 世界生成调试探针。
 *
 * 目标：把「部分区块只剩 y<=-5 的深板岩混合层、上面全空，但后台无报错」
 * 定位到具体是哪一环断的。
 *
 * 每生成一个区块，都会**回读 `ChunkData` 自己的内容**，打出一条竖向剖面：
 * `y15 .. y-16`，每个字符表示该层在采样列上的占用情况：
 * - `#` 所有采样列都有方块（完整层，比如地坪/天花板）
 * - `-` 部分采样列有方块（稀疏结构，比如柱子/家具）
 * - `.` 采样列全空
 *
 * 于是可以区分三种断点：
 * 1. 回读剖面完整、但世界里看不到 → 断在「写入之后」的提交/保存环节；
 * 2. 回读剖面就是残缺的 → 写入在生成期 `ChunkData` 里就没落地；
 * 3. 对残缺区块，会用内存缓冲（[WorldGenDemo.DemoChunkData]）把**同一个区域生成器**
 *    再跑一遍作为参考剖面：同一生成器 + 不同 `ChunkData`，差异就是答案。
 *
 * 说明：正常区块只在开局打前 [MAX_NORMAL_LOGS] 条（可用 [verbose] 关掉），
 * 异常区块每条都打（上限 [MAX_ANOMALY_LOGS] 条，避免刷屏）。
 */
object L1GenDebug {

    /** 是否打印正常区块的剖面（调试期默认开）。 */
    var verbose: Boolean = true

    private const val MAX_NORMAL_LOGS = 20
    private const val MAX_ANOMALY_LOGS = 200
    private const val SUMMARY_EVERY = 200

    private const val MIN_Y = -16
    private const val MAX_Y = 15

    /** 廉价采样列：用于「这个区块是不是缺层」的快速判断。 */
    private val SAMPLED = buildList {
        for (x in intArrayOf(0, 5, 10, 15)) for (z in intArrayOf(0, 5, 10, 15)) add(x to z)
    }

    /** 完整列：只在对异常区块出详细报告时使用。 */
    private val ALL = buildList { for (x in 0..15) for (z in 0..15) add(x to z) }

    private val generated = AtomicInteger()
    private val anomalies = AtomicInteger()
    private val normalLogged = AtomicInteger()
    private val anomalyLogged = AtomicInteger()

    /**
     * 回读并报告一个区块的生成结果。
     *
     * @param reference 用同一个区域生成器往任意 [ChunkData] 里写一遍的参考实现
     *                  （由 [GL1Terrain] 传入，避免递归调用 [GL1Terrain.generate]）
     */
    fun report(
        chunkX: Int,
        chunkZ: Int,
        region: RegionType,
        chunkData: ChunkData,
        reference: (ChunkData) -> Unit
    ) {
        val total = generated.incrementAndGet()
        val sampled = profile(chunkData, SAMPLED)
        val top = topOf(sampled)

        if (top >= MAX_Y) {
            if (verbose && normalLogged.get() < MAX_NORMAL_LOGS) {
                normalLogged.incrementAndGet()
                log(Level.INFO, "区块 ($chunkX, $chunkZ) $region 正常  y$MAX_Y..y$MIN_Y = $sampled")
            }
            if (total % SUMMARY_EVERY == 0) {
                log(Level.INFO, "累计 $total 个区块，剖面异常 ${anomalies.get()} 个")
            }
            return
        }

        // 异常：顶部该有内容的层是空的
        anomalies.incrementAndGet()
        if (anomalyLogged.incrementAndGet() > MAX_ANOMALY_LOGS) return

        val full = profile(chunkData, ALL)
        val refProfile = runCatching {
            val buffer = WorldGenDemo.DemoChunkData()
            reference(buffer)
            profile(buffer, ALL)
        }.getOrElse { "参考生成失败: ${it.javaClass.simpleName}: ${it.message}" }

        val lines = buildString {
            append("⚠ 区块 ($chunkX, $chunkZ) 区域 $region 剖面异常：")
            append("回读顶部 y=").append(if (top == Int.MIN_VALUE) "无（整块全空）" else top.toString())
            append("，预期 y=$MAX_Y\n")
            append("   chunkData  = ").append(chunkData.javaClass.name).append('\n')
            append("   高度范围    = minY=").append(chunkData.minHeight)
            append(" maxY=").append(chunkData.maxHeight).append('\n')
            append("   回读剖面    = ").append(full).append('\n')
            append("   参考剖面    = ").append(refProfile).append('\n')
            append("   判定        = ").append(verdict(full, refProfile))
        }
        log(Level.WARNING, lines)
    }

    /** 竖向剖面：`y15 .. y-16`，`#`=采样列全有 `-`=部分有 `.`=全空。 */
    private fun profile(chunkData: ChunkData, columns: List<Pair<Int, Int>>): String {
        val sb = StringBuilder(MAX_Y - MIN_Y + 1)
        for (y in MAX_Y downTo MIN_Y) {
            var solid = 0
            for ((x, z) in columns) {
                if (chunkData.getType(x, y, z) != Material.AIR) solid++
            }
            sb.append(
                when {
                    solid == 0 -> '.'
                    solid == columns.size -> '#'
                    else -> '-'
                }
            )
        }
        return sb.toString()
    }

    /** 剖面里最高的非空层；整条都空时返回 [Int.MIN_VALUE]。 */
    private fun topOf(profile: String): Int {
        val idx = profile.indexOfFirst { it != '.' }
        return if (idx < 0) Int.MIN_VALUE else MAX_Y - idx
    }

    private fun verdict(readback: String, reference: String): String {
        if (reference.startsWith("参考生成失败")) return "无法对比（参考生成失败）"
        val readTop = topOf(readback)
        val refTop = topOf(reference)
        return when {
            refTop >= MAX_Y && readTop < MAX_Y ->
                "生成器本身能写满（参考剖面正常），差异来自生成期 ChunkData → 写入没有落地"
            refTop < MAX_Y ->
                "参考剖面同样缺失（参考顶部 y=$refTop）→ 生成器自己中途返回/抛异常，看阶段失败堆栈"
            else -> "剖面判定异常但参考正常，请人工核对"
        }
    }

    /** 日志本身不能抛异常（极早期启动 / 离线工具下没有服务器实例）。 */
    private fun log(level: Level, message: String) {
        try {
            Bukkit.getLogger().log(level, "[L1-DEBUG] $message")
        } catch (_: Throwable) {
            // 没有服务器实例（离线工具 / 单元测试）时退化为标准输出，方便离线核对
            println("[L1-DEBUG][${level.name}] $message")
        }
    }
}

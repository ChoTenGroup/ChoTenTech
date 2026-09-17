package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import org.bukkit.generator.ChunkGenerator.ChunkData
import org.bukkit.Material
import kotlin.math.abs

/**
 * CorridorUtils - 走廊生成工具类
 * 用于检测相邻区块状态和生成走廊结构
 */
object CorridorUtils {

    /**
     * 根据世界种子和区块坐标判断是否为走廊区块
     * 使用确定性算法保证同一坐标总是生成相同结构
     */
    fun isCorridorChunk(chunkX: Int, chunkZ: Int, worldSeed: Long): Boolean {
        // 使用简单的哈希算法确定区块类型
        val hash = (chunkX * 374761393L + chunkZ * 668265263L + worldSeed) % 100
        return hash < 30  // 30% 的概率是走廊
    }

    /**
     * 检测四个方向是否有走廊开口
     * @return 四个布尔值，分别表示 北(z-)、南(z+)、西(x-)、东(x+) 是否有开口
     */
    fun detectOpenings(
        chunkX: Int,
        chunkZ: Int,
        worldSeed: Long
    ): BooleanArray {
        val openings = BooleanArray(4)

        // 检测北方 (z-)
        openings[0] = isCorridorChunk(chunkX, chunkZ - 1, worldSeed)

        // 检测南方 (z+)
        openings[1] = isCorridorChunk(chunkX, chunkZ + 1, worldSeed)

        // 检测西方 (x-)
        openings[2] = isCorridorChunk(chunkX - 1, chunkZ, worldSeed)

        // 检测东方 (x+)
        openings[3] = isCorridorChunk(chunkX + 1, chunkZ, worldSeed)

        return openings
    }

    /**
     * 在天花板开口处添加玻璃和灯
     * @param chunkData 区块数据
     * @param openings 四个方向的开口状态
     */
    fun generateCeilingOpenings(chunkData: ChunkData, openings: BooleanArray) {
        // 北侧开口 (z=0)
        if (openings[0]) {
            for (x in 7..8) {
                chunkData.setBlock(x, 6, 0, Material.GRAY_STAINED_GLASS)
                chunkData.setBlock(x, 7, 0, Material.SEA_LANTERN)
            }
        }

        // 南侧开口 (z=15)
        if (openings[1]) {
            for (x in 7..8) {
                chunkData.setBlock(x, 6, 15, Material.GRAY_STAINED_GLASS)
                chunkData.setBlock(x, 7, 15, Material.SEA_LANTERN)
            }
        }

        // 西侧开口 (x=0)
        if (openings[2]) {
            for (z in 7..8) {
                chunkData.setBlock(0, 6, z, Material.GRAY_STAINED_GLASS)
                chunkData.setBlock(0, 7, z, Material.SEA_LANTERN)
            }
        }

        // 东侧开口 (x=15)
        if (openings[3]) {
            for (z in 7..8) {
                chunkData.setBlock(15, 6, z, Material.GRAY_STAINED_GLASS)
                chunkData.setBlock(15, 7, z, Material.SEA_LANTERN)
            }
        }
    }

    /**
     * 根据开口方向生成走廊连接
     * 在开口处添加过渡方块
     */
    fun generateCorridorConnections(chunkData: ChunkData, openings: BooleanArray) {
        // 北侧连接
        if (openings[0]) {
            // 移除北侧墙壁的中间部分
            for (x in 5..10) {
                for (y in 1..5) {
                    chunkData.setBlock(x, y, 0, Material.AIR)
                }
            }
        }

        // 南侧连接
        if (openings[1]) {
            for (x in 5..10) {
                for (y in 1..5) {
                    chunkData.setBlock(x, y, 15, Material.AIR)
                }
            }
        }

        // 西侧连接
        if (openings[2]) {
            for (z in 5..10) {
                for (y in 1..5) {
                    chunkData.setBlock(0, y, z, Material.AIR)
                }
            }
        }

        // 东侧连接
        if (openings[3]) {
            for (z in 5..10) {
                for (y in 1..5) {
                    chunkData.setBlock(15, y, z, Material.AIR)
                }
            }
        }
    }
}
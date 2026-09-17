package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillLayer
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionBoundary
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionGateway
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.SubRegionLayout
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.FaceAttachable
import org.bukkit.block.data.Lightable
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
import org.bukkit.block.data.type.Switch
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * GL1PlainGothic - 哥特式走廊生成器
 *
 * 柱体：7 空 1 柱网格（柱距 8），石砖柱身 y1..y7。
 * 拱形：
 *   y7: 柱 梯 梯 台 台 台
 *   y6: 柱 梯 梯
 *   y5: 柱 梯
 *   y4: 柱
 *   梯 = TUFF_BRICK_STAIRS（倒置，朝向柱子外侧）
 *   台 = TUFF_BRICK_SLAB（上半砖），柱子之间 y7 连接
 * 照明：红石灯 y8（isLit=true），拉杆 y9（FLOOR 附着）
 */
class GL1PlainGothic(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    private val regionSizes = listOf(
        Pair(3, 3),
        Pair(4, 4)
    )

    private val horizontalFaces = listOf(
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST
    )

    private val pillarSpacing = 8
    private val pillarBottomY = 1
    private val pillarTopY = 7
    private val ceilingY = 8

    override fun generate(chunkData: ChunkData) {
        generateFoundation(chunkData)
        generatePillars(chunkData)
        generateArchStairs(chunkData)
        generateTopSlabs(chunkData)

        chunkData.fillLayer(ceilingY, Material.POLISHED_TUFF)
        for (y in 9..10) fillLayerDeepslateMixed(chunkData, y)
        for (y in 11..15) chunkData.fillLayer(y, Material.POLISHED_TUFF)

        generateRedstoneLamps(chunkData)

        // 内部区域边界 + GL1Terrain 的区域边界都要砌墙（generateWalls 内部按边判断）
        generateWalls(chunkData)
    }

    // ==================== 地基 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()
        for (y in WORLD_FLOOR_MIX_Y..0) fillLayerStoneMixed(chunkData, y)
    }

    // ==================== 柱子 ====================

    private fun generatePillars(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                if (x % pillarSpacing == 0 && z % pillarSpacing == 0) {
                    for (y in pillarBottomY..pillarTopY) {
                        chunkData.setBlock(x, y, z, Material.TUFF_BRICKS)
                    }
                }
            }
        }
    }

    // ==================== 拱形楼梯（梯） ====================

    /**
     * 柱子四方向倒置楼梯拱形：
     *
     *   y7: 柱 梯 梯 台 台 台
     *   y6: 柱 梯 梯
     *   y5: 柱 梯
     *   y4: 柱
     *
     * 遍历局部坐标 -2..17 来找到所有柱子（包括相邻区块边缘的柱子），
     * 避免区块交界处（局部 0 / 15）缺少楼梯的问题。
     */
    private fun generateArchStairs(chunkData: ChunkData) {
        val dirs = listOf(
            Triple(0, -1, BlockFace.SOUTH), // 柱北侧
            Triple(1, 0, BlockFace.WEST),   // 柱东侧
            Triple(0, 1, BlockFace.NORTH),  // 柱南侧
            Triple(-1, 0, BlockFace.EAST)   // 柱西侧
        )

        // 注意：遍历范围扩展到 -2..17，可以覆盖相邻区块边缘上的柱子
        // （例如局部 16 对应下一区块的柱子 0，其西侧偏移 1、2 = 局部 15、14）
        for (lx in -2..17) {
            for (lz in -2..17) {
                val wx = chunkX * 16 + lx
                val wz = chunkZ * 16 + lz
                if (wx % pillarSpacing != 0 || wz % pillarSpacing != 0) continue

                for ((dx, dz, facing) in dirs) {
                    // 偏移 1：y5 ~ y7
                    for (y in 5..7) {
                        setStair(chunkData, lx + dx, lz + dz, y, facing)
                    }
                    // 偏移 2：y6 ~ y7
                    for (y in 6..7) {
                        setStair(chunkData, lx + 2 * dx, lz + 2 * dz, y, facing)
                    }
                }
            }
        }
    }

    private fun setStair(
        chunkData: ChunkData,
        x: Int, z: Int, y: Int,
        facing: BlockFace
    ) {
        if (x !in 0..15 || z !in 0..15) return
        val data = Material.TUFF_BRICK_STAIRS.createBlockData() as Stairs
        data.half = Bisected.Half.TOP
        data.facing = facing
        chunkData.setBlock(x, y, z, data)
    }

    // ==================== 顶部连接（台 = 上半砖） ====================

    /**
     * y7 层柱子之间偏移 3~5 的位置用【上半砖】填满。
     *   0  1  2  3  4  5  6  7  8
     *   柱 梯 梯 台 台 台 梯 梯 柱
     */
    private fun generateTopSlabs(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                // 跳过柱子本身
                if (x % pillarSpacing == 0 && z % pillarSpacing == 0) continue

                val onXLine = z % pillarSpacing == 0 && x % pillarSpacing != 0
                val onZLine = x % pillarSpacing == 0 && z % pillarSpacing != 0
                if (!onXLine && !onZLine) continue

                val rel = if (onXLine) x % pillarSpacing else z % pillarSpacing
                if (rel !in 3..5) continue

                val slab = Material.TUFF_BRICK_SLAB.createBlockData() as Slab
                slab.type = Slab.Type.TOP
                chunkData.setBlock(x, pillarTopY, z, slab)
            }
        }
    }

    // ==================== 红石灯 / 拉杆 ====================

    /**
     * 7×7 空区中心 (4,4)、(12,4)、(4,12)、(12,12)：
     *   y8：红石灯，isLit = true（直接点亮）
     *   y9：拉杆，FLOOR 附着在灯顶，通电
     */
    private fun generateRedstoneLamps(chunkData: ChunkData) {
        for (cx in intArrayOf(4, 12)) {
            for (cz in intArrayOf(4, 12)) {
                if (cx !in 0..15 || cz !in 0..15) continue

                // 红石灯（y8），直接写入 lit=true
                val lampData = Material.REDSTONE_LAMP.createBlockData() as Lightable
                lampData.isLit = true
                chunkData.setBlock(cx, ceilingY, cz, lampData)

                // 拉杆（y9），附着在红石灯顶面
                val leverData = Material.LEVER.createBlockData() as Switch
                leverData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                val h = hashRandom(
                    chunkX * 16 + cx, ceilingY + 1,
                    chunkZ * 16 + cz, SALT_LEVER
                )
                leverData.facing = horizontalFaces[h % 4]
                leverData.isPowered = true
                chunkData.setBlock(cx, ceilingY + 1, cz, leverData)
            }
        }
    }

    // ==================== 边界墙壁 ====================

    private fun generateWalls(chunkData: ChunkData) {
        val region = getRegionInfo()
        val relX = chunkX - region.x
        val relZ = chunkZ - region.z

        // 子区域边界也要砌内墙（放大的容器内部会被切成几个小区域），并且同样要留门洞，
        // 否则放大后的地形会变成一堆密封盒子
        val rb = RegionBoundary.of(chunkX, chunkZ, worldSeed)
        val sub = SubRegionLayout.of(
            chunkX, chunkZ, worldSeed,
            region.x, region.z, region.width, region.length, regionSizes
        )

        // 除了自己的内部区域边界，GL1Terrain 的区域边界（邻居 RegionType 不同）也要砌墙，
        // 否则别的区域的外墙会直接裸露在本区域里。
        // 交界处的门洞按 RegionGateway 的方案留空（只有一层门，且不会多挖）；
        // 区域交界优先，其次才是子区域之间的通道。
        val planN = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.NORTH)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.NORTH, sub)
        val planS = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.SOUTH)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.SOUTH, sub)
        val planW = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.WEST)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.WEST, sub)
        val planE = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.EAST)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.EAST, sub)

        if (relX == 0 || RegionBoundary.has(rb, RegionBoundary.WEST) ||
            (sub != null && chunkX == sub.x)) {
            for (z in 0..15) for (y in 1..10)
                if (!RegionGateway.opensAt(planW, z, y)) chunkData.setBlock(0, y, z, Material.TUFF_BRICKS)
        }
        if (relX == region.width - 1 || RegionBoundary.has(rb, RegionBoundary.EAST) ||
            (sub != null && chunkX == sub.x + sub.w - 1)) {
            for (z in 0..15) for (y in 1..10)
                if (!RegionGateway.opensAt(planE, z, y)) chunkData.setBlock(15, y, z, Material.TUFF_BRICKS)
        }
        if (relZ == 0 || RegionBoundary.has(rb, RegionBoundary.NORTH) ||
            (sub != null && chunkZ == sub.z)) {
            for (x in 0..15) for (y in 1..10)
                if (!RegionGateway.opensAt(planN, x, y)) chunkData.setBlock(x, y, 0, Material.TUFF_BRICKS)
        }
        if (relZ == region.length - 1 || RegionBoundary.has(rb, RegionBoundary.SOUTH) ||
            (sub != null && chunkZ == sub.z + sub.h - 1)) {
            for (x in 0..15) for (y in 1..10)
                if (!RegionGateway.opensAt(planS, x, y)) chunkData.setBlock(x, y, 15, Material.TUFF_BRICKS)
        }

        RegionGateway.placeDoors(chunkData, planN)
        RegionGateway.placeDoors(chunkData, planS)
        RegionGateway.placeDoors(chunkData, planW)
        RegionGateway.placeDoors(chunkData, planE)
    }

    private fun isBoundaryChunk(): Boolean {
        val region = getRegionInfo()
        val relX = chunkX - region.x
        val relZ = chunkZ - region.z
        return relX == 0 || relX == region.width - 1 ||
                relZ == 0 || relZ == region.length - 1
    }

    // ==================== 混合填充 ====================

    private fun fillLayerStoneMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) for (z in 0..15) {
            val wx = chunkX * 16 + x
            val wz = chunkZ * 16 + z
            val r = hashRandom(wx, y, wz, SALT_STONE) % 6
            val mat = when {
                r < 3 -> Material.STONE
                r < 5 -> Material.ANDESITE
                else -> Material.GRAVEL
            }
            chunkData.setBlock(x, y, z, mat)
        }
    }

    private fun fillLayerDeepslateMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) for (z in 0..15) {
            val wx = chunkX * 16 + x
            val wz = chunkZ * 16 + z
            val r = hashRandom(wx, y, wz, SALT_DEEPSLATE) % 10
            val mat = when {
                r < 5 -> Material.DEEPSLATE
                r < 8 -> Material.COBBLED_DEEPSLATE
                else -> Material.DEEPSLATE_BRICKS
            }
            chunkData.setBlock(x, y, z, mat)
        }
    }

    // ==================== 区域信息 ====================

    private fun getRegionInfo(): RegionInfo {
        // 容器放大：超级块与区域尺寸都 ×2；容器内部再由 SubRegionLayout 用原始尺寸切子区域
        val superSize = 24
        val superX = Math.floorDiv(chunkX, superSize)
        val superZ = Math.floorDiv(chunkZ, superSize)
        val h = hashRandom(superX, 0, superZ, SALT_REGION)
        val (w0, l0) = regionSizes[h % regionSizes.size]
        val w = w0 * 2
        val l = l0 * 2

        val localX = chunkX - superX * superSize
        val localZ = chunkZ - superZ * superSize
        val regionX = superX * superSize + Math.floorDiv(localX, w) * w
        val regionZ = superZ * superSize + Math.floorDiv(localZ, l) * l
        return RegionInfo(regionX, regionZ, w, l)
    }

    // ==================== 确定性哈希 ====================

    private fun hashRandom(x: Int, y: Int, z: Int, salt: Long): Int {
        var h = worldSeed xor salt
        h = h * 6364136223846793005L + x
        h = h * 6364136223846793005L + y
        h = h * 6364136223846793005L + z
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFF).toInt()
    }

    private data class RegionInfo(
        val x: Int,
        val z: Int,
        val width: Int,
        val length: Int
    )

    companion object {
        private const val SALT_STONE = 301L
        private const val SALT_DEEPSLATE = 302L
        private const val SALT_REGION = 303L
        private const val SALT_LEVER = 304L
    }
}
package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillLayer
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator.ChunkData
import taboolib.common.util.random

/**
 * RoomCorridor - 房间走廊生成器
 * 生成带有蓝色/紫色混凝土主题的房间结构
 * 墙壁长度只有2格
 */
class RoomCorridor(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {
    // 房间尺寸配置（固定2X2区块）
    private val regionSizes = listOf(
        Pair(2, 2)   // 2X2
    )

    // 混合材质权重配置
    private val deepslateWeights = listOf(5, 3, 2)  // 深板岩、裂纹深板岩、深板岩砖的权重

    // 蓝色/紫色混凝土材料
    private val blueConcrete = listOf(
        Material.BLUE_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.CYAN_CONCRETE
    )

    private val purpleConcrete = listOf(
        Material.PURPLE_CONCRETE,
        Material.MAGENTA_CONCRETE,
        Material.PINK_CONCRETE
    )

    override fun generate(chunkData: ChunkData) {
        // 1. 基础地基层
        generateFoundation(chunkData)

        // 2. 生成房间墙壁（2格长度）
        generateRoomWalls(chunkData)

        // 3. 生成房间地板
        generateRoomFloor(chunkData)
    }

    /**
     * 生成基础地层
     */
    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        // 混合石块层
        for (y in WORLD_FLOOR_MIX_Y..0) {
            fillLayerStoneMixed(chunkData, y)
        }

        // 磨制凝灰岩覆盖层 (y不满16的部分)
        for (y in 5..15) {
            chunkData.fillLayer(y, Material.POLISHED_TUFF)
        }
    }

    /**
     * 生成房间墙壁（2格长度）
     */
    private fun generateRoomWalls(chunkData: ChunkData) {
        // 随机生成房间墙壁
        val wallCount = random(4, 8)

        for (i in 0 until wallCount) {
            val isHorizontal = random(0, 1) == 0
            val x = random(2, 13)
            val z = random(2, 13)
            val wallMaterial = if (random(0, 1) == 0) {
                blueConcrete[random(0, blueConcrete.size - 1)]
            } else {
                purpleConcrete[random(0, purpleConcrete.size - 1)]
            }

            // 墙壁长度只有2格
            if (isHorizontal) {
                // 水平墙壁
                for (dx in 0..1) {
                    if (x + dx in 0..15) {
                        for (y in 1..4) {
                            chunkData.setBlock(x + dx, y, z, wallMaterial)
                        }
                    }
                }
            } else {
                // 垂直墙壁
                for (dz in 0..1) {
                    if (z + dz in 0..15) {
                        for (y in 1..4) {
                            chunkData.setBlock(x, y, z + dz, wallMaterial)
                        }
                    }
                }
            }
        }
    }

    /**
     * 生成房间地板
     */
    private fun generateRoomFloor(chunkData: ChunkData) {
        // 随机生成房间地板
        val floorCount = random(3, 6)

        for (i in 0 until floorCount) {
            val x = random(2, 13)
            val z = random(2, 13)
            val floorMaterial = if (random(0, 1) == 0) {
                blueConcrete[random(0, blueConcrete.size - 1)]
            } else {
                purpleConcrete[random(0, purpleConcrete.size - 1)]
            }

            // 地板面积 2X2
            for (dx in 0..1) {
                for (dz in 0..1) {
                    if (x + dx in 0..15 && z + dz in 0..15) {
                        chunkData.setBlock(x + dx, 0, z + dz, floorMaterial)
                    }
                }
            }
        }
    }

    /**
     * 填充混合石块层
     */
    private fun fillLayerStoneMixed(chunkData: ChunkData, y: Int) {
        val materials = listOf(Material.STONE, Material.ANDESITE, Material.GRAVEL)
        for (x in 0..15) {
            for (z in 0..15) {
                chunkData.setBlock(x, y, z, ChunkGen.weightedRandom(materials, listOf(3, 2, 1)))
            }
        }
    }
}
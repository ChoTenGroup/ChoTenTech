package io.github.zzzyyylllty.chotentech.worldgeneration

import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import taboolib.common.util.random
import java.util.*
import kotlin.math.abs


class Level1Generator : ChunkGenerator() {
    override fun generateNoise(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        chunkData: ChunkData,
    ) {
        // 地板
        chunkData.fixedSetRegion(0, 0, 0, 15, 0, 15, Material.GRAY_CONCRETE)

        (abs(chunkX) + 1) % 3
        (abs(chunkZ) + 1) % 3

        val basicy = 0
        for (x in 0..15) {
            for (z in 0..15) {
                val isLight = ((x % 4) + (z % 4) == 0)
                // 天花板+玻璃 y=6
                if (isLight) {
                    chunkData.setBlock(x, basicy + 6, z, generateGlass())
                    if (isLight) chunkData.setBlock(x, basicy + 8, z, generateLight(chunkX + chunkZ))
                } else {
                    chunkData.setBlock(x, basicy + 6, z, Material.SMOOTH_STONE)
                    chunkData.setBlock(x, basicy + 8, z, Material.SMOOTH_STONE)
                }
            }
        }

        chunkData.fixedSetRegion(0, basicy + 1, 0, 15, basicy + 1, 15, Material.ANDESITE)
        chunkData.fixedSetRegion(7, basicy + 5, 7, 0, basicy + 5, 15, Material.BONE_BLOCK)
    }

    fun generateDarkWall(): Material {
        return when (random(1, 15)) {
            1 -> Material.BLACK_CONCRETE
            2 -> Material.GRAY_CONCRETE_POWDER
            else -> Material.GRAY_CONCRETE
        }
    }

    fun ChunkData.fixedSetLightWall(
        xMin: Int,
        yMin: Int,
        zMin: Int,
        xMax: Int,
        yMax: Int,
        zMax: Int,
        offset: Int
    ) {
        if (xMin <= xMax && yMin <= yMax && zMin <= zMax) {
            for (y in yMin..yMax) {
                for (x in xMin..xMax) {
                    for (z in zMin..zMax) {
                        this.setBlock(x, y, z, generateLightWall(offset))
                    }
                }
            }
        }
    }

    fun generateLightWall(offset: Int): Material {
        val rand = random(-1500, 0) + offset
        return if (rand >= 1500) {
            Material.GRAVEL
        } else if (rand >= 800 && random(0, 500) <= (rand - 100)) {
            Material.STONE_BRICKS
        } else if (rand >= 300 && random(0, 300) <= (rand - 100)) {
            Material.STONE
        } else if (rand >= 100 && random(0, 300) <= (rand - 100)) {
            Material.LIGHT_GRAY_CONCRETE_POWDER
        } else if (rand >= 100 && random(0, 300) <= (rand - 100)) {
            Material.WHITE_CONCRETE_POWDER
        } else {
            Material.WHITE_CONCRETE

        }
    }

    fun generateLight(offset: Int): Material {
        val rand = random(0, 500) + offset
        return if (rand >= 1000) {
            Material.BLACK_CONCRETE
        } else if (rand >= 400 && random(0, 500) <= (rand - 400)) {
            Material.BLACK_CONCRETE
        } else {
            Material.SEA_LANTERN
        }
    }
}


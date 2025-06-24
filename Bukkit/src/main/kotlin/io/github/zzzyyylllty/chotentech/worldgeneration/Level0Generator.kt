package io.github.zzzyyylllty.chotentech.worldgeneration

import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.ChunkGenerator.ChunkData
import org.bukkit.generator.WorldInfo
import taboolib.common.platform.function.info
import taboolib.common.util.random
import java.util.*
import kotlin.math.min

class Level0Generator : ChunkGenerator() {
    override fun generateNoise(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        chunkData: ChunkData,
    ) {

        chunkData.fixedSetRegion(0, -1, 0, 15, -1, 15, Material.WET_SPONGE)
        chunkData.fixedSetRegion(0, -2, 0, 15, -1, 15, Material.BEDROCK)
        chunkData.fixedSetRegion(0, 59, 0, 15, -1, 15, Material.BEDROCK)
        chunkData.fixedSetRegion(0, 60, 0, 15, -1, 15, Material.BEDROCK)
            for (basicy in listOf(0,10,20,30,40,50)) {
                for (x in 0..15) {
                    for (z in 0..15) {
                        val isLight = ((x % 4) + (z % 4) == 0)
                        // 天花板+玻璃 y=6
                        if (isLight) {
                            chunkData.setBlock(x, basicy + 6, z, generateGlass())
                            chunkData.setBlock(x, basicy + 8, z, generateLight(chunkX + chunkZ))
                        } else {
                            chunkData.setBlock(x, basicy + 6, z, Material.SMOOTH_STONE)
                            chunkData.setBlock(x, basicy + 8, z, Material.SMOOTH_STONE)
                        }
                    }
                }

                chunkData.fixedSetRegion(0, basicy, 0, 15, basicy, 15, Material.SPONGE)
                chunkData.fixedSetRegion(0, basicy + 9, 0, 15, basicy + 9, 15, Material.DEEPSLATE)

                // 墙体部分生成
                var loopTime = 0
                while (random(loopTime, 13) <= 12) {
                    val keepx = (random(0, 1) == 1)
                    val staticNumber = random(0, 15)
                    val fromNumber = random(0, 14)
                    val randomNumber = random(fromNumber + 1, 15)
                    val wallHeight = min(5, random(2, 10))
                    if (keepx) {
                        chunkData.fixedSetRegion(
                            staticNumber,
                            basicy + 2,
                            fromNumber,
                            staticNumber,
                            basicy + wallHeight,
                            randomNumber,
                            Material.STRIPPED_BIRCH_LOG
                        )
                        chunkData.fixedSetRegion(
                            staticNumber,
                            basicy + 1,
                            fromNumber,
                            staticNumber,
                            basicy + 1,
                            randomNumber,
                            Material.STRIPPED_OAK_LOG
                        )
                    } else {
                        chunkData.fixedSetRegion(
                            fromNumber,
                            basicy + 2,
                            staticNumber,
                            randomNumber,
                            basicy + wallHeight,
                            staticNumber,
                            Material.STRIPPED_BIRCH_LOG
                        )
                        chunkData.fixedSetRegion(
                            fromNumber,
                            basicy + 1,
                            staticNumber,
                            randomNumber,
                            basicy + 1,
                            staticNumber,
                            Material.STRIPPED_OAK_LOG
                        )
                    }
                    loopTime++
                }
            }


    }

}

fun generateGlass(): Material {
    return when (random(1, 10)) {
        1 -> Material.AIR
        2 -> Material.TINTED_GLASS
        3 -> Material.WHITE_STAINED_GLASS
        4 -> Material.GLASS
        else -> Material.LIGHT_GRAY_STAINED_GLASS
    }
}

fun generateLight(offset: Int): Material {
    val rand = random(0, 100) + min(offset, 180)
    return if (rand >= 1000) {
        Material.BLACK_CONCRETE
    } else if (rand >= 500 && random(0, 200) <= (rand - 100)) {
        Material.BLACK_CONCRETE
    } else {
        Material.SEA_LANTERN
    }
}

fun ChunkData.fixedSetRegion(xMin: Int, yMin: Int, zMin: Int, xMax: Int, yMax: Int, zMax: Int, type: Material) {
    info("$xMin $yMin $zMin $xMax $yMax $zMax $type")
        for (y in yMin..min(yMax,15)) {
            for (x in xMin..min(xMax,15)) {
                for (z in zMin..min(zMax, 15)) {
                    this.setBlock(x, y, z, type)
                }
            }
        }
}

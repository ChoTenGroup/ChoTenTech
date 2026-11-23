package io.github.zzzyyylllty.chotentech.worldgeneration

import io.github.zzzyyylllty.chotentech.worldgeneration.l2.Level2Terrain
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random

class Level2Generator : ChunkGenerator() {
    override fun generateNoise(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        chunkData: ChunkData,
    ) {
        Level2Terrain(chunkX, chunkZ, worldInfo.seed).generate(chunkData)
    }

    override fun getFixedSpawnLocation(world: World, random: Random): Location =
        Location(world, 0.5, 1.0, 0.5)
}

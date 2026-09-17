package io.github.zzzyyylllty.chotentech.worldgeneration

import io.github.zzzyyylllty.chotentech.worldgeneration.l1.GL1Terrain
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.ChunkGenerator.ChunkData
import org.bukkit.generator.WorldInfo
import java.util.*


class Level1Generator : ChunkGenerator() {

    override fun generateNoise(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        chunkData: ChunkData,
    ) {
        // 生成器必须保持为局部变量。
        // ChunkGenerator 实例由整个服务端共享，而区块生成是多线程的；
        // 若把 GL1Terrain 存进字段，相邻区块并发生成时会被其他线程覆盖，
        // 导致一整批区块用上别人的区块坐标 → 用错区域类型（例如该是 WALLS 的区块
        // 被当成 AQYILA 生成，于是「墙不见了」、区块上方出现空洞）。
        GL1Terrain(chunkX, chunkZ, worldInfo.seed).generate(chunkData)
    }
}

package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionLayout
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionType
import org.bukkit.block.Biome
import org.bukkit.event.world.ChunkLoadEvent
import taboolib.common.platform.event.SubscribeEvent

/**
 * GARDEN（丛林花园）区域的区块生物群系 = [Biome.JUNGLE]。
 *
 * 为什么不用生成阶段设置：`ChunkGenerator.ChunkData`（paper-api 1.21.4）**只有 `getBiome`、没有 `setBiome`**，
 * 所以区块生成时改不了生物群系，只能在区块加载后补一次。
 *
 * 按 4×4 的生物群系单元写（x/z 每次跳 4 格），一个区块 16 次调用；
 * 写完之后再加载时第一格判断就是 JUNGLE，直接 return，不会反复写。
 *
 * 只处理 GARDEN 区块，其它区域的生物群系原样不动。
 */
@SubscribeEvent
fun onGardenChunkLoad(event: ChunkLoadEvent) {
    val world = event.world
    val chunk = event.chunk

    if (RegionLayout.typeAt(world.seed, chunk.x, chunk.z) != RegionType.GARDEN) return

    val baseX = chunk.x * 16
    val baseZ = chunk.z * 16
    if (world.getBiome(baseX + 8, baseZ + 8) == Biome.JUNGLE) return

    for (x in 0..15 step 4) {
        for (z in 0..15 step 4) {
            world.setBiome(baseX + x, baseZ + z, Biome.JUNGLE)
        }
    }
}

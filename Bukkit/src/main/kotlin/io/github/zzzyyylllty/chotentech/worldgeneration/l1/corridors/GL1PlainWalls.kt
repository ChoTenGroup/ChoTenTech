package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * GL1PlainWalls - 墙壁生成器（`RegionType.WALLS`）
 *
 * 整根柱子是一块实心岩石，用来把各个地形区域隔开：
 *
 * ```
 * y = 16       基岩（世界封顶，与其它生成器一致）
 * y = -4 ~ 15  石头混合：闪长岩 / 花岗岩 / 安山岩 / 石头 / 圆石
 * y = -15 ~ -5 深板岩混合：深板岩 / 圆板深板岩 / 深板岩砖 / 深板岩瓦 / 裂纹深板岩砖
 * y = -16      基岩（世界底）
 * ```
 *
 * 混合是**按位置哈希**取的（[hash]），不再用 taboolib 的全局随机 ——
 * 后者同一区块每次生成结果都不一样（区块重新生成 / 并发生成时会对不上）。
 *
 * [GL1PlainUnbreakable] 继承本类，只在 [blockAt] 里再掺基岩。
 */
open class GL1PlainWalls(
    protected val chunkX: Int = 0,
    protected val chunkZ: Int = 0,
    protected val worldSeed: Long = 0
) : ChunkGen {

    override fun generate(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        // y <= -5：各种深板岩的混合
        for (y in WORLD_FLOOR_MIX_Y..-5) fillLayerMixed(chunkData, y, deepslateMix, deepslateWeights)

        // y >= -4：闪长岩 / 花岗岩 / 安山岩 / 石头 / 圆石的混合
        for (y in -4..15) fillLayerMixed(chunkData, y, stoneMix, stoneWeights)
    }

    /**
     * 单格材质的最终决定点，[base] 是按混合权重选出来的那一种。
     * 子类可以在这里替换（[GL1PlainUnbreakable] 就是在这里掺基岩的）。
     */
    protected open fun blockAt(x: Int, y: Int, z: Int, base: Material): Material = base

    /** 铺一整层混合材质（逐格按哈希取，确定性）。 */
    private fun fillLayerMixed(
        chunkData: ChunkData,
        y: Int,
        materials: List<Material>,
        weights: List<Int>
    ) {
        val total = weights.sum()
        for (x in 0..15) {
            for (z in 0..15) {
                var r = hash(x, y, z, SALT_MIX) % total
                var chosen = materials.last()
                for (i in materials.indices) {
                    r -= weights[i]
                    if (r < 0) {
                        chosen = materials[i]
                        break
                    }
                }
                chunkData.setBlock(x, y, z, blockAt(x, y, z, chosen))
            }
        }
    }

    /**
     * 按区块内局部坐标哈希（内部会换算成世界坐标，所以同一个方块在哪个区块里算出来都一样）。
     * [x] / [z] 必须传 0..15 的局部坐标。
     */
    protected fun hash(x: Int, y: Int, z: Int, salt: Long): Int {
        var h = worldSeed xor salt
        h = h * 6364136223846793005L + chunkX * 16 + x
        h = h * 6364136223846793005L + y
        h = h * 6364136223846793005L + chunkZ * 16 + z
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFF).toInt()
    }

    companion object {
        private const val SALT_MIX = 5301L

        /** y >= -4：闪长岩 / 花岗岩 / 安山岩 / 石头 / 圆石（等权混合）。 */
        private val stoneMix = listOf(
            Material.DIORITE,
            Material.GRANITE,
            Material.ANDESITE,
            Material.STONE,
            Material.COBBLESTONE
        )
        private val stoneWeights = listOf(1, 1, 1, 1, 1)

        /** y <= -5：各种深板岩。 */
        private val deepslateMix = listOf(
            Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE,
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES,
            Material.CRACKED_DEEPSLATE_BRICKS
        )
        private val deepslateWeights = listOf(5, 3, 2, 2, 1)
    }
}

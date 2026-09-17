package io.github.zzzyyylllty.chotentech.worldgeneration

import org.bukkit.Material
import org.bukkit.block.data.Bisected
import org.bukkit.generator.ChunkGenerator.ChunkData
import taboolib.common.util.random

/**
 * ChunkGen 接口 - 用于区块生成的基础接口
 * 提供基本的区块生成方法
 */
interface ChunkGen {
    fun generate(chunkData: ChunkData) {
        // 基础实现，子类可以覆盖
    }

    companion object {
        /**
         * 生成随机方块
         * @param blocks 方块列表
         * @return 随机选择的方块
         */
        fun randomBlock(vararg blocks: Material): Material {
            require(blocks.isNotEmpty()) { "blocks cannot be empty" }
            return blocks[random(0, blocks.size - 1)]
        }

        /**
         * 按权重随机选择材质
         * @param materials 材质列表
         * @param weights 权重列表，与 materials 对应
         * @return 随机选择的材质
         */
        fun weightedRandom(materials: List<Material>, weights: List<Int>): Material {
            require(materials.isNotEmpty()) { "materials cannot be empty" }
            require(materials.size == weights.size) { "materials and weights must have the same size" }
            require(weights.all { it > 0 }) { "weights must be positive" }

            val totalWeight = weights.sum()
            var randomValue = random(1, totalWeight)
            for (i in weights.indices) {
                randomValue -= weights[i]
                if (randomValue <= 0) {
                    return materials[i]
                }
            }
            return materials.first()
        }
    }
}

/**
 * ChunkData 扩展函数 - 扩展区块数据的功能
 */
/**
 * 填满一整层（x/z 都取满 0..15）。
 *
 * ⚠️ **Bukkit `ChunkData.setRegion` 的 xMax / yMax / zMax 是「排他」的（exclusive）。**
 * 这里必须传 `16` 和 `y + 1`。
 *
 * 曾经的写法是 `setRegion(0, y, 0, 15, y, 15, material)`：y 区间变成 `[y, y)` —— 一个空区间，
 * 于是整个调用被服务端**静默忽略**（不写方块、也不报错）。后果是所有用 `fillLayer` 铺的层
 * （-16 基岩、封顶磨制凝灰岩、淡灰色混凝土粉末、砂岩、白色陶瓦…）全部消失，
 * 只剩各生成器里用 `setBlock` 逐块写的内容（如深板岩混合层），
 * 表现就是「部分区块只剩底部的深板岩混合层、上面全空」。
 */
fun ChunkData.fillLayer(y: Int, material: Material) {
    this.setRegion(0, y, 0, 16, y + 1, 16, material)
}

/**
 * 世界最底层（整平的一层一材质）。
 *
 * 基岩从 y=-16 **上移到 y=-12**，腾出来的四格从下到上是：
 * 深板岩、深板岩砖、黑曜石、强化深板岩。
 */
fun ChunkData.fillWorldFloor() {
    fillLayer(WORLD_FLOOR_DECOR_Y, Material.DEEPSLATE)
    fillLayer(WORLD_FLOOR_DECOR_Y + 1, Material.DEEPSLATE_BRICKS)
    fillLayer(WORLD_FLOOR_DECOR_Y + 2, Material.OBSIDIAN)
    fillLayer(WORLD_FLOOR_DECOR_Y + 3, Material.REINFORCED_DEEPSLATE)
    fillLayer(WORLD_FLOOR_BEDROCK_Y, Material.BEDROCK)
}

/**
 * 世界最顶层（基岩从 y=16 **上移到 y=20**，腾出来的四格同样从下到上：
 * 深板岩、深板岩砖、黑曜石、强化深板岩）。
 *
 * GARDEN 不铺这一层（它自己用玻璃 + 屏障封顶）。
 */
fun ChunkData.fillWorldRoof() {
    fillLayer(WORLD_ROOF_DECOR_Y, Material.DEEPSLATE)
    fillLayer(WORLD_ROOF_DECOR_Y + 1, Material.DEEPSLATE_BRICKS)
    fillLayer(WORLD_ROOF_DECOR_Y + 2, Material.OBSIDIAN)
    fillLayer(WORLD_ROOF_DECOR_Y + 3, Material.REINFORCED_DEEPSLATE)
    fillLayer(WORLD_ROOF_BEDROCK_Y, Material.BEDROCK)
}

/** 底层装饰层最下面那一格（深板岩）。 */
const val WORLD_FLOOR_DECOR_Y = -16

/** 底层基岩（原来在 -16，上移 4 格）。 */
const val WORLD_FLOOR_BEDROCK_Y = -12

/** 原先的地层混合层现在从这一格开始（原来从 -15 开始）。 */
const val WORLD_FLOOR_MIX_Y = -11

/** 顶层装饰层最下面那一格（深板岩）。 */
const val WORLD_ROOF_DECOR_Y = 16

/** 顶层基岩（原来在 16，上移 4 格）。 */
const val WORLD_ROOF_BEDROCK_Y = 20

fun ChunkData.setBlockIfEmpty(x: Int, y: Int, z: Int, material: Material) {
    if (this.getBlockData(x, y, z).material == Material.AIR) {
        this.setBlock(x, y, z, material)
    }
}

/**
 * 两格高的植物（高草丛 `TALL_GRASS`、大型蕨 `LARGE_FERN`）。
 *
 * 这类方块是 `Bisected`：只放一个 `TALL_GRASS` 的话，游戏里**只会出现下半截**
 * （上半截是另一个方块，`half = TOP`）。
 *
 * @return 是否真的放下了（上面那格不是空气时不放，免得盖掉别的东西）
 */
fun ChunkData.setDoublePlant(x: Int, y: Int, z: Int, material: Material): Boolean {
    if (getType(x, y, z) != Material.AIR) return false
    if (getType(x, y + 1, z) != Material.AIR) return false

    val bottom = material.createBlockData() as Bisected
    bottom.half = Bisected.Half.BOTTOM
    setBlock(x, y, z, bottom)

    val top = material.createBlockData() as Bisected
    top.half = Bisected.Half.TOP
    setBlock(x, y + 1, z, top)
    return true
}
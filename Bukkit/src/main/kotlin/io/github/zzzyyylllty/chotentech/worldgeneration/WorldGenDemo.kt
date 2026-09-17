@file:Suppress("DEPRECATION", "REMOVAL")

package io.github.zzzyyylllty.chotentech.worldgeneration

import io.github.zzzyyylllty.chotentech.worldgeneration.l1.GL1Terrain
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainAquila
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainCorridor
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainGothic
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainFabled
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainGarden
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainGild
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainOuroboros
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainUnbreakable
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.GL1PlainWalls
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.RoomCorridor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.block.data.BlockData
import org.bukkit.generator.ChunkGenerator
import org.bukkit.material.MaterialData

/**
 * WorldGenDemo - 世界生成调试演示工具
 * 将走廊生成器的输出写入缓冲区，再落到玩家当前位置，用于预览结构。
 */
object WorldGenDemo {

    /** 可用的 demo 类型列表 */
    val availableTypes = listOf(
        "corridor", "aquila", "gild", "gothic", "fabled",
        "walls", "ouroboros", "room", "terrain"
    )

    /**
     * 各 demo 展示的区块尺寸。使用支持的最大尺寸，便于管理员观察完整布局。
     */
    private val demoLayouts = mapOf(
        "corridor" to DemoLayout(1, 1),
        "aquila" to DemoLayout(6, 2),
        "gild" to DemoLayout(8, 4),
        "gothic" to DemoLayout(4, 4),
        "fabled" to DemoLayout(2, 2),
        "walls" to DemoLayout(1, 1),
        "ouroboros" to DemoLayout(2, 8),
        "room" to DemoLayout(2, 2),
        "terrain" to DemoLayout(1, 1),
    )

    /**
     * 在指定位置生成一个 demo。
     * X/Z 会对齐到区块左下角，生成器收到真实的区块坐标和世界种子。
     * y=0 映射到 baseY；demo 覆盖生成器的完整高度范围。
     */
    fun generateDemo(world: World, baseX: Int, baseY: Int, baseZ: Int, type: String): String {
        if (!Bukkit.isPrimaryThread()) {
            return "世界生成 demo 必须在服务器主线程执行"
        }

        val normalizedType = type.lowercase()
        val layout = demoLayouts[normalizedType]
            ?: return "未知的 demo 类型: $type (可用: ${availableTypes.joinToString(", ")})"

        val originChunkX = Math.floorDiv(baseX, 16)
        val originChunkZ = Math.floorDiv(baseZ, 16)
        val originX = originChunkX * 16
        val originZ = originChunkZ * 16
        val worldSeed = world.seed
        val worldMinY = world.minHeight
        val worldMaxY = world.maxHeight
        var count = 0

        for (chunkOffsetX in 0 until layout.widthChunks) {
            for (chunkOffsetZ in 0 until layout.lengthChunks) {
                val chunkX = originChunkX + chunkOffsetX
                val chunkZ = originChunkZ + chunkOffsetZ
                val generator = createGenerator(normalizedType, chunkX, chunkZ, worldSeed)
                val buffer = DemoChunkData()
                generator.generate(buffer)

                for (x in 0..15) {
                    for (y in buffer.minY..buffer.maxY) {
                        val worldY = baseY + y
                        if (worldY !in worldMinY until worldMaxY) continue
                        for (z in 0..15) {
                            val block = world.getBlockAt(
                                originX + chunkOffsetX * 16 + x,
                                worldY,
                                originZ + chunkOffsetZ * 16 + z
                            )
                            val data = buffer.getBlockData(x, y, z)
                            if (data.material == Material.AIR) {
                                block.setType(Material.AIR, false)
                            } else {
                                block.setBlockData(data, false)
                            }
                            count++
                        }
                    }
                }
            }
        }

        val endX = originX + layout.widthChunks * 16 - 1
        val endZ = originZ + layout.lengthChunks * 16 - 1
        return "已生成 $normalizedType demo (${layout.widthChunks}x${layout.lengthChunks}区块，处理 $count 个方块) @ $originX,$baseY,$originZ..$endX,$endZ"
    }

    private fun createGenerator(type: String, chunkX: Int, chunkZ: Int, worldSeed: Long): ChunkGen {
        return when (type) {
            "corridor" -> GL1PlainCorridor(chunkX, chunkZ, worldSeed)
            "aquila" -> GL1PlainAquila(chunkX, chunkZ, worldSeed)
            "gild" -> GL1PlainGild(chunkX, chunkZ, worldSeed)
            "gothic" -> GL1PlainGothic(chunkX, chunkZ, worldSeed)
            "fabled" -> GL1PlainFabled(chunkX, chunkZ, worldSeed)
            "garden" -> GL1PlainGarden(chunkX, chunkZ, worldSeed)
            "walls" -> GL1PlainWalls(chunkX, chunkZ, worldSeed)
            "unbreakable" -> GL1PlainUnbreakable(chunkX, chunkZ, worldSeed)
            "ouroboros" -> GL1PlainOuroboros(chunkX, chunkZ, worldSeed)
            "room" -> RoomCorridor(chunkX, chunkZ, worldSeed)
            "terrain" -> GL1Terrain(chunkX, chunkZ, worldSeed)
            else -> error("Unsupported demo type: $type")
        }
    }

    private data class DemoLayout(val widthChunks: Int, val lengthChunks: Int)

    /**
     * DemoChunkData - 独立于真实区块的 ChunkData 缓冲实现。
     * Paper 的 ChunkData 仍要求实现旧版 MaterialData 重载，因此仅在本文件集中抑制弃用警告。
     */
    class DemoChunkData : ChunkGenerator.ChunkData {
        private val blocks = HashMap<Triple<Int, Int, Int>, BlockData>()

        val minY: Int = -16
        val maxY: Int = 16

        override fun getMinHeight(): Int = minY

        override fun getMaxHeight(): Int = maxY + 1

        override fun getBlockData(x: Int, y: Int, z: Int): BlockData {
            return blocks[Triple(x, y, z)] ?: Material.AIR.createBlockData()
        }

        override fun setBlock(x: Int, y: Int, z: Int, material: Material) {
            blocks[Triple(x, y, z)] = material.createBlockData()
        }

        override fun setBlock(x: Int, y: Int, z: Int, blockData: BlockData) {
            blocks[Triple(x, y, z)] = blockData
        }

        @Deprecated("Deprecated in Java")
        override fun setBlock(x: Int, y: Int, z: Int, materialData: MaterialData) {
            blocks[Triple(x, y, z)] = materialData.itemType.createBlockData()
        }

        override fun setRegion(
            xMin: Int,
            yMin: Int,
            zMin: Int,
            xMax: Int,
            yMax: Int,
            zMax: Int,
            material: Material
        ) {
            // ⚠️ xMax / yMax / zMax 是「排他」的，必须与 Bukkit 契约保持一致。
            // 这里曾经写成 ..xMax（包含），于是 demo 与真实生成行为不一致，
            // 把 fillLayer 的坐标 bug 整整掩盖了很久（demo 看起来正常、真世界缺层）。
            for (x in xMin until xMax) {
                for (y in yMin until yMax) {
                    for (z in zMin until zMax) {
                        setBlock(x, y, z, material)
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun setRegion(
            xMin: Int,
            yMin: Int,
            zMin: Int,
            xMax: Int,
            yMax: Int,
            zMax: Int,
            materialData: MaterialData
        ) {
            for (x in xMin until xMax) {
                for (y in yMin until yMax) {
                    for (z in zMin until zMax) {
                        setBlock(x, y, z, materialData)
                    }
                }
            }
        }

        override fun setRegion(
            xMin: Int,
            yMin: Int,
            zMin: Int,
            xMax: Int,
            yMax: Int,
            zMax: Int,
            blockData: BlockData
        ) {
            for (x in xMin until xMax) {
                for (y in yMin until yMax) {
                    for (z in zMin until zMax) {
                        setBlock(x, y, z, blockData)
                    }
                }
            }
        }

        override fun getType(x: Int, y: Int, z: Int): Material {
            return getBlockData(x, y, z).material
        }

        @Deprecated("Deprecated in Java")
        override fun getTypeAndData(x: Int, y: Int, z: Int): MaterialData {
            return MaterialData(getType(x, y, z))
        }

        @Deprecated("Deprecated in Java")
        override fun getData(x: Int, y: Int, z: Int): Byte = 0

        override fun getBiome(x: Int, y: Int, z: Int): Biome = Biome.PLAINS

        override fun getHeight(heightMap: org.bukkit.HeightMap, x: Int, z: Int): Int {
            for (y in maxY downTo minY) {
                if (getType(x, y, z) != Material.AIR) return y
            }
            return minY
        }
    }
}

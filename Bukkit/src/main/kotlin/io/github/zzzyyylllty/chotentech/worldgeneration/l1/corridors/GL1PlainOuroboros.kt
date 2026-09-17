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
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.FaceAttachable
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
import org.bukkit.block.data.type.Switch
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * GL1PlainOuroboros - 衔尾蛇走廊生成器
 *
 * 生成 2 宽的走廊区域，包含：
 *  - 双色大块地板（陶瓦 / 沙子）
 *  - 外边界墙、青色陶瓦天花板
 *  - 柱子、附加墙、按钮、末地烛、玻璃簇、自然木堆
 *  - 跨区块连续的 L 形管道
 *  - 3×3 ~ 6×6 小房间（含丛林木门、桌椅、灯笼、地毯）
 *  - 走廊内的简单家具
 */
class GL1PlainOuroboros(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    private val regionSizes = listOf(
        Pair(2, 2),
        Pair(2, 3),
        Pair(2, 4)
    )

    private val pipeSlabs = listOf(
        Material.NETHER_BRICK_SLAB,
        Material.DARK_PRISMARINE_SLAB
    )

    private val glassMaterials = listOf(
        Material.GLASS,
        Material.GRAY_STAINED_GLASS,
        Material.BLACK_STAINED_GLASS
    )

    private val pileMaterials = listOf(
        Material.OAK_PLANKS,
        Material.SPRUCE_PLANKS,
        Material.BIRCH_PLANKS,
        Material.JUNGLE_PLANKS,
        Material.ACACIA_PLANKS,
        Material.DARK_OAK_PLANKS,
        Material.GRAVEL,
        Material.SAND,
        Material.COBBLESTONE
    )

    private val woodButtons = listOf(
        Material.OAK_BUTTON,
        Material.SPRUCE_BUTTON,
        Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON,
        Material.ACACIA_BUTTON,
        Material.DARK_OAK_BUTTON
    )

    private val horizontalFaces = listOf(
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST
    )

    override fun generate(chunkData: ChunkData) {
        generateFoundation(chunkData)
        generateFloor(chunkData)
        // 内部区域边界 + GL1Terrain 的区域边界都要砌墙（generateWalls 内部按边判断）
        generateWalls(chunkData)
        generateCeiling(chunkData)
        generateExtraContent(chunkData)
        generateSmallRooms(chunkData)
        generateOutdoorFurniture(chunkData)
    }

    // ==================== 地基与地板 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        for (y in WORLD_FLOOR_MIX_Y..-10) fillLayerDeepslateMixed(chunkData, y)
        for (y in -9..-5) chunkData.fillLayer(y, Material.SANDSTONE)
        for (y in -4..-1) chunkData.fillLayer(y, Material.WHITE_TERRACOTTA)
    }

    /**
     * 双色大块地板：陶瓦 / 沙子。
     * 用 cellSize 大小的粗网格决定底色，细噪声只抖动边缘。
     */
    private fun generateFloor(chunkData: ChunkData) {
        val cellSize = 6

        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z

                val cellX = Math.floorDiv(wx, cellSize)
                val cellZ = Math.floorDiv(wz, cellSize)
                val patch = hashRandom(cellX, 0, cellZ, SALT_FLOOR) % 100

                val jitter = (hashRandom(wx, 1, wz, SALT_FLOOR) % 50) - 25

                val r = patch + jitter
                val mat = if (r < 50) Material.TERRACOTTA else Material.SAND
                chunkData.setBlock(x, 0, z, mat)
            }
        }
    }

    private fun fillLayerDeepslateMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) {
            for (z in 0..15) {
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
    }

    // ==================== 墙壁与天花板 ====================

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

        // 除了自己的内部区域边界，GL1Terrain 的区域边界（邻居 RegionType 不同）也要砌墙。
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

        val isLeft = relX == 0 || RegionBoundary.has(rb, RegionBoundary.WEST) ||
                (sub != null && chunkX == sub.x)
        val isRight = relX == region.width - 1 || RegionBoundary.has(rb, RegionBoundary.EAST) ||
                (sub != null && chunkX == sub.x + sub.w - 1)
        val isTop = relZ == 0 || RegionBoundary.has(rb, RegionBoundary.NORTH) ||
                (sub != null && chunkZ == sub.z)
        val isBottom = relZ == region.length - 1 || RegionBoundary.has(rb, RegionBoundary.SOUTH) ||
                (sub != null && chunkZ == sub.z + sub.h - 1)

        fun setWallColumn(x: Int, z: Int, plan: RegionGateway.Plan?, i: Int) {
            for (y in 1..3) {
                if (!RegionGateway.opensAt(plan, i, y)) chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_TERRACOTTA)
            }
            if (!RegionGateway.opensAt(plan, i, 4)) chunkData.setBlock(x, 4, z, Material.SMOOTH_STONE)
            for (y in 5..6) {
                if (!RegionGateway.opensAt(plan, i, y)) chunkData.setBlock(x, y, z, Material.GRAY_CONCRETE)
            }
        }

        if (isLeft) for (z in 0..15) setWallColumn(0, z, planW, z)
        if (isRight) for (z in 0..15) setWallColumn(15, z, planE, z)
        if (isTop) for (x in 0..15) setWallColumn(x, 0, planN, x)
        if (isBottom) for (x in 0..15) setWallColumn(x, 15, planS, x)

        RegionGateway.placeDoors(chunkData, planN)
        RegionGateway.placeDoors(chunkData, planS)
        RegionGateway.placeDoors(chunkData, planW)
        RegionGateway.placeDoors(chunkData, planE)
    }

    private fun generateCeiling(chunkData: ChunkData) {
        chunkData.fillLayer(7, Material.CYAN_TERRACOTTA)

        // 通用规则：结构最高层(7)以上用磨制凝灰岩覆盖到 y15
        for (y in 8..15) chunkData.fillLayer(y, Material.POLISHED_TUFF)
    }

    // ==================== 额外内容 ====================

    private fun generateExtraContent(chunkData: ChunkData) {
        val s1 = hashRandom(chunkX, 1, chunkZ, SALT_EXTRA_1)
        val s2 = hashRandom(chunkX, 2, chunkZ, SALT_EXTRA_2)
        val s4 = hashRandom(chunkX, 4, chunkZ, SALT_EXTRA_4)
        val s5 = hashRandom(chunkX, 5, chunkZ, SALT_EXTRA_5)
        val s6 = hashRandom(chunkX, 6, chunkZ, SALT_EXTRA_6)
        val s7 = hashRandom(chunkX, 7, chunkZ, SALT_EXTRA_7)
        val s8 = hashRandom(chunkX, 8, chunkZ, SALT_EXTRA_8)

        if (s1 % 100 < 5) generateSpongeFloor(chunkData, s1 / 100)

        generatePillars(chunkData)

        if (s2 % 100 < 4) generateExtraWall(chunkData, s2 / 100)

        generatePipes(chunkData)
        generateEndRods(chunkData, s4)

        // 玻璃刷新率调低到 40%
        if (s5 % 100 < 40) generateGlassClusters(chunkData, s5)
        generatePiles(chunkData, s6)
        generateButtons(chunkData, s7)
        if (s8 % 100 < 40) generateGlassClusters(chunkData, s8)
    }

    private fun generateSpongeFloor(chunkData: ChunkData, seed: Int) {
        val startX = 2 + seed % 10
        val startZ = 2 + (seed / 10) % 10
        for (dx in 0..1) {
            for (dz in 0..1) {
                val x = startX + dx
                val z = startZ + dz
                if (x in 0..15 && z in 0..15 && !isWallAt(x, z)) {
                    chunkData.setBlock(x, 0, z, Material.SPONGE)
                }
            }
        }
    }

    private fun generatePillars(chunkData: ChunkData) {
        val positions = listOf(3 to 3, 3 to 10, 10 to 3, 10 to 10)
        for ((idx, pos) in positions.withIndex()) {
            val (x0, z0) = pos
            val h = hashRandom(chunkX, idx, chunkZ, SALT_PILLAR) % 100
            if (h >= 60) continue

            for (dx in 0..1) {
                for (dz in 0..1) {
                    val x = x0 + dx
                    val z = z0 + dz
                    for (y in 1..3) chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_TERRACOTTA)
                    chunkData.setBlock(x, 4, z, Material.SMOOTH_STONE)
                    chunkData.setBlock(x, 5, z, Material.GRAY_CONCRETE)
                    chunkData.setBlock(x, 6, z, Material.GRAY_CONCRETE)
                }
            }
        }
    }

    private fun generateExtraWall(chunkData: ChunkData, seed: Int) {
        val alongX = seed % 2 == 0
        val length = 3 + (seed / 2) % 4
        if (alongX) {
            val z = 2 + (seed / 8) % 12
            val x0 = 1 + (seed / 64) % 10
            for (i in 0 until length) {
                val x = x0 + i
                if (x > 14) break
                if (isPillarArea(x, z)) continue
                for (y in 1..3) chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_TERRACOTTA)
                chunkData.setBlock(x, 4, z, Material.SMOOTH_STONE)
                chunkData.setBlock(x, 5, z, Material.GRAY_CONCRETE)
                chunkData.setBlock(x, 6, z, Material.GRAY_CONCRETE)
            }
        } else {
            val x = 2 + (seed / 8) % 12
            val z0 = 1 + (seed / 64) % 10
            for (i in 0 until length) {
                val z = z0 + i
                if (z > 14) break
                if (isPillarArea(x, z)) continue
                for (y in 1..3) chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_TERRACOTTA)
                chunkData.setBlock(x, 4, z, Material.SMOOTH_STONE)
                chunkData.setBlock(x, 5, z, Material.GRAY_CONCRETE)
                chunkData.setBlock(x, 6, z, Material.GRAY_CONCRETE)
            }
        }
    }

    // ==================== 管道（跨区块连续） ====================

    /**
     * 沿水平边界（恒定世界 z）的管道局部 x 位置。
     * 参数 worldZ 用边界的世界 z 值，因此相邻区块会算出相同结果。
     */
    private fun hBoundaryX(cx: Int, worldZ: Int): Int =
        hashRandom(cx, worldZ, 0, SALT_PIPE_H) % 16

    /**
     * 沿垂直边界（恒定世界 x）的管道局部 z 位置。
     */
    private fun vBoundaryZ(cz: Int, worldX: Int): Int =
        hashRandom(cz, worldX, 0, SALT_PIPE_V) % 16

    private fun generatePipes(chunkData: ChunkData) {
        // 4 条边界上的管道位置（局部坐标）
        val nX = hBoundaryX(chunkX, chunkZ * 16)           // 北边界(z=0)的局部 x
        val sX = hBoundaryX(chunkX, (chunkZ + 1) * 16)     // 南边界(z=15)的局部 x
        val wZ = vBoundaryZ(chunkZ, chunkX * 16)           // 西边界(x=0)的局部 z
        val eZ = vBoundaryZ(chunkZ, (chunkX + 1) * 16)     // 东边界(x=15)的局部 z

        val seed = hashRandom(chunkX, 3, chunkZ, SALT_EXTRA_3)
        val slab = pipeSlabs[seed % pipeSlabs.size]
        val pairing = (seed / pipeSlabs.size) % 3

        val pairs: List<Pair<Pair<Int, Int>, Pair<Int, Int>>> = when (pairing) {
            0 -> listOf(
                (nX to 0) to (sX to 15),
                (0 to wZ) to (15 to eZ)
            )
            1 -> listOf(
                (nX to 0) to (15 to eZ),
                (sX to 15) to (0 to wZ)
            )
            else -> listOf(
                (nX to 0) to (0 to wZ),
                (sX to 15) to (15 to eZ)
            )
        }

        for ((from, to) in pairs) {
            drawPipePath(chunkData, from, to, slab)
        }
    }

    private fun drawPipePath(
        chunkData: ChunkData,
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        slab: Material
    ) {
        val (x1, z1) = from
        val (x2, z2) = to

        // 转弯顺序确定性
        val turnSeed = (x1 * 31 + z1) xor (x2 * 17 + z2)
        val firstX = turnSeed % 2 == 0

        if (firstX) {
            drawLineX(chunkData, x1, x2, z1, slab)
            drawLineZ(chunkData, z1, z2, x2, slab)
        } else {
            drawLineZ(chunkData, z1, z2, x1, slab)
            drawLineX(chunkData, x1, x2, z2, slab)
        }
    }

    private fun drawLineX(chunkData: ChunkData, fromX: Int, toX: Int, z: Int, slab: Material) {
        val step = if (fromX <= toX) 1 else -1
        var x = fromX
        while (true) {
            tryPlacePipe(chunkData, x, z, slab)
            if (x == toX) break
            x += step
        }
    }

    private fun drawLineZ(chunkData: ChunkData, fromZ: Int, toZ: Int, x: Int, slab: Material) {
        val step = if (fromZ <= toZ) 1 else -1
        var z = fromZ
        while (true) {
            tryPlacePipe(chunkData, x, z, slab)
            if (z == toZ) break
            z += step
        }
    }

    private fun tryPlacePipe(chunkData: ChunkData, x: Int, z: Int, slab: Material) {
        if (x !in 0..15 || z !in 0..15) return
        if (isWallAt(x, z)) return
        if (isPillarArea(x, z)) return
        if (chunkData.getType(x, 6, z) != Material.AIR) return
        chunkData.setBlock(x, 6, z, slab)
    }

    // ==================== 末地烛 / 玻璃 ====================

    private fun generateEndRods(chunkData: ChunkData, seed: Int) {
        val count = 1 + seed % 3
        var s = seed / 3
        repeat(count) {
            val x = 2 + (s % 12)
            val z = 2 + ((s / 12) % 12)
            s /= 144
            val alongX = s % 2 == 0
            s /= 2

            val x1: Int
            val z1: Int
            val x2: Int
            val z2: Int
            val face1: BlockFace
            val face2: BlockFace
            if (alongX) {
                x1 = x; z1 = z; x2 = x + 1; z2 = z
                face1 = BlockFace.EAST
                face2 = BlockFace.WEST
            } else {
                x1 = x; z1 = z; x2 = x; z2 = z + 1
                face1 = BlockFace.SOUTH
                face2 = BlockFace.NORTH
            }

            if (x1 in 1..14 && z1 in 1..14 && x2 in 1..14 && z2 in 1..14 &&
                !isWallAt(x1, z1) && !isWallAt(x2, z2) &&
                !isPillarArea(x1, z1) && !isPillarArea(x2, z2)
            ) {
                if (chunkData.getType(x1, 6, z1) == Material.AIR) {
                    setDirectional(chunkData, Material.END_ROD, x1, 6, z1, face1)
                }
                if (chunkData.getType(x2, 6, z2) == Material.AIR) {
                    setDirectional(chunkData, Material.END_ROD, x2, 6, z2, face2)
                }
            }
        }
    }

    private fun generateGlassClusters(chunkData: ChunkData, seed: Int) {
        val count = 1 + seed % 3
        var s = seed / 3
        repeat(count) {
            val alongX = s % 2 == 0
            s /= 2
            val length = 2 + s % 4
            s /= 4
            val mat = glassMaterials[s % glassMaterials.size]
            s /= glassMaterials.size

            if (alongX) {
                val z = 1 + s % 14
                s /= 14
                val x0 = 1 + s % 12
                s /= 12
                for (i in 0 until length) {
                    val x = x0 + i
                    if (x > 14) break
                    if (isWallAt(x, z) || isPillarArea(x, z)) continue
                    if (chunkData.getType(x, 1, z) == Material.AIR) chunkData.setBlock(x, 1, z, mat)
                    if (chunkData.getType(x, 2, z) == Material.AIR) chunkData.setBlock(x, 2, z, mat)
                }
            } else {
                val x = 1 + s % 14
                s /= 14
                val z0 = 1 + s % 12
                s /= 12
                for (i in 0 until length) {
                    val z = z0 + i
                    if (z > 14) break
                    if (isWallAt(x, z) || isPillarArea(x, z)) continue
                    if (chunkData.getType(x, 1, z) == Material.AIR) chunkData.setBlock(x, 1, z, mat)
                    if (chunkData.getType(x, 2, z) == Material.AIR) chunkData.setBlock(x, 2, z, mat)
                }
            }
        }
    }

    // ==================== 自然木堆 ====================

    private fun generatePiles(chunkData: ChunkData, seed: Int) {
        val count = 1 + seed % 3
        var s = seed / 3
        repeat(count) {
            val baseMat = pileMaterials[s % pileMaterials.size]
            s /= pileMaterials.size
            val cx = 3 + s % 10
            s /= 10
            val cz = 3 + s % 10
            s /= 10
            val height = 1 + s % 4
            s /= 4
            val radius = 1 + s % 3
            s /= 3

            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = cx + dx
                    val z = cz + dz
                    if (x !in 1..14 || z !in 1..14) continue
                    if (isWallAt(x, z) || isPillarArea(x, z)) continue

                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz))

                    val noiseH = (hashRandom(x, 1, z, SALT_PILE) % 3) - 1
                    val h = height - dist + noiseH
                    if (h <= 0) continue

                    if (dist == radius &&
                        hashRandom(x, 2, z, SALT_PILE) % 100 < 50
                    ) continue

                    for (layer in 0 until h) {
                        if (chunkData.getType(x, 1 + layer, z) != Material.AIR) continue
                        val mix = hashRandom(x, 10 + layer, z, SALT_PILE) % 100
                        val mat = if (mix < 85) baseMat
                        else pileMaterials[
                            hashRandom(x, 20 + layer, z, SALT_PILE) % pileMaterials.size
                        ]
                        chunkData.setBlock(x, 1 + layer, z, mat)
                    }
                }
            }

            val scatter = hashRandom(cx, 50, cz, SALT_PILE) % 4
            val span = radius * 2 + 3
            for (i in 0 until scatter) {
                val ox = (hashRandom(cx, 60 + i, cz, SALT_PILE) % span) - radius - 1
                val oz = (hashRandom(cx, 70 + i, cz, SALT_PILE) % span) - radius - 1
                val x = cx + ox
                val z = cz + oz
                if (x !in 1..14 || z !in 1..14) continue
                if (isWallAt(x, z) || isPillarArea(x, z)) continue
                if (chunkData.getType(x, 1, z) == Material.AIR) {
                    chunkData.setBlock(x, 1, z, baseMat)
                }
            }
        }
    }

    // ==================== 按钮 ====================

    private fun generateButtons(chunkData: ChunkData, seed: Int) {
        val count = 1 + seed % 3
        var s = seed / 3
        repeat(count) {
            val x = 1 + (s % 14)
            val z = 1 + ((s / 14) % 14)
            val isWood = (s / 196) % 2 == 0
            val mat = if (isWood) {
                woodButtons[(s / 392) % woodButtons.size]
            } else {
                Material.STONE_BUTTON
            }
            s = s / 392 / 2

            val face = horizontalFaces[(s % 4 + 4) % 4]
            s /= 4

            if (!isWallAt(x, z) && !isPillarArea(x, z)) {
                if (chunkData.getType(x, 1, z) == Material.AIR) {
                    setFloorButton(chunkData, mat, x, 1, z, face)
                }
            }
        }
    }

    // ==================== 工具函数 ====================

    private fun setFloorButton(
        chunkData: ChunkData,
        material: Material,
        x: Int,
        y: Int,
        z: Int,
        facing: BlockFace
    ) {
        val data = material.createBlockData() as Switch
        data.attachedFace = FaceAttachable.AttachedFace.FLOOR
        data.facing = when (facing) {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST -> facing
            else -> BlockFace.NORTH
        }
        chunkData.setBlock(x, y, z, data)
    }

    private fun setDirectional(
        chunkData: ChunkData,
        material: Material,
        x: Int,
        y: Int,
        z: Int,
        facing: BlockFace
    ) {
        val data: BlockData = (material.createBlockData() as Directional).apply {
            this.facing = facing
        }
        chunkData.setBlock(x, y, z, data)
    }

    private fun isWallAt(x: Int, z: Int): Boolean {
        val region = getRegionInfo()
        val relX = chunkX - region.x
        val relZ = chunkZ - region.z
        if (relX == 0 && x == 0) return true
        if (relX == region.width - 1 && x == 15) return true
        if (relZ == 0 && z == 0) return true
        if (relZ == region.length - 1 && z == 15) return true
        return false
    }

    private fun isPillarArea(x: Int, z: Int): Boolean {
        return (x in 3..4 && z in 3..4) ||
                (x in 3..4 && z in 10..11) ||
                (x in 10..11 && z in 3..4) ||
                (x in 10..11 && z in 10..11)
    }

    private fun isBoundaryChunk(): Boolean {
        val region = getRegionInfo()
        val relX = chunkX - region.x
        val relZ = chunkZ - region.z
        return relX == 0 || relX == region.width - 1 ||
                relZ == 0 || relZ == region.length - 1
    }

    private fun getRegionInfo(): RegionInfo {
        // 容器放大：超级块与区域尺寸都 ×2；容器内部再由 SubRegionLayout 用原始尺寸切子区域
        val mega = 24
        val megaX = Math.floorDiv(chunkX, mega)
        val megaZ = Math.floorDiv(chunkZ, mega)
        val h = hashRandom(megaX, 0, megaZ, SALT_REGION)
        val (w0, l0) = regionSizes[h % regionSizes.size]
        val w = w0 * 2
        val l = l0 * 2

        val localX = chunkX - megaX * mega
        val localZ = chunkZ - megaZ * mega
        val regionX = megaX * mega + Math.floorDiv(localX, w) * w
        val regionZ = megaZ * mega + Math.floorDiv(localZ, l) * l
        return RegionInfo(regionX, regionZ, w, l)
    }

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

    // ==================== 小房间（3×3 ~ 6×6，覆盖柱子） ====================

    private fun generateSmallRooms(chunkData: ChunkData) {
        val h = hashRandom(chunkX, 99, chunkZ, SALT_ROOM)
        if (h % 100 >= 60) return

        var s = h / 100

        val sizeX = 3 + s % 4; s /= 4
        val sizeZ = 3 + s % 4; s /= 4

        val maxStartX = 15 - sizeX
        val maxStartZ = 15 - sizeZ
        if (maxStartX < 1 || maxStartZ < 1) return

        val startX = 1 + (s % maxStartX); s /= maxStartX
        val startZ = 1 + (s % maxStartZ); s /= maxStartZ

        val endX = startX + sizeX - 1
        val endZ = startZ + sizeZ - 1

        // 清空内部 y1..6（覆盖柱子等）
        for (x in startX + 1 until endX) {
            for (z in startZ + 1 until endZ) {
                for (y in 1..6) {
                    chunkData.setBlock(x, y, z, Material.AIR)
                }
            }
        }

        // 墙壁
        for (x in startX..endX) {
            for (z in startZ..endZ) {
                val onEdge = x == startX || x == endX || z == startZ || z == endZ
                if (!onEdge) continue
                for (y in 1..3) chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_TERRACOTTA)
                chunkData.setBlock(x, 4, z, Material.SMOOTH_STONE)
                chunkData.setBlock(x, 5, z, Material.GRAY_CONCRETE)
                chunkData.setBlock(x, 6, z, Material.GRAY_CONCRETE)
            }
        }

        // 开门：按哈希顺序尝试 4 个方向，找到门外是空气的那面墙
        val firstSide = s % 4; s /= 4
        val doorX0 = startX + sizeX / 2
        val doorZ0 = startZ + sizeZ / 2

        val sides = listOf(
            Triple(doorX0 to startZ, doorX0 to startZ - 1, BlockFace.NORTH),
            Triple(endX to doorZ0, endX + 1 to doorZ0, BlockFace.EAST),
            Triple(doorX0 to endZ, doorX0 to endZ + 1, BlockFace.SOUTH),
            Triple(startX to doorZ0, startX - 1 to doorZ0, BlockFace.WEST)
        )

        for (i in 0 until 4) {
            val (doorPos, outside, facing) = sides[(firstSide + i) % 4]
            val (dx, dz) = doorPos
            val (ox, oz) = outside

            if (ox !in 0..15 || oz !in 0..15) continue
            if (isWallAt(ox, oz)) continue
            if (isPillarArea(ox, oz)) continue
            if (chunkData.getType(ox, 1, oz) != Material.AIR) continue
            if (chunkData.getType(ox, 2, oz) != Material.AIR) continue

            placeDoor(chunkData, dx, dz, facing)
            break
        }

        // 室内家具
        generateRoomFurniture(chunkData, startX, startZ, endX, endZ, s)
        s /= 7

        // 天花板灯
        val cx = startX + sizeX / 2
        val cz = startZ + sizeZ / 2
        if (chunkData.getType(cx, 6, cz) == Material.AIR) {
            chunkData.setBlock(cx, 6, cz, Material.SEA_LANTERN)
        }

        // 地面地毯
        val carpetPos = findFreeFloor(chunkData, startX + 1, startZ + 1, endX - 1, endZ - 1)
            ?: (cx to cz)
        val carpet = when (s % 4) {
            0 -> Material.RED_CARPET
            1 -> Material.BLUE_CARPET
            2 -> Material.YELLOW_CARPET
            else -> Material.GREEN_CARPET
        }
        if (chunkData.getType(carpetPos.first, 1, carpetPos.second) == Material.AIR) {
            chunkData.setBlock(carpetPos.first, 1, carpetPos.second, carpet)
        }
    }

    private fun placeDoor(chunkData: ChunkData, x: Int, z: Int, facing: BlockFace) {
        if (x !in 0..15 || z !in 0..15) return

        val bottom = Material.JUNGLE_DOOR.createBlockData() as Door
        bottom.facing = facing
        bottom.half = Bisected.Half.BOTTOM
        bottom.hinge = Door.Hinge.LEFT
        chunkData.setBlock(x, 1, z, bottom)

        val top = Material.JUNGLE_DOOR.createBlockData() as Door
        top.facing = facing
        top.half = Bisected.Half.TOP
        top.hinge = Door.Hinge.LEFT
        chunkData.setBlock(x, 2, z, top)
    }

    // ==================== 室内家具 ====================

    private fun generateRoomFurniture(
        chunkData: ChunkData,
        startX: Int, startZ: Int,
        endX: Int, endZ: Int,
        seed: Int
    ) {
        var s = seed
        val ix1 = startX + 1
        val ix2 = endX - 1
        val iz1 = startZ + 1
        val iz2 = endZ - 1
        if (ix2 < ix1 || iz2 < iz1) return

        // 角落大件
        val corners = listOf(
            ix1 to iz1, ix2 to iz1,
            ix1 to iz2, ix2 to iz2
        )
        val (bx, bz) = corners[s % 4]; s /= 4
        val bigMat = when (s % 3) {
            0 -> Material.BOOKSHELF
            1 -> Material.CRAFTING_TABLE
            else -> Material.CHEST
        }; s /= 3
        if (chunkData.getType(bx, 1, bz) == Material.AIR) {
            chunkData.setBlock(bx, 1, bz, bigMat)
        }

        // 桌椅
        val cx = (ix1 + ix2) / 2
        val cz = (iz1 + iz2) / 2

        if (chunkData.getType(cx, 1, cz) == Material.AIR &&
            chunkData.getType(cx, 2, cz) == Material.AIR
        ) {
            chunkData.setBlock(cx, 1, cz, Material.OAK_PLANKS)
            val top = Material.OAK_SLAB.createBlockData() as Slab
            top.type = Slab.Type.TOP
            chunkData.setBlock(cx, 2, cz, top)
        }

        if (chunkData.getType(cx, 3, cz) == Material.AIR) {
            chunkData.setBlock(cx, 3, cz, Material.LANTERN)
        }

        // 椅子：Triple 解构
        val chairDirs = listOf(
            Triple(cx + 1, cz, BlockFace.WEST),
            Triple(cx - 1, cz, BlockFace.EAST),
            Triple(cx, cz + 1, BlockFace.NORTH),
            Triple(cx, cz - 1, BlockFace.SOUTH)
        )
        for ((chx, chz, face) in chairDirs) {
            if (chx !in ix1..ix2 || chz !in iz1..iz2) continue
            if (chunkData.getType(chx, 1, chz) != Material.AIR) continue
            val stair = Material.OAK_STAIRS.createBlockData() as Stairs
            stair.facing = face
            chunkData.setBlock(chx, 1, chz, stair)
            break
        }
    }

    private fun findFreeFloor(
        chunkData: ChunkData,
        x1: Int, z1: Int, x2: Int, z2: Int
    ): Pair<Int, Int>? {
        for (x in x1..x2) {
            for (z in z1..z2) {
                if (chunkData.getType(x, 1, z) == Material.AIR) return x to z
            }
        }
        return null
    }

    // ==================== 走廊家具 ====================

    private fun generateOutdoorFurniture(chunkData: ChunkData) {
        val h = hashRandom(chunkX, 77, chunkZ, SALT_OUTDOOR)
        var s = h

        val count = 1 + s % 3; s /= 3
        repeat(count) {
            val x = 1 + s % 14; s /= 14
            val z = 1 + s % 14; s /= 14
            if (isWallAt(x, z) || isPillarArea(x, z)) return@repeat
            if (chunkData.getType(x, 1, z) != Material.AIR) return@repeat
            if (isNextToRoomWall(chunkData, x, z)) return@repeat

            when (s % 4) {
                0 -> {
                    val stair = Material.OAK_STAIRS.createBlockData() as Stairs
                    stair.facing = horizontalFaces[(s / 4) % 4]
                    chunkData.setBlock(x, 1, z, stair)
                }
                1 -> chunkData.setBlock(x, 1, z, Material.LANTERN)
                2 -> chunkData.setBlock(x, 1, z, Material.FLOWER_POT)
                3 -> chunkData.setBlock(x, 1, z, Material.BARREL)
            }
            s /= 16
        }
    }

    private fun isNextToRoomWall(chunkData: ChunkData, x: Int, z: Int): Boolean {
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dx, dz) in dirs) {
            val nx = x + dx
            val nz = z + dz
            if (nx !in 0..15 || nz !in 0..15) continue
            if (chunkData.getType(nx, 1, nz) == Material.LIGHT_GRAY_TERRACOTTA) {
                return true
            }
        }
        return false
    }

    private data class RegionInfo(
        val x: Int,
        val z: Int,
        val width: Int,
        val length: Int
    )

    companion object {
        private const val SALT_FLOOR = 100L
        private const val SALT_DEEPSLATE = 101L
        private const val SALT_REGION = 102L
        private const val SALT_PILLAR = 103L
        private const val SALT_EXTRA_1 = 201L
        private const val SALT_EXTRA_2 = 202L
        private const val SALT_EXTRA_3 = 203L
        private const val SALT_EXTRA_4 = 204L
        private const val SALT_EXTRA_5 = 205L
        private const val SALT_EXTRA_6 = 206L
        private const val SALT_EXTRA_7 = 207L
        private const val SALT_EXTRA_8 = 208L
        private const val SALT_ROOM = 209L
        private const val SALT_PILE = 210L
        private const val SALT_OUTDOOR = 211L
        private const val SALT_PIPE_H = 212L
        private const val SALT_PIPE_V = 213L
    }
}
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
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Slab
import org.bukkit.generator.ChunkGenerator.ChunkData
import java.util.Random

/**
 * GL1PlainAquila - 鹰形走廊生成器
 */
class GL1PlainAquila(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    private val regionSizes = listOf(
        Pair(2, 2),
        Pair(2, 4),
        Pair(4, 2),
        Pair(2, 6),
        Pair(6, 2)
    )

    private val wallMaterials = setOf(
        Material.QUARTZ_PILLAR,
        Material.LIGHT_GRAY_CONCRETE,
        Material.WHITE_CONCRETE,
        Material.GRAY_CONCRETE,
        Material.DIORITE,
        Material.BASALT,
        Material.POLISHED_DIORITE
    )

    private val fenceTypes = listOf(
        Material.OAK_FENCE,
        Material.SPRUCE_FENCE,
        Material.BIRCH_FENCE,
        Material.JUNGLE_FENCE,
        Material.ACACIA_FENCE,
        Material.DARK_OAK_FENCE,
        Material.NETHER_BRICK_FENCE,
        Material.CRIMSON_FENCE,
        Material.WARPED_FENCE
    )

    override fun generate(chunkData: ChunkData) {
        generateFoundation(chunkData)
        generateFloor(chunkData)

        val region = getRegionInfo()
        // 区域边界以 GL1Terrain 的 RegionType 布局为准（RegionBoundary），
        // 否则会出现「一边砌墙、一边没砌」，把别的区域的外墙裸露在 Aquila 区域里。
        val rb = RegionBoundary.of(chunkX, chunkZ, worldSeed)
        // 子区域边界也要砌内墙（放大的容器内部会被切成几个小区域）
        val sub = SubRegionLayout.of(
            chunkX, chunkZ, worldSeed,
            region.x, region.z, region.w, region.h, regionSizes
        )
        val isLeft = chunkX == region.x || RegionBoundary.has(rb, RegionBoundary.WEST) ||
                (sub != null && chunkX == sub.x)
        val isRight = chunkX == region.x + region.w - 1 || RegionBoundary.has(rb, RegionBoundary.EAST) ||
                (sub != null && chunkX == sub.x + sub.w - 1)
        val isTop = chunkZ == region.z || RegionBoundary.has(rb, RegionBoundary.NORTH) ||
                (sub != null && chunkZ == sub.z)
        val isBottom = chunkZ == region.z + region.h - 1 || RegionBoundary.has(rb, RegionBoundary.SOUTH) ||
                (sub != null && chunkZ == sub.z + sub.h - 1)

        if (isLeft || isRight || isTop || isBottom) {
            generateWalls(chunkData, isLeft, isRight, isTop, isBottom)
        }

        generateRooms(chunkData)
        generateFurnitureInRooms(chunkData)

        val rand = Random(worldSeed * 31L + chunkX * 1337L + chunkZ * 7331L)
        generateExtraContent(chunkData, rand)

        generateDecorationPiles(chunkData)

        generatePipes(chunkData)
    }

    // ==================== 地层 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        for (y in WORLD_FLOOR_MIX_Y..-5) fillLayerDeepslateMixed(chunkData, y)

        for (y in -4..-1) {
            for (x in 0..15) {
                for (z in 0..15) {
                    val wx = chunkX * 16 + x
                    val wz = chunkZ * 16 + z
                    val r = hashRandom(wx, y, wz, SALT_STONE_MIX) % 3
                    val mat = when (r) {
                        0 -> Material.GRAVEL
                        1 -> Material.STONE
                        else -> Material.COBBLESTONE
                    }
                    chunkData.setBlock(x, y, z, mat)
                }
            }
        }

        // 天花板：y8~y9 磨制闪长岩
        chunkData.fillLayer(8, Material.POLISHED_DIORITE)
        chunkData.fillLayer(9, Material.POLISHED_DIORITE)

        // 通用规则：结构最高层(9)以上用磨制凝灰岩覆盖到 y15
        for (y in 10..15) chunkData.fillLayer(y, Material.POLISHED_TUFF)
    }

    private fun generateFloor(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val r = hashRandom(wx, 0, wz, SALT_FLOOR) % 4
                val mat = when (r) {
                    0, 1 -> Material.STONE
                    2 -> Material.ANDESITE
                    else -> Material.TUFF
                }
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

    // ==================== 区域信息 ====================

    private fun getRegionInfo(): RegionInfo {
        // 容器放大：超级块与区域尺寸都 ×2；容器内部再由 SubRegionLayout 用原始尺寸切子区域
        val mega = 24
        val megaX = Math.floorDiv(chunkX, mega)
        val megaZ = Math.floorDiv(chunkZ, mega)
        val h = hashRandom(megaX, 0, megaZ, SALT_REGION)
        val idx = ((h % regionSizes.size) + regionSizes.size) % regionSizes.size
        val (w0, hh0) = regionSizes[idx]
        val w = w0 * 2
        val hh = hh0 * 2

        val localX = chunkX - megaX * mega
        val localZ = chunkZ - megaZ * mega
        val regionX = megaX * mega + Math.floorDiv(localX, w) * w
        val regionZ = megaZ * mega + Math.floorDiv(localZ, hh) * hh
        return RegionInfo(regionX, regionZ, w, hh)
    }

    private fun getRegionInfoAt(wx: Int, wz: Int): RegionInfo {
        val cx = Math.floorDiv(wx, 16)
        val cz = Math.floorDiv(wz, 16)
        val mega = 24
        val megaX = Math.floorDiv(cx, mega)
        val megaZ = Math.floorDiv(cz, mega)
        val h = hashRandom(megaX, 0, megaZ, SALT_REGION)
        val idx = ((h % regionSizes.size) + regionSizes.size) % regionSizes.size
        val (w0, hh0) = regionSizes[idx]
        val w = w0 * 2
        val hh = hh0 * 2

        val localX = cx - megaX * mega
        val localZ = cz - megaZ * mega
        val regionX = megaX * mega + Math.floorDiv(localX, w) * w
        val regionZ = megaZ * mega + Math.floorDiv(localZ, hh) * hh
        return RegionInfo(regionX, regionZ, w, hh)
    }

    private fun isWallAt(wx: Int, wz: Int): Boolean {
        val region = getRegionInfoAt(wx, wz)
        val cx = Math.floorDiv(wx, 16)
        val cz = Math.floorDiv(wz, 16)
        val lx = Math.floorMod(wx, 16)
        val lz = Math.floorMod(wz, 16)
        val relX = cx - region.x
        val relZ = cz - region.z
        return (relX == 0 && lx == 0) ||
                (relX == region.w - 1 && lx == 15) ||
                (relZ == 0 && lz == 0) ||
                (relZ == region.h - 1 && lz == 15)
    }

    // ==================== 墙壁 ====================

    private fun generateWalls(
        chunkData: ChunkData,
        isLeft: Boolean, isRight: Boolean, isTop: Boolean, isBottom: Boolean
    ) {
        // 交界处的门洞按 RegionGateway 的方案留空（只有一层门，且不会多挖）
        // 子区域边界（容器内部切出来的小区域）之间也要能走通，否则放大后的地形会变成一堆密封盒子
        val region = getRegionInfo()
        val sub = SubRegionLayout.of(
            chunkX, chunkZ, worldSeed, region.x, region.z, region.w, region.h, regionSizes
        )
        val planN = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.NORTH)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.NORTH, sub)
        val planS = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.SOUTH)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.SOUTH, sub)
        val planW = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.WEST)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.WEST, sub)
        val planE = RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.EAST)
            ?: RegionGateway.planForSubRegion(chunkX, chunkZ, worldSeed, RegionBoundary.EAST, sub)

        fun placeWall(x: Int, z: Int, plan: RegionGateway.Plan?, i: Int) {
            if (!RegionGateway.opensAt(plan, i, 1)) chunkData.setBlock(x, 1, z, Material.QUARTZ_PILLAR)
            for (y in 2..7) {
                if (!RegionGateway.opensAt(plan, i, y)) chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_CONCRETE)
            }
        }

        if (isLeft)   for (z in 0..15) placeWall(0, z, planW, z)
        if (isRight)  for (z in 0..15) placeWall(15, z, planE, z)
        if (isTop)    for (x in 0..15) placeWall(x, 0, planN, x)
        if (isBottom) for (x in 0..15) placeWall(x, 15, planS, x)

        RegionGateway.placeDoors(chunkData, planN)
        RegionGateway.placeDoors(chunkData, planS)
        RegionGateway.placeDoors(chunkData, planW)
        RegionGateway.placeDoors(chunkData, planE)

        val rand = Random(worldSeed * 17L + chunkX * 8675309L + chunkZ * 3141592L)

        if (rand.nextInt(100) < 20) {
            generateProtrusion(chunkData, isLeft, isRight, isTop, isBottom, rand)
        }

        generateWallLampUnits(chunkData, isLeft, isRight, isTop, isBottom)

        val fencePositions = HashSet<Triple<Int, Int, Int>>()

        generateWallFenceDecorations(chunkData, isLeft, isRight, isTop, isBottom, rand, fencePositions)
        generateIronBarsOnWallTop(chunkData, isLeft, isRight, isTop, isBottom, rand, fencePositions)

        refreshFenceConnections(chunkData, fencePositions)

        if (rand.nextInt(100) < 30) {
            val chainX = 3 + rand.nextInt(10)
            val chainZ = 3 + rand.nextInt(10)
            for (y in 1..7) {
                if (chunkData.getType(chainX, y, chainZ) == Material.AIR) {
                    chunkData.setBlock(chainX, y, chainZ, Material.IRON_CHAIN)
                }
            }
        }
    }

    private fun generateProtrusion(
        chunkData: ChunkData,
        isLeft: Boolean, isRight: Boolean, isTop: Boolean, isBottom: Boolean,
        rand: Random
    ) {
        val length = 4 + rand.nextInt(9)
        val start = 2 + rand.nextInt(14 - length)

        for (i in 0 until length) {
            val isEnd = i == 0 || i == length - 1
            val pos = start + i

            if (isLeft) {
                if (isEnd) {
                    for (dx in 1..2) {
                        chunkData.setBlock(dx, 1, pos, Material.QUARTZ_PILLAR)
                        for (y in 2..7) chunkData.setBlock(dx, y, pos, Material.LIGHT_GRAY_CONCRETE)
                    }
                } else {
                    chunkData.setBlock(1, 1, pos, Material.GRAY_CONCRETE)
                    for (y in 2..7) chunkData.setBlock(1, y, pos, Material.DIORITE)
                }
            }
            if (isRight) {
                if (isEnd) {
                    for (dx in 14 downTo 13) {
                        chunkData.setBlock(dx, 1, pos, Material.QUARTZ_PILLAR)
                        for (y in 2..7) chunkData.setBlock(dx, y, pos, Material.LIGHT_GRAY_CONCRETE)
                    }
                } else {
                    chunkData.setBlock(14, 1, pos, Material.GRAY_CONCRETE)
                    for (y in 2..7) chunkData.setBlock(14, y, pos, Material.DIORITE)
                }
            }
            if (isTop) {
                if (isEnd) {
                    for (dz in 1..2) {
                        chunkData.setBlock(pos, 1, dz, Material.QUARTZ_PILLAR)
                        for (y in 2..7) chunkData.setBlock(pos, y, dz, Material.LIGHT_GRAY_CONCRETE)
                    }
                } else {
                    chunkData.setBlock(pos, 1, 1, Material.GRAY_CONCRETE)
                    for (y in 2..7) chunkData.setBlock(pos, y, 1, Material.DIORITE)
                }
            }
            if (isBottom) {
                if (isEnd) {
                    for (dz in 14 downTo 13) {
                        chunkData.setBlock(pos, 1, dz, Material.QUARTZ_PILLAR)
                        for (y in 2..7) chunkData.setBlock(pos, y, dz, Material.LIGHT_GRAY_CONCRETE)
                    }
                } else {
                    chunkData.setBlock(pos, 1, 14, Material.GRAY_CONCRETE)
                    for (y in 2..7) chunkData.setBlock(pos, y, 14, Material.DIORITE)
                }
            }
        }
    }

    private fun placeLampUnit(chunkData: ChunkData, x: Int, z: Int) {
        if (x !in 0..15 || z !in 0..15) return
        if (chunkData.getType(x, 5, z) != Material.AIR) return
        if (chunkData.getType(x, 4, z) != Material.AIR) return

        chunkData.setBlock(x, 5, z, Material.STONE_BRICK_WALL)
        val rod = Material.END_ROD.createBlockData() as Directional
        rod.facing = BlockFace.DOWN
        chunkData.setBlock(x, 4, z, rod)
    }

    private fun generateWallLampUnits(
        chunkData: ChunkData,
        isLeft: Boolean, isRight: Boolean, isTop: Boolean, isBottom: Boolean
    ) {
        if (isLeft)   for (z in 1..14 step 3) placeLampUnit(chunkData, 1, z)
        if (isRight)  for (z in 1..14 step 3) placeLampUnit(chunkData, 14, z)
        if (isTop)    for (x in 1..14 step 3) placeLampUnit(chunkData, x, 1)
        if (isBottom) for (x in 1..14 step 3) placeLampUnit(chunkData, x, 14)
    }

    private fun generateWallFenceDecorations(
        chunkData: ChunkData,
        isLeft: Boolean, isRight: Boolean, isTop: Boolean, isBottom: Boolean,
        rand: Random,
        positions: MutableSet<Triple<Int, Int, Int>>
    ) {
        val attempts = 1 + rand.nextInt(2)
        repeat(attempts) {
            val length = 3 + rand.nextInt(2)
            val height = 3 + rand.nextInt(2)
            val mat = fenceTypes[rand.nextInt(fenceTypes.size)]
            val startOffset = rand.nextInt(16 - length + 1)

            fun placeFencePanel(getX: (Int) -> Int, getZ: (Int) -> Int) {
                for (i in 0 until length) {
                    for (y in 1..height) {
                        val x = getX(i)
                        val z = getZ(i)
                        if (x !in 0..15 || z !in 0..15) continue
                        if (chunkData.getType(x, y, z) == Material.AIR) {
                            chunkData.setBlock(x, y, z, mat)
                            positions.add(Triple(x, y, z))
                        }
                    }
                }
            }

            when {
                isLeft -> placeFencePanel({ 1 }, { startOffset + it })
                isRight -> placeFencePanel({ 14 }, { startOffset + it })
                isTop -> placeFencePanel({ startOffset + it }, { 1 })
                isBottom -> placeFencePanel({ startOffset + it }, { 14 })
            }
        }
    }

    private fun generateIronBarsOnWallTop(
        chunkData: ChunkData,
        isLeft: Boolean, isRight: Boolean, isTop: Boolean, isBottom: Boolean,
        rand: Random,
        positions: MutableSet<Triple<Int, Int, Int>>
    ) {
        if (rand.nextInt(100) >= 50) return

        val length = 2 + rand.nextInt(5)
        val startOffset = rand.nextInt(16 - length + 1)

        fun placeBars(getX: (Int) -> Int, getZ: (Int) -> Int) {
            for (i in 0 until length) {
                val x = getX(i)
                val z = getZ(i)
                if (x !in 0..15 || z !in 0..15) continue
                if (chunkData.getType(x, 8, z) == Material.AIR) {
                    chunkData.setBlock(x, 8, z, Material.IRON_BARS)
                    positions.add(Triple(x, 8, z))
                }
            }
        }

        when {
            isLeft   -> placeBars({ 0 },      { startOffset + it })
            isRight  -> placeBars({ 15 },     { startOffset + it })
            isTop    -> placeBars({ startOffset + it }, { 0 })
            isBottom -> placeBars({ startOffset + it }, { 15 })
        }
    }

    private fun refreshFenceConnections(
        chunkData: ChunkData,
        positions: Set<Triple<Int, Int, Int>>
    ) {
        for ((x, y, z) in positions) {
            val data = chunkData.getBlockData(x, y, z) as? MultipleFacing ?: continue
            data.setFace(BlockFace.NORTH, isFenceLike(chunkData, x, y, z - 1))
            data.setFace(BlockFace.SOUTH, isFenceLike(chunkData, x, y, z + 1))
            data.setFace(BlockFace.EAST, isFenceLike(chunkData, x + 1, y, z))
            data.setFace(BlockFace.WEST, isFenceLike(chunkData, x - 1, y, z))
            chunkData.setBlock(x, y, z, data)
        }
    }

    private fun isFenceLike(chunkData: ChunkData, x: Int, y: Int, z: Int): Boolean {
        if (x !in 0..15 || z !in 0..15) return false
        val mat = chunkData.getType(x, y, z)
        if (mat == Material.AIR) return false
        if (mat == Material.IRON_BARS) return true
        if (mat.name.endsWith("_FENCE")) return true
        return mat.isSolid
    }

    // ==================== 房间 ====================

    private fun generateRooms(chunkData: ChunkData) {
        val region = getRegionInfo()
        val rMinX = region.x * 16
        val rMinZ = region.z * 16
        val rMaxX = (region.x + region.w) * 16 - 1
        val rMaxZ = (region.z + region.h) * 16 - 1

        val roomRand = Random(
            hashRandom(region.x, 0, region.z, SALT_ROOM).toLong() xor (worldSeed * 131L)
        )

        if (roomRand.nextInt(100) >= 80) return

        generateBoxRoom(chunkData, rMinX, rMinZ, rMaxX, rMaxZ, roomRand)
    }

    private fun generateBoxRoom(
        chunkData: ChunkData,
        rMinX: Int, rMinZ: Int, rMaxX: Int, rMaxZ: Int,
        rand: Random
    ) {
        val roomW = 7 + rand.nextInt(5)
        val roomH = 7 + rand.nextInt(5)

        val xRange = (rMaxX - rMinX + 1) - roomW - 2
        val zRange = (rMaxZ - rMinZ + 1) - roomH - 2
        if (xRange < 2 || zRange < 2) return

        val roomMinX = rMinX + 1 + rand.nextInt(xRange)
        val roomMinZ = rMinZ + 1 + rand.nextInt(zRange)
        val roomMaxX = roomMinX + roomW - 1
        val roomMaxZ = roomMinZ + roomH - 1

        val doorCount = 1 + rand.nextInt(2)
        val allSides = mutableListOf(0, 1, 2, 3)
        for (i in allSides.size - 1 downTo 1) {
            val j = rand.nextInt(i + 1)
            val tmp = allSides[i]
            allSides[i] = allSides[j]
            allSides[j] = tmp
        }
        val doorSides = allSides.take(doorCount).toSet()

        val doorX = roomMinX + roomW / 2
        val doorZ = roomMinZ + roomH / 2

        val doorHinge: Door.Hinge =
            if (rand.nextBoolean()) Door.Hinge.LEFT else Door.Hinge.RIGHT

        for (x in roomMinX..roomMaxX) {
            for (z in roomMinZ..roomMaxZ) {
                val onXEdge = x == roomMinX || x == roomMaxX
                val onZEdge = z == roomMinZ || z == roomMaxZ
                if (!onXEdge && !onZEdge) continue

                val localX = x - chunkX * 16
                val localZ = z - chunkZ * 16
                if (localX !in 0..15 || localZ !in 0..15) continue

                var doorPlaced = false
                if (0 in doorSides && z == roomMinZ && x == doorX) {
                    placeOakDoor(chunkData, localX, localZ, BlockFace.NORTH, doorHinge)
                    doorPlaced = true
                } else if (1 in doorSides && x == roomMaxX && z == doorZ) {
                    placeOakDoor(chunkData, localX, localZ, BlockFace.EAST, doorHinge)
                    doorPlaced = true
                } else if (2 in doorSides && z == roomMaxZ && x == doorX) {
                    placeOakDoor(chunkData, localX, localZ, BlockFace.SOUTH, doorHinge)
                    doorPlaced = true
                } else if (3 in doorSides && x == roomMinX && z == doorZ) {
                    placeOakDoor(chunkData, localX, localZ, BlockFace.WEST, doorHinge)
                    doorPlaced = true
                }

                if (doorPlaced) {
                    for (y in 3..7) {
                        chunkData.setBlock(localX, y, localZ, Material.LIGHT_GRAY_CONCRETE)
                    }
                    continue
                }

                val isCorner = onXEdge && onZEdge
                if (isCorner) {
                    for (y in 1..7) chunkData.setBlock(localX, y, localZ, Material.QUARTZ_PILLAR)
                } else {
                    chunkData.setBlock(localX, 1, localZ, Material.QUARTZ_PILLAR)
                    for (y in 2..7) chunkData.setBlock(localX, y, localZ, Material.LIGHT_GRAY_CONCRETE)
                }
            }
        }

        val cX = (roomMinX + roomMaxX) / 2
        val cZ = (roomMinZ + roomMaxZ) / 2
        val localCX = cX - chunkX * 16
        val localCZ = cZ - chunkZ * 16
        if (localCX in 0..15 && localCZ in 0..15) {
            if (chunkData.getType(localCX, 7, localCZ) == Material.AIR) {
                chunkData.setBlock(localCX, 7, localCZ, Material.SEA_LANTERN)
            }
        }
    }

    private fun placeOakDoor(
        cd: ChunkData,
        lx: Int, lz: Int,
        facing: BlockFace,
        hinge: Door.Hinge
    ) {
        if (lx !in 0..15 || lz !in 0..15) return

        val lower = Material.OAK_DOOR.createBlockData() as Door
        lower.facing = facing
        lower.hinge = hinge
        lower.isOpen = false
        lower.half = Bisected.Half.BOTTOM
        cd.setBlock(lx, 1, lz, lower)

        val upper = Material.OAK_DOOR.createBlockData() as Door
        upper.facing = facing
        upper.hinge = hinge
        upper.isOpen = false
        upper.half = Bisected.Half.TOP
        cd.setBlock(lx, 2, lz, upper)
    }

    private fun isInsideAnyRoom(wx: Int, wz: Int): Boolean {
        val region = getRegionInfo()
        val rMinX = region.x * 16
        val rMinZ = region.z * 16
        val rMaxX = (region.x + region.w) * 16 - 1
        val rMaxZ = (region.z + region.h) * 16 - 1

        val roomRand = Random(
            hashRandom(region.x, 0, region.z, SALT_ROOM).toLong() xor (worldSeed * 131L)
        )
        if (roomRand.nextInt(100) >= 80) return false

        val roomW = 7 + roomRand.nextInt(5)
        val roomH = 7 + roomRand.nextInt(5)
        val xRange = (rMaxX - rMinX + 1) - roomW - 2
        val zRange = (rMaxZ - rMinZ + 1) - roomH - 2
        if (xRange < 2 || zRange < 2) return false

        val roomMinX = rMinX + 1 + roomRand.nextInt(xRange)
        val roomMinZ = rMinZ + 1 + roomRand.nextInt(zRange)
        val roomMaxX = roomMinX + roomW - 1
        val roomMaxZ = roomMinZ + roomH - 1

        return wx in roomMinX..roomMaxX && wz in roomMinZ..roomMaxZ
    }

    // ==================== 房间家具 ====================

    private fun generateFurnitureInRooms(cd: ChunkData) {
        val cell = 3
        val pad = 6
        val lMinX = chunkX * 16
        val lMinZ = chunkZ * 16
        val lMaxX = lMinX + 15
        val lMaxZ = lMinZ + 15

        val firstAX = Math.floorDiv(lMinX - pad, cell) * cell + cell
        val lastAX = Math.floorDiv(lMaxX + pad, cell) * cell
        val firstAZ = Math.floorDiv(lMinZ - pad, cell) * cell + cell
        val lastAZ = Math.floorDiv(lMaxZ + pad, cell) * cell

        var ax = firstAX
        while (ax <= lastAX) {
            var az = firstAZ
            while (az <= lastAZ) {
                tryFurnitureAtAnchor(cd, ax, az)
                az += cell
            }
            ax += cell
        }
    }

    private fun tryFurnitureAtAnchor(cd: ChunkData, ax: Int, az: Int) {
        if (!isInsideAnyRoom(ax, az)) return

        val h = hashRandom(ax, 900, az, SALT_FURNITURE)
        if (h % 100 >= 35) return

        when ((h / 100) % 7) {
            0, 1 -> placeTable(cd, ax, az, h)
            2 -> placeBookshelfWall(cd, ax, az, h, 3, 2)
            3 -> placeBookshelfWall(cd, ax, az, h, 2, 3)
            4 -> placeChair(cd, ax, az, h)
            5 -> placeSingleProp(cd, ax, az, h)
            else -> placeChestStack(cd, ax, az, h)
        }
    }

    private fun setFurn(cd: ChunkData, wx: Int, wy: Int, wz: Int, mat: Material) {
        if (!isInsideAnyRoom(wx, wz)) return
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 0..255) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return
        cd.setBlock(lx, wy, lz, mat)
    }

    private fun setFurnData(cd: ChunkData, wx: Int, wy: Int, wz: Int, data: BlockData) {
        if (!isInsideAnyRoom(wx, wz)) return
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 0..255) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return
        cd.setBlock(lx, wy, lz, data)
    }

    private fun plankByHash(h: Int): Material = when (h % 4) {
        0 -> Material.OAK_PLANKS
        1 -> Material.SPRUCE_PLANKS
        2 -> Material.BIRCH_PLANKS
        else -> Material.DARK_OAK_PLANKS
    }

    private fun fenceByHash(h: Int): Material = when (h % 4) {
        0 -> Material.OAK_FENCE
        1 -> Material.SPRUCE_FENCE
        2 -> Material.BIRCH_FENCE
        else -> Material.DARK_OAK_FENCE
    }

    private fun slabByHash(h: Int): Material = when (h % 4) {
        0 -> Material.OAK_SLAB
        1 -> Material.SPRUCE_SLAB
        2 -> Material.BIRCH_SLAB
        else -> Material.DARK_OAK_SLAB
    }

    private fun placeTable(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val plank = plankByHash(h)
        val slabMat = slabByHash(h)
        val alongX = h % 2 == 0
        val length = if ((h / 2) % 2 == 0) 3 else 5

        for (i in 0 until length) {
            val wx = if (alongX) ax + i else ax
            val wz = if (alongX) az else az + i

            val isEnd = i == 0 || i == length - 1
            if (isEnd) {
                setFurn(cd, wx, 1, wz, plank)
            } else {
                val slabData = slabMat.createBlockData() as Slab
                slabData.type = Slab.Type.TOP
                setFurnData(cd, wx, 1, wz, slabData)
            }
        }
    }

    private fun placeBookshelfWall(
        cd: ChunkData, ax: Int, az: Int, h: Int,
        width: Int, height: Int
    ) {
        val alongX = h % 2 == 0
        for (i in 0 until width) {
            for (y in 1..height) {
                if (alongX) setFurn(cd, ax + i, y, az, Material.BOOKSHELF)
                else        setFurn(cd, ax, y, az + i, Material.BOOKSHELF)
            }
        }
    }

    private fun placeChair(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val fence = fenceByHash(h)
        val slab = slabByHash(h)

        setFurn(cd, ax, 1, az, fence)
        setFurn(cd, ax, 2, az, slab)
    }

    private fun placeSingleProp(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val mat = when ((h / 100) % 6) {
            0 -> Material.CHEST
            1 -> Material.CRAFTING_TABLE
            2 -> Material.BARREL
            3 -> Material.LECTERN
            4 -> Material.CAULDRON
            else -> Material.STONECUTTER
        }
        setFurn(cd, ax, 1, az, mat)
    }

    private fun placeChestStack(cd: ChunkData, ax: Int, az: Int, h: Int) {
        setFurn(cd, ax, 1, az, Material.CHEST)
        if (h % 2 == 0) {
            setFurn(cd, ax, 2, az, Material.CHEST)
        }
    }

    // ==================== 装饰堆（走廊 + 房间） ====================

    private fun generateDecorationPiles(cd: ChunkData) {
        val cell = 5
        val pad = 6
        val lMinX = chunkX * 16
        val lMinZ = chunkZ * 16
        val lMaxX = lMinX + 15
        val lMaxZ = lMinZ + 15

        val firstAX = Math.floorDiv(lMinX - pad, cell) * cell + cell
        val lastAX = Math.floorDiv(lMaxX + pad, cell) * cell
        val firstAZ = Math.floorDiv(lMinZ - pad, cell) * cell + cell
        val lastAZ = Math.floorDiv(lMaxZ + pad, cell) * cell

        var ax = firstAX
        while (ax <= lastAX) {
            var az = firstAZ
            while (az <= lastAZ) {
                tryDecorationAtAnchor(cd, ax, az)
                az += cell
            }
            ax += cell
        }
    }

    private fun tryDecorationAtAnchor(cd: ChunkData, ax: Int, az: Int) {
        val h = hashRandom(ax, 1200, az, SALT_DECORATION_PILE)
        if (h % 100 >= 7) return

        when ((h / 100) % 6) {
            0, 1, 2 -> placePlankPile(cd, ax, az, h)
            3 -> placeCobblePile(cd, ax, az, h)
            4 -> placeGravelPile(cd, ax, az, h)
            else -> placePlankPile(cd, ax, az, h)
        }
    }

    private fun setDeco(cd: ChunkData, wx: Int, wy: Int, wz: Int, mat: Material) {
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 1..7) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return
        cd.setBlock(lx, wy, lz, mat)
    }

    private fun setDecoData(cd: ChunkData, wx: Int, wy: Int, wz: Int, data: BlockData) {
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 1..7) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return
        cd.setBlock(lx, wy, lz, data)
    }

    private fun placePlankPile(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val (w, d, ht) = when ((h / 100) % 4) {
            0 -> Triple(2, 2, 2)
            1 -> Triple(2, 3, 2)
            2 -> Triple(3, 2, 2)
            else -> Triple(2, 2, 3)
        }

        val plank = plankByHash(h)
        val slab = slabByHash(h)

        val missing = HashSet<Pair<Int, Int>>()
        if (h % 5 == 0) missing.add(0 to 0)
        if ((h / 5) % 5 == 0) missing.add((w - 1) to 0)
        if ((h / 25) % 5 == 0) missing.add(0 to (d - 1))
        if ((h / 125) % 5 == 0) missing.add((w - 1) to (d - 1))

        for (dx in 0 until w) {
            for (dz in 0 until d) {
                val isCornerPos =
                    (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1)
                if (isCornerPos && (dx to dz) in missing) continue

                for (dy in 0 until ht) {
                    setDeco(cd, ax + dx, 1 + dy, az + dz, plank)
                }
            }
        }

        val slabCount = 1 + ((h / 1000) % 2)
        for (i in 0 until slabCount) {
            val sx = ax - 1 + ((h / (i * 17 + 5)) % (w + 2))
            val sz = az - 1 + ((h / (i * 29 + 7)) % (d + 2))
            setDeco(cd, sx, 1, sz, slab)
        }
    }

    private fun placeCobblePile(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val rnd = Random(h.toLong() * 6364136223846793005L + 99991L)

        val blocks = 5 + rnd.nextInt(8)
        val placed = mutableListOf<Pair<Int, Int>>()

        placed.add(ax to az)

        var count = 1
        var attempts = 0
        while (count < blocks && attempts < 60) {
            attempts++
            val (px, pz) = placed[rnd.nextInt(placed.size)]
            val dir = rnd.nextInt(4)
            val nx = px + when (dir) {
                0 -> -1
                1 -> 1
                else -> 0
            }
            val nz = pz + when (dir) {
                2 -> -1
                3 -> 1
                else -> 0
            }

            if (placed.any { it.first == nx && it.second == nz }) continue
            if (nx < ax - 3 || nx > ax + 3 || nz < az - 3 || nz > az + 3) continue

            placed.add(nx to nz)
            count++
        }

        for ((x, z) in placed) {
            val mat =
                if (rnd.nextInt(100) < 15) Material.MOSSY_COBBLESTONE
                else Material.COBBLESTONE
            setDeco(cd, x, 1, z, mat)
        }

        val slabCount = 1 + rnd.nextInt(2)
        repeat(slabCount) {
            val (px, pz) = placed[rnd.nextInt(placed.size)]
            val dir = rnd.nextInt(4)
            val sx = px + when (dir) {
                0 -> -1
                1 -> 1
                else -> 0
            }
            val sz = pz + when (dir) {
                2 -> -1
                3 -> 1
                else -> 0
            }
            setDeco(cd, sx, 1, sz, Material.COBBLESTONE_SLAB)
        }
    }

    private fun placeGravelPile(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val rnd = Random(h.toLong() * 6364136223846793005L + 77177L)

        val blocks = 5 + rnd.nextInt(8)
        val placed = mutableListOf<Pair<Int, Int>>()

        placed.add(ax to az)

        var count = 1
        var attempts = 0
        while (count < blocks && attempts < 60) {
            attempts++
            val (px, pz) = placed[rnd.nextInt(placed.size)]
            val dir = rnd.nextInt(4)
            val nx = px + when (dir) {
                0 -> -1
                1 -> 1
                else -> 0
            }
            val nz = pz + when (dir) {
                2 -> -1
                3 -> 1
                else -> 0
            }

            if (placed.any { it.first == nx && it.second == nz }) continue
            if (nx < ax - 3 || nx > ax + 3 || nz < az - 3 || nz > az + 3) continue

            placed.add(nx to nz)
            count++
        }

        for ((x, z) in placed) {
            setDeco(cd, x, 1, z, Material.GRAVEL)
        }

        val slabCount = 1 + rnd.nextInt(2)
        repeat(slabCount) {
            val (px, pz) = placed[rnd.nextInt(placed.size)]
            val dir = rnd.nextInt(4)
            val sx = px + when (dir) {
                0 -> -1
                1 -> 1
                else -> 0
            }
            val sz = pz + when (dir) {
                2 -> -1
                3 -> 1
                else -> 0
            }
            setDeco(cd, sx, 1, sz, Material.COBBLESTONE_SLAB)
        }
    }

    // ==================== 额外内容 ====================

    private fun generateExtraContent(chunkData: ChunkData, rand: Random) {
        generatePillars(chunkData)

        if (rand.nextInt(100) < 3)  generateFence(chunkData, rand)
    }

    private fun generatePillars(chunkData: ChunkData) {
        val sideH = hashRandom(chunkX, 0, chunkZ, SALT_PILLAR_SIDE)
        val sideX = if (sideH % 2 == 0) 4 else 11

        val positions = listOf(sideX to 4, sideX to 11)
        for ((idx, pos) in positions.withIndex()) {
            val (cx, cz) = pos
            val h = hashRandom(chunkX, idx + 100, chunkZ, SALT_PILLAR) % 100
            if (h >= 60) continue

            var placed = false
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val x = cx + dx
                    val z = cz + dz
                    if (x !in 0..15 || z !in 0..15) continue
                    if (chunkData.getType(x, 1, z) in wallMaterials) continue
                    if (isInsideAnyRoom(chunkX * 16 + x, chunkZ * 16 + z)) continue

                    var colPlaced = false
                    for (y in 1..7) {
                        if (chunkData.getType(x, y, z) == Material.AIR) {
                            chunkData.setBlock(x, y, z, Material.LIGHT_GRAY_CONCRETE)
                            colPlaced = true
                        }
                    }
                    if (colPlaced) placed = true
                }
            }

            if (!placed) continue

            val dir = hashRandom(chunkX, idx + 200, chunkZ, SALT_LAMP_PILLAR) % 4
            val (lx, lz) = when (dir) {
                0 -> Pair(cx + 2, cz)
                1 -> Pair(cx - 2, cz)
                2 -> Pair(cx, cz + 2)
                else -> Pair(cx, cz - 2)
            }
            if (lx in 1..14 && lz in 1..14) {
                placeLampUnit(chunkData, lx, lz)
            }
        }
    }

    private fun generateFence(chunkData: ChunkData, rand: Random) {
        val horizontal = rand.nextBoolean()
        val length = 2 + rand.nextInt(4)
        val height = 3 + rand.nextInt(2)

        val positions = HashSet<Triple<Int, Int, Int>>()

        if (horizontal) {
            val z = 2 + rand.nextInt(12)
            val x0 = 2 + rand.nextInt((12 - length).coerceAtLeast(1))
            for (i in 0 until length) {
                val x = x0 + i
                if (x > 15) break
                for (y in 1..height) {
                    if (chunkData.getType(x, y, z) == Material.AIR) {
                        chunkData.setBlock(x, y, z, Material.IRON_BARS)
                        positions.add(Triple(x, y, z))
                    }
                }
            }
        } else {
            val x = 2 + rand.nextInt(12)
            val z0 = 2 + rand.nextInt((12 - length).coerceAtLeast(1))
            for (i in 0 until length) {
                val z = z0 + i
                if (z > 15) break
                for (y in 1..height) {
                    if (chunkData.getType(x, y, z) == Material.AIR) {
                        chunkData.setBlock(x, y, z, Material.IRON_BARS)
                        positions.add(Triple(x, y, z))
                    }
                }
            }
        }

        refreshFenceConnections(chunkData, positions)
    }

    // ==================== 管道 ====================

    private fun generatePipes(cd: ChunkData) {
        val y = 6
        val cell = 8
        val connectChance = 42

        val lMinX = chunkX * 16
        val lMinZ = chunkZ * 16
        val lMaxX = lMinX + 15
        val lMaxZ = lMinZ + 15

        val rightCache = HashMap<Pair<Int, Int>, Boolean>()
        val downCache = HashMap<Pair<Int, Int>, Boolean>()

        fun hasRight(gx: Int, gz: Int): Boolean =
            rightCache.getOrPut(gx to gz) {
                hashRandom(gx, 600, gz, SALT_PIPE) % 100 < connectChance
            }

        fun hasDown(gx: Int, gz: Int): Boolean =
            downCache.getOrPut(gx to gz) {
                hashRandom(gx, 601, gz, SALT_PIPE) % 100 < connectChance
            }

        val firstGX = Math.floorDiv(lMinX, cell) - 1
        val lastGX = Math.floorDiv(lMaxX, cell)
        val firstGZ = Math.floorDiv(lMinZ, cell) - 1
        val lastGZ = Math.floorDiv(lMaxZ, cell)

        val pipeCells = HashMap<Pair<Int, Int>, Boolean>()

        for (gx in firstGX..lastGX) {
            for (gz in firstGZ..lastGZ) {
                val nodeX = gx * cell
                val nodeZ = gz * cell

                val connectLeft = hasRight(gx - 1, gz)
                val connectRight = hasRight(gx, gz)
                val connectUp = hasDown(gx, gz - 1)
                val connectDown = hasDown(gx, gz)

                val count = (if (connectLeft) 1 else 0) +
                        (if (connectRight) 1 else 0) +
                        (if (connectUp) 1 else 0) +
                        (if (connectDown) 1 else 0)

                if (count > 0) {
                    val isStraight =
                        (count == 2) &&
                                ((connectLeft && connectRight) || (connectUp && connectDown))
                    val isNodeSolid = !isStraight
                    if (nodeX in lMinX..lMaxX && nodeZ in lMinZ..lMaxZ) {
                        pipeCells[nodeX to nodeZ] = isNodeSolid
                    }
                }

                if (connectRight) {
                    for (i in 1 until cell) {
                        val wx = nodeX + i
                        val wz = nodeZ
                        if (wx in lMinX..lMaxX && wz in lMinZ..lMaxZ) {
                            pipeCells[wx to wz] = false
                        }
                    }
                }

                if (connectDown) {
                    for (i in 1 until cell) {
                        val wx = nodeX
                        val wz = nodeZ + i
                        if (wx in lMinX..lMaxX && wz in lMinZ..lMaxZ) {
                            pipeCells[wx to wz] = false
                        }
                    }
                }
            }
        }

        trimPipes(pipeCells)

        for ((key, isSolid) in pipeCells) {
            val (wx, wz) = key
            setPipeCell(cd, wx, y, wz, isSolid)
        }

        generateIronBarsOnPipeWalls(cd, pipeCells)
    }

    /**
     * 在贴墙的管道格 y7 层生成延伸的铁栅栏。
     *  - 沿墙切线方向延伸，长度 **10~18 格**（比之前长一两倍）
     *  - **1/2 概率**作为锚点（比之前更多）
     *  - 只在管道格紧贴区域边界墙时生成
     */
    private fun generateIronBarsOnPipeWalls(
        cd: ChunkData,
        pipeCells: Map<Pair<Int, Int>, Boolean>
    ) {
        val y = 7
        val barPositions = HashSet<Pair<Int, Int>>()
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        for ((wx, wz) in pipeCells.keys) {
            for ((dx, dz) in dirs) {
                if (!isWallAt(wx + dx, wz + dz)) continue

                val h = hashRandom(wx, 700, wz, SALT_IRON_BAR)
                // 1/2 概率作为锚点（原来 1/4）
                if (h % 2 != 0) continue

                // 沿墙的切线方向
                val tanX = if (dx != 0) 0 else 1
                val tanZ = if (dz != 0) 0 else 1

                // 长度 10~18（原来 5~9）
                val length = 10 + (h / 2) % 9
                val startOffset = (h / 40) % length

                for (i in -startOffset until (length - startOffset)) {
                    val bx = wx + tanX * i
                    val bz = wz + tanZ * i
                    // 延伸的过程中必须始终贴同一面墙
                    if (!isWallAt(bx + dx, bz + dz)) continue
                    barPositions.add(bx to bz)
                }
            }
        }

        for ((wx, wz) in barPositions) {
            setIronBar(cd, wx, y, wz)
        }
        for ((wx, wz) in barPositions) {
            refreshIronBarConnections(cd, wx, y, wz, barPositions)
        }
    }

    private fun setIronBar(cd: ChunkData, wx: Int, wy: Int, wz: Int) {
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 0..255) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return
        cd.setBlock(lx, wy, lz, Material.IRON_BARS)
    }

    private fun refreshIronBarConnections(
        cd: ChunkData,
        wx: Int, wy: Int, wz: Int,
        positions: Set<Pair<Int, Int>>
    ) {
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        val data = cd.getBlockData(lx, wy, lz) as? MultipleFacing ?: return

        fun neighborHasBar(nx: Int, nz: Int): Boolean {
            val nlx = nx - chunkX * 16
            val nlz = nz - chunkZ * 16
            return nlx in 0..15 && nlz in 0..15 && (nx to nz) in positions
        }

        data.setFace(BlockFace.NORTH, neighborHasBar(wx, wz - 1))
        data.setFace(BlockFace.SOUTH, neighborHasBar(wx, wz + 1))
        data.setFace(BlockFace.EAST,  neighborHasBar(wx + 1, wz))
        data.setFace(BlockFace.WEST,  neighborHasBar(wx - 1, wz))
        cd.setBlock(lx, wy, lz, data)
    }

    private fun shouldHavePipeAt(wx: Int, wz: Int): Boolean {
        val cell = 8
        val connectChance = 42
        val nodeX = Math.floorDiv(wx, cell) * cell
        val nodeZ = Math.floorDiv(wz, cell) * cell
        val gx = nodeX / cell
        val gz = nodeZ / cell
        val relX = wx - nodeX
        val relZ = wz - nodeZ

        if (relX == 0 && relZ == 0) {
            val cl = hashRandom(gx - 1, 600, gz, SALT_PIPE) % 100 < connectChance
            val cr = hashRandom(gx, 600, gz, SALT_PIPE) % 100 < connectChance
            val cu = hashRandom(gx, 601, gz - 1, SALT_PIPE) % 100 < connectChance
            val cd = hashRandom(gx, 601, gz, SALT_PIPE) % 100 < connectChance
            return cl || cr || cu || cd
        }
        if (relZ == 0 && relX > 0) {
            return hashRandom(gx, 600, gz, SALT_PIPE) % 100 < connectChance
        }
        if (relX == 0 && relZ > 0) {
            return hashRandom(gx, 601, gz, SALT_PIPE) % 100 < connectChance
        }
        return false
    }

    private fun trimPipes(pipeCells: MutableMap<Pair<Int, Int>, Boolean>) {
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        var changed = true
        while (changed) {
            changed = false
            val toRemove = mutableListOf<Pair<Int, Int>>()
            for ((wx, wz) in pipeCells.keys) {
                var neighbors = 0
                for ((dx, dz) in dirs) {
                    val nx = wx + dx
                    val nz = wz + dz
                    val nlx = nx - chunkX * 16
                    val nlz = nz - chunkZ * 16
                    if (nlx in 0..15 && nlz in 0..15) {
                        if ((nx to nz) in pipeCells) neighbors++
                    } else {
                        if (shouldHavePipeAt(nx, nz)) neighbors++
                    }
                }
                if (neighbors <= 1) {
                    val hasWall = dirs.any { (dx, dz) -> isWallAt(wx + dx, wz + dz) }
                    if (!hasWall) {
                        toRemove.add(wx to wz)
                    }
                }
            }
            if (toRemove.isNotEmpty()) {
                changed = true
                toRemove.forEach { pipeCells.remove(it) }
            }
        }
    }

    private fun setPipeCell(cd: ChunkData, wx: Int, wy: Int, wz: Int, isSolid: Boolean) {
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 0..255) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return

        if (isSolid) {
            cd.setBlock(lx, wy, lz, Material.SMOOTH_STONE)
        } else {
            val slab = Material.SMOOTH_STONE_SLAB.createBlockData() as Slab
            slab.type = Slab.Type.TOP
            cd.setBlock(lx, wy, lz, slab)
        }
    }

    // ==================== 工具 ====================

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

    private data class RegionInfo(val x: Int, val z: Int, val w: Int, val h: Int)

    companion object {
        private const val SALT_FLOOR = 501L
        private const val SALT_STONE_MIX = 502L
        private const val SALT_DEEPSLATE = 503L
        private const val SALT_REGION = 504L
        private const val SALT_PILLAR = 505L
        private const val SALT_PILLAR_SIDE = 506L
        private const val SALT_ROOM = 507L
        private const val SALT_LAMP_PILLAR = 508L
        private const val SALT_FURNITURE = 509L
        private const val SALT_DECORATION_PILE = 510L
        private const val SALT_PIPE = 511L
        private const val SALT_IRON_BAR = 512L
    }
}
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
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Wall
import org.bukkit.generator.ChunkGenerator.ChunkData
import java.util.Random

class GL1PlainGild(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    private val regionSizes = listOf(
        Pair(2, 4),
        Pair(4, 2),
        Pair(8, 4),
        Pair(4, 8)
    )

    private val stoneWallMaterials = listOf(
        Material.DIORITE_WALL,
        Material.GRANITE_WALL,
        Material.ANDESITE_WALL,
        Material.STONE_BRICK_WALL
    )

    private val fenceChancePercent = 3

    override fun generate(chunkData: ChunkData) {
        generateFoundation(chunkData)
        generateFloor(chunkData)
        generateSpecialStructures(chunkData)

        // 内部区域边界 + GL1Terrain 的区域边界都要砌墙（generateWalls 内部按边判断）
        generateWalls(chunkData)
        generateRooms(chunkData)
        generateSpruceWalls(chunkData)
        generateCeilingCollapse(chunkData)
        generateInnerRooms(chunkData)

        updateWallBlockStates(chunkData)

        generateDecorations(chunkData)
        generateExtraContent(chunkData)
    }

    // ==================== 地基 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        for (y in WORLD_FLOOR_MIX_Y..-10) fillLayerDeepslateMixed(chunkData, y)
        for (y in -9..-5)  chunkData.fillLayer(y, Material.SANDSTONE)
        for (y in -4..-1)  fillLayerSandMixed(chunkData, y)
        for (y in 13..15)  chunkData.fillLayer(y, Material.POLISHED_TUFF)
    }

    // ==================== 地面 ====================

    private fun generateFloor(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val r = hashRandom(wx, 0, wz, SALT_FLOOR) % 3
                val material = if (r == 0) Material.SAND else Material.SANDSTONE
                chunkData.setBlock(x, 0, z, material)
            }
        }
        generateStonePatches(chunkData)
    }

    private fun generateStonePatches(chunkData: ChunkData) {
        val cellSize = 12
        val threshold = 0.55
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val n = valueNoise(wx, wz, cellSize, SALT_STONE_PATCH)
                if (n > threshold) {
                    chunkData.setBlock(x, 0, z, Material.STONE)
                }
            }
        }
    }

    private fun valueNoise(wx: Int, wz: Int, cellSize: Int, salt: Long): Double {
        val x0 = Math.floorDiv(wx, cellSize)
        val z0 = Math.floorDiv(wz, cellSize)
        val x1 = x0 + 1
        val z1 = z0 + 1
        val fx = (wx - x0 * cellSize).toDouble() / cellSize
        val fz = (wz - z0 * cellSize).toDouble() / cellSize

        val inv = 1.0 / Int.MAX_VALUE.toDouble()
        val v00 = hashRandom(x0, 0, z0, salt).toDouble() * inv
        val v10 = hashRandom(x1, 0, z0, salt).toDouble() * inv
        val v01 = hashRandom(x0, 0, z1, salt).toDouble() * inv
        val v11 = hashRandom(x1, 0, z1, salt).toDouble() * inv

        val sx = fx * fx * (3.0 - 2.0 * fx)
        val sz = fz * fz * (3.0 - 2.0 * fz)

        val a = v00 * (1.0 - sx) + v10 * sx
        val b = v01 * (1.0 - sx) + v11 * sx
        return a * (1.0 - sz) + b * sz
    }

    // ==================== 边界墙 ====================

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

        if (relX == 0 || RegionBoundary.has(rb, RegionBoundary.WEST) ||
            (sub != null && chunkX == sub.x)) {
            for (z in 0..15) for (y in 1..12)
                if (!RegionGateway.opensAt(planW, z, y)) chunkData.setBlock(0, y, z, Material.WHITE_CONCRETE)
        }
        if (relX == region.width - 1 || RegionBoundary.has(rb, RegionBoundary.EAST) ||
            (sub != null && chunkX == sub.x + sub.w - 1)) {
            for (z in 0..15) for (y in 1..12)
                if (!RegionGateway.opensAt(planE, z, y)) chunkData.setBlock(15, y, z, Material.WHITE_CONCRETE)
        }
        if (relZ == 0 || RegionBoundary.has(rb, RegionBoundary.NORTH) ||
            (sub != null && chunkZ == sub.z)) {
            for (x in 0..15) for (y in 1..12)
                if (!RegionGateway.opensAt(planN, x, y)) chunkData.setBlock(x, y, 0, Material.WHITE_CONCRETE)
        }
        if (relZ == region.length - 1 || RegionBoundary.has(rb, RegionBoundary.SOUTH) ||
            (sub != null && chunkZ == sub.z + sub.h - 1)) {
            for (x in 0..15) for (y in 1..12)
                if (!RegionGateway.opensAt(planS, x, y)) chunkData.setBlock(x, y, 15, Material.WHITE_CONCRETE)
        }

        RegionGateway.placeDoors(chunkData, planN)
        RegionGateway.placeDoors(chunkData, planS)
        RegionGateway.placeDoors(chunkData, planW)
        RegionGateway.placeDoors(chunkData, planE)
    }

    // ==================== 特殊结构 ====================

    private fun generateSpecialStructures(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                if (x % 4 == 0 || z % 4 == 0) {
                    chunkData.setBlock(x, 8, z, Material.SPRUCE_SLAB)
                }
            }
        }
        for (x in 0..15 step 4) {
            for (z in 0..15 step 4) {
                chunkData.setBlock(x, 10, z, Material.GLOWSTONE)
            }
        }
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val idx = hashRandom(wx, 11, wz, SALT_WALL) % stoneWallMaterials.size
                chunkData.setBlock(x, 11, z, stoneWallMaterials[idx])
            }
        }
        for (x in 0..15) {
            for (z in 0..15) {
                chunkData.setBlock(x, 12, z, Material.MUD_BRICKS)
            }
        }
    }

    // ==================== 天花板塌陷区 ====================

    private fun generateCeilingCollapse(chunkData: ChunkData) {
        val region = getRegionInfo()
        val zone = getCollapseZoneForRegion(region) ?: return

        val chunkMinX = chunkX * 16
        val chunkMinZ = chunkZ * 16

        if (zone.maxX < chunkMinX || zone.minX > chunkMinX + 15) return
        if (zone.maxZ < chunkMinZ || zone.minZ > chunkMinZ + 15) return

        val regionMinX = region.x * 16
        val regionMinZ = region.z * 16
        val regionMaxX = (region.x + region.width) * 16 - 1
        val regionMaxZ = (region.z + region.length) * 16 - 1

        val innerMinX = regionMinX + 1
        val innerMinZ = regionMinZ + 1
        val innerMaxX = regionMaxX - 1
        val innerMaxZ = regionMaxZ - 1

        val backNorth = zone.minZ == innerMinZ
        val backSouth = zone.maxZ == innerMaxZ
        val backWest  = zone.minX == innerMinX
        val backEast  = zone.maxX == innerMaxX

        val wallThickness = 2 + Math.floorMod(
            hashRandom(region.x, 902, region.z, SALT_COLLAPSE), 4
        )

        val wallTopY = 12

        // ---------- 1. 下移天花板 ----------
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkMinX + x
                val wz = chunkMinZ + z
                if (wx !in zone.minX..zone.maxX) continue
                if (wz !in zone.minZ..zone.maxZ) continue

                val topMat     = chunkData.getType(x,  8, z)
                val wasGlow    = chunkData.getType(x, 10, z) == Material.GLOWSTONE
                val wasWallMat = chunkData.getType(x, 11, z)

                for (y in 5..12) chunkData.setBlock(x, y, z, Material.AIR)

                // 网格木结构 / 木台阶 下移：y=8 → y=5
                if (topMat == Material.SPRUCE_SLAB ||
                    topMat == Material.STRIPPED_SPRUCE_LOG) {
                    chunkData.setBlock(x, 5, z, topMat)
                }

                // 萤石 下移：y=10 → y=7
                if (wasGlow) {
                    chunkData.setBlock(x, 7, z, Material.GLOWSTONE)
                }

                // 各色石墙 下移：y=11 → y=8，上方 2 层石砖
                if (wasWallMat in stoneWallMaterials) {
                    chunkData.setBlock(x,  8, z, wasWallMat)
                    chunkData.setBlock(x,  9, z, Material.STONE_BRICKS)
                    chunkData.setBlock(x, 10, z, Material.STONE_BRICKS)
                }
            }
        }

        // ---------- 2. 两侧墙壁（厚度 2~5，y=1..12） ----------
        val sideAlongX = backNorth || backSouth

        val lenMinZ = zone.minZ
        val lenMaxZ = zone.maxZ
        val lenMinX = zone.minX
        val lenMaxX = zone.maxX

        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkMinX + x
                val wz = chunkMinZ + z

                if (wx in zone.minX..zone.maxX && wz in zone.minZ..zone.maxZ) continue
                if (wx !in innerMinX..innerMaxX) continue
                if (wz !in innerMinZ..innerMaxZ) continue

                val isSideWall = if (sideAlongX) {
                    val left  = wx in (zone.minX - wallThickness)..(zone.minX - 1)
                    val right = wx in (zone.maxX + 1)..(zone.maxX + wallThickness)
                    (left || right) && wz in lenMinZ..lenMaxZ
                } else {
                    val top    = wz in (zone.minZ - wallThickness)..(zone.minZ - 1)
                    val bottom = wz in (zone.maxZ + 1)..(zone.maxZ + wallThickness)
                    (top || bottom) && wx in lenMinX..lenMaxX
                }
                if (!isSideWall) continue

                for (y in 1..wallTopY) {
                    chunkData.setBlock(x, y, z, Material.WHITE_CONCRETE)
                }
            }
        }
    }

    private fun getCollapseZoneForRegion(region: RegionInfo): RoomRect? {
        val h = hashRandom(region.x, 700, region.z, SALT_COLLAPSE)
        if (Math.floorMod(h, 100) >= 70) return null

        val width  = 6 + Math.floorMod(h / 100, 5)
        val length = 6 + Math.floorMod(h / 500, 5)

        val innerMinX = region.x * 16 + 1
        val innerMinZ = region.z * 16 + 1
        val innerMaxX = (region.x + region.width) * 16 - 2
        val innerMaxZ = (region.z + region.length) * 16 - 2

        val innerW = innerMaxX - innerMinX + 1
        val innerH = innerMaxZ - innerMinZ + 1
        if (width > innerW || length > innerH) return null

        val wallSide = Math.floorMod(h / 100_000, 4)

        return when (wallSide) {
            0 -> {
                val availX = innerW - width
                val x0 = innerMinX + Math.floorMod(h / 5_000, availX + 1)
                RoomRect(x0, innerMinZ, x0 + width - 1, innerMinZ + length - 1)
            }
            1 -> {
                val availX = innerW - width
                val x0 = innerMinX + Math.floorMod(h / 5_000, availX + 1)
                RoomRect(x0, innerMaxZ - length + 1, x0 + width - 1, innerMaxZ)
            }
            2 -> {
                val availZ = innerH - length
                val z0 = innerMinZ + Math.floorMod(h / 5_000, availZ + 1)
                RoomRect(innerMinX, z0, innerMinX + width - 1, z0 + length - 1)
            }
            else -> {
                val availZ = innerH - length
                val z0 = innerMinZ + Math.floorMod(h / 5_000, availZ + 1)
                RoomRect(innerMaxX - width + 1, z0, innerMaxX, z0 + length - 1)
            }
        }
    }

    // ==================== y11 墙体 blockstate 更新 ====================

    private fun updateWallBlockStates(chunkData: ChunkData) {
        val region = getRegionInfo()
        val rooms = getRoomsForRegion(region) + getInnerRoomsForRegion(region)

        for (x in 0..15) {
            for (z in 0..15) {
                val mat = chunkData.getType(x, 11, z)
                if (mat !in stoneWallMaterials) continue

                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z

                val wall = mat.createBlockData() as Wall

                wall.setHeight(BlockFace.NORTH,
                    resolveSideHeight(wx, wz - 1, rooms, region, chunkData, x, z - 1))
                wall.setHeight(BlockFace.SOUTH,
                    resolveSideHeight(wx, wz + 1, rooms, region, chunkData, x, z + 1))
                wall.setHeight(BlockFace.WEST,
                    resolveSideHeight(wx - 1, wz, rooms, region, chunkData, x - 1, z))
                wall.setHeight(BlockFace.EAST,
                    resolveSideHeight(wx + 1, wz, rooms, region, chunkData, x + 1, z))

                val aboveType = chunkData.getType(x, 12, z)
                wall.isUp = aboveType.isSolid

                chunkData.setBlock(x, 11, z, wall)
            }
        }
    }

    private fun resolveSideHeight(
        wx: Int, wz: Int,
        rooms: List<RoomRect>, region: RegionInfo,
        chunkData: ChunkData,
        localX: Int, localZ: Int
    ): Wall.Height {
        val neighbor: Material? = if (localX in 0..15 && localZ in 0..15) {
            chunkData.getType(localX, 11, localZ)
        } else {
            predictMaterialAt(wx, wz, rooms, region)
        }

        if (neighbor == null) return Wall.Height.NONE
        if (neighbor == Material.AIR) return Wall.Height.NONE

        return if (neighbor in stoneWallMaterials || neighbor.isSolid) {
            Wall.Height.TALL
        } else {
            Wall.Height.NONE
        }
    }

    private fun predictMaterialAt(
        wx: Int, wz: Int,
        rooms: List<RoomRect>, region: RegionInfo
    ): Material {
        val cx = Math.floorDiv(wx, 16)
        val cz = Math.floorDiv(wz, 16)
        val relX = cx - region.x
        val relZ = cz - region.z
        val inChunkX = Math.floorMod(wx, 16)
        val inChunkZ = Math.floorMod(wz, 16)

        if (relX == 0 && inChunkX == 0) return Material.WHITE_CONCRETE
        if (relX == region.width - 1 && inChunkX == 15) return Material.WHITE_CONCRETE
        if (relZ == 0 && inChunkZ == 0) return Material.WHITE_CONCRETE
        if (relZ == region.length - 1 && inChunkZ == 15) return Material.WHITE_CONCRETE

        val room = rooms.firstOrNull { wx in it.minX..it.maxX && wz in it.minZ..it.maxZ }
        if (room != null) {
            val onXEdge = wx == room.minX || wx == room.maxX
            val onZEdge = wz == room.minZ || wz == room.maxZ
            return if (onXEdge || onZEdge) Material.WHITE_CONCRETE
            else Material.DEEPSLATE
        }

        val idx = hashRandom(wx, 11, wz, SALT_WALL) % stoneWallMaterials.size
        return stoneWallMaterials[idx]
    }

    // ==================== 空旷区判定 ====================

    private fun isEmptyZone(region: RegionInfo): Boolean {
        val zoneSize = 4
        val superX = Math.floorDiv(region.x, zoneSize)
        val superZ = Math.floorDiv(region.z, zoneSize)
        val h = Math.floorMod(
            hashRandom(superX, -10, superZ, SALT_EMPTY_ZONE), 100
        )
        return h < 40
    }

    // ==================== 边界矩形房间 ====================

    private fun generateRooms(chunkData: ChunkData) {
        val region = getRegionInfo()
        val rooms = getRoomsForRegion(region)
        if (rooms.isEmpty()) return

        val chunkMinX = chunkX * 16
        val chunkMinZ = chunkZ * 16

        for (room in rooms) {
            if (room.maxX < chunkMinX || room.minX > chunkMinX + 15) continue
            if (room.maxZ < chunkMinZ || room.minZ > chunkMinZ + 15) continue

            val door = computeRoomDoor(room, region)
            val doorX = door?.x ?: Int.MIN_VALUE
            val doorZ = door?.z ?: Int.MIN_VALUE

            for (x in 0..15) {
                for (z in 0..15) {
                    val wx = chunkMinX + x
                    val wz = chunkMinZ + z
                    if (wx !in room.minX..room.maxX) continue
                    if (wz !in room.minZ..room.maxZ) continue

                    if (door != null && wx == doorX && wz == doorZ) {
                        placeJungleDoor(chunkData, x, z, door.facing, door.hinge)
                        for (y in 3..12) chunkData.setBlock(x, y, z, Material.WHITE_CONCRETE)
                        continue
                    }

                    val onXEdge = wx == room.minX || wx == room.maxX
                    val onZEdge = wz == room.minZ || wz == room.maxZ
                    val isCorner = onXEdge && onZEdge

                    when {
                        isCorner -> for (y in 1..12)
                            chunkData.setBlock(x, y, z, Material.STRIPPED_SPRUCE_LOG)
                        onXEdge || onZEdge -> for (y in 1..12)
                            chunkData.setBlock(x, y, z, Material.WHITE_CONCRETE)
                        else -> {
                            chunkData.setBlock(x, 9, z, Material.MUD_BRICKS)
                            chunkData.setBlock(x, 10, z, Material.MUD_BRICKS)
                            chunkData.setBlock(x, 11, z, Material.DEEPSLATE)
                            chunkData.setBlock(x, 12, z, Material.DEEPSLATE)
                        }
                    }
                }
            }

            placeRoomLamps(chunkData, room, chunkMinX, chunkMinZ)
            placeRoomFurniture(chunkData, room)
        }
    }

    private fun getRoomsForRegion(region: RegionInfo): List<RoomRect> {
        if (isEmptyZone(region)) return emptyList()

        val presenceHash = Math.floorMod(
            hashRandom(region.x, -1, region.z, SALT_ROOM_PRESENCE), 100
        )
        if (presenceHash >= 25) return emptyList()

        val rooms = mutableListOf<RoomRect>()
        val regionMinX = region.x * 16
        val regionMinZ = region.z * 16
        val regionMaxX = (region.x + region.width) * 16 - 1
        val regionMaxZ = (region.z + region.length) * 16 - 1

        val innerMinX = regionMinX + 1
        val innerMinZ = regionMinZ + 1
        val innerMaxX = regionMaxX - 1
        val innerMaxZ = regionMaxZ - 1

        val arr = intArrayOf(0, 1, 2, 3)
        for (i in 3 downTo 1) {
            val j = Math.floorMod(hashRandom(region.x, i * 17, region.z, SALT_ROOM_SIDES), i + 1)
            val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
        }
        val sides = arr.take(3)

        for (side in sides) {
            val length = 6 + (hashRandom(region.x, side, region.z, SALT_ROOM_LEN) % 6)
            val roomWidth = 6

            val room: RoomRect = when (side) {
                0 -> {
                    val range = (innerMaxZ - innerMinZ + 1) - roomWidth
                    val pos = if (range > 0)
                        Math.floorMod(hashRandom(region.x, side + 200, region.z, SALT_ROOM_POS), range + 1) else 0
                    val z0 = innerMinZ + pos
                    RoomRect(innerMinX, z0, innerMinX + length - 1, z0 + roomWidth - 1)
                }
                1 -> {
                    val range = (innerMaxZ - innerMinZ + 1) - roomWidth
                    val pos = if (range > 0)
                        Math.floorMod(hashRandom(region.x, side + 200, region.z, SALT_ROOM_POS), range + 1) else 0
                    val z0 = innerMinZ + pos
                    RoomRect(innerMaxX - length + 1, z0, innerMaxX, z0 + roomWidth - 1)
                }
                2 -> {
                    val range = (innerMaxX - innerMinX + 1) - roomWidth
                    val pos = if (range > 0)
                        Math.floorMod(hashRandom(region.x, side + 200, region.z, SALT_ROOM_POS), range + 1) else 0
                    val x0 = innerMinX + pos
                    RoomRect(x0, innerMinZ, x0 + roomWidth - 1, innerMinZ + length - 1)
                }
                else -> {
                    val range = (innerMaxX - innerMinX + 1) - roomWidth
                    val pos = if (range > 0)
                        Math.floorMod(hashRandom(region.x, side + 200, region.z, SALT_ROOM_POS), range + 1) else 0
                    val x0 = innerMinX + pos
                    RoomRect(x0, innerMaxZ - length + 1, x0 + roomWidth - 1, innerMaxZ)
                }
            }
            rooms.add(room)
        }

        return rooms
    }

    private fun computeRoomDoor(room: RoomRect, region: RegionInfo): DoorInfo? {
        val w = room.maxX - room.minX + 1
        val h = room.maxZ - room.minZ + 1
        if (w < 5 || h < 5) return null

        val innerMinX = region.x * 16 + 1
        val innerMinZ = region.z * 16 + 1
        val innerMaxX = (region.x + region.width) * 16 - 2
        val innerMaxZ = (region.z + region.length) * 16 - 2

        val candidates = mutableListOf<Int>()
        if (room.minX > innerMinX) candidates.add(0)
        if (room.maxX < innerMaxX) candidates.add(1)
        if (room.minZ > innerMinZ) candidates.add(2)
        if (room.maxZ < innerMaxZ) candidates.add(3)
        if (candidates.isEmpty()) return null

        val doorRand = hashRandom(room.minX, 1, room.minZ, SALT_DOOR)
        val doorSide = candidates[Math.floorMod(doorRand, candidates.size)]
        val hinge = if ((doorRand / 4) % 2 == 0) Door.Hinge.LEFT else Door.Hinge.RIGHT

        return when (doorSide) {
            0 -> DoorInfo(room.minX, room.minZ + h / 2, BlockFace.EAST, hinge)
            1 -> DoorInfo(room.maxX, room.minZ + h / 2, BlockFace.WEST, hinge)
            2 -> DoorInfo(room.minX + w / 2, room.minZ, BlockFace.SOUTH, hinge)
            else -> DoorInfo(room.minX + w / 2, room.maxZ, BlockFace.NORTH, hinge)
        }
    }

    // ==================== 内部隔墙横梁 ====================

    private fun generateSpruceWalls(chunkData: ChunkData) {
        if (isBoundaryChunk()) return

        val region = getRegionInfo()
        val relX = chunkX - region.x
        val relZ = chunkZ - region.z
        val alongX = (relX + relZ) % 2 == 0

        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val onLine = if (alongX) wz % 8 == 0 else wx % 8 == 0
                if (!onLine) continue
                chunkData.setBlock(x, 8, z, Material.STRIPPED_SPRUCE_LOG)
            }
        }
    }

    // ==================== 内部小房间 ====================

    private fun generateInnerRooms(chunkData: ChunkData) {
        val region = getRegionInfo()
        val rooms = getInnerRoomsForRegion(region)
        if (rooms.isEmpty()) return

        val chunkMinX = chunkX * 16
        val chunkMinZ = chunkZ * 16
        val collapse = getCollapseZoneForRegion(region)

        for (room in rooms) {
            if (room.maxX < chunkMinX || room.minX > chunkMinX + 15) continue
            if (room.maxZ < chunkMinZ || room.minZ > chunkMinZ + 15) continue
            if (collapse != null && overlap(room, collapse)) continue

            val door = computeInnerRoomDoor(room)
            val doorX = door.x
            val doorZ = door.z

            for (x in 0..15) {
                for (z in 0..15) {
                    val wx = chunkMinX + x
                    val wz = chunkMinZ + z
                    if (wx !in room.minX..room.maxX) continue
                    if (wz !in room.minZ..room.maxZ) continue

                    if (wx == doorX && wz == doorZ) {
                        placeJungleDoor(chunkData, x, z, door.facing, door.hinge)
                        for (y in 3..12) chunkData.setBlock(x, y, z, Material.WHITE_CONCRETE)
                        continue
                    }

                    val onXEdge = wx == room.minX || wx == room.maxX
                    val onZEdge = wz == room.minZ || wz == room.maxZ
                    val isCorner = onXEdge && onZEdge

                    when {
                        isCorner -> for (y in 1..12)
                            chunkData.setBlock(x, y, z, Material.STRIPPED_SPRUCE_LOG)
                        onXEdge || onZEdge -> for (y in 1..12)
                            chunkData.setBlock(x, y, z, Material.WHITE_CONCRETE)
                        else -> {
                            chunkData.setBlock(x, 9, z, Material.MUD_BRICKS)
                            chunkData.setBlock(x, 10, z, Material.MUD_BRICKS)
                            chunkData.setBlock(x, 11, z, Material.DEEPSLATE)
                            chunkData.setBlock(x, 12, z, Material.DEEPSLATE)
                        }
                    }
                }
            }

            placeRoomLamps(chunkData, room, chunkMinX, chunkMinZ)
            placeRoomFurniture(chunkData, room)
        }
    }

    private fun getInnerRoomsForRegion(region: RegionInfo): List<RoomRect> {
        if (isEmptyZone(region)) return emptyList()

        val presenceHash = Math.floorMod(
            hashRandom(region.x, -2, region.z, SALT_ROOM_PRESENCE), 100
        )
        if (presenceHash >= 12) return emptyList()

        val rooms = mutableListOf<RoomRect>()
        val regionMinX = region.x * 16
        val regionMinZ = region.z * 16
        val regionMaxX = (region.x + region.width) * 16 - 1
        val regionMaxZ = (region.z + region.length) * 16 - 1

        repeat(2) { idx ->
            val h = hashRandom(region.x, 500 + idx * 500, region.z, SALT_INNER_ROOM)

            val roomWidth = 5 + h % 3
            val roomLength = 5 + (h / 5) % 5

            val availX = (regionMaxX - regionMinX + 1) - roomWidth - 16
            val availZ = (regionMaxZ - regionMinZ + 1) - roomLength - 16
            if (availX <= 0 || availZ <= 0) return@repeat

            val offsetX = 8 + (h / 40) % (availX + 1)
            val offsetZ = 8 + (h / 200) % (availZ + 1)

            val roomMinX = regionMinX + offsetX
            val roomMinZ = regionMinZ + offsetZ

            val newRoom = RoomRect(
                roomMinX, roomMinZ,
                roomMinX + roomWidth - 1,
                roomMinZ + roomLength - 1
            )

            if (rooms.any { overlap(it, newRoom) }) return@repeat
            rooms.add(newRoom)
        }
        return rooms
    }

    private fun computeInnerRoomDoor(room: RoomRect): DoorInfo {
        val w = room.maxX - room.minX + 1
        val h = room.maxZ - room.minZ + 1

        val doorRand = hashRandom(room.minX, 2, room.minZ, SALT_DOOR)
        val doorSide = Math.floorMod(doorRand, 4)
        val hinge = if ((doorRand / 4) % 2 == 0) Door.Hinge.LEFT else Door.Hinge.RIGHT

        return when (doorSide) {
            0 -> DoorInfo(room.minX, room.minZ + h / 2, BlockFace.EAST, hinge)
            1 -> DoorInfo(room.maxX, room.minZ + h / 2, BlockFace.WEST, hinge)
            2 -> DoorInfo(room.minX + w / 2, room.minZ, BlockFace.SOUTH, hinge)
            else -> DoorInfo(room.minX + w / 2, room.maxZ, BlockFace.NORTH, hinge)
        }
    }

    private fun overlap(a: RoomRect, b: RoomRect): Boolean =
        !(a.maxX < b.minX || a.minX > b.maxX || a.maxZ < b.minZ || a.minZ > b.maxZ)

    private fun placeJungleDoor(
        cd: ChunkData, lx: Int, lz: Int,
        facing: BlockFace, hinge: Door.Hinge
    ) {
        if (lx !in 0..15 || lz !in 0..15) return

        val lower = Material.JUNGLE_DOOR.createBlockData() as Door
        lower.facing = facing; lower.hinge = hinge; lower.isOpen = false
        lower.half = Bisected.Half.BOTTOM
        cd.setBlock(lx, 1, lz, lower)

        val upper = Material.JUNGLE_DOOR.createBlockData() as Door
        upper.facing = facing; upper.hinge = hinge; upper.isOpen = false
        upper.half = Bisected.Half.TOP
        cd.setBlock(lx, 2, lz, upper)
    }

    // ==================== 房间内路灯 ====================

    private fun placeRoomLamps(
        cd: ChunkData, room: RoomRect,
        chunkMinX: Int, chunkMinZ: Int
    ) {
        val positions = listOf(
            room.minX + 1 to room.minZ + 1,
            room.maxX - 1 to room.minZ + 1,
            room.minX + 1 to room.maxZ - 1,
            room.maxX - 1 to room.maxZ - 1
        )
        for ((wx, wz) in positions) {
            val lx = wx - chunkMinX
            val lz = wz - chunkMinZ
            if (lx !in 0..15 || lz !in 0..15) continue
            if (cd.getType(lx, 1, lz) != Material.AIR) continue
            if (cd.getType(lx, 2, lz) != Material.AIR) continue
            if (cd.getType(lx, 3, lz) != Material.AIR) continue
            if (cd.getType(lx, 4, lz) != Material.AIR) continue

            cd.setBlock(lx, 1, lz, Material.STONE_BRICK_WALL)
            cd.setBlock(lx, 2, lz, Material.SPRUCE_FENCE)
            cd.setBlock(lx, 3, lz, Material.SPRUCE_FENCE)
            cd.setBlock(lx, 4, lz, Material.LANTERN)
        }
    }

    // ==================== 房间家具 ====================

    private fun placeRoomFurniture(cd: ChunkData, room: RoomRect) {
        val w = room.maxX - room.minX + 1
        val h = room.maxZ - room.minZ + 1
        if (w < 6 || h < 6) return

        val innerMinX = room.minX + 2
        val innerMinZ = room.minZ + 2
        val innerMaxX = room.maxX - 2
        val innerMaxZ = room.maxZ - 2

        val innerW = innerMaxX - innerMinX + 1
        val innerH = innerMaxZ - innerMinZ + 1
        if (innerW <= 0 || innerH <= 0) return

        val h1 = hashRandom(room.minX, 100, room.minZ, SALT_ROOM_FURNITURE)
        val h2 = hashRandom(room.minX, 200, room.minZ, SALT_ROOM_FURNITURE)
        val h3 = hashRandom(room.minX, 300, room.minZ, SALT_ROOM_FURNITURE)
        val h4 = hashRandom(room.minX, 400, room.minZ, SALT_ROOM_FURNITURE)
        val h5 = hashRandom(room.minX, 500, room.minZ, SALT_ROOM_FURNITURE)
        val h6 = hashRandom(room.minX, 600, room.minZ, SALT_ROOM_FURNITURE)

        val (ax1, az1) = pickNearWall(innerMinX, innerMinZ, innerMaxX, innerMaxZ, h1, h2)
        placeFurnitureByType(cd, ax1, az1, h3)

        if (h4 % 100 < 60) {
            val (ax2, az2) = pickNearWall(innerMinX, innerMinZ, innerMaxX, innerMaxZ, h4, h5)
            placeFurnitureByType(cd, ax2, az2, h6)
        }
    }

    private fun pickNearWall(
        innerMinX: Int, innerMinZ: Int,
        innerMaxX: Int, innerMaxZ: Int,
        h1: Int, h2: Int
    ): Pair<Int, Int> {
        val w = innerMaxX - innerMinX + 1
        val h = innerMaxZ - innerMinZ + 1

        return if (Math.floorMod(h1, 100) < 85) {
            val side = Math.floorMod(h1 / 100, 4)
            when (side) {
                0 -> (innerMinX + Math.floorMod(h2, w)) to innerMinZ
                1 -> innerMaxX to (innerMinZ + Math.floorMod(h2, h))
                2 -> (innerMinX + Math.floorMod(h2, w)) to innerMaxZ
                else -> innerMinX to (innerMinZ + Math.floorMod(h2, h))
            }
        } else {
            (innerMinX + Math.floorMod(h1, w)) to (innerMinZ + Math.floorMod(h2, h))
        }
    }

    /**
     * 按类型派发多方块家具（共 12 种）。
     */
    private fun placeFurnitureByType(cd: ChunkData, ax: Int, az: Int, h: Int) {
        when (Math.floorMod(h, 12)) {
            0 -> placeTableWithChairs(cd, ax, az, h)
            1 -> placeWorkshop(cd, ax, az, h)
            2 -> placeBookshelfWall(cd, ax, az, h, 3, 2)
            3 -> placeEnchantingCorner(cd, ax, az, h)
            4 -> placeHayStack(cd, ax, az, h)
            5 -> placeDoubleChest(cd, ax, az, h)
            6 -> placeBrewingCorner(cd, ax, az, h)
            7 -> placeSmithy(cd, ax, az, h)
            8 -> placeStorageWall(cd, ax, az, h)
            9 -> placeLecternCorner(cd, ax, az, h)
            10 -> placeCompostArea(cd, ax, az, h)
            else -> placeLounge(cd, ax, az, h)
        }
    }

    // ---------- 已有家具 ----------

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

    private fun placeTableWithChairs(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val plank = plankByHash(h)
        val slabMat = slabByHash(h)
        val fence = fenceByHash(h)
        val alongX = h % 2 == 0
        val length = if ((h / 2) % 2 == 0) 3 else 5

        for (i in 0 until length) {
            val wx = if (alongX) ax + i else ax
            val wz = if (alongX) az else az + i
            val isEnd = i == 0 || i == length - 1
            if (isEnd) setFurn(cd, wx, 1, wz, plank)
            else       setFurn(cd, wx, 1, wz, slabMat)
        }

        if (alongX) {
            setFurn(cd, ax - 1, 1, az, fence); setFurn(cd, ax - 1, 2, az, slabMat)
            setFurn(cd, ax + length, 1, az, fence); setFurn(cd, ax + length, 2, az, slabMat)
        } else {
            setFurn(cd, ax, 1, az - 1, fence); setFurn(cd, ax, 2, az - 1, slabMat)
            setFurn(cd, ax, 1, az + length, fence); setFurn(cd, ax, 2, az + length, slabMat)
        }
    }

    private fun placeWorkshop(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            setFurn(cd, ax,     1, az, Material.CRAFTING_TABLE)
            setFurn(cd, ax + 1, 1, az, Material.FURNACE)
            setFurn(cd, ax + 2, 1, az, Material.CHEST)
            setFurn(cd, ax + 2, 2, az, Material.CHEST)
        } else {
            setFurn(cd, ax, 1, az,     Material.CRAFTING_TABLE)
            setFurn(cd, ax, 1, az + 1, Material.FURNACE)
            setFurn(cd, ax, 1, az + 2, Material.CHEST)
            setFurn(cd, ax, 2, az + 2, Material.CHEST)
        }
    }

    private fun placeEnchantingCorner(cd: ChunkData, ax: Int, az: Int, h: Int) {
        setFurn(cd, ax,     1, az,     Material.ENCHANTING_TABLE)
        setFurn(cd, ax - 1, 1, az,     Material.BOOKSHELF)
        setFurn(cd, ax + 1, 1, az,     Material.BOOKSHELF)
        setFurn(cd, ax,     1, az - 1, Material.BOOKSHELF)
        setFurn(cd, ax,     1, az + 1, Material.BOOKSHELF)
        setFurn(cd, ax - 1, 2, az - 1, Material.BOOKSHELF)
        setFurn(cd, ax + 1, 2, az + 1, Material.BOOKSHELF)
    }

    private fun placeHayStack(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        val count = 2 + (h / 4) % 3
        for (i in 0 until count) {
            val wx = if (alongX) ax + i else ax
            val wz = if (alongX) az else az + i
            setFurn(cd, wx, 1, wz, Material.HAY_BLOCK)
            if (i == 0 || i == count - 1) {
                setFurn(cd, wx, 2, wz, Material.HAY_BLOCK)
            }
        }
    }

    private fun placeDoubleChest(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            setFurn(cd, ax,     1, az, Material.CHEST)
            setFurn(cd, ax + 1, 1, az, Material.CHEST)
        } else {
            setFurn(cd, ax, 1, az,     Material.CHEST)
            setFurn(cd, ax, 1, az + 1, Material.CHEST)
        }
    }

    // ---------- 新增家具 ----------

    /**
     * 炼药台：酿造台 + 炼药锅 + 叠高的书架。
     */
    private fun placeBrewingCorner(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            setFurn(cd, ax,     1, az, Material.BREWING_STAND)
            setFurn(cd, ax + 1, 1, az, Material.CAULDRON)
            setFurn(cd, ax - 1, 1, az, Material.BOOKSHELF)
            setFurn(cd, ax - 1, 2, az, Material.BOOKSHELF)
        } else {
            setFurn(cd, ax, 1, az,     Material.BREWING_STAND)
            setFurn(cd, ax, 1, az + 1, Material.CAULDRON)
            setFurn(cd, ax, 1, az - 1, Material.BOOKSHELF)
            setFurn(cd, ax, 2, az - 1, Material.BOOKSHELF)
        }
    }

    /**
     * 铁匠台：锻造台 + 铁砧 + 熔炉，三格一排。
     */
    private fun placeSmithy(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            setFurn(cd, ax,     1, az, Material.SMITHING_TABLE)
            setFurn(cd, ax + 1, 1, az, Material.ANVIL)
            setFurn(cd, ax + 2, 1, az, Material.FURNACE)
        } else {
            setFurn(cd, ax, 1, az,     Material.SMITHING_TABLE)
            setFurn(cd, ax, 1, az + 1, Material.ANVIL)
            setFurn(cd, ax, 1, az + 2, Material.FURNACE)
        }
    }

    /**
     * 储物墙：底层 3 个木桶，上层 2 个箱子。
     */
    private fun placeStorageWall(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            setFurn(cd, ax,     1, az, Material.BARREL)
            setFurn(cd, ax + 1, 1, az, Material.BARREL)
            setFurn(cd, ax + 2, 1, az, Material.BARREL)
            setFurn(cd, ax,     2, az, Material.CHEST)
            setFurn(cd, ax + 2, 2, az, Material.CHEST)
        } else {
            setFurn(cd, ax, 1, az,     Material.BARREL)
            setFurn(cd, ax, 1, az + 1, Material.BARREL)
            setFurn(cd, ax, 1, az + 2, Material.BARREL)
            setFurn(cd, ax, 2, az,     Material.CHEST)
            setFurn(cd, ax, 2, az + 2, Material.CHEST)
        }
    }

    /**
     * 讲台角：中央讲台 + 四周书架。
     */
    private fun placeLecternCorner(cd: ChunkData, ax: Int, az: Int, h: Int) {
        setFurn(cd, ax,     1, az,     Material.LECTERN)
        setFurn(cd, ax - 1, 1, az,     Material.BOOKSHELF)
        setFurn(cd, ax + 1, 1, az,     Material.BOOKSHELF)
        setFurn(cd, ax,     1, az - 1, Material.BOOKSHELF)
        setFurn(cd, ax,     1, az + 1, Material.BOOKSHELF)
        setFurn(cd, ax - 1, 2, az - 1, Material.BOOKSHELF)
        setFurn(cd, ax + 1, 2, az + 1, Material.BOOKSHELF)
    }

    /**
     * 堆肥农场角：堆肥桶 + 高干草堆 + 木桶。
     */
    private fun placeCompostArea(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            setFurn(cd, ax,     1, az, Material.COMPOSTER)
            setFurn(cd, ax + 1, 1, az, Material.HAY_BLOCK)
            setFurn(cd, ax + 1, 2, az, Material.HAY_BLOCK)
            setFurn(cd, ax + 2, 1, az, Material.BARREL)
        } else {
            setFurn(cd, ax, 1, az,     Material.COMPOSTER)
            setFurn(cd, ax, 1, az + 1, Material.HAY_BLOCK)
            setFurn(cd, ax, 2, az + 1, Material.HAY_BLOCK)
            setFurn(cd, ax, 1, az + 2, Material.BARREL)
        }
    }

    /**
     * 休息区：两把椅子（栅栏 + 台阶）夹一张茶几。
     */
    private fun placeLounge(cd: ChunkData, ax: Int, az: Int, h: Int) {
        val alongX = h % 2 == 0
        val slab = slabByHash(h)
        val fence = fenceByHash(h)
        if (alongX) {
            setFurn(cd, ax - 1, 1, az, fence); setFurn(cd, ax - 1, 2, az, slab)
            setFurn(cd, ax,     1, az, slab)
            setFurn(cd, ax + 1, 1, az, fence); setFurn(cd, ax + 1, 2, az, slab)
        } else {
            setFurn(cd, ax, 1, az - 1, fence); setFurn(cd, ax, 2, az - 1, slab)
            setFurn(cd, ax, 1, az,     slab)
            setFurn(cd, ax, 1, az + 1, fence); setFurn(cd, ax, 2, az + 1, slab)
        }
    }

    // ==================== 家具底层工具 ====================

    private fun setFurn(cd: ChunkData, wx: Int, wy: Int, wz: Int, mat: Material) {
        val lx = wx - chunkX * 16
        val lz = wz - chunkZ * 16
        if (lx !in 0..15 || lz !in 0..15) return
        if (wy !in 0..255) return
        if (cd.getType(lx, wy, lz) != Material.AIR) return
        cd.setBlock(lx, wy, lz, mat)
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

    // ==================== 地面装饰物 ====================

    /**
     * 走廊装饰：全部为多方块，位置偏向区块角落。
     * 已删除原来的 placeChest（2 个箱子叠一起），换成了 placeLanternTower。
     */
    private fun generateDecorations(chunkData: ChunkData) {
        val count = hashRandom(chunkX, 0, chunkZ, SALT_DECO_COUNT) % 5
        for (i in 0..count) {
            val h = hashRandom(chunkX, i * 137 + 1, chunkZ, SALT_DECO_POS)
            val inCorner = (h % 10) < 7
            val x: Int
            val z: Int
            if (inCorner) {
                val corner = (h / 10) % 4
                val inX = (h / 40) % 5
                val inZ = (h / 200) % 5
                x = if (corner == 0 || corner == 2) 1 + inX else 10 + inX
                z = if (corner == 0 || corner == 1) 1 + inZ else 10 + inZ
            } else {
                x = 1 + (h / 40) % 14
                z = 1 + (h / 200) % 14
            }

            val type = (h / 256) % 36
            when {
                type < 5  -> placeWoodPlank(chunkData, x, z, h)
                type < 12 -> placeSandGravelPile(chunkData, x, z, h)
                type < 15 -> placeQuartzShelf(chunkData, x, z, h)
                type < 18 -> placeStoneBrickTable(chunkData, x, z, h)
                type < 21 -> placePlankStack(chunkData, x, z, h)
                type < 24 -> placeLanternPost(chunkData, x, z, h)
                type < 27 -> placeBarrelStack(chunkData, x, z, h)
                type < 30 -> placeBookshelfStack(chunkData, x, z, h)
                type < 33 -> placeCorridorDoubleChest(chunkData, x, z, h)   // ← 改名
                else      -> placeLanternTower(chunkData, x, z, h)
            }
        }
    }

    private fun placeSandGravelPile(cd: ChunkData, x: Int, z: Int, h: Int) {
        val rnd = Random(h.toLong() * 6364136223846793005L + 55555L)
        val mat = if (h % 3 == 0) Material.GRAVEL else Material.SAND
        val slabMat = if (mat == Material.SAND) Material.SANDSTONE_SLAB
        else Material.COBBLESTONE_SLAB

        val blocks = 4 + rnd.nextInt(7)
        val placed = mutableListOf<Pair<Int, Int>>()
        placed.add(x to z)

        var count = 1
        var attempts = 0
        while (count < blocks && attempts < 40) {
            attempts++
            val (px, pz) = placed[rnd.nextInt(placed.size)]
            val dir = rnd.nextInt(4)
            val nx = px + when (dir) { 0 -> -1; 1 -> 1; else -> 0 }
            val nz = pz + when (dir) { 2 -> -1; 3 -> 1; else -> 0 }
            if (placed.any { it.first == nx && it.second == nz }) continue
            if (nx !in 1..14 || nz !in 1..14) continue
            placed.add(nx to nz)
            count++
        }

        for ((px, pz) in placed) {
            if (cd.getType(px, 1, pz) == Material.AIR) cd.setBlock(px, 1, pz, mat)
            if (rnd.nextInt(100) < 25 && cd.getType(px, 2, pz) == Material.AIR)
                cd.setBlock(px, 2, pz, mat)
        }

        if (rnd.nextInt(100) < 50) {
            val (px, pz) = placed[rnd.nextInt(placed.size)]
            val dir = rnd.nextInt(4)
            val sx = px + when (dir) { 0 -> -1; 1 -> 1; else -> 0 }
            val sz = pz + when (dir) { 2 -> -1; 3 -> 1; else -> 0 }
            if (sx in 1..14 && sz in 1..14 && cd.getType(sx, 1, sz) == Material.AIR)
                cd.setBlock(sx, 1, sz, slabMat)
        }
    }

    private fun placeWoodPlank(cd: ChunkData, x: Int, z: Int, h: Int) {
        val mat = when (h % 3) {
            0 -> Material.OAK_PLANKS
            1 -> Material.SPRUCE_PLANKS
            else -> Material.BIRCH_PLANKS
        }
        val count = 1 + (h / 3) % 3
        for (i in 0 until count) {
            val hx = hashRandom(x, i, z, SALT_DECO_POS)
            val dx = hx % 3 - 1
            val dz = (hx / 3) % 3 - 1
            val nx = x + dx
            val nz = z + dz
            if (nx !in 0..15 || nz !in 0..15) continue
            if (cd.getType(nx, 1, nz) == Material.AIR) cd.setBlock(nx, 1, nz, mat)
        }
    }

    private fun placeQuartzShelf(cd: ChunkData, x: Int, z: Int, h: Int) {
        val alongX = h % 2 == 0
        for (i in 0..2) {
            val nx = if (alongX) x + i else x
            val nz = if (alongX) z else z + i
            if (nx !in 0..15 || nz !in 0..15) continue
            if (cd.getType(nx, 1, nz) != Material.AIR) continue
            cd.setBlock(nx, 1, nz, Material.QUARTZ_BLOCK)
            if (cd.getType(nx, 2, nz) == Material.AIR)
                cd.setBlock(nx, 2, nz, Material.QUARTZ_SLAB)
        }
    }

    private fun placeStoneBrickTable(cd: ChunkData, x: Int, z: Int, h: Int) {
        for (dx in 0..1) for (dz in 0..1) {
            val nx = x + dx
            val nz = z + dz
            if (nx !in 0..15 || nz !in 0..15) continue
            if (cd.getType(nx, 1, nz) != Material.AIR) continue
            if (cd.getType(nx, 2, nz) != Material.AIR) continue
            cd.setBlock(nx, 1, nz, Material.STONE_BRICK_WALL)
            cd.setBlock(nx, 2, nz, Material.STONE_BRICK_SLAB)
        }
    }

    private fun placePlankStack(cd: ChunkData, x: Int, z: Int, h: Int) {
        val mat = when (h % 4) {
            0 -> Material.OAK_PLANKS
            1 -> Material.SPRUCE_PLANKS
            2 -> Material.BIRCH_PLANKS
            else -> Material.JUNGLE_PLANKS
        }
        val alongX = h % 2 == 0
        for (i in 0..1) {
            val nx = if (alongX) x + i else x
            val nz = if (alongX) z else z + i
            if (nx !in 0..15 || nz !in 0..15) continue
            if (cd.getType(nx, 1, nz) == Material.AIR) cd.setBlock(nx, 1, nz, mat)
        }
        if (cd.getType(x, 2, z) == Material.AIR) cd.setBlock(x, 2, z, mat)
    }

    private fun placeLanternPost(cd: ChunkData, x: Int, z: Int, h: Int) {
        if (cd.getType(x, 1, z) != Material.AIR) return
        if (cd.getType(x, 2, z) != Material.AIR) return
        if (cd.getType(x, 3, z) != Material.AIR) return
        cd.setBlock(x, 1, z, Material.STONE_BRICK_WALL)
        cd.setBlock(x, 2, z, Material.SPRUCE_FENCE)
        cd.setBlock(x, 3, z, Material.LANTERN)
    }

    /**
     * 高灯柱：3 格石砖柱 + 灯笼 + 顶部石砖台阶（替换原 placeChest）。
     */
    private fun placeLanternTower(cd: ChunkData, x: Int, z: Int, h: Int) {
        if (cd.getType(x, 1, z) != Material.AIR) return
        if (cd.getType(x, 2, z) != Material.AIR) return
        if (cd.getType(x, 3, z) != Material.AIR) return
        if (cd.getType(x, 4, z) != Material.AIR) return
        if (cd.getType(x, 5, z) != Material.AIR) return

        cd.setBlock(x, 1, z, Material.STONE_BRICKS)
        cd.setBlock(x, 2, z, Material.STONE_BRICK_WALL)
        cd.setBlock(x, 3, z, Material.STONE_BRICK_WALL)
        cd.setBlock(x, 4, z, Material.LANTERN)
        cd.setBlock(x, 5, z, Material.STONE_BRICK_SLAB)
    }

    private fun placeBarrelStack(cd: ChunkData, x: Int, z: Int, h: Int) {
        val count = 1 + (h / 4) % 3
        for (i in 0 until count) {
            val hx = hashRandom(x, i * 31, z, SALT_DECO_POS)
            val dx = hx % 2
            val dz = (hx / 2) % 2
            val nx = x + dx
            val nz = z + dz
            if (nx !in 0..15 || nz !in 0..15) continue
            if (cd.getType(nx, 1, nz) != Material.AIR) continue
            cd.setBlock(nx, 1, nz, Material.BARREL)
            if ((hx / 4) % 3 == 0 && cd.getType(nx, 2, nz) == Material.AIR)
                cd.setBlock(nx, 2, nz, Material.BARREL)
        }
    }

    private fun placeBookshelfStack(cd: ChunkData, x: Int, z: Int, h: Int) {
        if (cd.getType(x, 1, z) != Material.AIR) return
        cd.setBlock(x, 1, z, Material.BOOKSHELF)
        if (h % 2 == 0 && cd.getType(x, 2, z) == Material.AIR)
            cd.setBlock(x, 2, z, Material.BOOKSHELF)
    }

    private fun placeCorridorDoubleChest(cd: ChunkData, x: Int, z: Int, h: Int) {
        val alongX = h % 2 == 0
        if (alongX) {
            if (x + 1 > 15) return
            if (cd.getType(x, 1, z) == Material.AIR) cd.setBlock(x, 1, z, Material.CHEST)
            if (cd.getType(x + 1, 1, z) == Material.AIR) cd.setBlock(x + 1, 1, z, Material.CHEST)
        } else {
            if (z + 1 > 15) return
            if (cd.getType(x, 1, z) == Material.AIR) cd.setBlock(x, 1, z, Material.CHEST)
            if (cd.getType(x, 1, z + 1) == Material.AIR) cd.setBlock(x, 1, z + 1, Material.CHEST)
        }
    }

    // ==================== 额外内容 ====================

    private fun generateExtraContent(chunkData: ChunkData) {
        val r = hashRandom(chunkX, 0, chunkZ, SALT_FENCE) % 100
        if (r < fenceChancePercent) generateFence(chunkData)
    }

    private fun generateFence(chunkData: ChunkData) {
        val h = hashRandom(chunkX, 1, chunkZ, SALT_FENCE_TYPE)
        val alongX = h % 2 == 0
        val length = 3 + (h / 2) % 4
        val maxOffset = (16 - length).coerceAtLeast(1)

        val baseX = if (alongX) 1 + (h / 8) % maxOffset else 1 + (h / 8) % 14
        val baseZ = if (alongX) 1 + (h / 64) % 14 else 1 + (h / 8) % maxOffset

        for (i in 0 until length) {
            val x = if (alongX) baseX + i else baseX
            val z = if (alongX) baseZ else baseZ + i
            if (x !in 0..15 || z !in 0..15) continue
            for (y in 1..3) {
                if (chunkData.getType(x, y, z) == Material.AIR)
                    chunkData.setBlock(x, y, z, Material.SPRUCE_FENCE)
            }
        }
    }

    // ==================== 区域信息 ====================

    private fun isBoundaryChunk(): Boolean {
        val region = getRegionInfo()
        val relX = chunkX - region.x
        val relZ = chunkZ - region.z
        val sub = SubRegionLayout.of(
            chunkX, chunkZ, worldSeed,
            region.x, region.z, region.width, region.length, regionSizes
        )
        return relX == 0 || relX == region.width - 1 ||
                relZ == 0 || relZ == region.length - 1 ||
                (sub != null && (chunkX == sub.x || chunkX == sub.x + sub.w - 1 ||
                        chunkZ == sub.z || chunkZ == sub.z + sub.h - 1))
    }

    private fun getRegionInfo(): RegionInfo {
        // 容器放大：超级块与区域尺寸都 ×2；容器内部再由 SubRegionLayout 用原始尺寸切子区域
        val superSize = 16
        val superX = Math.floorDiv(chunkX, superSize)
        val superZ = Math.floorDiv(chunkZ, superSize)
        val h = hashRandom(superX, 0, superZ, SALT_REGION)
        val (w0, l0) = regionSizes[h % regionSizes.size]
        val w = w0 * 2
        val l = l0 * 2

        val localX = chunkX - superX * superSize
        val localZ = chunkZ - superZ * superSize
        val regionX = superX * superSize + Math.floorDiv(localX, w) * w
        val regionZ = superZ * superSize + Math.floorDiv(localZ, l) * l

        return RegionInfo(regionX, regionZ, w, l)
    }

    // ==================== 混合填充 ====================

    private fun fillLayerDeepslateMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) for (z in 0..15) {
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

    private fun fillLayerSandMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) for (z in 0..15) {
            val wx = chunkX * 16 + x
            val wz = chunkZ * 16 + z
            val r = hashRandom(wx, y, wz, SALT_SAND) % 5
            val mat = if (r < 3) Material.SAND else Material.SANDSTONE
            chunkData.setBlock(x, y, z, mat)
        }
    }

    // ==================== 哈希随机 ====================

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

    // ==================== 数据类 ====================

    private data class RegionInfo(
        val x: Int,
        val z: Int,
        val width: Int,
        val length: Int
    )

    private data class RoomRect(
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int
    )

    private data class DoorInfo(
        val x: Int,
        val z: Int,
        val facing: BlockFace,
        val hinge: Door.Hinge
    )

    companion object {
        private const val SALT_DEEPSLATE = 1L
        private const val SALT_SAND = 2L
        private const val SALT_FLOOR = 3L
        private const val SALT_WALL = 4L
        private const val SALT_REGION = 5L
        private const val SALT_ROOM = 6L
        private const val SALT_FENCE = 7L
        private const val SALT_FENCE_TYPE = 8L
        private const val SALT_STONE_PATCH = 9L
        private const val SALT_ROOM_LEN = 10L
        private const val SALT_ROOM_POS = 11L
        private const val SALT_ROOM_SIDES = 12L
        private const val SALT_INNER_ROOM = 13L
        private const val SALT_DECO_COUNT = 14L
        private const val SALT_DECO_POS = 15L
        private const val SALT_DOOR = 18L
        private const val SALT_ROOM_PRESENCE = 19L
        private const val SALT_ROOM_FURNITURE = 20L
        private const val SALT_EMPTY_ZONE = 21L
        private const val SALT_COLLAPSE = 22L
    }
}
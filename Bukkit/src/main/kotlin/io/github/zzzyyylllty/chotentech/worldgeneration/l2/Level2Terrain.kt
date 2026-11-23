package io.github.zzzyyylllty.chotentech.worldgeneration.l2

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_ROOF_DECOR_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import org.bukkit.Axis
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Lightable
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.Rail
import org.bukkit.block.data.type.Lantern
import org.bukkit.block.data.type.Speleothem
import org.bukkit.block.data.type.Wall
import org.bukkit.generator.ChunkGenerator.ChunkData
import kotlin.math.abs

internal class Level2Terrain(chunkX: Int, chunkZ: Int, seed: Long) : ChunkGen {
    private val baseX = chunkX * 16
    private val baseZ = chunkZ * 16
    private val layout = Level2Layout(seed)
    private val halo = Array(20) { x ->
        Array(20) { z -> layout.column(baseX + x - 2, baseZ + z - 2) }
    }
    private val directional = HashMap<Pair<Material, BlockFace>, BlockData>()
    private val oriented = HashMap<Pair<Material, Axis>, BlockData>()
    private val railData = HashMap<Rail.Shape, BlockData>()
    private val pipes = HashMap<Boolean, BlockData>()
    private val workshopBays = HashMap<Pair<Int, Int>, Boolean>()
    private val hangingLantern by lazy { (Material.LANTERN.createBlockData() as Lantern).apply { isHanging = true } }
    private val hangingDripstone by lazy {
        (Material.POINTED_DRIPSTONE.createBlockData() as Speleothem).apply { verticalDirection = BlockFace.DOWN }
    }
    private val indicator by lazy {
        (Material.WAXED_WEATHERED_COPPER_BULB.createBlockData() as Lightable).apply { isLit = true }
    }

    override fun generate(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()
        chunkData.setRegion(0, WORLD_FLOOR_MIX_Y, 0, 16, 0, 16, Material.DEEPSLATE)

        for (x in 0..15) for (z in 0..15) {
            val column = column(x, z)
            val wx = baseX + x
            val wz = baseZ + z
            val palette = palettes.getValue(column.region)
            val rail = column.space == Level2Space.RAILWAY
            val track = if (rail) trackShape(wx, wz) else null
            val floor = when {
                rail -> railwayFloor(wx, wz, track != null)
                column.space == Level2Space.DOORWAY && atRoomEntrance(x, z) -> Material.POLISHED_ANDESITE
                column.region == Level2Region.CLAY -> sediment(palette.floor, wx, wz, 41)
                else -> pick(palette.floor, wx, wz, 41)
            }
            chunkData.setBlock(x, 0, z, floor)
            if (column.space == Level2Space.SOLID) {
                for (y in 1 until column.ceiling + 2) {
                    val material = if (y == 1 && column.region == Level2Region.PIPE) Material.STONE_BRICKS
                        else pick(palette.wall, wx, wz, 42 + y)
                    chunkData.setBlock(x, y, z, material)
                }
                cap(chunkData, x, column.ceiling + 2, z)
            } else {
                val roof = when {
                    rail -> if (railDistance(wx, wz).mod(12) < 2) Material.POLISHED_DEEPSLATE else Material.SMOOTH_STONE
                    column.space == Level2Space.DOORWAY && atRoomEntrance(x, z) -> Material.CHISELED_STONE_BRICKS
                    column.region == Level2Region.CLAY -> sediment(palette.roof, wx, wz, 55)
                    else -> pick(palette.roof, wx, wz, 55)
                }
                chunkData.setBlock(x, column.ceiling, z, roof)
                cap(chunkData, x, column.ceiling + 1, z)
            }
        }

        for (x in 0..15) for (z in 0..15) {
            val column = column(x, z)
            when (column.space) {
                Level2Space.SOLID, Level2Space.DOORWAY -> Unit
                Level2Space.RAILWAY -> railway(chunkData, x, z, column.ceiling)
                Level2Space.ROOM, Level2Space.CORRIDOR -> decorate(chunkData, x, z, column)
            }
        }
    }

    private fun cap(data: ChunkData, x: Int, y: Int, z: Int) {
        data.setRegion(x, y, z, x + 1, WORLD_ROOF_DECOR_Y, z + 1, Material.DEEPSLATE)
    }

    private fun decorate(data: ChunkData, x: Int, z: Int, column: Level2Column) {
        if (column.roomUse == Level2RoomUse.EMPTY) return
        val wx = baseX + x
        val wz = baseZ + z
        val wall = wallSide(x, z, column.space)
        if (!column.core && wall != null) {
            val northSouth = wall.modX != 0
            val along = if (northSouth) wz else wx
            val bayStart = along - along.mod(8)
            val bay = if (northSouth) layout.pick(wx, bayStart, 60, 100) else layout.pick(bayStart, wz, 60, 100)
            val segment = along.mod(8)
            if (column.roomUse == Level2RoomUse.STORAGE) {
                if (segment in 1..5 && bay < 76) storage(data, x, z, segment, bay)
            } else if (column.roomUse == Level2RoomUse.OFFICE) {
                if (segment in 1..6 && bay < 82) officeWall(data, x, z, wall, segment, bay)
            } else if (column.roomUse == Level2RoomUse.UTILITY) {
                if (segment in 1..6 && bay < 91) utilityWall(data, x, z, wall, segment, bay)
            } else {
                when (column.region) {
                    Level2Region.PIPE -> if (bay < 89) {
                        pipe(data, x, z, wall, bay, segment, column.ceiling)
                        if (segment == 6 && bay >= 54) valve(data, x, z, wall)
                    }
                    Level2Region.DUST -> when {
                        bay < 57 && segment < 6 -> machine(data, x, z, wall, segment, lit = false)
                        bay in 57..74 && segment in 1..5 -> dustCollected(data, x, z, wall, segment, wx, wz)
                    }
                    Level2Region.INDUSTRIAL -> when {
                        bay < 26 -> pipe(data, x, z, wall, bay, segment, column.ceiling)
                        bay in 26..62 && segment < 6 -> machine(data, x, z, wall, segment, lit = bay < 31)
                        bay in 63..88 && segment in 1..4 -> storage(data, x, z, segment, bay)
                    }
                    Level2Region.CLAY -> if (bay < 42 && segment < 5 && !column(x - wall.modX, z - wall.modZ).core) {
                        clayWall(data, x, z, wall, segment)
                    }
                }
            }
            if (wallAt(x - wall.modX, z - wall.modZ) &&
                bay in 82..95 && segment == 4) fireEquipment(data, x, z, wall, wx, wz)
        }

        when (column.roomUse) {
            Level2RoomUse.STORAGE -> roomRacks(data, x, z, wx, wz, column.region)
            Level2RoomUse.WORKSHOP -> workshop(data, x, z, wx, wz)
            Level2RoomUse.OFFICE -> office(data, x, z, wx, wz)
            Level2RoomUse.UTILITY -> utility(data, x, z, wx, wz)
            else -> floorDetails(data, x, z, wx, wz, column)
        }
        cornerDetails(data, x, z, wx, wz, column)

        val ceilingLamp = column.core || wall != null ||
            (column.space == Level2Space.ROOM && wx.mod(12) == 5 && wz.mod(12) == 5)
        val fixtureY = column.ceiling - 1
        if (!data.getType(x, fixtureY, z).isAir) return
        val hasPower = layout.noise(wx, wz, 52, 64) > 0.43
        val lightSpacing = when {
            column.region == Level2Region.DUST -> 36
            column.core -> 15
            else -> 26
        }
        if (ceilingLamp && hasPower && layout.pick(wx, wz, 65, lightSpacing) == 0) {
            if (column.region == Level2Region.CLAY)
                hangLight(data, x, fixtureY, z, lantern = true, supportY = column.ceiling)
            else data.setBlock(x, column.ceiling, z, Material.END_ROD)
        } else if (column.region == Level2Region.CLAY && fixtureY >= 4 &&
            layout.noise(wx, wz, 7, 66) > 0.64 && layout.pick(wx, wz, 67, 10) == 0
        ) {
            data.setBlock(x, fixtureY, z, hangingDripstone)
        }
    }

    /** True only where the current column is the first clear row of the room interior. */
    private fun wallAt(x: Int, z: Int): Boolean = wallSide(x, z, column(x, z).space) != null

    private fun atRoomEntrance(x: Int, z: Int): Boolean = directions.any { face ->
        column(x + face.modX, z + face.modZ).space == Level2Space.ROOM
    }

    // Side fixtures can only occupy a wall-adjacent strip with two open blocks inward.
    private fun wallSide(x: Int, z: Int, space: Level2Space): BlockFace? = directions.firstOrNull { face ->
        column(x + face.modX, z + face.modZ).space == Level2Space.SOLID &&
            (1..2).all { inward -> column(x - inward * face.modX, z - inward * face.modZ).space == space }
    }

    private fun pipe(data: ChunkData, x: Int, z: Int, wall: BlockFace, bay: Int, segment: Int, ceiling: Int) {
        data.setBlock(x, 1, z, Material.STONE)
        val axis = if (wall.modX != 0) Axis.Z else Axis.X
        if (bay < 22) {
            data.setBlock(x, 2, z, facing(Material.STONE_BRICK_STAIRS, wall))
        } else if (bay < 54) {
            val material = if (segment == 0 || segment == 4) Material.CHISELED_STONE_BRICKS else Material.POLISHED_BASALT
            if (material == Material.POLISHED_BASALT) data.setBlock(x, 2, z, oriented(material, axis))
            else data.setBlock(x, 2, z, material)
        } else {
            val northSouth = wall.modX != 0
            val block = pipes.getOrPut(northSouth) {
                (Material.COBBLESTONE_WALL.createBlockData() as Wall).apply {
                    isUp = false
                    for (face in directions) {
                        val along = if (northSouth) face.modZ != 0 else face.modX != 0
                        setHeight(face, if (along) Wall.Height.LOW else Wall.Height.NONE)
                    }
                }
            }
            data.setBlock(x, 2, z, block)
        }
        if (ceiling >= 5 && bay >= 32) {
            val conduit = if (segment == 0) Material.POLISHED_ANDESITE else Material.WAXED_WEATHERED_COPPER_CHAIN
            if (segment == 0) data.setBlock(x, ceiling - 1, z, conduit)
            else data.setBlock(x, ceiling - 1, z, oriented(conduit, axis))
        }
    }

    private fun machine(data: ChunkData, x: Int, z: Int, wall: BlockFace, segment: Int, lit: Boolean) {
        data.setBlock(x, 1, z, if (segment == 0 || segment == 5) Material.CAULDRON else Material.POLISHED_DEEPSLATE)
        when (segment) {
            0, 5 -> data.setBlock(x, 2, z, oriented(Material.POLISHED_BASALT, Axis.Y))
            1 -> data.setBlock(x, 2, z, facing(Material.BLAST_FURNACE, wall.oppositeFace))
            2 -> if (lit) data.setBlock(x, 2, z, indicator) else data.setBlock(x, 2, z, Material.REDSTONE_LAMP)
            3 -> data.setBlock(x, 2, z, Material.WAXED_OXIDIZED_COPPER_GRATE)
            4 -> data.setBlock(x, 2, z, Material.IRON_BARS)
        }
        data.setBlock(x, 3, z, if (segment in 1..4) Material.TUFF_BRICK_SLAB else Material.POLISHED_ANDESITE)
    }

    private fun workshop(data: ChunkData, x: Int, z: Int, worldX: Int, worldZ: Int) {
        val offsetX = worldX.mod(16)
        val offsetZ = worldZ.mod(16)
        if (offsetX !in 5..10 || offsetZ !in 6..8) return
        val startX = worldX - offsetX
        val startZ = worldZ - offsetZ
        val place = workshopBays.getOrPut(startX to startZ) {
            layout.pick(startX, startZ, 63, 3) == 0 &&
                (3..12).all { dx ->
                    (4..10).all { dz -> layout.column(startX + dx, startZ + dz).roomUse == Level2RoomUse.WORKSHOP }
                }
        }
        if (!place) return
        when (offsetZ) {
            6 -> machine(data, x, z, BlockFace.SOUTH, offsetX - 5, lit = offsetX == 7)
            8 -> machine(data, x, z, BlockFace.NORTH, 10 - offsetX, lit = false)
            else -> {
                data.setBlock(x, 1, z, Material.POLISHED_DEEPSLATE)
                data.setBlock(x, 2, z, Material.WAXED_OXIDIZED_CUT_COPPER)
                data.setBlock(x, 3, z, Material.TUFF_BRICKS)
            }
        }
    }

    private fun storage(data: ChunkData, x: Int, z: Int, segment: Int, bay: Int) {
        when {
            segment == 1 || segment == 5 -> data.setBlock(x, 1, z, Material.SPRUCE_FENCE)
            segment == 2 -> data.setBlock(x, 1, z, Material.BARREL)
            segment == 3 -> {
                data.setBlock(x, 1, z, Material.SPRUCE_PLANKS)
                data.setBlock(x, 2, z, if (bay % 3 == 0) Material.SPONGE else Material.SPRUCE_PLANKS)
            }
            else -> {
                data.setBlock(x, 1, z, Material.SPRUCE_SLAB)
                if (segment == 4) data.setBlock(x, 2, z, Material.WAXED_OXIDIZED_CUT_COPPER)
            }
        }
    }

    private fun dustCollected(data: ChunkData, x: Int, z: Int, wall: BlockFace, segment: Int, wx: Int, wz: Int) {
        when {
            segment == 1 -> data.setBlock(x, 1, z, Material.CHIPPED_ANVIL)
            segment == 2 -> data.setBlock(x, 1, z, Material.DEEPSLATE_TILES)
            segment == 3 -> {
                data.setBlock(x, 1, z, Material.CRAFTING_TABLE)
                data.setBlock(x, 2, z, Material.GRAY_CONCRETE_POWDER)
            }
            segment == 4 -> data.setBlock(x, 1, z, pick(dustScrap, wx, wz, 77))
            else -> data.setBlock(x, 1, z, Material.LECTERN)
        }
    }

    private fun clayWall(data: ChunkData, x: Int, z: Int, wall: BlockFace, segment: Int) {
        when (segment) {
            1 -> data.setBlock(x, 1, z, Material.PACKED_MUD)
            2 -> {
                data.setBlock(x, 1, z, Material.MUD_BRICKS)
                if ((baseX + x + baseZ + z).mod(6) == 0) data.setBlock(x, 2, z, Material.MANGROVE_ROOTS)
            }
            3 -> data.setBlock(x, 1, z, Material.BROWN_TERRACOTTA)
            4 -> data.setBlock(x, 1, z, facing(Material.STONE_BRICK_STAIRS, wall))
            else -> data.setBlock(x, 1, z, pick(clayRubble, baseX + x, baseZ + z, 78))
        }
    }

    private fun valve(data: ChunkData, x: Int, z: Int, wall: BlockFace) {
        data.setBlock(x, 2, z, facing(Material.STONE_BUTTON, wall.oppositeFace))
        data.setBlock(x, 3, z, Material.POLISHED_ANDESITE)
        data.setBlock(x, 4, z, Material.LIGHTNING_ROD)
    }

    private fun fireEquipment(data: ChunkData, x: Int, z: Int, wall: BlockFace, wx: Int, wz: Int) {
        if (data.getType(x, 1, z) != Material.AIR) return
        val offset = layout.pick(wx, wz, 79, 3)
        data.setBlock(x, 1, z, Material.CAULDRON)
        data.setBlock(x, 2, z, if (offset == 0) Material.WAXED_OXIDIZED_COPPER_GRATE else Material.STONE_BRICK_WALL)
        data.setBlock(x, 3, z, facing(Material.IRON_TRAPDOOR, wall.oppositeFace))
    }

    private fun roomRacks(data: ChunkData, x: Int, z: Int, wx: Int, wz: Int, region: Level2Region) {
        val offsetX = wx.mod(11)
        val offsetZ = wz.mod(11)
        if (offsetX !in 4..6 || offsetZ !in 4..6) return
        when {
            offsetX == 5 && offsetZ == 5 -> {
                data.setBlock(x, 1, z, Material.BARREL)
                data.setBlock(x, 2, z, if (layout.pick(wx, wz, 80, 3) == 0) Material.SPONGE else Material.BARREL)
            }
            offsetX % 2 == 0 -> {
                data.setBlock(x, 1, z, Material.SPRUCE_PLANKS)
                data.setBlock(x, 2, z, Material.SPRUCE_FENCE)
            }
            offsetZ % 3 == 0 -> data.setBlock(x, 1, z, if (region == Level2Region.CLAY) Material.TERRACOTTA else Material.CHEST)
            else -> data.setBlock(x, 1, z, Material.SPRUCE_SLAB)
        }
    }

    private fun floorDetails(data: ChunkData, x: Int, z: Int, wx: Int, wz: Int, column: Level2Column) {
        if (column.core || column.space != Level2Space.CORRIDOR) return
        val edge = wallSide(x, z, column.space) != null
        if (!edge) return
        if (layout.pick(wx, wz, 81, 100) != 0) return
        when (layout.pick(wx, wz, 82, 5)) {
            0 -> data.setBlock(x, 1, z, Material.GRAVEL)
            1 -> data.setBlock(x, 1, z, Material.CHIPPED_ANVIL)
            2 -> data.setBlock(x, 1, z, Material.IRON_BARS)
            3 -> data.setBlock(x, 1, z, Material.OAK_PLANKS)
            else -> data.setBlock(x, 1, z, Material.EXPOSED_COPPER_GRATE)
        }
    }

    private fun cornerDetails(data: ChunkData, x: Int, z: Int, wx: Int, wz: Int, column: Level2Column) {
        if (column.core || column.space == Level2Space.SOLID) return
        val openSides = directions.count { face -> column(x + face.modX, z + face.modZ).space != Level2Space.SOLID }
        if (openSides != 2) return
        if (layout.pick(wx, wz, 83, 64) != 0) return
        when (column.region) {
            Level2Region.PIPE -> data.setBlock(x, 1, z, Material.POLISHED_BASALT)
            Level2Region.DUST -> data.setBlock(x, 1, z, Material.CHIPPED_ANVIL)
            Level2Region.INDUSTRIAL -> data.setBlock(x, 1, z, Material.IRON_BARS)
            Level2Region.CLAY -> data.setBlock(x, 1, z, Material.PACKED_MUD)
        }
    }

    private fun officeWall(data: ChunkData, x: Int, z: Int, wall: BlockFace, segment: Int, bay: Int) {
        if (bay < 32) {
            data.setBlock(x, 1, z, Material.SMOOTH_STONE)
            data.setBlock(x, 2, z, facing(Material.OAK_TRAPDOOR, wall.oppositeFace))
            if (segment == 3) data.setBlock(x, 3, z, Material.OAK_SLAB)
        } else {
            data.setBlock(x, 1, z, Material.OAK_PLANKS)
            if (segment % 2 == 0) data.setBlock(x, 2, z, Material.OAK_PLANKS)
        }
    }

    private fun office(data: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val offsetX = wx.mod(14)
        val offsetZ = wz.mod(14)
        if (offsetX !in 4..9 || offsetZ !in 4..6) return
        if (!layout.column(wx - offsetX + 6, wz - offsetZ + 5).roomUse.isOffice()) return
        when {
            offsetZ == 5 && offsetX in 6..7 -> {
                data.setBlock(x, 1, z, Material.SPRUCE_PLANKS)
                data.setBlock(x, 2, z, Material.CHEST)
            }
            offsetZ == 6 && offsetX in 5..8 -> data.setBlock(x, 1, z, Material.OAK_FENCE)
            offsetX == 5 -> data.setBlock(x, 1, z, Material.BOOKSHELF)
            offsetX == 8 -> data.setBlock(x, 1, z, Material.OAK_PLANKS)
        }
    }

    private fun utilityWall(data: ChunkData, x: Int, z: Int, wall: BlockFace, segment: Int, bay: Int) {
        if (bay < 42) {
            data.setBlock(x, 1, z, Material.IRON_BARS)
            if (segment == 2 || segment == 4) data.setBlock(x, 2, z, facing(Material.SPRUCE_TRAPDOOR, wall.oppositeFace))
            else data.setBlock(x, 2, z, Material.CUT_COPPER)
            data.setBlock(x, 3, z, Material.TUFF_BRICKS)
        } else {
            data.setBlock(x, 1, z, Material.DEEPSLATE_TILES)
            if (segment in 1..4) data.setBlock(x, 2, z, Material.COPPER_GRATE)
        }
    }

    private fun utility(data: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val offsetX = wx.mod(15)
        val offsetZ = wz.mod(15)
        if (offsetX !in 4..9 || offsetZ !in 4..6) return
        if (!layout.column(wx - offsetX + 6, wz - offsetZ + 5).roomUse.isUtility()) return
        when {
            offsetX == 5 && offsetZ in 5..6 -> {
                data.setBlock(x, 1, z, Material.WAXED_COPPER_GRATE)
                data.setBlock(x, 2, z, Material.WAXED_COPPER_GRATE)
            }
            offsetX == 8 && offsetZ == 5 -> data.setBlock(x, 1, z, Material.REDSTONE_LAMP)
            offsetZ == 6 && offsetX in 6..7 -> data.setBlock(x, 1, z, Material.POLISHED_BASALT)
        }
    }

    private fun Level2RoomUse?.isOffice(): Boolean = this == Level2RoomUse.OFFICE
    private fun Level2RoomUse?.isUtility(): Boolean = this == Level2RoomUse.UTILITY

    private fun railway(data: ChunkData, x: Int, z: Int, ceiling: Int) {
        val wx = baseX + x
        val wz = baseZ + z
        trackShape(wx, wz)?.let { shape ->
            val track = railData.getOrPut(shape) { (Material.RAIL.createBlockData() as Rail).apply { this.shape = shape } }
            data.setBlock(x, 1, z, track)
        }
        val northSouth = abs(wx) <= abs(wz)
        val along = if (northSouth) wz else wx
        val across = if (northSouth) wx else wz
        val spacing = if (along >= 0) 5 else 13
        if (abs(across) == 6 && along.mod(18) == spacing) {
            when (layout.pick(wx, wz, 70, 2)) {
                0 -> hangLight(data, x, ceiling - 1, z, lantern = true, supportY = ceiling)
                else -> hangLight(data, x, ceiling - 1, z, lantern = false, supportY = ceiling)
            }
        }
        if (abs(wx) == 2 && abs(wz) == 2) data.setBlock(x, ceiling, z, Material.SEA_LANTERN)
        val ceilingRadius = topRailwayRadius(wx, wz)
        if (along.mod(38) == 11 && abs(across) == ceilingRadius && ceilingRadius > 9) {
            data.setBlock(x, ceiling, z, Material.SEA_LANTERN)
            data.setBlock(x, ceiling - 1, z, oriented(Material.IRON_CHAIN, Axis.Y))
        } else if (along.mod(32) == 7 && abs(across) == ceilingRadius && ceilingRadius > 11) {
            data.setBlock(x, ceiling, z, Material.SEA_LANTERN)
            data.setBlock(x, ceiling - 1, z, oriented(Material.WAXED_EXPOSED_COPPER_CHAIN, Axis.Y))
        }

    }

    private fun railwayFloor(x: Int, z: Int, track: Boolean): Material {
        val across = minOf(abs(x), abs(z))
        val along = railDistance(x, z)
        return when {
            along <= 4 && across <= 2 -> if (x == 0 || z == 0) Material.POLISHED_ANDESITE else Material.STONE_BRICKS
            across == 5 -> if (along.mod(24) == 8) Material.SEA_LANTERN else Material.YELLOW_TERRACOTTA
            across == 6 && along.mod(4) < 2 -> Material.BLACK_TERRACOTTA
            across > 4 -> Material.STONE_BRICKS
            along.mod(2) == 0 && (track || across in 2..4) -> Material.DARK_OAK_PLANKS
            else -> Material.GRAVEL
        }
    }

    private fun railDistance(x: Int, z: Int): Int = maxOf(abs(x), abs(z))
    private fun topRailwayRadius(x: Int, z: Int): Int {
        val along = if (abs(x) <= abs(z)) z else x
        return 9 + ((3 * layout.noise(along, 0, 64, 12)).toInt())
    }

    private fun hangLight(data: ChunkData, x: Int, y: Int, z: Int, lantern: Boolean, supportY: Int) {
        if (y >= supportY || !data.getType(x, y + 1, z).isSolid()) return
        data.setBlock(x, y, z, if (lantern) hangingLantern else facing(Material.END_ROD, BlockFace.DOWN))
    }

    private fun facing(material: Material, face: BlockFace): BlockData = directional.getOrPut(material to face) {
        (material.createBlockData() as Directional).apply { facing = face }
    }

    private fun oriented(material: Material, axis: Axis): BlockData = oriented.getOrPut(material to axis) {
        (material.createBlockData() as Orientable).apply { this.axis = axis }
    }

    private fun column(x: Int, z: Int): Level2Column = halo[x + 2][z + 2]

    private fun pick(materials: List<Material>, x: Int, z: Int, salt: Int): Material =
        materials[layout.pick(x, z, salt, materials.size)]

    private fun sediment(materials: List<Material>, x: Int, z: Int, salt: Int): Material =
        materials[(layout.noise(x, z, 7, salt) * materials.size).toInt()]

    private data class Palette(val floor: List<Material>, val wall: List<Material>, val roof: List<Material>)

    companion object {
        // Four curves join adjacent approaches. Each arm has two continuous tracks.
        internal fun trackShape(x: Int, z: Int): Rail.Shape? {
            if (abs(x) == 3 && abs(z) > 3) return Rail.Shape.NORTH_SOUTH
            if (abs(z) == 3 && abs(x) > 3) return Rail.Shape.EAST_WEST
            if (abs(x) != 3 || abs(z) != 3) return null
            return when {
                z < 0 && x < 0 -> Rail.Shape.NORTH_WEST
                z < 0 -> Rail.Shape.NORTH_EAST
                x < 0 -> Rail.Shape.SOUTH_WEST
                else -> Rail.Shape.SOUTH_EAST
            }
        }

        private val directions = listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
        private val clayRubble = listOf(Material.MUD_BRICK_SLAB, Material.COARSE_DIRT, Material.GRAVEL, Material.DRIPSTONE_BLOCK)
        private val dustScrap = listOf(Material.COPPER_GRATE, Material.DEEPSLATE_TILES, Material.CRACKED_STONE_BRICKS)
        private val palettes = mapOf(
            Level2Region.PIPE to Palette(
                listOf(Material.STONE, Material.STONE, Material.ANDESITE),
                listOf(Material.STONE, Material.STONE, Material.ANDESITE, Material.COBBLESTONE, Material.STONE_BRICKS),
                listOf(Material.SMOOTH_STONE),
            ),
            Level2Region.DUST to Palette(
                listOf(Material.GRAY_CONCRETE, Material.TUFF, Material.ANDESITE),
                listOf(Material.TUFF, Material.GRAY_CONCRETE, Material.COBBLED_DEEPSLATE, Material.PACKED_MUD),
                listOf(Material.GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.TUFF),
            ),
            Level2Region.INDUSTRIAL to Palette(
                listOf(Material.STONE_BRICKS, Material.STONE_BRICKS, Material.POLISHED_ANDESITE),
                listOf(Material.POLISHED_ANDESITE, Material.TUFF_BRICKS, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS),
                listOf(Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.POLISHED_ANDESITE),
            ),
            Level2Region.CLAY to Palette(
                listOf(Material.TERRACOTTA, Material.DIRT, Material.COARSE_DIRT, Material.GRAVEL, Material.RED_SAND),
                listOf(Material.TERRACOTTA, Material.BROWN_TERRACOTTA, Material.COARSE_DIRT, Material.PACKED_MUD),
                listOf(Material.TERRACOTTA, Material.BROWN_TERRACOTTA, Material.DIRT, Material.PACKED_MUD),
            ),
        )
    }
}

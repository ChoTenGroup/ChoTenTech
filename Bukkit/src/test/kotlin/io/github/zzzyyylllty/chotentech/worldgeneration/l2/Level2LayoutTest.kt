package io.github.zzzyyylllty.chotentech.worldgeneration.l2

import org.bukkit.block.data.Rail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Level2LayoutTest {
    @Test
    fun `rooms and corridors do not leave sealed pockets at chunk borders`() {
        val start = -288
        val size = 577
        for (seed in listOf(42L, -7607734702912L)) {
            val layout = Level2Layout(seed)
            val spaces = Array(size) { x -> Array(size) { z -> layout.column(start + x, start + z).space } }
            val reached = Array(size) { BooleanArray(size) }
            val queue = ArrayDeque<Int>()
            fun visit(x: Int, z: Int) {
                if (x !in 0 until size || z !in 0 until size || reached[x][z] || spaces[x][z] == Level2Space.SOLID) return
                reached[x][z] = true
                queue.addLast(x * size + z)
            }
            visit(-start, -start)
            // A passage leaving the sampled map can reconnect outside it.
            for (i in 0 until size) {
                visit(i, 0)
                visit(i, size - 1)
                visit(0, i)
                visit(size - 1, i)
            }
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index / size
                val z = index % size
                visit(x - 1, z)
                visit(x + 1, z)
                visit(x, z - 1)
                visit(x, z + 1)
            }
            var rooms = 0
            var doorways = 0
            for (x in 0 until size) for (z in 0 until size) {
                val space = spaces[x][z]
                if (space == Level2Space.ROOM) rooms++
                if (space == Level2Space.DOORWAY) doorways++
                if (space != Level2Space.SOLID) {
                    assertTrue(reached[x][z]) { "Sealed $space at (${start + x}, ${start + z}) with seed $seed" }
                }
            }
            assertTrue(rooms > 0 && doorways > 0)
        }
    }

    @Test
    fun `room uses appear throughout the infinite grid`() {
        val layout = Level2Layout(42L)
        val uses = mutableSetOf<Level2RoomUse>()
        for (x in -640..640 step 7) for (z in -640..640 step 7) {
            layout.column(x, z).roomUse?.let { uses += it }
            if (uses.containsAll(Level2RoomUse.entries)) return
        }
        assertTrue(uses.containsAll(Level2RoomUse.entries)) { "Missing room types: ${Level2RoomUse.entries - uses}" }
    }

    @Test
    fun `railway does not cut through rooms on the negative axes`() {
        val layout = Level2Layout(42L)
        // Any of these columns may fall inside the railway's maximum width.
        for (along in -768..767) for (offset in -12..-1) {
            assertTrue(layout.column(offset, along).space != Level2Space.ROOM) {
                "Room intrudes into the railway clearance at ($offset, $along)"
            }
            assertTrue(layout.column(along, offset).space != Level2Space.ROOM) {
                "Room intrudes into the railway clearance at ($along, $offset)"
            }
        }
    }

    @Test
    fun `tracks continue across the negative and positive axes`() {
        val layout = Level2Layout(42L)
        for (along in listOf(-29_999_985, -97, -96, -17, -16, -4, 4, 15, 16, 96, 97, 29_999_984)) {
            for (offset in listOf(-3, 3)) {
                assertEquals(Level2Space.RAILWAY, layout.column(offset, along).space)
                assertEquals(Level2Space.RAILWAY, layout.column(along, offset).space)
                assertEquals(Rail.Shape.NORTH_SOUTH, Level2Terrain.trackShape(offset, along))
                assertEquals(Rail.Shape.EAST_WEST, Level2Terrain.trackShape(along, offset))
            }
        }
        assertEquals(Rail.Shape.NORTH_WEST, Level2Terrain.trackShape(-3, -3))
        assertEquals(Rail.Shape.NORTH_EAST, Level2Terrain.trackShape(3, -3))
        assertEquals(Rail.Shape.SOUTH_WEST, Level2Terrain.trackShape(-3, 3))
        assertEquals(Rail.Shape.SOUTH_EAST, Level2Terrain.trackShape(3, 3))
    }
}

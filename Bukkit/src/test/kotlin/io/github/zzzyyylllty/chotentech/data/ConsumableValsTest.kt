package io.github.zzzyyylllty.chotentech.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConsumableValsTest {
    @Test
    fun `numbers and numeric strings retain positive and negative deltas`() {
        val values = mapOf(
            "hunger" to 10, "saturation" to "20.5", "health" to -3,
            "san" to "-5", "water" to -12.5,
            "nutrition" to mapOf("grain" to 0, "vitamin" to "8", "protein" to -2, "fat" to 1.5),
        )
        val result = ConsumableVals.read(values::get)
        assertTrue(result.configured)
        assertEquals(10.0, result.hunger)
        assertEquals(20.5, result.saturation)
        assertEquals(-3.0, result.health)
        assertEquals(-5.0, result.san)
        assertEquals(-12.5, result.water)
        assertEquals(mapOf("grain" to 0.0, "vitamin" to 8.0, "protein" to -2.0, "fat" to 1.5), result.nutrition)
        assertEquals(25.0, ConsumableVals.read { if (it == "water") 25 else null }.water)
    }

    @Test
    fun `zero and water only items suppress vanilla food fallback`() {
        assertTrue(ConsumableVals.read { if (it == "water") 0 else null }.configured)
        assertTrue(ConsumableVals.read { if (it == "hunger") 0 else null }.configured)
        assertFalse(ConsumableVals.read(mapOf("lore" to "ordinary item")::get).configured)
        assertFalse(ConsumableVals.read { null }.configured)
    }

    @Test
    fun `nested potion effects preserve ticks levels and defaults`() {
        val effects = listOf(
            mapOf("effect" to "NIGHT_VISION", "tick" to "40", "level" to 1),
            mapOf("effect" to "BLINDNESS", "tick" to 10, "level" to 0),
            mapOf("effect" to "SPEED"), "invalid", emptyMap<String, Any>(),
        )
        val result = ConsumableVals.read { if (it == "effects") effects else null }
        assertEquals(listOf(
            ConsumableVals.Effect("NIGHT_VISION", 40, 0),
            ConsumableVals.Effect("BLINDNESS", 10, -1),
            ConsumableVals.Effect("SPEED", 40, 0),
        ), result.effects)
    }

    @Test
    fun `malformed and nonfinite numbers never reach player state`() {
        val values = mapOf(
            "water" to Double.NaN, "san" to "Infinity", "hunger" to "bad",
            "saturation" to true, "health" to Double.NEGATIVE_INFINITY,
            "nutrition" to mapOf("fat" to "NaN", "vitamin" to 8),
        )
        val result = ConsumableVals.read(values::get)
        assertNull(result.water)
        assertNull(result.san)
        assertNull(result.hunger)
        assertNull(result.saturation)
        assertNull(result.health)
        assertEquals(mapOf("vitamin" to 8.0), result.nutrition)
    }
}

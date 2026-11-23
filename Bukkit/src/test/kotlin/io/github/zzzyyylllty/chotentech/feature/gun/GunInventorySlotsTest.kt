package io.github.zzzyyylllty.chotentech.feature.gun

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GunInventorySlotsTest {
    @Test
    fun `full player inventory scan stops before armor and offhand slots`() {
        assertEquals((0 until 36).toList(), GunInventorySlots.storageSlots(41).toList())
    }

    @Test
    fun `short inventory scan stays within available slots`() {
        assertEquals((0 until 9).toList(), GunInventorySlots.storageSlots(9).toList())
        assertEquals(emptyList<Int>(), GunInventorySlots.storageSlots(0).toList())
    }

    @Test
    fun `oversized inventory scan is capped at storage capacity`() {
        assertEquals((0 until 36).toList(), GunInventorySlots.storageSlots(54).toList())
    }
}

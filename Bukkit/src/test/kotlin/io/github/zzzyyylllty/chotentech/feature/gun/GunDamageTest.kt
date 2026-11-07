package io.github.zzzyyylllty.chotentech.feature.gun

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GunDamageTest {
    @Test
    fun `distance factor is full at range and decays after it`() {
        assertEquals(1.0, GunDamage.distanceFactor(0.0, 32.0), 1e-9)
        assertEquals(1.0, GunDamage.distanceFactor(32.0, 32.0), 1e-9)
        assertEquals(0.7, GunDamage.distanceFactor(48.0, 32.0), 1e-9)
        assertEquals(0.4, GunDamage.distanceFactor(64.0, 32.0), 1e-9)
        assertEquals(0.0, GunDamage.distanceFactor(128.0, 32.0), 1e-9)
    }

    @Test
    fun `negative hit distance cannot increase damage`() {
        assertEquals(1.0, GunDamage.distanceFactor(-10.0, 32.0), 1e-9)
    }
}

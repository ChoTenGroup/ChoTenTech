package io.github.zzzyyylllty.chotentech.feature.gun

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GunBallisticsTest {
    @Test
    fun `pierce allowance is consumed by each hit and stops at zero`() {
        assertEquals(0, GunBallistics.consumePierce(1))
        assertEquals(1, GunBallistics.consumePierce(2))
        assertEquals(0, GunBallistics.consumePierce(0))
        assertEquals(0, GunBallistics.consumePierce(-1))
    }
}

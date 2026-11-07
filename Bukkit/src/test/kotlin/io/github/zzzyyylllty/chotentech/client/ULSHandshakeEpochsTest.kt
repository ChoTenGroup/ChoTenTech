package io.github.zzzyyylllty.chotentech.client

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ULSHandshakeEpochsTest {
    @Test
    fun `newer failed handshake still invalidates older queued success`() {
        val epochs = ULSHandshakeEpochs()
        val uuid = UUID.randomUUID()
        val h1 = epochs.begin(uuid)
        assertTrue(epochs.matches(uuid, h1))
        val h2 = epochs.begin(uuid)
        assertNotEquals(h1, h2)
        assertFalse(epochs.matches(uuid, h1))
        assertTrue(epochs.matches(uuid, h2))
        epochs.invalidate(uuid)
        assertFalse(epochs.matches(uuid, h2))
        val h3 = epochs.begin(uuid)
        assertFalse(epochs.matches(uuid, h1))
        assertTrue(epochs.matches(uuid, h3))
        epochs.clear()
        assertFalse(epochs.matches(uuid, h3))
    }
}

package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ULSMenuCodecTest {
    private fun readVarInt(input: DataInputStream): Int {
        var value = 0
        var shift = 0
        while (true) {
            val byte = input.readUnsignedByte()
            value = value or ((byte and 127) shl shift)
            if ((byte and 128) == 0) return value
            shift += 7
        }
    }

    private fun readString(input: DataInputStream): String =
        input.readNBytes(readVarInt(input)).toString(Charsets.UTF_8)

    private fun menu(): ULSMenuDefinition = ULSMenuDefinition.builder("test:menu", "Title").build()

    @Test
    fun `menu packets start with menu ID and long nonce`() {
        val nonce = 0x102030405060708L
        val open = DataInputStream(ByteArrayInputStream(ULSMenuCodec.encodeOpen(menu(), nonce)))
        assertEquals("test:menu", readString(open))
        assertEquals(nonce, open.readLong())
        assertEquals("Title", readString(open))
        assertTrue(open.available() > 0)

        val data = DataInputStream(ByteArrayInputStream(ULSMenuCodec.encodeData("test:menu", nonce, mapOf("a" to "b"))))
        assertEquals("test:menu", readString(data))
        assertEquals(nonce, data.readLong())
        assertEquals(1, readVarInt(data))
        assertEquals("a", readString(data))
        assertEquals("b", readString(data))
        assertEquals(0, data.available())

        val close = ULSMenuCodec.encodeClose("test:menu", nonce)
        val closeData = DataInputStream(ByteArrayInputStream(close))
        assertEquals("test:menu", readString(closeData))
        assertEquals(nonce, closeData.readLong())
        assertEquals(0, closeData.available())
        assertEquals(ULSMenuCodec.DismissPayload("test:menu", nonce), ULSMenuCodec.decodeDismiss(close))
        assertNull(ULSMenuCodec.decodeDismiss(close + byteArrayOf(0)))
        assertNull(ULSMenuCodec.decodeDismiss(close.dropLast(1).toByteArray()))
    }

    @Test
    fun `golden menu open and action match Fabric byte layout`() {
        val nonce = 0x1020304050607080L
        val menu = ULSMenuDefinition.builder("x", "t").size(16, 16).build()
        val open = ULSMenuCodec.encodeOpen(menu, nonce)
        assertEquals("01781020304050607080017410100004726f6f74000000", open.toHex())
        val action = ULSMenuCodec.encodeAction("x", nonce, "a", "p")
        assertEquals("0178102030405060708001610170", action.toHex())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    @Test
    fun `reject forged action body with trailing bytes malformed UTF8 or missing nonce`() {
        val nonce = -0x102030405060708L
        val valid = ULSMenuCodec.encodeAction("test:menu", nonce, "do.thing", "payload")
        assertEquals(ULSMenuCodec.ActionPayload("test:menu", nonce, "do.thing", "payload"), ULSMenuCodec.decodeAction(valid))
        assertNull(ULSMenuCodec.decodeAction(valid + byteArrayOf(0)))
        assertNull(ULSMenuCodec.decodeAction(valid.dropLast(1).toByteArray()))
        assertNull(ULSMenuCodec.decodeAction(byteArrayOf(1, 0xFF.toByte()) + valid.drop(2)))

        val oldFormat = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { it.writeByte(1); it.writeByte('m'.code); it.writeByte(1); it.writeByte('a'.code); it.writeByte(0) }
        }.toByteArray()
        assertNull(ULSMenuCodec.decodeAction(oldFormat))
    }

    @Test
    fun `reject menu exceeding element and condition depth or shared budget`() {
        val tooDeep = (0..ULSProtocol.MAX_MENU_DEPTH).fold< Int, ULSMenuCondition >(
            ULSMenuCondition.eq("a", "b")
        ) { previous, _ -> ULSMenuCondition.not(previous) }
        val deepRoot = ULSMenuElement.of(ULSMenuElementType.ROOT).visibleWhen(tooDeep).build()
        assertThrows(IllegalArgumentException::class.java) {
            ULSMenuCodec.encodeOpen(ULSMenuDefinition.builder("deep", "Deep").root(deepRoot).build(), 7L)
        }

        val conditions = List(ULSProtocol.MAX_MENU_ELEMENTS) { ULSMenuCondition.eq("a", "b") }
        val wideRoot = ULSMenuElement.of(ULSMenuElementType.ROOT)
            .visibleWhen(ULSMenuCondition.All(conditions)).build()
        assertThrows(IllegalArgumentException::class.java) {
            ULSMenuCodec.encodeOpen(ULSMenuDefinition.builder("wide", "Wide").root(wideRoot).build(), 7L)
        }
    }

    @Test
    fun `reject oversize menu data before network send`() {
        val variables = (0 until ULSProtocol.MAX_MENU_VARIABLES).associate { "k$it" to "x".repeat(ULSProtocol.MAX_TEXT_LEN) }
        assertThrows(IllegalArgumentException::class.java) {
            ULSMenuCodec.encodeData("test:menu", 42L, variables)
        }
    }
}

package dev.draftingroom5

import org.junit.Assert.*
import org.junit.Test

class CurrentJsonInputTest {
    @Test fun validUtf8RoundTripsAndMalformedBytesAreRejected() {
        val value = "Routine — 💪"
        assertEquals(value, readBoundedCurrentJson(value.byteInputStream()))
        assertThrows(IllegalArgumentException::class.java) {
            readBoundedCurrentJson(byteArrayOf(0xc3.toByte(), 0x28).inputStream())
        }
    }

    @Test fun streamIsBoundedEvenWhenItsLengthIsUnknown() {
        var consumed = 0
        val endless = object : java.io.InputStream() {
            override fun read(): Int { consumed++; return 32 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                java.util.Arrays.fill(bytes, offset, offset + length, 32.toByte())
                consumed += length
                return length
            }
        }
        assertThrows(IllegalArgumentException::class.java) { readBoundedCurrentJson(endless) }
        assertTrue(consumed <= MAX_DOCUMENT_BYTES + 8_192)
    }
}

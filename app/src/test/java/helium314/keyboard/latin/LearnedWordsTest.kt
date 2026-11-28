// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.dictionary.history.LearnedWords
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class LearnedWordsTest {
    // header as the native library writes it: magic, version, flags, size, then "key", "value" strings ending in 0x1F
    private fun header(vararg attributes: Pair<String, String>): File {
        val body = ByteArrayOutputStream()
        attributes.forEach { (k, v) -> body.write(k.toByteArray()); body.write(0x1F); body.write(v.toByteArray()); body.write(0x1F) }
        val size = 12 + body.size()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x9B.toByte(), 0xC1.toByte(), 0x3A, 0xFE.toByte(), 0, 0x193.shr(8).toByte(), 0x193.toByte(), 0, 0))
        out.write(byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()).copyOfRange(1, 4))
        out.write(body.toByteArray())
        return File.createTempFile("history", ".header").apply { writeBytes(out.toByteArray()); deleteOnExit() }
    }

    @Test fun readsTheWordCount() {
        val attributes = LearnedWords.headerAttributes(header("dictionary" to "history.en_GB", "UNIGRAM_COUNT" to "123", "BIGRAM_COUNT" to "45"))
        assertEquals("123", attributes["UNIGRAM_COUNT"])
        assertEquals("45", attributes["BIGRAM_COUNT"])
    }

    @Test fun brokenFileGivesNothing() {
        assertEquals(emptyMap(), LearnedWords.headerAttributes(File.createTempFile("broken", ".header").apply { writeBytes(byteArrayOf(1, 2)) }))
    }
}

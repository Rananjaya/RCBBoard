// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import kotlin.test.Test
import helium314.keyboard.latin.makedict.DictIO
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class) // necessary because there is logging in writeBinaryDictionary
class DictionaryTest {
    // this is mostly for checking internals
    @Test fun readCombined() {
        val dict = DictIO.readCombinedDictionary("src/test/resources/somelist.combined")
        val pp = dict.first { it.mWord == "patty-pan" }
        assert(pp.mIsPossiblyOffensive)
        assertEquals(15, pp.probability)
        val i = dict.first { it.mWord == "i" }
        assert(!i.mIsPossiblyOffensive)
        assertEquals(15, i.mShortcutTargets!!.single().probability)
        assertEquals(16, i.probability)
        val ag = dict.first { it.mWord == "again" }
        assertEquals(168, ag.probability)
        assertEquals("when", ag.bigrams.first().mWord)
        assertEquals(1, ag.bigrams.first().mProbabilityInfo.mProbability)
        assertEquals("when", ag.mNgrams.first().mTargetWord.mWord)
    }

    // "Suggest emojis" off must also skip the bundled emoji dict, and not re-extract it after the cached copy is filtered
    @Test fun bundledEmojiDictRespectsSuggestEmojisSetting() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val locale = java.util.Locale.forLanguageTag("si")
        val factory = helium314.keyboard.latin.dictionary.DictionaryFactory

        val (_, bundledOn) = factory.getAvailableDictsForLocale(locale, context, true)
        assertTrue(bundledOn.any { it.startsWith("emoji_") }, "$bundledOn")
        assertTrue("main_si.dict" in bundledOn, "$bundledOn")

        val (_, bundledOff) = factory.getAvailableDictsForLocale(locale, context, false)
        assertTrue(bundledOff.none { it.startsWith("emoji_") }, "$bundledOff")
        assertTrue("main_si.dict" in bundledOff, "$bundledOff") // Sinhala words unaffected

        // already extracted copy, as on a phone after first use
        val cacheDir = helium314.keyboard.latin.dictionary.DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)!!
        File(cacheDir, "emoji.dict").writeText("x")
        val (cachedOff, bundledOff2) = factory.getAvailableDictsForLocale(locale, context, false)
        assertTrue(cachedOff.none { it.name == "emoji.dict" })
        assertTrue(bundledOff2.none { it.startsWith("emoji_") }, "$bundledOff2")
        val (cachedOn, _) = factory.getAvailableDictsForLocale(locale, context, true)
        assertTrue(cachedOn.any { it.name == "emoji.dict" })
    }

    @Test fun readAndWrite() {
        val dict = DictIO.readCombinedDictionary("src/test/resources/somelist.combined")
        val tempFile = "src/test/resources/somelist.temp"
        DictIO.writeCombinedDictionary(tempFile, dict)
        val dict2 = DictIO.readCombinedDictionary(tempFile)
        assertEquals(dict.count(), dict2.count())
        File(tempFile).delete()
        DictIO.writeBinaryDictionary(tempFile, dict)
        // can't read it without lib...
        File(tempFile).delete()
    }

    // the bundled English dictionaries have words with shortcuts and bigrams: the encoder must not fail on them
    // ("Increased size ?!") and must write the shortcut list before the bigrams, as the native reader expects
    @Test fun writeWordWithShortcutAndBigram() {
        val input = File.createTempFile("shortcut_bigram", ".combined")
        input.writeText("""
            dictionary=main:en_gb,locale=en_GB,description=test,date=1,version=1
             word=a,f=100
              shortcut=b,f=whitelist
              bigram=c,f=200
             word=b,f=90
             word=c,f=80
        """.trimIndent() + "\n")
        val output = File.createTempFile("shortcut_bigram", ".dict")
        DictIO.writeBinaryDictionary(output.absolutePath, DictIO.readCombinedDictionary(input.absolutePath))
        val bytes = output.readBytes()
        val headerSize = java.nio.ByteBuffer.wrap(bytes, 8, 4).int
        var pos = headerSize + 1 // root PtNode array: one byte for its size, then the PtNode of "a"
        val flags = bytes[pos].toInt() and 0xFF
        assertTrue(flags and 0x08 != 0 && flags and 0x04 != 0, "a should have shortcuts and bigrams, flags $flags")
        pos += 3 // flags, character, frequency (no children)
        // shortcut list first: its size is a 2-byte number (small, so the first byte is 0); a bigram would start
        // with a flags byte that has address type bits set
        assertEquals(0, bytes[pos].toInt(), "shortcut list size expected before the bigrams")
        assertTrue(bytes[pos + 1] > 2)
        input.delete()
        output.delete()
    }

    @Test fun readCombinedOutput() {
        val dict = DictIO.readCombinedDictionary("src/test/resources/somelist.combined")
        val tempFile = File("src/test/resources/somelist.temp")
        DictIO.writeCombinedDictionary(tempFile.absolutePath, dict)
        val dict2 = DictIO.readCombinedDictionary(tempFile.absolutePath)
        val tempFile2 = File("src/test/resources/somelist2.temp")
        DictIO.writeCombinedDictionary(tempFile2.absolutePath, dict2)
        assertEquals(tempFile.readText(), tempFile2.readText())
        assertTrue(tempFile.readText().contains("shortcut="))
        tempFile.delete()
        tempFile2.delete()
    }

    @Test fun readCombinedOutputForHistoryDict() {
        val dict = DictIO.readCombinedDictionary("src/test/resources/history_export.combined")
        val tempFile = File("src/test/resources/somelist.temp")
        DictIO.writeCombinedDictionary(tempFile.absolutePath, dict)
        val dict2 = DictIO.readCombinedDictionary(tempFile.absolutePath)
        val tempFile2 = File("src/test/resources/somelist2.temp")
        DictIO.writeCombinedDictionary(tempFile2.absolutePath, dict2)
        assertEquals(tempFile.readText(), tempFile2.readText())
        tempFile.delete()
        tempFile2.delete()
    }
}

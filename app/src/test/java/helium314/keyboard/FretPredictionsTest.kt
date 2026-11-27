// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.keyboard.FretPredictionRenderer
import helium314.keyboard.keyboard.FretPredictions
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWords
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BlackBerry 10 style predictions of the Fret keyboard style */
class FretPredictionsTest {
    private val dict = Dictionary.PhonyDictionary("")
    private fun info(word: String, kind: Int = SuggestedWordInfo.KIND_COMPLETION) =
        SuggestedWordInfo(word, "", 1, kind, dict, 0, 0)
    private fun words(typed: String, vararg suggestions: String, style: InputStyle = InputStyle.TYPING): SuggestedWords {
        val typedInfo = info(typed, SuggestedWordInfo.KIND_TYPED)
        return SuggestedWords(listOf(typedInfo) + suggestions.map { info(it) }, null, typedInfo, true, false, false, style, 0)
    }
    private fun letters(map: Map<Int, SuggestedWordInfo>) = map.entries.associate { it.key.toChar() to it.value.word }

    @AfterTest fun reset() {
        FretPredictions.clear()
        FretPredictions.layoutMatchesLanguage = true
    }

    @Test fun `In-Letter is the default`() {
        assertEquals("in_letter", Defaults.PREF_FRET_PREDICTIONS)
        assertEquals(FretPredictions.Mode.IN_LETTER, FretPredictions.Mode.fromPref(Defaults.PREF_FRET_PREDICTIONS))
        assertEquals(FretPredictions.Mode.IN_COLUMN, FretPredictions.Mode.fromPref("in_column"))
        assertEquals(FretPredictions.Mode.OFF, FretPredictions.Mode.fromPref("off"))
        assertEquals(FretPredictions.Mode.IN_LETTER, FretPredictions.Mode.fromPref("unknown"))
    }

    @Test fun `a word goes above the key of its next letter`() {
        // the BlackBerry 10 example: after "Blac", "BlackBerry" sits above K
        assertEquals(mapOf('k' to "BlackBerry"), letters(FretPredictions.mapToNextLetters(listOf(info("BlackBerry")), "Blac")))
    }

    @Test fun `before the first letter a prediction goes above its first letter`() {
        assertEquals(mapOf('t' to "the", 'a' to "and"),
            letters(FretPredictions.mapToNextLetters(listOf(info("the"), info("and")), "")))
    }

    @Test fun `the best ranked word wins a key and at most three words are shown`() {
        val candidates = listOf("held", "help", "hello", "helped", "helmet", "hell").map { info(it) }
        assertEquals(mapOf('d' to "held", 'p' to "help", 'l' to "hello"), letters(FretPredictions.mapToNextLetters(candidates, "hel")))
    }

    @Test fun `corrections that do not continue the typed word and non-letters are left out`() {
        val candidates = listOf("gel", "he'll", "hero").map { info(it) }
        assertEquals(mapOf('r' to "hero"), letters(FretPredictions.mapToNextLetters(candidates, "he")))
    }

    @Test fun `matching ignores case and keeps the word's casing`() {
        val map = FretPredictions.mapToNextLetters(listOf(info("Hello")), "he")
        assertEquals("Hello", map['l'.code]?.word)
    }

    @Test fun `In-Letter mode skips the typed word and looks up keys case-insensitively`() {
        FretPredictions.update(words("hel", "held", "help"), FretPredictions.Mode.IN_LETTER)
        assertEquals("held", FretPredictions.wordForLetter('d'.code)?.word)
        assertEquals("help", FretPredictions.wordForLetter('P'.code)?.word)
        assertNull(FretPredictions.wordForLetter('l'.code))
        assertEquals("hel", FretPredictions.typedPrefix)
    }

    @Test fun `In-Column mode puts the best word in the middle`() {
        FretPredictions.update(words("hel", "he'll", "held", "help"), FretPredictions.Mode.IN_COLUMN)
        assertEquals("held", FretPredictions.wordForColumn(0)?.word)
        assertEquals("he'll", FretPredictions.wordForColumn(1)?.word)
        assertEquals("help", FretPredictions.wordForColumn(2)?.word)
        assertNull(FretPredictions.wordForLetter('d'.code))
    }

    @Test fun `Off and gesture typing show no words`() {
        FretPredictions.update(words("hel", "held"), FretPredictions.Mode.OFF)
        assertNull(FretPredictions.wordForLetter('d'.code))
        FretPredictions.update(words("hel", "held", style = InputStyle.UPDATE_BATCH), FretPredictions.Mode.IN_LETTER)
        assertNull(FretPredictions.wordForLetter('d'.code))
    }

    private fun assertNoOverlap(xs: FloatArray, widths: FloatArray, factor: Float, gap: Float) {
        for (i in 1 until xs.size)
            assertTrue(xs[i] >= xs[i - 1] + widths[i - 1] * factor + gap - 0.01f, "word $i overlaps word ${i - 1}: ${xs.toList()}")
    }

    @Test fun `words on neighbouring keys are pushed apart`() {
        // "still" above I and "story" above O, keys 108 px apart, both words 130 px wide
        val widths = floatArrayOf(130f, 130f)
        val (xs, factor) = FretPredictionRenderer.packRow(floatArrayOf(810f, 918f), widths, 4f, 1076f, 8f)
        assertEquals(1f, factor)
        assertNoOverlap(xs, widths, factor, 8f)
        assertTrue(xs.last() + widths.last() <= 1076f)
    }

    @Test fun `a long word at the edge stays inside the keyboard`() {
        val (xs, _) = FretPredictionRenderer.packRow(floatArrayOf(1026f), floatArrayOf(240f), 4f, 1076f, 8f)
        assertEquals(1076f - 240f, xs[0])
        val (xs2, _) = FretPredictionRenderer.packRow(floatArrayOf(20f), floatArrayOf(240f), 4f, 1076f, 8f)
        assertEquals(4f, xs2[0])
    }

    @Test fun `a full row is condensed instead of overlapping or clipping`() {
        val widths = floatArrayOf(500f, 500f, 500f)
        val (xs, factor) = FretPredictionRenderer.packRow(floatArrayOf(200f, 540f, 880f), widths, 4f, 1076f, 8f)
        assertTrue(factor < 1f)
        assertNoOverlap(xs, widths, factor, 8f)
        assertTrue(xs[0] >= 4f && xs.last() + widths.last() * factor <= 1076.01f)
    }

    @Test fun `layouts in another script than the language keep the suggestion strip`() {
        // Singlish: Sinhala words typed with the QWERTY layout
        FretPredictions.layoutMatchesLanguage = false
        FretPredictions.update(words("මම", "මමත්", "මමයි"), FretPredictions.Mode.IN_LETTER)
        assertEquals(FretPredictions.Mode.OFF, FretPredictions.mode)
        assertEquals(false, FretPredictions.hidesSuggestionStrip(FretPredictions.Mode.IN_LETTER))
        FretPredictions.layoutMatchesLanguage = true
        assertEquals(true, FretPredictions.hidesSuggestionStrip(FretPredictions.Mode.IN_LETTER))
        assertEquals(true, FretPredictions.hidesSuggestionStrip(FretPredictions.Mode.IN_COLUMN))
        assertEquals(false, FretPredictions.hidesSuggestionStrip(FretPredictions.Mode.OFF))
    }

    @Test fun `the best emoji suggestion goes above the comma key`() {
        FretPredictions.update(words("thank", "thanks", "🙏", "thanked", "🤝"), FretPredictions.Mode.IN_LETTER)
        assertEquals("🙏", FretPredictions.emojiWord()?.word)
        assertEquals("thanks", FretPredictions.wordForLetter('s'.code)?.word)
        FretPredictions.update(words("thank", "thanks", "🙏"), FretPredictions.Mode.IN_COLUMN)
        assertNull(FretPredictions.emojiWord())
    }
}

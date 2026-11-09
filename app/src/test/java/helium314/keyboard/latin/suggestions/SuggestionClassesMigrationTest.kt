// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.SinhalaLookup
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.singlish.SinglishRepaya
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * RCBBoard: checks for the migration of the suggestion classes to Kotlin (upstream 6c36d91d, a74cd059, 7a106de3,
 * aec7f831): RCBBoard code that builds or merges SuggestedWordInfo keeps every field and its results, and
 * punctuation suggestions don't crash on key specs without a label or output text.
 */
@RunWith(RobolectricTestRunner::class)
class SuggestionClassesMigrationTest {
    private val zwj = "‍"
    private val dict = Dictionary.PhonyDictionary("test")

    private fun info(word: String, score: Int, kind: Int = SuggestedWordInfo.KIND_CORRECTION, source: Dictionary = dict) =
        SuggestedWordInfo(word, "prev", score, kind, source, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)

    // ---- SinglishRepaya keeps all fields when it rebuilds a suggestion in the ZWJ form ----

    @Test fun `singlish repaya rebuild keeps every suggestion field`() {
        val source = Dictionary.PhonyDictionary("main")
        val original = SuggestedWordInfo("කර්ම", "මම", 1234,
            SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
            source, 3, 7)
        val result = SinglishRepaya.compatibleSuggestions("කර්${zwj}ම", listOf(original)) { emptyList() }
        val rebuilt = result.single()
        assertEquals("කර්${zwj}ම", rebuilt.word) // only the word changes, to the ZWJ form
        assertEquals(original.prevWordsContext, rebuilt.prevWordsContext)
        assertEquals(original.score, rebuilt.score)
        assertEquals(original.kindAndFlags, rebuilt.kindAndFlags)
        assertSame(original.sourceDict, rebuilt.sourceDict)
        assertEquals(original.indexOfTouchPointOfSecondWord, rebuilt.indexOfTouchPointOfSecondWord)
        assertEquals(original.autoCommitFirstWordConfidence, rebuilt.autoCommitFirstWordConfidence)
        assertEquals(original.applicationSpecifiedCompletionInfo, rebuilt.applicationSpecifiedCompletionInfo)
        assertTrue(rebuilt.isAppropriateForAutoCorrection)
    }

    @Test fun `singlish repaya keeps a suggestion that needs no change as the same object`() {
        val alreadyZwj = info("කර්${zwj}ම", 100)
        assertSame(alreadyZwj, SinglishRepaya.compatibleSuggestions("කර්${zwj}ම", listOf(alreadyZwj)) { emptyList() }.single())
    }

    // ---- SinhalaLookup.mergeSuggestions keeps its results ----

    @Test fun `sinhala merge keeps the primary list when there is nothing normalized, and the reverse`() {
        val primary = arrayListOf(info("අපි", 10))
        assertSame(primary, SinhalaLookup.mergeSuggestions(primary, null))
        assertSame(primary, SinhalaLookup.mergeSuggestions(primary, arrayListOf()))
        val normalized = arrayListOf(info("අපේ", 10))
        assertSame(normalized, SinhalaLookup.mergeSuggestions(null, normalized))
        assertSame(normalized, SinhalaLookup.mergeSuggestions(arrayListOf(), normalized))
        assertNull(SinhalaLookup.mergeSuggestions(null, null))
    }

    @Test fun `sinhala merge order, scores and NFC preference`() {
        val decomposedApe = info("අපේ", 50) // අපේ, decomposed vowel sign
        val composedApe = info("අපේ", 50)
        val api = info("අපි", 90)
        val primary = arrayListOf(api, decomposedApe, info("ගම", 30))
        val normalized = arrayListOf(composedApe, info("ගම", 80), info("මම", 20))
        val merged = SinhalaLookup.mergeSuggestions(primary, normalized)!!
        // the order of the primary list, entries only in the normalized list at the end
        assertEquals(listOf("අපි", "අපේ", "ගම", "මම"), merged.map { it.word })
        assertSame(api, merged[0]) // the dictionary's own objects are returned
        assertSame(composedApe, merged[1]) // equal score: the NFC spelling is preferred
        assertEquals(80, merged[2].score) // higher score wins
        assertEquals(20, merged[3].score)
    }

    // ---- data class: equal content is not the same suggestion (Stage 3 relies on identity, see InputLogicTest) ----

    @Test fun `copied suggestion is equal but not the same object`() {
        val original = info("ගෙදර", 1)
        val copy = original.copy()
        assertEquals(original, copy)
        assertNotSame(original, copy)
    }

    // ---- punctuation suggestions ----

    @Test fun `default punctuation suggestions keep their words and labels`() {
        val specs = arrayOf("!", "?", ",", ":", ";", "\"", "(", ")", "'", "-", "/", "@", "_", "...")
        val suggestions = PunctuationSuggestions.newPunctuationSuggestions(specs)
        for (i in specs.indices) {
            assertEquals(specs[i], suggestions.getWord(i))
            assertEquals(specs[i], suggestions.getLabel(i))
        }
    }

    @Test fun `punctuation suggestion with an icon has no label but does not crash`() {
        // possible with user-defined punctuation suggestions; the Java version returned null here, Kotlin !! crashed
        val suggestions = PunctuationSuggestions.newPunctuationSuggestions(arrayOf("!icon/clipboard_action_key|abc", "a|bc"))
        assertEquals("", suggestions.getLabel(0))
        assertEquals("abc", suggestions.getWord(0))
        assertEquals("a", suggestions.getLabel(1))
        assertEquals("bc", suggestions.getWord(1))
        assertEquals("abc", suggestions.getInfo(0).word)
    }
}

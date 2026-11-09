// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import helium314.keyboard.latin.makedict.DictIO
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RCBBoard Singlish phase 2B stage 3: the conservative auto-correction rules, with the bundled Sinhala word list. */
class SinglishAutoCorrectionTest {
    private val zwj = "‍"

    /** What Space would commit instead of the transliteration of [latin], or null to keep it. */
    private fun correctionFor(latin: String, isValid: (String) -> Boolean = sinhalaWords::contains): String? {
        val typed = SinglishTransliterator.transliterate(latin)
        return SinglishAutoCorrection.choose(typed, SinglishCandidates.candidates(typed, isValid))
    }

    // ---- the everyday examples ----

    @Test fun `one clear spelling is chosen`() {
        assertEquals("ගෙදර", correctionFor("gedara"))
        assertEquals("සිංහල", correctionFor("sinhala")) // cost 1, while සිංහළ costs 2
        assertEquals("ආයුබෝවන්", correctionFor("ayubowan")) // cost 2
        assertEquals("දෘෂ්ටි", correctionFor("dhrushti"))
    }

    @Test fun `repaya words are corrected to the ZWJ form`() {
        assertEquals("අර්${zwj}ථය", correctionFor("arthaya")) // the word list has අර්ථය without ZWJ
    }

    @Test fun `valid typed words are never replaced`() {
        assertNull(correctionFor("kohomada")) // කොහොමඩ is in the word list, although කොහොමද is too
        assertNull(correctionFor("mama"))
        assertNull(correctionFor("gedhara")) // typed exactly
        assertNull(correctionFor("karma")) // valid without the repaya ZWJ
        assertNull(correctionFor("prashnaya")) // rakaransaya, valid as typed
    }

    @Test fun `a word valid only in the personal or learned dictionaries is kept too`() {
        // the validity check covers every dictionary the suggestions use; a word known only there counts
        val typed = SinglishTransliterator.transliterate("gedara")
        val learned = sinhalaWords + typed
        assertNull(SinglishAutoCorrection.choose(typed, SinglishCandidates.candidates(typed, learned::contains)))
    }

    // ---- ambiguity, cost and length ----

    @Test fun `equally cheap spellings are not guessed`() {
        val typed = SinglishTransliterator.transliterate("gedara") // ගෙඩර
        val tie = setOf("ගෙදර", "ගේඩර") // both one change away
        assertNull(SinglishAutoCorrection.choose(typed, SinglishCandidates.candidates(typed, tie::contains)))
        // a cheaper unique spelling wins over a tie at a higher cost
        val cheaperUnique = setOf("ගෙදර", "ගේදර", "ගෙදරා")
        assertEquals("ගෙදර", SinglishAutoCorrection.choose(typed, SinglishCandidates.candidates(typed, cheaperUnique::contains)))
    }

    @Test fun `at most two changes`() {
        val typed = "ගෙඩර"
        assertEquals("ගේදර", SinglishAutoCorrection.choose(typed, SinglishCandidates.candidates(typed, setOf("ගේදර")::contains)))
        assertNull(SinglishAutoCorrection.choose(typed, SinglishCandidates.candidates(typed, setOf("ගේදරා")::contains))) // 3 changes
        assertEquals(2, SinglishAutoCorrection.MAX_COST)
    }

    @Test fun `words shorter than three code points are not corrected`() {
        assertEquals(3, SinglishAutoCorrection.MIN_LENGTH)
        val two = "මඩ" // 2 code points, with a valid one-change spelling
        assertNull(SinglishAutoCorrection.choose(two, SinglishCandidates.candidates(two, setOf("මද")::contains)))
        val three = "ගඩර" // 3 code points
        assertEquals("ගදර", SinglishAutoCorrection.choose(three, SinglishCandidates.candidates(three, setOf("ගදර")::contains)))
    }

    @Test fun `words with digits are not corrected`() {
        val typed = "ගෙඩර1"
        assertNull(SinglishAutoCorrection.choose(typed, listOf(SinglishCandidates.Candidate("ගෙදර1", 1))))
    }

    @Test fun `unknown words and words without spellings are kept`() {
        assertNull(correctionFor("zbazbazba")) // no valid spelling
        assertNull(SinglishAutoCorrection.choose("", emptyList()))
        assertNull(SinglishAutoCorrection.choose("abc", SinglishCandidates.candidates("abc", sinhalaWords::contains)))
        assertNull(correctionFor("gedara", isValid = { false }))
    }

    @Test fun `no correction when the generated spellings were cut off at the winning cost`() {
        val long = SinglishTransliterator.transliterate("dhinapothedhiyadhoodhagedharata")
        val variants = SinglishCandidates.variants(long)
        assertEquals(SinglishCandidates.MAX_VARIANTS, variants.size) // the limit was reached ...
        val lastCost = variants.last().cost
        // ... while generating spellings with lastCost changes, so another one with that cost may be missing
        val onlyAtLastCost = variants.last { it.cost == lastCost }.word
        assertNull(SinglishAutoCorrection.choose(long, SinglishCandidates.candidates(long, setOf(onlyAtLastCost)::contains)))
        // a cheaper level was generated completely, so a unique spelling there is fine
        val cheaper = variants.first { it.cost == lastCost - 1 }.word
        assertEquals(cheaper, SinglishAutoCorrection.choose(long, SinglishCandidates.candidates(long, setOf(cheaper)::contains)))
    }

    @Test fun `the decision only uses the given spellings`() {
        // no dictionary access of its own: the same input always gives the same answer
        val typed = "ගෙඩර"
        val candidates = listOf(SinglishCandidates.Candidate("ගෙදර", 1), SinglishCandidates.Candidate("ගේදර", 2))
        repeat(3) { assertEquals("ගෙදර", SinglishAutoCorrection.choose(typed, candidates)) }
        assertEquals("ගෙදර", SinglishAutoCorrection.choose(typed, candidates.reversed()))
    }

    companion object {
        private val sinhalaWords: Set<String> by lazy {
            val file = listOf(File("third_party/sinhala-dictionary/main_si.combined"),
                File("../third_party/sinhala-dictionary/main_si.combined")).first { it.isFile }
            DictIO.readCombinedDictionary(file.path).map { it.mWord }.toHashSet()
        }
    }
}

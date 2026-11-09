// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import helium314.keyboard.latin.makedict.DictIO
import helium314.keyboard.latin.singlish.SinglishCandidates.Candidate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCBBoard Singlish phase 2B stage 1, checked against the bundled Sinhala word list (third_party/sinhala-dictionary). */
class SinglishCandidatesTest {
    private val zwj = "‍"

    private fun dictionaryHas(word: String) = word in sinhalaWords
    private fun candidatesFor(latin: String) = SinglishCandidates.candidates(SinglishTransliterator.transliterate(latin), ::dictionaryHas)
    private fun assertCostsAscending(list: List<Candidate>) =
        assertEquals(list.map { it.cost }.sorted(), list.map { it.cost }, "$list")

    // ---- the six everyday spellings from the phase 2B investigation ----

    @Test fun `gedara finds ගෙදර with one change`() {
        assertEquals("ගෙඩර", SinglishTransliterator.transliterate("gedara")) // phase 1 result, unchanged
        assertFalse(dictionaryHas("ගෙඩර"))
        val candidates = candidatesFor("gedara")
        assertEquals(Candidate("ගෙදර", 1), candidates.first(), "$candidates")
        assertCostsAscending(candidates)
    }

    @Test fun `kohomada keeps the valid typed word first and offers කොහොමද`() {
        assertEquals("කොහොමඩ", SinglishTransliterator.transliterate("kohomada"))
        assertTrue(dictionaryHas("කොහොමඩ")) // the word list has both spellings and no frequencies
        val candidates = candidatesFor("kohomada")
        assertEquals(listOf(Candidate("කොහොමඩ", 0), Candidate("කොහොමද", 1)), candidates.take(2), "$candidates")
        assertCostsAscending(candidates)
    }

    @Test fun `ayubowan finds ආයුබෝවන් with two changes`() {
        assertEquals("අයුබොවන්", SinglishTransliterator.transliterate("ayubowan"))
        val candidates = candidatesFor("ayubowan")
        assertTrue(Candidate("ආයුබෝවන්", 2) in candidates, "$candidates")
        assertTrue(candidates.none { it.cost < 2 }, "$candidates")
        assertCostsAscending(candidates)
    }

    @Test fun `sinhala finds සිංහල with one change for the letter sequence`() {
        assertEquals("සින්හල", SinglishTransliterator.transliterate("sinhala"))
        val candidates = candidatesFor("sinhala")
        assertEquals(Candidate("සිංහල", 1), candidates.first(), "$candidates")
        assertCostsAscending(candidates)
    }

    @Test fun `arthaya finds අර්ථය and keeps the repaya ZWJ`() {
        assertEquals("අර්${zwj}තය", SinglishTransliterator.transliterate("arthaya"))
        assertTrue(dictionaryHas("අර්ථය")) // stored without ZWJ
        assertFalse(dictionaryHas("අර්${zwj}ථය"))
        val candidates = candidatesFor("arthaya")
        assertEquals(Candidate("අර්${zwj}ථය", 1), candidates.first(), "$candidates")
        assertCostsAscending(candidates)
    }

    @Test fun `dhrushti finds දෘෂ්ටි with one change`() {
        assertEquals("දෘශ්ටි", SinglishTransliterator.transliterate("dhrushti"))
        val candidates = candidatesFor("dhrushti")
        assertEquals(Candidate("දෘෂ්ටි", 1), candidates.first(), "$candidates")
        assertCostsAscending(candidates)
    }

    // ---- ambiguity classes and costs ----

    @Test fun `each ambiguity class costs one change`() {
        assertEquals(listOf(Candidate("ද", 0), Candidate("ධ", 1), Candidate("ඩ", 1), Candidate("දා", 1)),
            SinglishCandidates.variants("ද", maxSubstitutions = 1))
        assertEquals(listOf(Candidate("ත", 0), Candidate("ථ", 1), Candidate("ට", 1), Candidate("තා", 1)),
            SinglishCandidates.variants("ත", maxSubstitutions = 1))
        // a changed letter and the inserted ා combine: two changes
        assertEquals(Candidate("ධා", 2), SinglishCandidates.variants("ද").first { it.word == "ධා" })
        assertEquals(listOf(Candidate("අ", 0), Candidate("ආ", 1)), SinglishCandidates.variants("අ"))
        assertEquals(listOf(Candidate("කි", 0), Candidate("කී", 1)), SinglishCandidates.variants("කි"))
        assertEquals(listOf(Candidate("ශ්", 0), Candidate("ෂ්", 1)), SinglishCandidates.variants("ශ්"))
        assertTrue(Candidate("ඹ", 1) in SinglishCandidates.variants("ම්බ"))
        assertTrue(Candidate("ංග", 1) in SinglishCandidates.variants("න්ග"))
    }

    @Test fun `changes combine and the cost counts every change`() {
        val variants = SinglishCandidates.variants("ගෙඩර")
        assertEquals(0, variants.first { it.word == "ගෙඩර" }.cost)
        assertEquals(1, variants.first { it.word == "ගෙදර" }.cost)
        assertEquals(1, variants.first { it.word == "ගේඩර" }.cost)
        assertEquals(2, variants.first { it.word == "ගේදර" }.cost)
        assertEquals(3, variants.first { it.word == "ගේදරා" }.cost)
        assertCostsAscending(variants)
        assertEquals(variants.map { it.word }.distinct(), variants.map { it.word }) // each spelling once
    }

    // ---- limits ----

    @Test fun `at most 64 spellings and 3 changes, cheapest first`() {
        val long = SinglishTransliterator.transliterate("dhinapothedhiyadhoodhagedharata") // many ambiguous letters
        val variants = SinglishCandidates.variants(long)
        assertEquals(SinglishCandidates.MAX_VARIANTS, variants.size)
        assertEquals(Candidate(long, 0), variants.first())
        assertTrue(variants.all { it.cost <= SinglishCandidates.MAX_SUBSTITUTIONS })
        assertCostsAscending(variants)
        // the limit drops expensive spellings first: every single change is still there
        val unlimited = SinglishCandidates.variants(long, maxSubstitutions = 1, maxVariants = Int.MAX_VALUE)
        assertTrue(unlimited.size < SinglishCandidates.MAX_VARIANTS, "${unlimited.size}")
        assertTrue(variants.containsAll(unlimited))
    }

    @Test fun `no more than 3 changes even without the spelling limit`() {
        val variants = SinglishCandidates.variants("ගෙඩර", maxVariants = Int.MAX_VALUE)
        assertEquals(3, variants.maxOf { it.cost })
        assertFalse(variants.any { it.word == "ගේදරා" && it.cost > 3 })
        val oneChange = SinglishCandidates.variants("ගෙඩර", maxSubstitutions = 1)
        assertTrue(oneChange.all { it.cost <= 1 })
        assertFalse(oneChange.any { it.word == "ගේදර" })
    }

    @Test fun `candidates respect the limits too`() {
        val everything: (String) -> Boolean = { true }
        val word = SinglishTransliterator.transliterate("dhinapothedhiyadhoodhagedharata")
        assertEquals(SinglishCandidates.MAX_VARIANTS, SinglishCandidates.candidates(word, everything).size)
        assertTrue(SinglishCandidates.candidates("ගෙඩර", everything, maxSubstitutions = 1).all { it.cost <= 1 })
        assertEquals(listOf(Candidate("ගෙඩර", 0)), SinglishCandidates.candidates("ගෙඩර", everything, maxVariants = 1))
    }

    // ---- ZWJ (phase 2A) ----

    @Test fun `rakaransaya and yansaya keep their ZWJ in every spelling`() {
        for (latin in listOf("prashnaya", "vidhyaa", "shrii")) {
            val word = SinglishTransliterator.transliterate(latin)
            assertTrue(zwj in word, latin)
            assertTrue(SinglishCandidates.variants(word).all { zwj in it.word }, latin)
        }
        assertTrue(dictionaryHas("ප්${zwj}රශ්නය")) // rakaransaya is stored with ZWJ
        assertEquals(Candidate("ප්${zwj}රශ්නය", 0), candidatesFor("prashnaya").first())
        assertEquals(Candidate("විද්${zwj}යා", 0), candidatesFor("vidhyaa").first())
    }

    @Test fun `repaya words stored without ZWJ are found and returned with ZWJ`() {
        assertEquals("කාර්${zwj}ය", SinglishTransliterator.transliterate("kaarya"))
        assertTrue(dictionaryHas("කාර්ය"))
        assertFalse(dictionaryHas("කාර්${zwj}ය"))
        assertEquals(Candidate("කාර්${zwj}ය", 0), candidatesFor("kaarya").first())
        assertEquals(Candidate("කර්${zwj}ම", 0), candidatesFor("karma").first())
        // a dictionary that only knows the form without ZWJ still gives the ZWJ form, once
        val onlyWithoutZwj: (String) -> Boolean = { it == "කර්ම" }
        assertEquals(listOf(Candidate("කර්${zwj}ම", 0)), SinglishCandidates.candidates("කර්${zwj}ම", onlyWithoutZwj))
        assertTrue(SinglishCandidates.candidates("කර්${zwj}ම", ::dictionaryHas).all { "ර්${zwj}" in it.word || "ර්" !in it.word })
    }

    // ---- already valid words, and words without alternatives ----

    @Test fun `already valid words come first with cost 0`() {
        for (latin in listOf("mama", "gama", "kAAma", "amma")) {
            val word = SinglishTransliterator.transliterate(latin)
            assertTrue(dictionaryHas(word), "$latin $word")
            val candidates = candidatesFor(latin)
            assertEquals(Candidate(word, 0), candidates.first(), latin)
            assertCostsAscending(candidates)
        }
    }

    @Test fun `words without alternatives`() {
        // nothing ambiguous: only the word itself
        assertEquals(listOf(Candidate("ක්", 0)), SinglishCandidates.variants("ක්"))
        assertEquals(listOf(Candidate("abc", 0)), SinglishCandidates.variants("abc"))
        assertEquals(emptyList(), SinglishCandidates.candidates("abc", ::dictionaryHas))
        assertEquals(emptyList(), SinglishCandidates.candidates("", ::dictionaryHas))
        assertEquals(emptyList(), SinglishCandidates.variants("ක්", maxVariants = 0))
        // ambiguous letters, but no spelling is a word
        val nonsense = SinglishTransliterator.transliterate("zbazbazba")
        assertTrue(SinglishCandidates.variants(nonsense).size > 1)
        assertEquals(emptyList(), SinglishCandidates.candidates(nonsense, ::dictionaryHas))
    }

    @Test fun `the transliterated word itself is never changed`() {
        for (latin in listOf("gedara", "kohomada", "ayubowan", "sinhala", "arthaya", "dhrushti", "mama")) {
            val word = SinglishTransliterator.transliterate(latin)
            assertEquals(Candidate(word, 0), SinglishCandidates.variants(word).first(), latin)
            assertEquals(1, SinglishCandidates.variants(word).count { it.cost == 0 }, latin)
        }
    }

    companion object {
        private val sinhalaWords: Set<String> by lazy {
            val file = listOf(File("third_party/sinhala-dictionary/main_si.combined"),
                File("../third_party/sinhala-dictionary/main_si.combined")).first { it.isFile }
            DictIO.readCombinedDictionary(file.path).map { it.mWord }.toHashSet()
        }
    }
}

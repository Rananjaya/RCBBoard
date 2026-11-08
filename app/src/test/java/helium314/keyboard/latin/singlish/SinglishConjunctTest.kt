// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.makedict.DictIO
import java.io.File
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 2A: rakaransaya, yansaya, repaya, vocalic ෘ ෲ and ක්ෂ, checked as exact code points.
 * Expectations are built from named code points, not from Sinhala literals, so a missing or extra
 * ZWJ (U+200D) can not hide in a string that looks the same.
 */
class SinglishConjunctTest {
    private val ka = 0x0D9A; private val ga = 0x0D9C; private val ta = 0x0DA7; private val tha = 0x0DAD
    private val thha = 0x0DAE; private val dha = 0x0DAF; private val na = 0x0DB1; private val pa = 0x0DB4
    private val ma = 0x0DB8; private val ya = 0x0DBA; private val ra = 0x0DBB; private val la = 0x0DBD
    private val va = 0x0DC0; private val sha = 0x0DC1; private val ssa = 0x0DC2; private val ha = 0x0DC4
    private val nna = 0x0DAB; private val a = 0x0D85; private val i = 0x0D89; private val anusvara = 0x0D82
    private val aaSign = 0x0DCF; private val iSign = 0x0DD2; private val iiSign = 0x0DD3; private val uSign = 0x0DD4
    private val vocalicR = 0x0DD8; private val vocalicRr = 0x0DF2
    private val hal = 0x0DCA; private val zwj = 0x200D

    private fun cps(s: String) = s.codePoints().toArray().toList()
    private fun hex(l: List<Int>) = l.joinToString(" ") { "U+%04X".format(it) }

    private fun assertCodePoints(input: String, vararg expected: Int) {
        val out = SinglishTransliterator.transliterate(input)
        assertEquals(hex(expected.toList()), hex(cps(out)), "input \"$input\" gave $out")
        assertEquals(Normalizer.normalize(out, Normalizer.Form.NFC), out, "not NFC: $input")
    }

    // ---- rule 1: rakaransaya ----

    @Test fun `rule 1 rakaransaya`() {
        assertCodePoints("kra", ka, hal, zwj, ra) // ක්‍ර
        assertCodePoints("prashnaya", pa, hal, zwj, ra, sha, hal, na, ya) // ප්‍රශ්නය
        assertCodePoints("shrii", sha, hal, zwj, ra, iiSign) // ශ්‍රී
        assertCodePoints("indhra", i, na, hal, dha, hal, zwj, ra) // ඉන්ද්‍ර
        assertCodePoints("thri", tha, hal, zwj, ra, iSign) // ත්‍රි
    }

    // ---- rule 2: yansaya ----

    @Test fun `rule 2 yansaya`() {
        assertCodePoints("vidhyaa", va, iSign, dha, hal, zwj, ya, aaSign) // විද්‍යා
        assertCodePoints("anya", a, na, hal, zwj, ya) // අන්‍ය
        assertCodePoints("kya", ka, hal, zwj, ya)
        assertCodePoints("thya", tha, hal, zwj, ya)
        assertCodePoints("aya", a, ya) // after a vowel y is the letter ය
        assertCodePoints("aayuboowan".take(4), 0x0D86, ya, uSign) // ආයු: Phase 1 example unchanged
    }

    // ---- rule 3: repaya ----

    @Test fun `rule 3 repaya`() {
        assertCodePoints("karma", ka, ra, hal, zwj, ma) // කර්‍ම
        assertCodePoints("kaarya", ka, aaSign, ra, hal, zwj, ya) // කාර්‍ය
        assertCodePoints("arthhaya", a, ra, hal, zwj, thha, ya) // අර්‍ථය (ථ is thh)
        assertCodePoints("arthaya", a, ra, hal, zwj, tha, ya) // th is dental ත, see docs/singlish.md
        assertCodePoints("dharmaya", dha, ra, hal, zwj, ma, ya)
    }

    @Test fun `r keeps its other meanings`() {
        assertCodePoints("kar", ka, ra, hal) // word end: ර්, no repaya
        assertCodePoints("ra", ra)
        assertCodePoints("ruwan", ra, uSign, va, na, hal) // රුවන්: r at the start is ර
        assertCodePoints("kuru", ka, uSign, ra, uSign) // after a vowel ru is රු, not ෘ
        assertCodePoints("gedhara", ga, 0x0DD9, dha, ra) // Phase 1 example unchanged
    }

    // ---- rule 4: vocalic ෘ ෲ ----

    @Test fun `rule 4 vocalic r signs`() {
        assertCodePoints("kruthi", ka, vocalicR, tha, iSign) // කෘති
        assertCodePoints("gruha", ga, vocalicR, ha) // ගෘහ
        assertCodePoints("dhruShti", dha, vocalicR, ssa, hal, ta, iSign) // දෘෂ්ටි (ෂ is Sh/S)
        assertCodePoints("dhrushti", dha, vocalicR, sha, hal, ta, iSign) // sh is ශ, see docs/singlish.md
        assertCodePoints("kru", ka, vocalicR)
        assertCodePoints("kruu", ka, vocalicRr) // ruu before ru: කෲ
    }

    // ---- rule 5: ksh ----

    @Test fun `rule 5 ksh`() {
        assertCodePoints("akshara", a, ka, hal, ssa, ra) // අක්ෂර
        assertCodePoints("kshaNa", ka, hal, ssa, nna) // ක්ෂණ
        assertCodePoints("ksh", ka, hal, ssa, hal)
        assertCodePoints("kSha", ka, hal, ssa) // the Phase 1 spelling still works
        assertCodePoints("ks", ka, hal, 0x0DC3, hal) // k + s is not ksh
    }

    // ---- rule 6: explicit R and Y ----

    @Test fun `rule 6 explicit R and Y`() {
        assertCodePoints("kRu", ka, hal, zwj, ra, uSign) // ක්‍රු: R keeps rakaransaya before u
        assertCodePoints("kYa", ka, hal, zwj, ya)
        assertCodePoints("kRa", ka, hal, zwj, ra)
        assertCodePoints("kaRma", ka, ra, hal, zwj, ma)
        assertEquals(SinglishTransliterator.transliterate("kra"), SinglishTransliterator.transliterate("kRa"))
        assertEquals(SinglishTransliterator.transliterate("kya"), SinglishTransliterator.transliterate("kYa"))
        assertEquals(SinglishTransliterator.transliterate("karma"), SinglishTransliterator.transliterate("kaRma"))
    }

    // ---- several conjuncts in one word ----

    @Test fun `multiple conjuncts in one word`() {
        // ශ්‍රීලංකාව
        assertCodePoints("shriilaxkaava", sha, hal, zwj, ra, iiSign, la, anusvara, ka, aaSign, va)
        // විද්‍යාර්‍ථියා: yansaya and repaya
        assertCodePoints("vidhyaarthhiyaa", va, iSign, dha, hal, zwj, ya, aaSign, ra, hal, zwj, thha, iSign, ya, aaSign)
        // ප්‍රශ්නය + ක්‍රම: two rakaransaya
        assertCodePoints("prakramaya", pa, hal, zwj, ra, ka, hal, zwj, ra, ma, ya)
        // අක්ෂර + ෘ
        assertCodePoints("kruShikarma", ka, vocalicR, ssa, iSign, ka, ra, hal, zwj, ma)
    }

    // ---- composing: every prefix is a complete, valid sequence ----

    @Test fun `backspace prefixes reconvert cleanly`() {
        // what the composing text shows after each backspace (the combiner reconverts the shorter Latin text)
        assertCodePoints("kra", ka, hal, zwj, ra); assertCodePoints("kr", ka, hal, zwj, ra, hal); assertCodePoints("k", ka, hal)
        assertCodePoints("kya", ka, hal, zwj, ya); assertCodePoints("ky", ka, hal, zwj, ya, hal)
        assertCodePoints("karma", ka, ra, hal, zwj, ma); assertCodePoints("karm", ka, ra, hal, zwj, ma, hal)
        assertCodePoints("kar", ka, ra, hal); assertCodePoints("ka", ka)
        assertCodePoints("kruu", ka, vocalicRr); assertCodePoints("kru", ka, vocalicR); assertCodePoints("kr", ka, hal, zwj, ra, hal)
        assertCodePoints("ksh", ka, hal, ssa, hal); assertCodePoints("ks", ka, hal, 0x0DC3, hal)
        // a ZWJ is never left dangling at the end of a prefix
        for (word in listOf("prashnaya", "vidhyaarthhiyaa", "shriilaxkaava", "kaarya", "akshara", "kruthi")) {
            for (end in 1..word.length) {
                val out = SinglishTransliterator.transliterate(word.take(end))
                assertFalse(out.endsWith("‍"), "${word.take(end)} -> ${hex(cps(out))}")
                assertFalse(out.contains("‍‍"), word.take(end))
                assertTrue(out.split("‍").drop(1).all { it.isNotEmpty() && it[0] in 'ක'..'ෆ' },
                    "ZWJ must be followed by a consonant: ${word.take(end)} -> ${hex(cps(out))}")
            }
        }
    }

    // ---- rule 7: dictionary compatibility for repaya ----

    @Test fun `repaya ZWJ is removed only for repaya`() {
        assertEquals("කර්ම", SinglishRepaya.withoutRepayaZwj("කර්‍ම"))
        assertEquals("කාර්ය", SinglishRepaya.withoutRepayaZwj("කාර්‍ය"))
        // rakaransaya and yansaya keep their ZWJ, the word list stores them with it
        assertEquals("ක්‍ර", SinglishRepaya.withoutRepayaZwj("ක්‍ර"))
        assertEquals("ක්‍ය", SinglishRepaya.withoutRepayaZwj("ක්‍ය"))
        assertEquals("ශ්‍රී", SinglishRepaya.withoutRepayaZwj("ශ්‍රී"))
        assertEquals("mama", SinglishRepaya.withoutRepayaZwj("mama"))
    }

    @Test fun `repaya ZWJ is added only for repaya`() {
        assertEquals("කර්‍ම", SinglishRepaya.withRepayaZwj("කර්ම"))
        assertEquals("කර්‍ම", SinglishRepaya.withRepayaZwj("කර්‍ම")) // already correct
        assertEquals("ගෙදර්", SinglishRepaya.withRepayaZwj("ගෙදර්")) // final ර් is not repaya
        assertEquals("ක්‍ර", SinglishRepaya.withRepayaZwj("ක්‍ර"))
        assertEquals("color", SinglishRepaya.withRepayaZwj("color"))
        for (w in listOf("karma", "kaarya", "dharmaya", "vidhyaarthhiyaa")) {
            val typed = SinglishTransliterator.transliterate(w)
            assertEquals(typed, SinglishRepaya.withRepayaZwj(SinglishRepaya.withoutRepayaZwj(typed)), w)
        }
    }

    @Test fun `repaya words typed in Singlish match the bundled dictionary through the lookup form`() {
        val dictionary = DictIO.readCombinedDictionary(sinhalaCombined().path)
        // dhh: ධර්මය; vidhyaarthha: විද්‍යාර්ථ keeps the yansaya ZWJ and loses only the repaya ZWJ
        for (w in listOf("karma", "dhharmaya", "kaaryaya", "arthhaya", "vidhyaarthha")) {
            val typed = SinglishTransliterator.transliterate(w)
            val lookup = SinglishRepaya.withoutRepayaZwj(typed)
            assertTrue(typed.contains("ර්‍"), "$w should contain repaya with ZWJ: ${hex(cps(typed))}")
            assertFalse(dictionary.hasWord(typed), "$w: the word list has no ZWJ repaya form of $typed")
            assertTrue(dictionary.hasWord(lookup), "$w: $lookup (without ZWJ) should be in main_si")
        }
        // rakaransaya and yansaya words match directly, the word list uses ZWJ for them
        for (w in listOf("prashnaya", "shrii", "vidhyaava")) {
            val typed = SinglishTransliterator.transliterate(w)
            assertTrue(dictionary.hasWord(typed), "$w -> $typed should be in main_si")
        }
        // vocalic ෘ and ක්ෂ words
        for (w in listOf("kruthi", "gruha", "akshara", "kshaNika")) { // ක්ෂණික (the list has no ක්ෂණය)
            val typed = SinglishTransliterator.transliterate(w)
            assertTrue(dictionary.hasWord(typed), "$w -> $typed should be in main_si")
        }
    }

    @Test fun `compatible suggestions add the lookup results and show them with ZWJ`() {
        fun info(word: String, score: Int) = SuggestedWordInfo(word, "", score, SuggestedWordInfo.KIND_CORRECTION,
            Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        val typed = SinglishTransliterator.transliterate("karma") // කර්‍ම
        var lookedUp: String? = null
        val result = SinglishRepaya.compatibleSuggestions(typed, listOf(info("කරම", 100))) { word ->
            lookedUp = word
            listOf(info("කර්ම", 300), info("කර්මය", 200), info("කරම", 50))
        }
        assertEquals("කර්ම", lookedUp) // the dictionary was asked for the form without ZWJ
        assertEquals(listOf("කර්‍ම", "කර්‍මය", "කරම"), result.map { it.mWord })
        assertEquals(listOf(300, 200, 100), result.map { it.mScore }) // best score kept for the duplicate
        assertTrue(result.none { it.mWord.contains("ර්") && !it.mWord.contains("ර්‍") })
    }

    @Test fun `compatible suggestions do not run a second lookup without repaya`() {
        var calls = 0
        val result = SinglishRepaya.compatibleSuggestions(SinglishTransliterator.transliterate("kra"), emptyList()) {
            calls++; emptyList()
        }
        assertEquals(0, calls)
        assertTrue(result.isEmpty())
    }

    private fun sinhalaCombined(): File =
        listOf(File("../third_party/sinhala-dictionary/main_si.combined"), File("third_party/sinhala-dictionary/main_si.combined"))
            .first { it.isFile }
}

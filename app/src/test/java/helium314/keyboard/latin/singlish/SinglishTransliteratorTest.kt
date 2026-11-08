// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import helium314.keyboard.latin.makedict.DictIO
import java.io.File
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCBBoard Singlish rules, see docs/singlish.md. Plain JVM test, no Android needed. */
class SinglishTransliteratorTest {
    private val hal = "්"
    private val zwj = "‍"

    private fun assertSinglish(expected: String, input: String) {
        val actual = SinglishTransliterator.transliterate(input)
        assertEquals(codePoints(expected), codePoints(actual), "input \"$input\": expected $expected, got $actual")
    }

    private fun codePoints(s: String) = s.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }

    // ---- the examples from the feature request (phase 1 spellings) ----

    @Test fun `phase 1 example words`() {
        assertSinglish("මම", "mama")
        assertSinglish("ගෙදර", "gedhara")
        assertSinglish("කොහොමද", "kohomadha")
        assertSinglish("ආයුබෝවන්", "aayuboowan")
        assertSinglish("සිංහල", "sixhala")
    }

    @Test fun `phase 1 example words are in the bundled Sinhala dictionary`() {
        val dictionary = DictIO.readCombinedDictionary(sinhalaCombined().path)
        for (input in listOf("mama", "gedhara", "kohomadha", "aayuboowan", "sixhala")) {
            val word = SinglishTransliterator.transliterate(input)
            assertTrue(dictionary.hasWord(word), "$input -> $word is not in main_si")
        }
    }

    /** Documents what phase 1 does with the intuitive spellings; phase 2 candidates should fix these. */
    @Test fun `intuitive spellings follow the rules literally in phase 1`() {
        assertSinglish("ගෙඩර", "gedara") // d is retroflex ඩ; dental ද is dh
        assertSinglish("කොහොමඩ", "kohomada")
        assertSinglish("අයුබොවන්", "ayubowan") // long vowels must be typed
        assertSinglish("සින්හල", "sinhala") // ං is x
    }

    // ---- vowels ----

    @Test fun `standalone vowels`() {
        assertSinglish("අ", "a"); assertSinglish("ආ", "aa")
        assertSinglish("ඇ", "A"); assertSinglish("ඈ", "Aa"); assertSinglish("ඈ", "AA")
        assertSinglish("ඉ", "i"); assertSinglish("ඊ", "ii")
        assertSinglish("උ", "u"); assertSinglish("ඌ", "uu")
        assertSinglish("එ", "e"); assertSinglish("ඒ", "ee")
        assertSinglish("ඔ", "o"); assertSinglish("ඕ", "oo")
        assertSinglish("ඓ", "ai"); assertSinglish("ඖ", "au")
    }

    @Test fun `vowel signs after a consonant`() {
        assertSinglish("ක", "ka"); assertSinglish("කා", "kaa")
        assertSinglish("කැ", "kA"); assertSinglish("කෑ", "kAa"); assertSinglish("කෑ", "kAA")
        assertSinglish("කි", "ki"); assertSinglish("කී", "kii")
        assertSinglish("කු", "ku"); assertSinglish("කූ", "kuu")
        assertSinglish("කෙ", "ke"); assertSinglish("කේ", "kee")
        assertSinglish("කො", "ko"); assertSinglish("කෝ", "koo")
        assertSinglish("කෛ", "kai"); assertSinglish("කෞ", "kau")
    }

    @Test fun `short and long vowels differ`() {
        assertSinglish("මල", "mala"); assertSinglish("මාල", "maala")
        assertSinglish("ගිය", "giya"); assertSinglish("ගීය", "giiya")
        assertSinglish("පොත", "potha"); assertSinglish("පෝත", "pootha")
    }

    @Test fun `vowel after a vowel is independent`() {
        assertSinglish("කඑ", "kae") // there is no "ae" key, ඇ is A
        assertSinglish("ගොඉ", "goi")
    }

    // ---- consonants ----

    @Test fun `every consonant with inherent vowel and with hal`() {
        for (rule in SinglishRules.rules.filter { it.kind == SinglishRules.Kind.CONSONANT }) {
            assertSinglish(rule.letter, rule.key + "a")
            assertSinglish(rule.letter + hal, rule.key)
        }
    }

    @Test fun `plain consonants`() {
        assertSinglish("ක", "ka"); assertSinglish("ග", "ga"); assertSinglish("ච", "cha"); assertSinglish("ජ", "ja")
        assertSinglish("න", "na"); assertSinglish("ප", "pa"); assertSinglish("බ", "ba"); assertSinglish("ම", "ma")
        assertSinglish("ය", "ya"); assertSinglish("ර", "ra"); assertSinglish("ල", "la"); assertSinglish("ව", "va")
        assertSinglish("ව", "wa"); assertSinglish("ස", "sa"); assertSinglish("හ", "ha"); assertSinglish("ෆ", "fa")
    }

    @Test fun `aspirated consonants`() {
        assertSinglish("ඛ", "kha"); assertSinglish("ඝ", "gha"); assertSinglish("ඡ", "chha"); assertSinglish("ඣ", "jha")
        assertSinglish("ථ", "thha"); assertSinglish("ධ", "dhha"); assertSinglish("ඵ", "pha"); assertSinglish("භ", "bha")
    }

    @Test fun `retroflex and dental consonants`() {
        assertSinglish("ට", "ta"); assertSinglish("ඨ", "Ta"); assertSinglish("ත", "tha")
        assertSinglish("ඩ", "da"); assertSinglish("ඪ", "Da"); assertSinglish("ද", "dha")
        assertSinglish("න", "na"); assertSinglish("ණ", "Na")
        assertSinglish("ල", "la"); assertSinglish("ළ", "La")
        assertSinglish("ස", "sa"); assertSinglish("ශ", "sha"); assertSinglish("ෂ", "Sa"); assertSinglish("ෂ", "Sha")
    }

    @Test fun `prenasalised consonants and other nasals`() {
        assertSinglish("ඟ", "zga"); assertSinglish("ඦ", "zja"); assertSinglish("ඬ", "zda"); assertSinglish("ඳ", "zdha")
        assertSinglish("ඹ", "zba"); assertSinglish("ඹ", "Ba"); assertSinglish("ඤ", "zka"); assertSinglish("ඥ", "zha")
        assertSinglish("ඞ", "Xa")
        assertSinglish("හඳ", "hazdha") // moon
        assertSinglish("අඹ", "aBa") // mango
    }

    @Test fun `consonant clusters get hal`() {
        assertSinglish("ක්ක්", "kk")
        assertSinglish("අම්ම", "amma")
        assertSinglish("පන්සල", "pansala")
    }

    // ---- signs and conjuncts ----

    @Test fun `anusvara and visarga`() {
        assertSinglish("ං", "x")
        assertSinglish("කං", "kx") // after a consonant: inherent vowel + ං
        assertSinglish("කං", "kax")
        assertSinglish("සිංහල", "sixhala")
        assertSinglish("දුඃඛ", "dhuHkha")
        assertSinglish("ඃ", "H")
    }

    @Test fun `yansaya`() {
        assertSinglish("ක$hal${zwj}ය", "kYa")
        assertSinglish("විද$hal${zwj}යා", "vidhYaa")
        assertSinglish("ය", "Ya") // no consonant before: plain ය
    }

    @Test fun `rakaransaya`() {
        assertSinglish("ප$hal${zwj}ර", "pRa")
        assertSinglish("ශ$hal${zwj}රී", "shRii")
        assertSinglish("ක$hal${zwj}රු", "kRu")
    }

    @Test fun `repaya`() {
        assertSinglish("කර$hal${zwj}ම", "kaRma") // R after a vowel, before a consonant
        assertSinglish("කර${hal}ම", "karma") // plain r gives ර් without ZWJ, as in the word list
        assertSinglish("ර", "Ra") // R before a vowel is ර
        assertSinglish("අර", "aRa")
        assertSinglish("ර$hal", "R") // R at the end is ර්
    }

    // ---- longest match ----

    @Test fun `longest match`() {
        assertSinglish("ඳ", "zdha"); assertSinglish("ඬ", "zda"); assertSinglish("ද", "dha"); assertSinglish("ඩ", "da")
        assertSinglish("ධ", "dhha")
        assertSinglish("ථ", "thha"); assertSinglish("ත", "tha"); assertSinglish("ට", "ta")
        assertSinglish("ඡ", "chha"); assertSinglish("ච", "cha")
        assertSinglish("ශ", "sha"); assertSinglish("ශ$hal", "sh") // sh is one letter, not ස් + හ
        assertSinglish("ආ", "aa"); assertSinglish("ආඅ", "aaa")
        assertSinglish("ඈ", "Aa"); assertSinglish("ඇ", "A")
        assertSinglish("ක$hal${zwj}ය", "kYa"); assertSinglish("ක${hal}ය", "kya") // Y is yansaya, y is ය
    }

    @Test fun `capital letters without their own rule type like lowercase`() {
        assertSinglish("මම", "Mama")
        assertSinglish("ගෙදර", "Gedhara")
        assertSinglish("ක", "Ka")
        // capitals with their own meaning keep it
        assertSinglish("ඨ", "Ta"); assertSinglish("ළ", "La"); assertSinglish("ණ", "Na")
    }

    // ---- boundaries, punctuation, numbers, other characters ----

    @Test fun `word boundaries`() {
        assertSinglish("මම ගෙදර", "mama gedhara")
        assertSinglish("කොහොමද?", "kohomadha?")
        assertSinglish("මම, ඔයා.", "mama, oyaa.")
        assertSinglish("ගෙදර් ", "gedhar ") // hal before the space
    }

    @Test fun `numbers and punctuation pass through unchanged`() {
        assertSinglish("2026", "2026")
        assertSinglish("ක1", "ka1")
        assertSinglish("ක්1", "k1")
        assertSinglish("!?.,;:-'\"()", "!?.,;:-'\"()")
        assertSinglish("මම-2", "mama-2")
    }

    @Test fun `unknown characters pass through`() {
        assertSinglish("c", "c"); assertSinglish("q", "q"); assertSinglish("z", "z")
        assertSinglish("ක්c", "kc")
        assertSinglish("€", "€")
        assertSinglish("😀", "😀")
        assertSinglish("කඅ", "කa") // existing Sinhala text is kept
    }

    @Test fun `empty input`() {
        assertSinglish("", "")
    }

    @Test fun `output is NFC normalised and uses the precomposed vowel signs`() {
        for (input in listOf("kee", "koo", "kau", "kAa", "shRii", "aayuboowan", "kohomadha", "vidhYaa")) {
            val out = SinglishTransliterator.transliterate(input)
            assertEquals(Normalizer.normalize(out, Normalizer.Form.NFC), out, input)
        }
        assertEquals(listOf(0x0D9A, 0x0DDA), SinglishTransliterator.transliterate("kee").codePoints().toArray().toList())
        assertEquals(listOf(0x0D9A, 0x0DDD), SinglishTransliterator.transliterate("koo").codePoints().toArray().toList())
        assertEquals(listOf(0x0D9A, 0x0DDE), SinglishTransliterator.transliterate("kau").codePoints().toArray().toList())
    }

    @Test fun `output is deterministic`() {
        val inputs = listOf("mama", "gedhara", "kohomadha", "aayuboowan", "sixhala", "kaRma")
        val first = inputs.map { SinglishTransliterator.transliterate(it) }
        repeat(3) { assertEquals(first, inputs.map { SinglishTransliterator.transliterate(it) }) }
    }

    @Test fun `only ASCII letters are collected as Singlish letters`() {
        for (c in ('a'..'z') + ('A'..'Z')) assertTrue(SinglishTransliterator.isSinglishLetter(c.code), "$c")
        for (c in listOf('0', '9', ' ', '.', ',', '\'', '-', 'é', 'ක')) assertFalse(SinglishTransliterator.isSinglishLetter(c.code), "$c")
    }

    // ---- the documented table is the implemented table ----

    @Test fun `docs singlish table matches the rules`() {
        val documented = Regex("^\\| `([^`]+)` \\| (\\S+) \\|").let { regex ->
            singlishDoc().readLines().mapNotNull { line -> regex.find(line)?.let { it.groupValues[1] to it.groupValues[2] } }
        }.toMap()
        val implemented = SinglishRules.rules.associate { it.key to it.letter }
        assertEquals(implemented.keys.sorted(), documented.keys.sorted(), "keys in docs/singlish.md")
        for ((key, letter) in implemented) assertEquals(letter, documented[key], "letter for `$key` in docs/singlish.md")
    }

    @Test fun `rule keys are unique and ASCII`() {
        assertEquals(SinglishRules.rules.size, SinglishRules.rules.map { it.key }.toSet().size)
        assertTrue(SinglishRules.rules.all { r -> r.key.all { SinglishTransliterator.isSinglishLetter(it.code) } })
    }

    private fun singlishDoc(): File =
        listOf(File("../docs/singlish.md"), File("docs/singlish.md")).first { it.isFile }

    private fun sinhalaCombined(): File =
        listOf(File("../third_party/sinhala-dictionary/main_si.combined"), File("third_party/sinhala-dictionary/main_si.combined"))
            .first { it.isFile }
}

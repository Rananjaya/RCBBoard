// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.compat.CompatBreakIterator
import helium314.keyboard.latin.App
import helium314.keyboard.latin.common.WordBreakIterator
import helium314.keyboard.latin.common.getTouchedWordRange
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RCBBoard: baseline (stage 0 of the upstream Kotlin migration) for the word at the cursor in Sinhala text:
 * vowel signs and hal kirīma (combining marks, not letters), anusvara, the ZWJ of rakaransaya, digits and
 * punctuation. Records the current behaviour, so that the upstream changes to word boundaries
 * (87b6e5dd) and TextRange -> TextAtCursor (4f15ec84) show up as test failures that need a decision.
 * Some entries record known limitations; they are marked as such.
 */
@RunWith(RobolectricTestRunner::class)
class SinhalaWordBoundaryTest {
    private val zwj = "‍"
    private val si = Locale.forLanguageTag("si-LK")
    private val sp = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, si)

    /** The iterator InputLogic uses for the word at the cursor (via RichInputConnection / getTouchedWordRange). */
    private fun spacingBounds(before: String, after: String): Pair<Int, Int> =
        WordBreakIterator.getForSpacingAndPunctuations(sp).also { it.text = before + after }.getTouchedWordBounds(before.length)

    /** The default word iterator, whose getTouchedWordBounds upstream 87b6e5dd changes. */
    private fun defaultBounds(before: String, after: String): Pair<Int, Int> =
        CompatBreakIterator.getWordInstance(si).also { it.text = before + after }.getTouchedWordBounds(before.length)

    /** The word range InputLogic gets from RichInputConnection, as (start, end). */
    private fun touchedRange(before: String, after: String): Pair<Int, Int> {
        val range = getTouchedWordRange(before, after, sp)
        return (before.length - range.numberOfCharsInWordBeforeCursor) to (before.length + range.numberOfCharsInWordAfterCursor)
    }

    private fun assertBoth(before: String, after: String, expected: Pair<Int, Int>) {
        assertEquals(expected, spacingBounds(before, after), "[$before|$after]")
        assertEquals(expected, touchedRange(before, after), "[$before|$after] (touched word range)")
    }

    @Test fun `word with vowel signs and hal is one word wherever the cursor is`() {
        assertBoth("", "අපි", 0 to 3)
        assertBoth("අ", "පි ගෙදර", 0 to 3)
        assertBoth("අප", "ි ගෙදර", 0 to 3) // between the consonant and its vowel sign
        assertBoth("අපි", " ගෙදර", 0 to 3) // after a word that ends with a vowel sign
        assertBoth("කර්", " යමු", 0 to 3) // after a word that ends with hal kirīma
        assertBoth("කර", "් යමු", 0 to 3)
        assertBoth("අපි ", "ගෙදර", 4 to 8)
    }

    @Test fun `anusvara stays inside the word`() {
        assertBoth("සිං", "හල", 0 to 5)
        assertBoth("සි", "ංහල", 0 to 5)
    }

    @Test fun `punctuation, digits and latin text end a sinhala word`() {
        assertBoth("අපි,", " ගෙදර", 4 to 4)
        assertBoth("ගෙදර.", "", 5 to 5)
        assertBoth("අපි1", "23", 4 to 4)
        assertBoth("abc ", "අපි", 4 to 7)
        assertBoth("abcඅ", "පි", 3 to 6) // latin and sinhala letters are separate words
    }

    @Test fun `known limitation - the ZWJ of rakaransaya splits the word for InputLogic`() {
        // ක්‍රම is one word, but the spacing-and-punctuation iterator stops at the ZWJ
        assertBoth("ක්${zwj}ර", "ම", 3 to 5)
        assertBoth("ක්${zwj}", "රම", 3 to 5)
        assertBoth("ක්", "${zwj}රම", 0 to 2)
    }

    @Test fun `default word iterator - current behaviour at word ends and with ZWJ`() {
        // the default iterator treats ක්‍රම as one word ...
        assertEquals(0 to 5, defaultBounds("ක්${zwj}ර", "ම"))
        assertEquals(0 to 5, defaultBounds("ක්", "${zwj}රම"))
        assertEquals(0 to 3, defaultBounds("අ", "පි ගෙදර"))
        assertEquals(0 to 5, defaultBounds("සි", "ංහල"))
        // at the start and the end of the text it finds the word, also when it ends with a vowel sign or hal
        // (since upstream 87b6e5dd; before, it reported no word at the start and after such word ends)
        assertEquals(0 to 3, defaultBounds("", "අපි"))
        assertEquals(0 to 3, defaultBounds("අපි", ""))
        assertEquals(0 to 3, defaultBounds("කර්", ""))
        // known limitation: right after a word in the middle of the text it reports no word
        // (87b6e5dd only changes the start and end of the text)
        assertEquals(3 to 3, defaultBounds("අපි", " ගෙදර"))
        assertEquals(3 to 3, defaultBounds("කර්", " යමු"))
        // digits belong to the word for the default iterator
        assertEquals(0 to 6, defaultBounds("අපි1", "23"))
    }
}

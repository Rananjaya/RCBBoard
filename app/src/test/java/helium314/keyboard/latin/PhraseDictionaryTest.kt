// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.dictionary.PhraseDictionary
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** reads the bundled English phrase files (assets/phrases) through PhraseDictionary */
@RunWith(RobolectricTestRunner::class)
class PhraseDictionaryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val uk = PhraseDictionary.create(context, Locale.UK)!!
    private val settings = SettingsValuesForSuggestion(true, false)

    private fun predictions(dictionary: PhraseDictionary, vararg words: String, typed: String = ""): List<String>? {
        val context = NgramContext(*words.reversed().map { NgramContext.WordInfo(it) }.toTypedArray())
        return dictionary.getSuggestions(ComposedData(InputPointers(1), false, typed), context, 0, settings, 0, 1f, null)
            ?.map { it.word }
    }

    @Test fun onlyEnglishLocalesHavePhrases() {
        assertNotNull(PhraseDictionary.create(context, Locale.US))
        assertNull(PhraseDictionary.create(context, Locale.forLanguageTag("si-LK"))) // Sinhala unchanged
        assertNull(PhraseDictionary.create(context, Locale.GERMAN))
    }

    // examples of the M3 brief; they are not rules in the code, the data is counted from sentences
    @Test fun commonPhrasesAreInTheTopThree() {
        mapOf(
            listOf("a", "nice") to "day", listOf("nice", "to") to "meet",
            listOf("how", "are") to "you", listOf("would", "like") to "to", listOf("you", "very") to "much",
            listOf("let", "me") to "know", listOf("soon", "as") to "possible", listOf("forward", "to") to "seeing",
        ).forEach { (context, expected) ->
            val words = predictions(uk, *context.toTypedArray())!!
            assertTrue(expected in words.take(3), "$context -> $words")
        }
    }

    @Test fun capitalisedFirstWordFallsBackToLowerCase() {
        assertEquals(predictions(uk, "how", "are"), predictions(uk, "How", "are"))
    }

    @Test fun nothingWhileTypingOrWithoutTwoWords() {
        assertNull(predictions(uk, "how", "are", typed = "y"))
        assertNull(predictions(uk, "are"))
        assertNull(predictions(uk, "qwxz", "are"))
        val sentenceStart = NgramContext(NgramContext.WordInfo("are"), NgramContext.WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
        assertNull(uk.getSuggestions(ComposedData(InputPointers(1), false, ""), sentenceStart, 0, settings, 0, 1f, null))
    }

    @Test fun phrasesAreNeverWordsOfTheirOwn() {
        assertTrue(!uk.isInDictionary("day"))
    }

    @Test fun britishSpellingInEnglishUk() {
        val us = PhraseDictionary.create(context, Locale.US)!!
        val ukWords = predictions(uk, "my", "favourite").orEmpty() + predictions(uk, "the", "colour").orEmpty()
        val usWords = predictions(us, "my", "favorite").orEmpty() + predictions(us, "the", "color").orEmpty()
        assertTrue(ukWords.isNotEmpty() && usWords.isNotEmpty(), "$ukWords / $usWords")
        assertNull(predictions(uk, "my", "favorite"), "American spelling is not a context in English (UK)")
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.core.content.edit
import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.event.SinglishCombiner
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.makedict.DictIO
import helium314.keyboard.latin.singlish.SinglishCandidates
import helium314.keyboard.latin.singlish.SinglishTransliterator
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWordInfo.Companion.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
import helium314.keyboard.latin.suggestions.SuggestedWordInfo.Companion.KIND_SHORTCUT
import helium314.keyboard.latin.suggestions.SuggestedWordInfo.Companion.KIND_WHITELIST
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("NonAsciiCharacters")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowBinaryDictionaryUtils::class,
    ShadowFacilitator::class,
])
class SuggestTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val suggest get() = latinIME.mInputLogic.mSuggest

    private val confidenceModest = 0.24f
    private val confidenceAggressive = 0.65f
    private val confidenceVeryAggressive = 0.9f

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @BeforeTest fun reset() {
        latinIME.prefs().edit { clear() }
        currentTypingLocale = Locale.ENGLISH
        tapTypingSuggestions = suggestionResults(emptyList())
        glideTypingSuggestions = suggestionResults(emptyList())
        nextWordSuggestions = suggestionResults(emptyList())
        validWords = emptySet()
        validWordLookups = 0
    }

    @Test fun `'on' to 'in' if 'in' was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "on",
            listOf(suggestion("on", 1800000, locale), suggestion("in", 600000, locale)),
            suggestion("in", 240, locale),
            null, // never typed "on" in this context
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first suggestion score is too low
    }

    @Test fun `'ill' to 'I'll' if 'ill' not used before in this context, and I'll is whitelisted`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            null,
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
        // correction because both empty scores are 0, which should be fine (next check is comparing empty scores)
    }

    @Test fun `not 'ill' to 'I'll' if only 'ill' was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            suggestion("ill", 200, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first empty score not high enough
    }

    @Test fun `'ill' to 'I'll' if both have same ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 200, locale),
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no 'ill' to 'I'll' if 'ill' has somewhat better ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 211, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no English 'I' for Polish 'i' when typing in Polish`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale("pl"),
            confidenceVeryAggressive
        )
        assert(!result.last()) // should not be corrected
        // not even checking at modest and aggressive thresholds, this is a locale thing
        // if very aggressive, still no correction because locale matches with typed word only
    }

    @Test fun `English 'I' instead of Polish 'i' when typing in English`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale.ENGLISH,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no English 'in' instead of French 'un' when typing in French`() {
        val result = shouldBeAutoCorrected(
            "un",
            listOf(suggestion("in", Int.MAX_VALUE, Locale.ENGLISH), suggestion("un", 1500000, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because of locale matching
    }

    @Test fun `no 'né' instead of 'ne'`() {
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, Locale.FRENCH), suggestion("né", 1900000-1, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because score is lower
    }

    @Test fun `'né' instead of 'ne' if 'né' in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            null,
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `'né' instead of 'ne' if 'né' has clearly better score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 215, locale),
            suggestion("ne", 200, locale),
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no 'né' instead of 'ne' if both with same score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no 'ne' instead of 'né'`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "né",
            listOf(suggestion("ne", 600000, locale), suggestion("né", 1600000, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not even allowed to check because of low score for ne
    }

    @Test fun `shortcuts might be autocorrected by default`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 700000, locale, true)),
            null,
            null,
            locale,
            confidenceAggressive
        )
        assert(result.last()) // should be corrected

        val result2 = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 300000, locale, true)),
            null,
            null,
            locale,
            confidenceModest
        )
        assert(!result2.last()) // should not be corrected
    }

    @Test fun `shortcuts are not autocorrected when setting is off`() {
        val prefs = latinIME.prefs()
        prefs.edit { putBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, false) }
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 12000000, locale, true)),
            null,
            null,
            locale,
            confidenceAggressive
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `typed word is first suggestion`() { // first suggestion will not be shown to the user
        tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 123, currentTypingLocale)))
        val results = getSuggestedWords(false, "henlo", CapsMode.OFF)
        assertEquals(listOf("henlo", "hello"), results.infoList().map { it.word })

        tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 123, currentTypingLocale)))
        val results2 = getSuggestedWords(false, "hello", CapsMode.OFF)
        assertEquals(listOf("hello"), results2.infoList().map { it.word })
    }

    @Test fun `typed word is second suggestion if autocorrect is pending`() {
        enableAutocorrect(confidenceVeryAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("hello", 650000, currentTypingLocale), // 600000 is the limit
            suggestion("hell", 620000, currentTypingLocale),
            suggestion("hem", 100000, currentTypingLocale),
        ))
        val results = getSuggestedWords(false, "henlo", CapsMode.OFF)
        assert(results.mWillAutoCorrect)
        assertEquals(5, results.infoList().size)
        assertEquals("henlo", results.infoList()[0].word) // typed word, not shown
        assertEquals("hello", results.infoList()[1].word) // autocorrection is first suggestion
        assertEquals("henlo", results.infoList()[2].word) // if autocorrection is pending, typed word is next suggestion
        assertEquals("hell", results.infoList()[3].word)
        assertEquals("hem", results.infoList()[4].word)
    }

    @Test fun `CenterSuggestionTextToEnter has typed text or autocorrect as first suggestion`() {
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("surge", 1000),
            suggestion("sure", 935),
            suggestion("siege", 925),
        ))
        val results = getSuggestedWords(false, "suge", CapsMode.OFF)
        assertEquals(listOf("suge", "surge", "sure", "siege"), results.infoList().map { it.word })

        latinIME.prefs().edit { putBoolean(Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, true) }
        // first suggestion duplicated because the first element in the list is not shown to the user
        val results2 = getSuggestedWords(false, "suge", CapsMode.OFF)
        assertEquals(listOf("suge", "suge", "surge", "sure", "siege"), results2.infoList().map { it.word })
    }

    @Test fun `empty typed word gives next word suggestions`() {
        nextWordSuggestions = suggestionResults(listOf(
            suggestion("another", 24, currentTypingLocale),
            suggestion("next", 123, currentTypingLocale),
        ))
        val results = getSuggestedWords(false, "", CapsMode.OFF)
        assertEquals(listOf("next", "another"), results.infoList().map { it.word })
    }

    @Test fun `single letter suggestions are suppressed dependent on score of other suggestions`() {
        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("a", 100),
            suggestion("next", 95), // more than 94% -> moved to top
        ))
        val results = getSuggestedWords(true, "", CapsMode.OFF)
        assertEquals(listOf("next", "a"), results.infoList().map { it.word })
        assertEquals(2, results.infoList().size)
        assertEquals(93, results.infoList()[1].score) // score is 93% of original

        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("a", 100),
            suggestion("next", 90),
        ))
        val results2 = getSuggestedWords(true, "", CapsMode.OFF)
        assertEquals(listOf("a", "next"), results2.infoList().map { it.word })
    }

    @Test fun `prefer next word suggestions`() {
        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("ab", 1000),
            suggestion("next", 935),
            suggestion("something", 925), // not used because less than 93%
        ))
        nextWordSuggestions = suggestionResults(listOf(
            suggestion("next", 180),
            suggestion("something", 175),
            suggestion("ab", 160), // not used because less than 170
        ))
        val results = getSuggestedWords(true, "", CapsMode.OFF)
        assertEquals(listOf("next", "ab", "something"), results.infoList().map { it.word })
        assertEquals(listOf(935, 1000, 925), results.infoList().map { it.score })

        nextWordSuggestions = suggestionResults(listOf(
            suggestion("next", 180),
            suggestion("something", 175),
            suggestion("ab", 172), // now it's used
        ))
        assertEquals("ab", getSuggestedWords(true, "", CapsMode.OFF).infoList()[0].word)
    }

    @Test fun `single quotes at end are attached to suggestions`() {
        // the single quote is a word connector, so it's part of the typed word if at the end (or in the middle, but we're not interested in that)
        // the suggestions are for the normal word without ', and then the ' is attached
        // this has weird effects, because there are suggestions where the ' does not fit
        //  e.g. for emoji suggestions and languages where ' isn't used in words
        // todo: improve the situation on this!
        //  disable for shortcuts?
        //  maybe also could be removed from word connectors for a bunch of languages

        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("ab", 1000),
            suggestion("next", 935),
            suggestion("something", 925),
            suggestion("🎄", 924),
        ))
        val results = getSuggestedWords(false, "someword'", CapsMode.OFF)
        assertEquals(listOf("someword'", "ab'", "next'", "something'", "🎄'"), results.infoList().map { it.word })
    }

    @Test fun `tap typing suggestions depend on typed word capitalization, ignoring automatic caps modes`() {
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        listOf(CapsMode.OFF, CapsMode.AUTO, CapsMode.AUTO_LOCKED).forEach { mode ->
            // not capitalized -> original suggestions
            assertEquals(listOf("but", "buy", "bit", "😢", "Butter"),
                getSuggestedWords(false, "but", mode).infoList().map { it.word })

            // capitalized -> capitalized suggestions
            assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
                getSuggestedWords(false, "But", mode).infoList().map { it.word })

            // more than one uppercase character, but not all -> original suggestions
            assertEquals(listOf("BuT", "but", "buy", "bit", "😢", "Butter"),
                getSuggestedWords(false, "BuT", mode).infoList().map { it.word })

            // full uppercase typed word -> uppercase suggestions
            assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
                getSuggestedWords(false, "BUT", mode).infoList().map { it.word })
        }
    }

    @Test fun `next word and glide typing suggestions use automatic caps modes`() {
        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))
        assertEquals(listOf("but", "buy", "bit", "😢", "Butter"),
            getSuggestedWords(true, "", CapsMode.OFF).infoList().map { it.word })
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
            getSuggestedWords(true, "", CapsMode.AUTO).infoList().map { it.word })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(true, "", CapsMode.AUTO_LOCKED).infoList().map { it.word })

        nextWordSuggestions = glideTypingSuggestions
        assertEquals(listOf("but", "buy", "bit", "😢", "Butter"),
            getSuggestedWords(false, "", CapsMode.OFF).infoList().map { it.word })
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
            getSuggestedWords(false, "", CapsMode.AUTO).infoList().map { it.word })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(false, "", CapsMode.AUTO_LOCKED).infoList().map { it.word })
    }

    @Test fun `no autocorrect if more than one uppercase character in typed word, but not all uppercase`() {
        enableAutocorrect(confidenceVeryAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 650000),
            suggestion("bit", 620000),
            suggestion("buy", 500000),
            suggestion("😢", 100000),
            suggestion("Butter", 80000),
        ))

        val result = getSuggestedWords(false, "bur", CapsMode.OFF)
        assert(result.mWillAutoCorrect)
        assertEquals(listOf("bur", "but", "bur", "bit", "buy", "😢", "Butter"), result.infoList().map { it.word })

        val result2 = getSuggestedWords(false, "Bur", CapsMode.OFF)
        assert(result2.mWillAutoCorrect)
        assertEquals(listOf("Bur", "But", "Bur", "Bit", "Buy", "😢", "Butter"), result2.infoList().map { it.word })

        val result3 = getSuggestedWords(false, "BUr", CapsMode.OFF)
        assert(!result3.mWillAutoCorrect)
        assertEquals(listOf("BUr", "but", "bit", "buy", "😢", "Butter"), result3.infoList().map { it.word })

        val result4 = getSuggestedWords(false, "BUR", CapsMode.OFF)
        assert(result4.mWillAutoCorrect)
        assertEquals(listOf("BUR", "BUT", "BUR", "BIT", "BUY", "😢", "BUTTER"), result4.infoList().map { it.word })
    }

    @Test fun `suggestions use manual caps modes`() {
        // added in https://github.com/HeliBorg/HeliBoard/pull/1807 / cb0eae695f0cbd061e5bbcc416d6e14d18d869d8
        // manual caps mode can be set any time, even if there are already suggestions
        // on the phone this will give different suggestions because there is different proximityInfo (coming from keyboard, which is different when shifted)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "but", CapsMode.MANUAL)
        assert(!result.mWillAutoCorrect)
        assertEquals(listOf("but", "But", "Buy", "Bit", "😢", "Butter"), result.infoList().map { it.word })

        // caps lock -> uppercase suggestions
        assertEquals(listOf("but", "BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(false, "but", CapsMode.MANUAL_LOCKED).infoList().map { it.word })

        // same for glide typing
        glideTypingSuggestions = tapTypingSuggestions
        val result2 = getSuggestedWords(true, "", CapsMode.MANUAL)
        assert(!result2.mWillAutoCorrect)
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"), result2.infoList().map { it.word })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(true, "", CapsMode.MANUAL_LOCKED).infoList().map { it.word })

        // same for next word suggestions
        nextWordSuggestions = tapTypingSuggestions
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
            getSuggestedWords(false, "", CapsMode.MANUAL).infoList().map { it.word })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(false, "", CapsMode.MANUAL_LOCKED).infoList().map { it.word })
    }

    @Test fun `autocorrect will "correct" capitalization with manual caps modes`() {
        enableAutocorrect(confidenceAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "but", CapsMode.MANUAL)
        assert(result.mWillAutoCorrect)
        assertEquals(listOf("but", "But", "Buy", "Bit", "😢", "Butter"), result.infoList().map { it.word })

        // caps lock -> uppercase suggestions
        val result2 = getSuggestedWords(false, "but", CapsMode.MANUAL_LOCKED)
        assert(result2.mWillAutoCorrect)
        assertEquals(listOf("but", "BUT", "BUY", "BIT", "😢", "BUTTER"), result2.infoList().map { it.word })

        glideTypingSuggestions = tapTypingSuggestions
        val result3 = getSuggestedWords(true, "", CapsMode.MANUAL)
        // mWillAutoCorrect is true on phone, because after glide typing is done, the result is set as typed word
        // when pressing shift to capitalize it, autocorrect will only work if the typed word changes when capitalized. this does not work without typed word
        //assert(result3.mWillAutoCorrect)
        assertEquals("but", result3.typedWordInfo.word)
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"), result3.infoList().map { it.word })
    }

    @Test fun `no caps mode autocorrect if word has many uppercase letters`() {
        // bug report in https://github.com/HeliBorg/HeliBoard/issues/2162
        enableAutocorrect(confidenceAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "buT", CapsMode.MANUAL)
        assert(!result.mWillAutoCorrect)
        assertEquals(listOf("buT", "BuT", "But", "Buy", "Bit", "😢", "Butter"), result.infoList().map { it.word })

        // caps lock -> uppercase suggestions
        val result2 = getSuggestedWords(false, "buT", CapsMode.MANUAL_LOCKED)
        assert(!result2.mWillAutoCorrect)
        assertEquals(listOf("buT", "BUT", "BUY", "BIT", "😢", "BUTTER"), result2.infoList().map { it.word })
    }

    @Test fun `normal autocorrect works with manual caps modes`() {
        enableAutocorrect(confidenceAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 650000),
            suggestion("bit", 620000),
            suggestion("buy", 500000),
            suggestion("😢", 100000),
            suggestion("Butter", 80000),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "bur", CapsMode.MANUAL)
        assert(result.mWillAutoCorrect)
        assertEquals(listOf("bur", "But", "Bur", "Bit", "Buy", "😢", "Butter"), result.infoList().map { it.word })

        // caps lock -> uppercase suggestions
        val result2 = getSuggestedWords(false, "bur", CapsMode.MANUAL_LOCKED)
        assert(result2.mWillAutoCorrect)
        assertEquals(listOf("bur", "BUT", "BUR", "BIT", "BUY", "😢", "BUTTER"), result2.infoList().map { it.word })

        // score too low will correct to capitalized typed word (except if using very aggressive threshold)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 6500),
            suggestion("bit", 6200),
            suggestion("buy", 5000),
            suggestion("😢", 1000),
            suggestion("Butter", 800),
        ))
        val result3 = getSuggestedWords(false, "bur", CapsMode.MANUAL)
        assert(result3.mWillAutoCorrect)
        assertEquals(listOf("bur", "Bur", "But", "Bit", "Buy", "😢", "Butter"), result3.infoList().map { it.word })

        val result4 = getSuggestedWords(false, "bur", CapsMode.MANUAL_LOCKED)
        assert(result4.mWillAutoCorrect)
        assertEquals(listOf("bur", "BUR", "BUT", "BIT", "BUY", "😢", "BUTTER"), result4.infoList().map { it.word })
    }

    // ---- RCBBoard: the "Emoji suggestions" setting (suggest_emojis) ----

    private fun setSuggestEmojis(enabled: Boolean) = latinIME.prefs().edit { putBoolean(Settings.PREF_SUGGEST_EMOJIS, enabled) }
    private fun words(results: SuggestedWords) = results.infoList().map { it.word }

    @Test fun `emoji suggestions are enabled by default on a fresh install`() {
        // reset() cleared all preferences, like a fresh install
        assertTrue(Defaults.PREF_SUGGEST_EMOJIS)
        assertFalse(latinIME.prefs().contains(Settings.PREF_SUGGEST_EMOJIS))
        assertTrue(Settings.getValues().mSuggestEmojis)
    }

    @Test fun `emoji candidates appear while emoji suggestions are enabled`() {
        tapTypingSuggestions = suggestionResults(listOf(suggestion("smile", 300), suggestion("smiled", 200), suggestion("😀", 100)))
        val results = getSuggestedWords(false, "smil", CapsMode.OFF)
        assertTrue("😀" in words(results), "${words(results)}")
    }

    @Test fun `no emoji candidates while emoji suggestions are disabled`() {
        setSuggestEmojis(false)
        assertFalse(Settings.getValues().mSuggestEmojis) // applied at once, no restart
        tapTypingSuggestions = suggestionResults(listOf(suggestion("😀", 500), suggestion("smile", 300), suggestion("❤️", 250), suggestion("smiled", 200)))
        val typing = getSuggestedWords(false, "smil", CapsMode.OFF)
        assertTrue(words(typing).none { isEmoji(it) }, "${words(typing)}")
        assertTrue(words(typing).containsAll(listOf("smile", "smiled")), "${words(typing)}")

        // next word predictions, e.g. an emoji learned in the user history
        nextWordSuggestions = suggestionResults(listOf(suggestion("😂", 300), suggestion("next", 200)))
        val predictions = getSuggestedWords(false, "", CapsMode.OFF)
        assertEquals(listOf("next"), words(predictions))

        // gesture typing
        glideTypingSuggestions = suggestionResults(listOf(suggestion("😀", 500), suggestion("hello", 400), suggestion("hell", 300)))
        val gesture = getSuggestedWords(true, "", CapsMode.OFF)
        assertTrue(words(gesture).none { isEmoji(it) }, "${words(gesture)}")
        assertTrue(words(gesture).containsAll(listOf("hello", "hell")), "${words(gesture)}")
    }

    @Test fun `toggling emoji suggestions off and on applies immediately`() {
        tapTypingSuggestions = suggestionResults(listOf(suggestion("smile", 300), suggestion("😀", 100)))
        assertTrue("😀" in words(getSuggestedWords(false, "smil", CapsMode.OFF)))
        setSuggestEmojis(false)
        assertFalse("😀" in words(getSuggestedWords(false, "smil", CapsMode.OFF)))
        setSuggestEmojis(true)
        assertTrue("😀" in words(getSuggestedWords(false, "smil", CapsMode.OFF)))
        setSuggestEmojis(false)
        assertFalse("😀" in words(getSuggestedWords(false, "smil", CapsMode.OFF)))
    }

    @Test fun `emoji suggestions setting persists across a restart`() {
        setSuggestEmojis(false)
        val restarted = Robolectric.setupService(LatinIME::class.java) // new keyboard service, same stored preferences
        assertFalse(restarted.prefs().getBoolean(Settings.PREF_SUGGEST_EMOJIS, Defaults.PREF_SUGGEST_EMOJIS))
        assertFalse(Settings.getValues().mSuggestEmojis)
        setSuggestEmojis(true)
        Robolectric.setupService(LatinIME::class.java)
        assertTrue(Settings.getValues().mSuggestEmojis)
    }

    @Test fun `English and Sinhala word suggestions are unchanged when emoji suggestions are disabled`() {
        val english = listOf(suggestion("colour", 300), suggestion("color", 200), suggestion("colours", 100))
        val si = Locale.forLanguageTag("si-LK")
        val sinhala = listOf(suggestion("මම", 300, si), suggestion("මට", 200, si), suggestion("මගේ", 100, si))
        for (enabled in listOf(true, false)) {
            setSuggestEmojis(enabled)
            currentTypingLocale = Locale.ENGLISH
            tapTypingSuggestions = suggestionResults(english)
            assertEquals(listOf("colo", "colour", "color", "colours"), words(getSuggestedWords(false, "colo", CapsMode.OFF)), "emoji on: $enabled")
            currentTypingLocale = si
            tapTypingSuggestions = suggestionResults(sinhala)
            assertEquals(listOf("ම", "මම", "මට", "මගේ"), words(getSuggestedWords(false, "ම", CapsMode.OFF)), "emoji on: $enabled")
        }
    }

    @Test fun `main dictionaries are reloaded when the emoji setting changed since they were loaded`() {
        assertFalse(LatinIME.emojiSettingChangedSinceMainDictionaryLoad(null, true)) // not loaded yet: normal first load
        assertFalse(LatinIME.emojiSettingChangedSinceMainDictionaryLoad(null, false))
        assertFalse(LatinIME.emojiSettingChangedSinceMainDictionaryLoad(true, true))
        assertFalse(LatinIME.emojiSettingChangedSinceMainDictionaryLoad(false, false))
        assertTrue(LatinIME.emojiSettingChangedSinceMainDictionaryLoad(true, false)) // turned off: remove the emoji dictionary
        assertTrue(LatinIME.emojiSettingChangedSinceMainDictionaryLoad(false, true)) // turned on: load it again
    }

    // ---- RCBBoard Singlish phase 2B: dictionary spellings for ambiguous letters ----

    private val si = Locale.forLanguageTag("si-LK")
    private val zwj = "‍"

    /** Types [latin] on the Sinhala (Singlish) keyboard and returns the suggestions for a copy of the word composer, like InputLogic. */
    private fun singlish(latin: String): SuggestedWords {
        currentTypingLocale = si
        return getSuggestedWords(false, latin, CapsMode.OFF, SinglishCombiner.COMBINING_SPEC, throughCopy = true) // as InputLogic calls Suggest
    }

    private fun useRealSinhalaDictionary() { validWords = realSinhalaWords }

    @Test fun `singlish suggests the dictionary spelling of the six everyday words`() {
        useRealSinhalaDictionary()
        val expected = mapOf(
            "gedara" to "ගෙදර",
            "kohomada" to "කොහොමද",
            "ayubowan" to "ආයුබෝවන්",
            "sinhala" to "සිංහල",
            "arthaya" to "අර්${zwj}ථය", // the word list has අර්ථය, shown with the repaya ZWJ (phase 2A)
            "dhrushti" to "දෘෂ්ටි",
        )
        for ((latin, spelling) in expected) {
            val strip = words(singlish(latin))
            assertTrue(spelling in strip.take(4), "$latin: $strip") // typed word and at most 3 spellings
        }
    }

    @Test fun `singlish keeps the transliterated word first and unchanged`() {
        useRealSinhalaDictionary()
        for (latin in listOf("gedara", "kohomada", "ayubowan", "sinhala", "arthaya", "dhrushti", "mama")) {
            val result = singlish(latin)
            val typed = SinglishTransliterator.transliterate(latin)
            assertEquals(typed, result.mTypedWordInfo?.word, latin)
            assertEquals(typed, words(result).first(), latin)
            assertEquals(1, words(result).count { it == typed }, latin)
        }
    }

    @Test fun `singlish spellings are ordered by cost and limited to three`() {
        useRealSinhalaDictionary()
        for (latin in listOf("gedara", "ayubowan", "sinhala", "arthaya", "dhrushti")) {
            val typed = SinglishTransliterator.transliterate(latin)
            val expected = SinglishCandidates.candidates(typed, realSinhalaWords::contains)
                .filter { it.cost > 0 }.take(3).map { it.word }
            assertEquals(expected, words(singlish(latin)).drop(1).take(expected.size), latin)
        }
        // everything valid: still only 3 spellings, the cheapest ones
        val variants = SinglishCandidates.variants(SinglishTransliterator.transliterate("gedara"))
        validWords = variants.map { it.word }.toSet()
        val strip = words(singlish("gedara"))
        assertEquals(listOf("ගෙඩර") + variants.filter { it.cost == 1 }.take(3).map { it.word }, strip)
    }

    @Test fun `singlish keeps an already valid typed word and offers the other spelling`() {
        useRealSinhalaDictionary()
        val result = singlish("kohomada")
        assertEquals(listOf("කොහොමඩ", "කොහොමද"), words(result).take(2))
    }

    @Test fun `singlish shows no spelling the dictionary does not have`() {
        validWords = emptySet()
        assertEquals(listOf("ගෙඩර"), words(singlish("gedara")))
        validWords = setOf("ගෙදර")
        assertEquals(listOf("ගෙඩර", "ගෙදර"), words(singlish("gedara")))
        // nothing ambiguous and nothing valid
        assertEquals(listOf("ක්"), words(singlish("k")))
    }

    @Test fun `singlish spellings are not duplicated and keep the dictionary's own entry`() {
        useRealSinhalaDictionary()
        val native = suggestion("ගෙදර", 300, si)
        tapTypingSuggestions = suggestionResults(listOf(native, suggestion("ගෙදරට", 200, si), suggestion("ගෙදරදී", 100, si)))
        val result = singlish("gedara")
        val strip = words(result)
        assertEquals(strip.distinct(), strip)
        assertEquals(1, strip.count { it == "ගෙදර" })
        assertEquals("ගෙදර", strip[1])
        assertTrue(result.getInfo(1) === native, "the dictionary's suggestion is reused, not a copy")
        assertTrue(strip.containsAll(listOf("ගෙදරට", "ගෙදරදී")), "$strip") // normal Sinhala suggestions stay
    }

    @Test fun `singlish spellings with emoji suggestions on and off`() {
        useRealSinhalaDictionary()
        for (enabled in listOf(true, false)) {
            setSuggestEmojis(enabled)
            tapTypingSuggestions = suggestionResults(listOf(suggestion("ගෙදරට", 300, si), suggestion("🏠", 200, si)))
            val strip = words(singlish("gedara"))
            assertEquals("ගෙදර", strip[1], "emoji on: $enabled")
            assertEquals(enabled, "🏠" in strip, "emoji on: $enabled, $strip")
        }
    }

    @Test fun `singlish spellings do not change the auto-correction decision`() {
        // with auto-correction off in this test field; the stage 3 tests below cover it switched on
        tapTypingSuggestions = suggestionResults(listOf(suggestion("ගෙදරට", 1500000, si), suggestion("ගෙඩරම", 900000, si)))
        validWords = emptySet()
        val before = singlish("gedara")
        useRealSinhalaDictionary()
        val after = singlish("gedara")
        assertEquals(before.mWillAutoCorrect, after.mWillAutoCorrect)
        assertEquals(before.mTypedWordValid, after.mTypedWordValid)
        if (before.mWillAutoCorrect) assertEquals(before.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION), after.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
        assertTrue("ගෙදර" in words(after))
    }

    @Test fun `a spelling that is also the auto-correction stays the auto-correction`() {
        // with auto-correction off in this test field; the stage 3 tests below cover it switched on
        val autoCorrection = suggestion("ගෙදර", 1500000, si)
        tapTypingSuggestions = suggestionResults(listOf(autoCorrection, suggestion("ගෙදරට", 200, si)))
        validWords = emptySet()
        val before = singlish("gedara")
        useRealSinhalaDictionary()
        val after = singlish("gedara")
        assertEquals(before.mWillAutoCorrect, after.mWillAutoCorrect)
        if (after.mWillAutoCorrect) assertTrue(after.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION) === autoCorrection)
        assertEquals(1, words(after).count { it == "ගෙදර" })
    }

    @Test fun `English, Sinhala and Wijesekara suggestions are unchanged and do not look up spellings`() {
        useRealSinhalaDictionary()
        currentTypingLocale = Locale.ENGLISH
        tapTypingSuggestions = suggestionResults(listOf(suggestion("colour", 300), suggestion("color", 200)))
        assertEquals(listOf("colo", "colour", "color"), words(getSuggestedWords(false, "colo", CapsMode.OFF))) // English (US/UK): no combiner
        // the Sinhala and Wijesekara keyboards type Sinhala letters directly, without the Singlish combiner
        currentTypingLocale = si
        tapTypingSuggestions = suggestionResults(listOf(suggestion("ගෙඩරට", 300, si)))
        assertEquals(listOf("ගෙඩර", "ගෙඩරට"), words(getSuggestedWords(false, "ගෙඩර", CapsMode.OFF)))
        assertEquals(0, validWordLookups)
    }

    @Test fun `singlish spellings are the same in incognito mode and only read the dictionary`() {
        useRealSinhalaDictionary()
        val normal = words(singlish("gedara"))
        latinIME.prefs().edit { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        assertTrue(Settings.getValues().mIncognitoModeEnabled)
        assertEquals(normal, words(singlish("gedara")))
        assertTrue(validWordLookups > 0)
    }

    @Test fun `singlish spellings are not logged`() {
        useRealSinhalaDictionary()
        ShadowLog.reset()
        val strip = words(singlish("gedara")) + words(singlish("arthaya"))
        val logged = ShadowLog.getLogs().joinToString("\n") { it.msg ?: "" }
        for (text in strip + listOf("gedara", "arthaya")) assertFalse(text in logged, "logged: $text")
    }

    // ---- RCBBoard Singlish phase 2B stage 3: conservative auto-correction (smart space) ----

    private fun enableAutoCorrection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_MORE_AUTO_CORRECTION, true) } // auto-correct also in this test field
        assertTrue(Settings.getValues().mAutoCorrectEnabled)
    }

    private fun autoCorrection(result: SuggestedWords) =
        if (result.mWillAutoCorrect) result.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION) else null

    @Test fun `singlish auto-corrects to the one clear dictionary spelling`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        assertEquals("ගෙදර", autoCorrection(singlish("gedara")))
        assertEquals("සිංහල", autoCorrection(singlish("sinhala")))
        assertEquals("ආයුබෝවන්", autoCorrection(singlish("ayubowan")))
        assertEquals("අර්${zwj}ථය", autoCorrection(singlish("arthaya"))) // ZWJ form
        assertEquals("දෘෂ්ටි", autoCorrection(singlish("dhrushti")))
    }

    @Test fun `singlish never auto-corrects a valid typed word`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        for (latin in listOf("kohomada", "mama", "gedhara", "karma")) {
            val result = singlish(latin)
            assertNull(autoCorrection(result), latin)
            assertEquals(SinglishTransliterator.transliterate(latin), result.mTypedWordInfo?.word, latin)
        }
        assertTrue("කොහොමද" in words(singlish("kohomada"))) // still offered (stage 2)
    }

    @Test fun `singlish shows the typed word too when auto-correcting, so it can be kept`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        val strip = words(singlish("gedara"))
        assertEquals("ගෙඩර", strip[0]) // the typed word
        assertEquals("ගෙදර", strip[1]) // the auto-correction
        assertTrue("ගෙඩර" in strip.drop(2), "$strip") // and visible in the strip to undo it (HeliBoard behaviour)
        assertEquals(1, strip.count { it == "ගෙදර" })
    }

    @Test fun `singlish does not guess between equally cheap spellings`() {
        enableAutoCorrection()
        validWords = setOf("ගෙදර", "ගේඩර")
        val result = singlish("gedara")
        assertNull(autoCorrection(result))
        assertTrue(words(result).containsAll(listOf("ගෙදර", "ගේඩර")), "${words(result)}") // both still suggested
    }

    @Test fun `singlish auto-correction respects the cost and length limits`() {
        enableAutoCorrection()
        validWords = setOf("ගේදරා") // three changes away
        assertNull(autoCorrection(singlish("gedara")))
        validWords = setOf("ගේදර") // two changes away
        assertEquals("ගේදර", autoCorrection(singlish("gedara")))
        validWords = setOf("මද") // ම + ඩ: only two code points
        assertNull(autoCorrection(singlish("mada")))
    }

    @Test fun `singlish auto-correction is off with the auto-correction setting`() {
        enableAutoCorrection()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, false) }
        assertFalse(Settings.getValues().mAutoCorrectEnabled)
        useRealSinhalaDictionary()
        val result = singlish("gedara")
        assertNull(autoCorrection(result))
        assertEquals("ගෙදර", words(result)[1]) // still suggested
    }

    @Test fun `singlish ignores fuzzy native auto-corrections`() {
        // only the Singlish rules decide on the Singlish keyboard; a high-scoring native correction is just a suggestion
        enableAutoCorrection()
        validWords = emptySet()
        tapTypingSuggestions = suggestionResults(listOf(suggestion("පෙඩරල්", Int.MAX_VALUE, si), suggestion("ෆෙඩරල්", 1500000, si)))
        val result = singlish("gedara")
        assertNull(autoCorrection(result))
        assertTrue(words(result).containsAll(listOf("පෙඩරල්", "ෆෙඩරල්")))
    }

    @Test fun `singlish auto-correction uses the stage 2 spellings without extra lookups`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        validWordLookups = 0
        singlish("gedara")
        val withCorrection = validWordLookups
        validWordLookups = 0
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, false) }
        singlish("gedara")
        assertEquals(validWordLookups, withCorrection) // the same lookups as for the suggestions alone
    }

    @Test fun `singlish auto-correction is the same in incognito mode`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        latinIME.prefs().edit { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        assertEquals("ගෙදර", autoCorrection(singlish("gedara"))) // learning is decided when committing (InputLogicTest)
    }

    @Test fun `english and native sinhala auto-correction are unchanged by the singlish rules`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        currentTypingLocale = si
        tapTypingSuggestions = suggestionResults(listOf(suggestion("ගෙඩරට", 300, si)))
        val sinhala = getSuggestedWords(false, "ගෙඩර", CapsMode.OFF) // the Sinhala / Wijesekara keyboards: no combiner
        assertNull(autoCorrection(sinhala))
        assertEquals(listOf("ගෙඩර", "ගෙඩරට"), words(sinhala))
        currentTypingLocale = Locale.ENGLISH
        tapTypingSuggestions = suggestionResults(listOf(suggestion("colour", 300), suggestion("color", 200)))
        assertEquals(listOf("colo", "colour", "color"), words(getSuggestedWords(false, "colo", CapsMode.OFF)).take(3))
        assertEquals(0, validWordLookups)
    }

    // ---- RCBBoard: baseline for the upstream Kotlin migration (stage 0): resumed words ----

    @Test fun `resumed singlish word gets the dictionary spellings but is not auto-corrected`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        currentTypingLocale = si
        val result = getSuggestedWords(false, "ගෙඩර", CapsMode.OFF, SinglishCombiner.COMBINING_SPEC, throughCopy = true, resumed = true)
        assertEquals("ගෙඩර", result.mTypedWordInfo?.word)
        assertEquals("ගෙදර", words(result)[1]) // stage 2 spellings also for a resumed word
        assertFalse(result.mWillAutoCorrect) // stage 3: resumed words are never auto-corrected
    }

    @Test fun `resumed native sinhala word gets no singlish spellings`() {
        enableAutoCorrection()
        useRealSinhalaDictionary()
        currentTypingLocale = si
        tapTypingSuggestions = suggestionResults(listOf(suggestion("ගෙඩරට", 300, si)))
        val result = getSuggestedWords(false, "ගෙඩර", CapsMode.OFF, throughCopy = true, resumed = true)
        assertEquals(listOf("ගෙඩර", "ගෙඩරට"), words(result))
        assertFalse(result.mWillAutoCorrect)
        assertEquals(0, validWordLookups)
    }

    // ---- RCBBoard: Suggest gets a copy of the WordComposer (InputLogic.getSuggestedWords) ----

    @Test fun `singlish suggestions work through the copied word composer, as InputLogic calls Suggest`() {
        val zwj = "\u200D"
        currentTypingLocale = Locale.forLanguageTag("si-LK")
        // the word list stores repaya without ZWJ; phase 2A shows it with ZWJ on the Singlish keyboard only
        tapTypingSuggestions = suggestionResults(listOf(suggestion("කර්ම", 300), suggestion("කර්මය", 200)))
        val direct = getSuggestedWords(false, "karma", CapsMode.OFF, SinglishCombiner.COMBINING_SPEC)
        val throughCopy = getSuggestedWords(false, "karma", CapsMode.OFF, SinglishCombiner.COMBINING_SPEC, throughCopy = true)
        assertEquals("කර්${zwj}ම", throughCopy.mTypedWordInfo?.word) // typed through the Singlish combiner
        assertEquals(words(direct), words(throughCopy))
        assertTrue(words(throughCopy).containsAll(listOf("කර්${zwj}ම", "කර්${zwj}මය")), "${words(throughCopy)}")
        assertTrue(words(throughCopy).none { "ර්ම" in it }, "repaya without ZWJ must not be shown: ${words(throughCopy)}")
    }

    @Test fun `copied word composer without a combiner gives the same suggestions as before`() {
        currentTypingLocale = Locale.ENGLISH
        tapTypingSuggestions = suggestionResults(listOf(suggestion("colour", 300), suggestion("color", 200)))
        assertEquals(words(getSuggestedWords(false, "colo", CapsMode.OFF)),
            words(getSuggestedWords(false, "colo", CapsMode.OFF, throughCopy = true)))
    }

    private fun getSuggestedWords(gesture: Boolean, typedWord: String, capsMode: CapsMode,
                                  combiningSpec: String? = null, throughCopy: Boolean = false, resumed: Boolean = false): SuggestedWords {
        val wc = WordComposer()
        if (combiningSpec != null) wc.restartCombining(combiningSpec)
        if (gesture) wc.setBatchInputPointers(InputPointers(1))
        if (resumed) { // as InputLogic does when the cursor moves into a word: the word is already text
            val codePoints = StringUtils.toCodePointArray(typedWord)
            wc.setComposingWord(codePoints, CoordinateUtils.newCoordinateArray(codePoints.size, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE))
        } else if (typedWord.isNotEmpty()) {
            StringUtils.toCodePointArray(typedWord).forEach {
                val e = Event.createEventForCodePointFromAlreadyTypedText(it, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
                wc.applyProcessedEvent(wc.processEvent(e))
            }
        }
        // we ignore the special treatment of CapsMode.AUTO here (depends on settings, input type, and text before cursor)
        if (gesture)
            wc.setCapitalizedModeAtStartComposingTime(capsMode) // done in inputlogic when entering batch mode
        wc.adviseCapitalizedModeBeforeFetchingSuggestions(capsMode) // done in inputLogic before getSuggestedWords

        val params = KeyboardParams()
        val elementId = when (capsMode) {
            CapsMode.MANUAL_LOCKED -> KeyboardElement.ALPHABET_SHIFT_LOCKED
            CapsMode.MANUAL -> KeyboardElement.ALPHABET_MANUAL_SHIFTED
            CapsMode.AUTO -> KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED
            else -> KeyboardElement.ALPHABET // off
        }
        params.GRID_HEIGHT = 1
        params.GRID_WIDTH = 1
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(elementId)

        suggest.clearNextWordSuggestionsCache()
        return suggest.getSuggestedWords(
            if (throughCopy) wc.copy() else wc, NgramContext.EMPTY_PREV_WORDS_INFO, Keyboard(params), Settings.getValues().mSettingsValuesForSuggestion,
            Settings.getValues().mAutoCorrectEnabled, InputStyle.NONE, 0
        )
    }

    private fun enableAutocorrect(autoCorrectThreshold: Float) {
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_AUTO_CORRECTION, true)
            putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, autoCorrectThreshold)
            putBoolean(Settings.PREF_MORE_AUTO_CORRECTION, true)
        }
        suggest.setAutoCorrectionThreshold(Settings.getValues().mAutoCorrectionThreshold)
    }

    private fun shouldBeAutoCorrected(word: String, // typed word
                              suggestions: List<SuggestedWordInfo>, // suggestions ordered by score, including suggestion for typed word if in dictionary
                              firstSuggestionForEmpty: SuggestedWordInfo?, // first suggestion if typed word would be empty (null if none)
                              typedWordSuggestionForEmpty: SuggestedWordInfo?, // suggestion for actually typed word if typed word would be empty (null if none)
                              typingLocale: Locale, // used for checking whether suggestion locale is the same, relevant e.g. for English i -> I shortcut, but we want Polish i
                              autoCorrectThreshold: Float
    ): List<Boolean> {
        enableAutocorrect(autoCorrectThreshold)
        currentTypingLocale = typingLocale
        val suggestionsContainer = ArrayList<SuggestedWordInfo>().apply { addAll(suggestions) }
        val suggestionResults = SuggestionResults(suggestions.size, NgramContext.EMPTY_PREV_WORDS_INFO, false)
        suggestions.forEach { suggestionResults.add(it) }

        // store the original SuggestedWordInfo for typed word, as it will be removed
        // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
        val typedWordFirstOccurrenceWordInfo: SuggestedWordInfo? = suggestionsContainer.firstOrNull { it.word == word }

        val firstOccurrenceOfTypedWordInSuggestions =
            SuggestedWordInfo.removeDupsAndTypedWord(word, suggestionsContainer)

        return suggest.shouldBeAutoCorrected(
            StringUtils.getTrailingSingleQuotesCount(word),
            word,
            suggestionsContainer.firstOrNull(), // todo: get from suggestions? mostly it's just removing the typed word, right?
            { firstSuggestionForEmpty to typedWordSuggestionForEmpty },
            true, // doesn't make sense otherwise
            WordComposer.getComposerForTest(false),
            suggestionResults,
            firstOccurrenceOfTypedWordInSuggestions,
            typedWordFirstOccurrenceWordInfo
        ).toList()
    }
}

private var currentTypingLocale = Locale.ENGLISH
private var tapTypingSuggestions = suggestionResults(emptyList())
private var glideTypingSuggestions = suggestionResults(emptyList())
private var nextWordSuggestions = suggestionResults(emptyList())
// RCBBoard Singlish: words the facilitator reports as valid, and how often it was asked
private var validWords: Set<String> = emptySet()
private var validWordLookups = 0

fun suggestion(word: String, score: Int, locale: Locale = currentTypingLocale, shortcut: Boolean = false) =
    SuggestedWordInfo(
        word,
        "", // irrelevant

        // typically IntMax for whitelisted, 1.5M for exact match, 600k for close match
        // when previous word context is empty, scores are usually 200+ if word is known and somewhat often used, 0 if unknown
        score,
        if (score == Int.MAX_VALUE) KIND_WHITELIST
            else if (shortcut) KIND_SHORTCUT // whitelist & shortcut only counts a whitelist
            else KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, // shortcuts seem to never have this flag
        TestDict(locale),
        0, // irrelevant
        0 // irrelevant?
    )

fun suggestionResults(suggestions: List<SuggestedWordInfo>, isBeginningOfSentence: Boolean = false) =
    SuggestionResults(18, if (isBeginningOfSentence) NgramContext.BEGINNING_OF_SENTENCE else NgramContext.EMPTY_PREV_WORDS_INFO, false).apply { addAll(suggestions) }

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator {
    @Implementation
    fun getCurrentLocale(): Locale = currentTypingLocale
    @Implementation
    fun getMainLocale(): Locale = currentTypingLocale
    @Implementation
    fun hasAtLeastOneInitializedMainDictionary() = true // otherwise no autocorrect
    @Implementation
    fun isValidSuggestionWord(word: String): Boolean {
        validWordLookups++
        return word in validWords
    }
    @Implementation
    // what is relevant for facilitator?
    // only detecting whether we want next word suggestions (typed word empty and not batch mode)
    fun getSuggestionResults(composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard,
                             settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, inputStyle: InputStyle
    ): SuggestionResults = when {
        composedData.mIsBatchMode -> glideTypingSuggestions
        composedData.mTypedWord.isEmpty() -> nextWordSuggestions
        else -> tapTypingSuggestions
    }
}

private class TestDict(locale: Locale) : Dictionary(TYPE_MAIN, locale) {
    override fun getSuggestions(
        composedData: ComposedData?,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo> {
        return ArrayList()
    }

    override fun isInDictionary(word: String?): Boolean = false
}

// the bundled Sinhala word list (third_party/sinhala-dictionary), read once
private val realSinhalaWords: Set<String> by lazy {
    val file = listOf(java.io.File("third_party/sinhala-dictionary/main_si.combined"),
        java.io.File("../third_party/sinhala-dictionary/main_si.combined")).first { it.isFile }
    DictIO.readCombinedDictionary(file.path).map { it.mWord }.toHashSet()
}

// RCBBoard: SuggestedWords.mSuggestedWordInfoList is protected since SuggestedWords moved to latin.suggestions
// (upstream 6c36d91d), and becomes the public suggestedWordInfoList with upstream aec7f831; until then read it via getInfo
private fun SuggestedWords.infoList(): List<SuggestedWordInfo> = (0 until size()).map { getInfo(it) }

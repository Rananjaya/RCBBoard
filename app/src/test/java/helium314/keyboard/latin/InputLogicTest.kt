// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.os.Handler
import android.os.Message
import android.text.InputType
import android.view.inputmethod.*
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowInputMethodService.Companion.composingEnd
import helium314.keyboard.ShadowInputMethodService.Companion.composingStart
import helium314.keyboard.ShadowInputMethodService.Companion.selectedText
import helium314.keyboard.ShadowInputMethodService.Companion.selectionEnd
import helium314.keyboard.ShadowInputMethodService.Companion.selectionStart
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.event.SinglishCombiner
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ShadowFacilitator2.Companion.lastAddedWord
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.inputlogic.SpaceState
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getTimestampFormatter
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.mainLayoutName
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.math.min
import kotlin.streams.asSequence
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class InputLogicTest {
    private val latinIME = run {
        // must be set before LatinIME.onCreate loads settings, see the comment in reset()
        org.robolectric.RuntimeEnvironment.getApplication().prefs()
            .edit { putBoolean(Settings.PREF_INLINE_EMOJI_SEARCH, false) }
        Robolectric.setupService(LatinIME::class.java)
    }
    private val settingsValues get() = Settings.getValues()
    private val inputLogic get() = latinIME.mInputLogic
    private val connection: RichInputConnection get() = inputLogic.mConnection
    private val composerReader = InputLogic::class.java.getDeclaredField("mWordComposer").apply { isAccessible = true }
    private val composer get() = composerReader.get(inputLogic) as WordComposer
    private val spaceStateReader = InputLogic::class.java.getDeclaredField("mSpaceState").apply { isAccessible = true }
    private val spaceState get() = spaceStateReader.get(inputLogic) as Int
    private val beforeComposingReader = RichInputConnection::class.java.getDeclaredField("mCommittedTextBeforeComposingText").apply { isAccessible = true }
    private val connectionTextBeforeComposingText get() = (beforeComposingReader.get(connection) as CharSequence).toString()
    private val composingReader = RichInputConnection::class.java.getDeclaredField("mComposingText").apply { isAccessible = true }
    private val connectionComposingText get() = (composingReader.get(connection) as CharSequence).toString()

    private val textBeforeCursor get() = ShadowInputMethodService.textBeforeCursor
    private val textAfterCursor get() = ShadowInputMethodService.textAfterCursor
    private val composingText get() = ShadowInputMethodService.composingText
    private val text get() = ShadowInputMethodService.text
    private val cursor get() = ShadowInputMethodService.cursor
    private var currentInputType get() = ShadowInputMethodService.currentInputType
        set(value) { ShadowInputMethodService.currentInputType = value }

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @Test fun inputCode() {
        input('c')
        assertEquals("c", textBeforeCursor)
        assertEquals("c", getTextFromConnection())
        assertEquals("", textAfterCursor)
        assertEquals("c", composingText)
        latinIME.mHandler.onFinishInput()
        assertEquals("", composingText)
    }

    @Test fun delete() {
        setText("hello there ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there", text)
        assertEquals("there", composingText)
    }

    @Test fun deleteMultiCodepointText() {
        setText("hello there \uD83E\uDF00")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)
    }

    @Test fun deleteCombinedText() {
        setText("hello there э́")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)

        setText("hello there H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)
    }

    @Test fun deleteInsideWord() {
        setText("hello you there")
        setCursorPosition(8) // after o in you
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello yu there", text)
        assertEquals("yu", composingText)
    }

    @Test fun insertLetterIntoWord() {
        setText("hello")
        setCursorPosition(3) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getTextFromConnection())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
        assertEquals("", composingText)
    }

    @Test fun insertLetterIntoWordWithWeirdEditor() {
        currentInputType = 180225 // should not change much, but just to be sure
        setText("hello")
        setCursorPosition(3, weirdTextField = true) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getTextFromConnection())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    @Test fun insertLetterIntoOneOfSeveralWords() {
        setText("hello my friend")
        setCursorPosition(7) // between m and y
        input('a')
        assertEquals("may", getWordAtCursor())
        assertEquals("hello may friend", getTextFromConnection())
        assertEquals(8, getCursorPosition())
        assertEquals(8, cursor)
    }

    @Test fun combineHangul() {
        val ko = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first()
        latinIME.switchToSubtype(ko)
        chainInput("ㅂㄱㅑ")
        assertEquals("ㅂ갸", text)
    }

    @Test fun emojiHangul() {
        val ko = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first()
        latinIME.switchToSubtype(ko)
        input(0x1F970)
        assertEquals("\uD83E\uDD70", text)
    }

    // todo: make it work, but it might not be that simple because adding is done in combiner
    //  https://github.com/HeliBorg/HeliBoard/issues/214
    @Test fun insertLetterIntoWordHangulFails() {
        if (BuildConfig.BUILD_TYPE == "runTests") return
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input('ㄲ') // fails, as expected from the hangul issue when processing the event in onCodeInput
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getWordAtCursor())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getTextFromConnection())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", textBeforeCursor + textAfterCursor)
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    // see issue 1447
    @Test fun separatorAfterHangul() {
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛ.")
        assertEquals("ㅛ.", text)
    }

    @Test fun deleteHangulInDebugMode() { // issue 1551, later only happened on phone
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        setText("ㅛㅛ ")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
    }

    @Test fun separatorUnselectsWord() {
        setText("hello")
        assertEquals("hello", composingText)
        input('.')
        assertEquals("", composingText)
    }

    @Test fun autospace() {
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
    }

    @Test fun autospaceButWithTextAfter() {
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        assertEquals("hello.a there", text)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
        assertEquals("hello. a there", text)
    }

    @Test fun noAutospaceInUrlField() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("example.net")
        assertEquals("example. net", text)
        lastAddedWord = ""
        setText("")
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("example.net")
        assertEquals("", lastAddedWord)
        assertEquals("example.net", text)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceInUrlFieldWhenPickingSuggestion() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("exam")
        pickSuggestion("example")
        assertEquals("example", text)
        input('.')
        assertEquals("example.", text)
    }

    @Test fun noAutospaceForDetectedUrl() { // "light" version, should work without url detection
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("http://example.net")
        assertEquals("http://example.net", text)
        assertEquals("http", lastAddedWord)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceForDetectedEmail() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("mail@example.com")
        assertEquals("mail@example.com", text)
        assertEquals("mail@example", lastAddedWord) // todo: do we want this? not really nice, but don't want to be too aggressive with URL detection disabled
        assertEquals("com", composingText) // todo: maybe this should still see the whole address as a single word? or don't be too aggressive?
        setText("")
        lastAddedWord = ""
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("mail@example.com")
        assertEquals("", lastAddedWord)
        assertEquals("mail@example.com", composingText)
    }

    @Test fun urlDetectionThings() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("...h")
        assertEquals("...h", text)
        assertEquals("h", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("bla..", text)
        assertEquals("", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.c")
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, true) }
        input("bla")
        input('.')
        functionalKeyPress(KeyCode.SHIFT) // should remove the phantom space (in addition to normal effect)
        input('c')
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
    }

    @Test fun stripSeparatorsBeforeAddingToHistoryWithURLDetection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("example.com.")
        assertEquals("example.com.", composingText)
        input(' ')
        assertEquals("example.com", lastAddedWord)
    }

    @Test fun dontSelectConsecutiveSeparatorsWithURLDetection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("", composingText)
        assertEquals("bla..", text)
    }

    @Test fun selectDoesSelect() {
        setText("this is some text")
        setCursorPosition(3, 8)
        assertEquals("s is ", text.substring(3, 8))
    }

    @Test fun noComposingForPasswordFields() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        input('a')
        input('b')
        assertEquals("", composingText)
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        input('.')
        input('c')
        assertEquals("", composingText)
    }

    @Test fun `don't select whole thing as composing word if URL detection disabled`() {
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and selecting`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example.com", composingText)
        setText("http://bla.com http://example.com ")
        setCursorPosition(29) // between l and e
        assertEquals("example.com", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and typing`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://example.com")
        assertEquals("example.com", composingText)
    }

    @Test fun `don't add partial URL to history`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http:/") // just so lastAddedWord isn't set to http
        chainInput("/bla.com")
        assertEquals("", lastAddedWord)
    }

    @Test fun urlProperlySelected() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete com
        // todo: do we really want no composing text?
        //  probably not... try not to break composing
        assertEquals("", composingText)
        chainInput("net")
        assertEquals("example.net", composingText)
    }

    @Test fun urlProperlySelectedWhenNotDeletingFullTld() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete om
        // todo: this is a weird difference to deleting the full TLD (see urlProperlySelected)
        //  what do we want here? (probably consistency)
        assertEquals("example.c/here", composingText)
        chainInput("z")
        assertEquals("", composingText) // todo: this is a weird difference to deleting the full TLD
//        assertEquals("example.cz", composingText) // fails, but probably would be better than above
    }

    @Test fun dontCommitPartialUrlBeforeFirstPeriod() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        // type http://bla. -> bla not selected, but clearly url, also means http://bla is committed which we probably don't want
        chainInput("http://bla.")
        assertEquals("bla.", composingText)
    }

    @Test fun `intermediate commits in text field without protocol`() {
        chainInput("bla.")
        assertEquals("bla", lastAddedWord)
        chainInput("com/")
        assertEquals("com", lastAddedWord)
        chainInput("img.jpg")
        assertEquals("img", lastAddedWord)
        assertEquals("jpg", composingText)
    }

    @Test fun `intermediate commit in text field without protocol and with URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.com/img.jpg")
        assertEquals("bla", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `only protocol commit in text field with protocol and URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol and URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol and with URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `don't accidentally detect some other text fields as URI`() {
        // see comment in InputLogic.textBeforeCursorMayBeUrlOrSimilar
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)
        chainInput("Hey,why")
        assertEquals("Hey, why", text)
    }

    @Test fun `URL detection does not trigger on non-words`() {
        // first make sure it works without URL detection
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
        // then with URL detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
    }

    @Test fun `autospace after selecting a suggestion`() {
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    @Test fun `autospace works in URL field when input isn't URL`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/215
    // https://github.com/HeliBorg/HeliBoard/issues/229
    @Test fun `autospace works in URL field when input isn't URL, also for multiple suggestions`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        pickSuggestion("is")
        assertEquals("this is", text)
        pickSuggestion("not")
        assertEquals("this is not", text)
        input('c')
        assertEquals("this is not c", text)
        assertEquals("c", composingText)
    }

    @Test fun `emoji is added to dictionary`() {
        // check both text and codepoint input
        chainInput("hello ")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        reset()
        chainInput("hello ")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)

        reset()
        chainInput("hello ")
        input("why 🤗 ") // not added because it's not only emoji (input can come from pasting)
        assertEquals("hello", lastAddedWord)
    }

    @Test fun `emoji uses phantom space`() {
        // check both text and codepoint input
        pickSuggestion("hi")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)
        assertEquals("hi \uD83E\uDD17", text)
        reset()
        pickSuggestion("hi")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        assertEquals("hi ${StringUtils.newSingleCodePointString(0x1F36D)}", text)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/230
    @Test fun `no autospace after opening quotes`() {
        chainInput("\"Hi\" \"h")
        assertEquals("\"Hi\" \"h", text)
        assertEquals("h", composingText)
        reset()
        chainInput("\"Hi\", \"h")
        assertEquals("\"Hi\", \"h", text)
        assertEquals("h", composingText)
    }

    @Test fun `autospace works in URL field when starting with quotes`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input("\"")
        pickSuggestion("this")
        input("i")
        assertEquals("\"this i", text)
    }

    @Test fun `double space results in period and space, and delete removes the period`() {
        chainInput("hello")
        input(' ')
        input(' ')
        assertEquals("hello. ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ", text)
    }

    @Test fun `no weird space inside multi-"`() {
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)

        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)
    }

    @Test fun `autospace still happens after "`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if next word is in quotes`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"\"you\"")
        assertEquals("\"hello\" \"you\"", text)
    }

    @Test fun `autospace propagates over "`() {
        input('"')
        pickSuggestion("hello")
        assertEquals(spaceState, SpaceState.PHANTOM) // picking a suggestion sets phantom space state
        chainInput("\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if nex word is in " and after comma`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\",\"you\"")
        assertEquals("\"hello\", \"you\"", text)
    }

    @Test fun `autospace in json editor`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("{\"label\":\"")
        assertEquals("{\"label\": \"", text)
        input('c')
        assertEquals("{\"label\": \"c", text)
    }

    @Test fun `text input and delete`() {
        input("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", text)

        reset()
        input("hello ")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `emoji text input and delete`() {
        input("🕵🏼")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        input("\uD83D\uDD75\uD83C\uDFFC")
        input(' ')
        assertEquals("🕵🏼 ", text)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun `emoji search result is inserted into host editor`() {
        setText("hi ")
        // ZWJ sequence, skin tone, flag, keycap (multi code point), plain single code point
        val emojis = listOf("👩‍💻", "👍🏽", "🇱🇰", "1️⃣", "😀")
        var expected = "hi "
        emojis.forEach { emoji ->
            emojiSearchDone(emoji) // repeated searches
            expected += emoji
            assertEquals(expected, text)
            checkConnectionConsistency()
        }
        // typing continues normally after the inserted emoji
        chainInput("ok")
        assert(text == expected + "ok" || text == "$expected ok") { text }
    }

    @Test fun `emoji search result is not inserted while search field is still the editor`() {
        setText("hi ")
        ShadowInputMethodService.privateImeOptions = "helium314.keyboard.keyboard.emoji.search.500,"
        emojiSearchDone("😀")
        assertEquals("hi ", text)
    }

    @Test fun `emoji search done guard`() {
        val action = helium314.keyboard.keyboard.emoji.EmojiSearchActivity.EMOJI_SEARCH_DONE_ACTION
        assertEquals(true, StuffReceiver.shouldHandleEmojiSearchDone(action, false))
        assertEquals(false, StuffReceiver.shouldHandleEmojiSearchDone(action, true))
        assertEquals(false, StuffReceiver.shouldHandleEmojiSearchDone("other", false))
        assertEquals(false, StuffReceiver.shouldHandleEmojiSearchDone(null, false))
    }

    // Privacy regression test: when RichInputConnection.setComposingText's sanity check fails in a
    // field without suggestions (such as a password field), the warning must contain only lengths,
    // never the typed text or the text before the cursor.
    @Test fun failedComposingTextCheckDoesNotLogTypedText() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertFalse(settingsValues.mInputAttributes.mShouldShowSuggestions)
        val secret = "hunter2Secret"
        // The test input connection returns the first characters before the cursor ("h"), which
        // differ from the last composed character ("t"), so the check fails and the warning is logged.
        assertFalse(connection.setComposingText(secret, 1))
        val newLog = Log.getLog(20).map { it.toString() }
        assertTrue(newLog.any { "did set composing text of length ${secret.length}" in it }, "warning not logged")
        assertTrue(newLog.none { "hunter" in it || "Secret" in it }, "typed text was logged")
    }

    // ---- Sinhala (Wijesekara) layout: typing through the input pipeline ----
    // Key outputs are those of layouts/main/sinhala_wijesekara.json (checked key by key in
    // SinhalaWijesekaraLayoutTest). A key with one code point is sent as a key event, a
    // multi-code-point key (yansaya, rakaransaya, repaya, join) as text, like on a device.

    private val wAl = "්"
    private val wZwj = "‍"
    private val wRakaransaya = wAl + wZwj + "ර" // ` key
    private val wYansaya = wAl + wZwj + "ය"     // Shift+H
    private val wRepaya = "ර" + wAl + wZwj      // Shift+`
    private val wJoin = wAl + wZwj                   // \ key

    private fun switchToWijesekara() {
        val subtype = SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .first { it.mainLayoutName() == "sinhala_wijesekara" }
        latinIME.switchToSubtype(subtype)
        currentScript = ScriptUtils.SCRIPT_SINHALA
        setText("")
    }

    private fun pressKeys(vararg keys: String) {
        for (key in keys) {
            if (key.codePointCount(0, key.length) == 1) input(key.codePointAt(0))
            else input(key)
        }
    }

    private fun codePoints(s: String) = s.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }

    private fun assertTyped(expected: String, vararg keys: String) {
        switchToWijesekara()
        pressKeys(*keys)
        assertEquals(codePoints(expected), codePoints(text), "keys ${keys.joinToString(",") { codePoints(it) }}")
    }

    @Test fun `wijesekara independent vowels are typed directly`() {
        // normal / Shift / long-press keys; each is one code point, nothing is combined
        for (vowel in listOf("අ", "ආ", "උ", "ඌ", "ඉ", "ඊ", "එ", "ඒ", "ඔ", "ඕ")) {
            assertTyped(vowel, vowel)
        }
    }

    @Test fun `wijesekara consonants and consonant vowel combinations`() {
        assertTyped("ක", "ක")                       // ක
        assertTyped("කා", "ක", "ා")       // කා = ක + ා
        assertTyped("ගා", "ග", "ා")       // ගා
        assertTyped("මා", "ම", "ා")       // මා
        assertTyped("කෙ", "ක", "ෙ")       // කෙ: Unicode order, sign after consonant
        assertTyped("කො", "ක", "ො")       // කො from the ෙ long-press (single code point)
    }

    @Test fun `wijesekara ZWJ sequences`() {
        // ශ්‍රී = ශ (Shift+Y) + rakaransaya (` key) + ී (Shift+S)
        assertTyped("ශ්‍රී", "ශ", wRakaransaya, "ී")
        assertTyped("ක්‍ය", "ක", wYansaya)                   // ක්‍ය
        assertTyped("කර්‍ම", "ක", wRepaya, "ම")    // කර්‍ම
        assertTyped("ක්‍ෂ", "ක", wJoin, "ෂ")           // ක්‍ෂ via join key
    }

    @Test fun `wijesekara keeps typed sequences exactly, without merging`() {
        // අ + ා is not turned into ආ, and ෙ + ා is not composed into ො: what is typed is what is sent
        assertTyped("අා", "අ", "ා")
        assertTyped("කො", "ක", "ෙ", "ා")
    }

    @Test fun `wijesekara backspace`() {
        switchToWijesekara()
        pressKeys("\u0D9A", "\u0DCF") // කා
        functionalKeyPress(KeyCode.DELETE)
        assertEquals(codePoints("\u0D9A"), codePoints(text)) // removes the vowel sign only

        // ශ්‍රී: ී, then ර, then the cluster ශ + al-lakuna + ZWJ together (no orphan ZWJ is left).
        // Deletion is done by the shared input logic, so this is the same on every Sinhala layout.
        switchToWijesekara()
        pressKeys("\u0DC1", wRakaransaya, "\u0DD3")
        val expectedSteps = listOf("\u0DC1\u0DCA\u200D\u0DBB", "\u0DC1\u0DCA\u200D", "")
        val actualSteps = mutableListOf<String>()
        while (text.isNotEmpty() && actualSteps.size < 6) {
            functionalKeyPress(KeyCode.DELETE)
            actualSteps.add(codePoints(text))
        }
        assertEquals(expectedSteps.map { codePoints(it) }, actualSteps)
    }

    @Test fun `wijesekara composing word for suggestions`() {
        // ordinary keys build one composing word, so suggestions and learning see the whole word
        switchToWijesekara()
        pressKeys("\u0D9A", "\u0DCF", "\u0DB8", "\u0DBB") // කාමර
        assertEquals("\u0D9A\u0DCF\u0DB8\u0DBB", composer.typedWord)

        // multi-code-point keys commit their text directly. This is the same on the existing
        // Sinhala layout (its ්‍ර key), so it is a shared limitation, not a change in behaviour.
        switchToWijesekara()
        pressKeys("\u0DC1", wRakaransaya, "\u0DD3")
        val wijesekaraComposing = composer.typedWord
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .first { it.mainLayoutName() == "sinhala" })
        currentScript = ScriptUtils.SCRIPT_SINHALA
        setText("")
        pressKeys("\u0DC1", wRakaransaya, "\u0DD3")
        assertEquals(composer.typedWord, wijesekaraComposing)
    }

    @Test fun `switching between English, Wijesekara and the existing Sinhala layout`() {
        val en = SubtypeSettings.getResourceSubtypesForLocale("en-US".constructLocale()).first()
        val siExisting = SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .first { it.mainLayoutName() == "sinhala" }

        latinIME.switchToSubtype(en)
        currentScript = ScriptUtils.SCRIPT_LATIN
        setText("")
        chainInput("ok")
        assertEquals("ok", text)

        assertTyped("කා", "ක", "ා")
        // the test InputMethodManager always reports English, so check the stored selection
        assertTrue(latinIME.prefs().getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.contains("MAIN:sinhala_wijesekara"))

        latinIME.switchToSubtype(siExisting)
        currentScript = ScriptUtils.SCRIPT_SINHALA
        setText("")
        pressKeys("ම", "ා") // මා on the existing layout
        assertEquals("මා", text)
        val selected = latinIME.prefs().getString(Settings.PREF_SELECTED_SUBTYPE, "")!!
        assertTrue(selected.contains("MAIN:sinhala") && !selected.contains("sinhala_wijesekara"), selected)

        latinIME.switchToSubtype(en)
        currentScript = ScriptUtils.SCRIPT_LATIN
        setText("")
        chainInput("hi")
        assertEquals("hi", text)
    }

    // ---- RCBBoard Sinhala (Singlish), see docs/singlish.md ----

    // getResourceSubtypesForLocale can repeat entries in tests (SubtypeSettings.init runs per LatinIME), so use first
    private fun singlishSubtype() = SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
        .first { it.getExtraValueOf(Constants.Subtype.ExtraValue.COMBINING_RULES) == "si_singlish" }

    private fun switchToSinglish() {
        // setText restarts input with the combining rules of the test InputMethodManager's subtype
        // (always English), so clear the text first and switch afterwards, as the Hangul tests do
        setText("")
        latinIME.switchToSubtype(singlishSubtype())
        currentScript = ScriptUtils.SCRIPT_SINHALA
    }

    @Test fun `singlish subtype is a separate qwerty si-LK subtype`() {
        val subtype = singlishSubtype()
        assertEquals("qwerty", subtype.mainLayoutName())
        assertTrue(subtype.isAsciiCapable)
        assertTrue(subtype.extraValue.split(",").contains("EmojiCapable"), subtype.extraValue)
        // the existing Sinhala layouts have no combiner
        val others = SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .distinctBy { it.hashCode() }.filter { it.hashCode() != subtype.hashCode() }
        assertEquals(setOf("sinhala", "sinhala_wijesekara"), others.mapNotNull { it.mainLayoutName() }.toSet())
        assertTrue(others.none { it.extraValue.contains("CombiningRules") })
    }

    @Test fun `singlish composes Sinhala and space commits it`() {
        switchToSinglish()
        chainInput("mama")
        assertEquals("මම", composer.typedWord) // the Sinhala word goes to the dictionary for suggestions
        assertEquals("මම", composingText.toString())
        assertEquals("මම", text)
        input(' ')
        assertEquals("මම ", text)
        assertFalse(composer.isComposingWord)
        chainInput("gedhara")
        input(' ')
        assertEquals("මම ගෙදර ", text)
    }

    @Test fun `singlish example words`() {
        for ((latin, sinhala) in listOf("mama" to "මම", "gedhara" to "ගෙදර", "kohomadha" to "කොහොමද",
                "aayuboowan" to "ආයුබෝවන්", "sixhala" to "සිංහල")) {
            switchToSinglish()
            chainInput("$latin ")
            assertEquals("$sinhala ", text, latin)
        }
    }

    @Test fun `singlish reconverts the whole word on every key`() {
        switchToSinglish()
        chainInput("k")
        assertEquals("ක්", text)
        chainInput("o")
        assertEquals("කො", text)
        chainInput("o")
        assertEquals("කෝ", text) // o + o is one long vowel, not two signs
    }

    @Test fun `singlish backspace removes one Latin letter and reconverts`() {
        switchToSinglish()
        chainInput("gedhar")
        assertEquals("ගෙදර්", text)
        functionalKeyPress(KeyCode.DELETE) // gedha
        assertEquals("ගෙද", text)
        assertEquals("ගෙද", composer.typedWord)
        functionalKeyPress(KeyCode.DELETE) // gedh: dh is still one letter
        assertEquals("ගෙද්", text)
        functionalKeyPress(KeyCode.DELETE) // ged: d is ඩ on its own
        assertEquals("ගෙඩ්", text)
        chainInput("hara")
        assertEquals("ගෙදර", text)
    }

    @Test fun `singlish backspace on the last letter keeps committed text`() {
        switchToSinglish()
        chainInput("mama ")
        chainInput("ka")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertFalse(composer.isComposingWord)
        assertEquals("මම ", text)
        // with nothing composed, backspace deletes text as usual
        val before = text
        functionalKeyPress(KeyCode.DELETE)
        assertEquals(before.dropLast(1), text)
    }

    @Test fun `singlish punctuation and digits commit the word first`() {
        switchToSinglish()
        chainInput("kohomadha?")
        assertEquals("කොහොමද?", text)
        assertFalse(composer.isComposingWord)
        switchToSinglish()
        chainInput("mama2")
        assertEquals("මම2", text)
        switchToSinglish()
        chainInput("mama, oyaa.")
        assertEquals("මම, ඔයා.", text)
    }

    @Test fun `singlish capital letters select their own Sinhala letters`() {
        switchToSinglish()
        chainInput("Ta La Na Sa aBa kaRma vidhYaa ")
        assertEquals("ඨ ළ ණ ෂ අඹ කර්‍ම විද්‍යා ", text)
    }

    @Test fun `singlish does not change English or the other Sinhala layouts`() {
        switchToSinglish()
        chainInput("mama ")
        assertEquals("මම ", text)

        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("en-US".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_LATIN
        setText("")
        chainInput("mama")
        assertEquals("mama", text)

        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("en-GB".constructLocale()).first())
        setText("")
        chainInput("colour")
        assertEquals("colour", text)

        assertTyped("කා", "ක", "ා") // Wijesekara keys still type Sinhala directly
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .first { it.mainLayoutName() == "sinhala" })
        currentScript = ScriptUtils.SCRIPT_SINHALA
        setText("")
        pressKeys("ම", "ා")
        assertEquals("මා", text)
    }

    // ---- Singlish phase 2A: conjuncts with ZWJ (U+200D), checked as code points ----

    private fun cps(s: CharSequence) = s.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }

    @Test fun `singlish conjunct words are composed and committed with ZWJ`() {
        for ((latin, expected) in listOf(
            "kra" to "ක්‍ර",
            "prashnaya" to "ප්‍රශ්නය",
            "shrii" to "ශ්‍රී",
            "vidhyaa" to "විද්‍යා",
            "karma" to "කර්‍ම",
            "kaarya" to "කාර්‍ය",
            "kruthi" to "කෘති",
            "kruu" to "කෲ",
            "akshara" to "අක්ෂර",
            "kRu" to "ක්‍රු",
        )) {
            switchToSinglish()
            chainInput(latin)
            assertEquals(cps(expected), cps(composer.typedWord), "composing $latin")
            assertEquals(cps(expected), cps(text), "shown $latin")
            input(' ')
            assertEquals(cps("$expected "), cps(text), "committed $latin")
        }
    }

    @Test fun `singlish backspace through conjuncts reconverts every step`() {
        fun steps(latin: String, vararg afterEachDelete: String) {
            switchToSinglish()
            chainInput(latin)
            for (expected in afterEachDelete) {
                functionalKeyPress(KeyCode.DELETE)
                assertEquals(cps(expected), cps(text), "deleting in $latin")
                assertFalse(text.endsWith("‍"), "dangling ZWJ in $latin")
            }
        }
        steps("kra", "ක්‍ර්", "ක්") // ක්‍ර → ක්‍ර් → ක්
        steps("kya", "ක්‍ය්", "ක්") // ක්‍ය → ක්‍ය් → ක්
        steps("karma", "කර්‍ම්", "කර්", "ක") // කර්‍ම් → කර් → ක
        steps("kruu", "කෘ", "ක්‍ර්", "ක්") // කෲ → කෘ → ක්‍ර් → ක්
        steps("ksha", "ක්ෂ්", "ක්ස්", "ක්") // ක්ෂ → ක්ෂ් → ක්ස් → ක්
        // the Latin composing state survives the deletes: typing on gives the full word again
        switchToSinglish()
        chainInput("prash")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        chainInput("shnaya ")
        assertEquals(cps("ප්‍රශ්නය "), cps(text))
    }

    @Test fun `singlish punctuation after a conjunct commits it unchanged`() {
        switchToSinglish()
        chainInput("shrii.")
        assertEquals(cps("ශ්‍රී."), cps(text))
        switchToSinglish()
        chainInput("karma,")
        assertEquals(cps("කර්‍ම,"), cps(text))
    }

    @Test fun `singlish looks up no spellings and shows no suggestions in a password field`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        switchToSinglish()
        ShadowFacilitator2.validWordLookups = 0
        chainInput("gedara")
        assertFalse(settingsValues.needsToLookupSuggestions())
        inputLogic.performUpdateSuggestionStripSync(settingsValues, InputStyle.TYPING)
        assertTrue(inputLogic.mSuggestedWords.isEmpty())
        assertEquals(0, ShadowFacilitator2.validWordLookups)
    }

    // ---- Singlish phase 2B stage 3: smart space ----
    // The suggestions (Suggest + SinglishAutoCorrection, see SuggestTest) prepare the correction; here
    // it is prepared directly, as for the other auto-correction tests, and the keys are pressed.

    private fun singlishWithAutoCorrect() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        switchToSinglish()
    }

    /** What the suggestions prepare when [correction] is the auto-correction for the composing [typed] word. */
    private fun prepareSinglishCorrection(typed: String, correction: String) {
        assertEquals(typed, composer.typedWord)
        val typedInfo = SuggestedWordInfo(typed, "", 0, SuggestedWordInfo.KIND_TYPED, dict, 0, 0)
        val info = SuggestedWordInfo(correction, "", 0, SuggestedWordInfo.KIND_CORRECTION, dict, 0, 0)
        latinIME.mInputLogic.setSuggestedWords(SuggestedWords(ArrayList(listOf(typedInfo, info)), null, typedInfo, false, true, false, InputStyle.NONE, 0))
    }

    @Test fun `singlish space commits the prepared correction`() {
        // before stage 3 the combiner hand-back cleared the correction and ගෙඩර was committed
        singlishWithAutoCorrect()
        chainInput("gedara")
        getAutocorrectedWithSpaceAfter("ගෙදර", "ගෙඩර")
        assertEquals("ගෙදර ", text)
        assertFalse(composer.isComposingWord)
    }

    @Test fun `singlish punctuation commits the correction before the character`() {
        singlishWithAutoCorrect()
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        input('.')
        assertEquals("ගෙදර.", text)
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        input(',')
        assertEquals("ගෙදර.ගෙදර,", text)
    }

    @Test fun `singlish newline in a multi-line field commits the correction`() {
        ShadowInputMethodService.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION // as multi-line fields have
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        switchToSinglish()
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        input('\n')
        assertEquals("ගෙදර\n", text)
    }

    @Test fun `singlish repaya correction is committed in the ZWJ form`() {
        val zwj = "\u200D"
        singlishWithAutoCorrect()
        chainInput("arthaya")
        prepareSinglishCorrection("අර්${zwj}තය", "අර්${zwj}ථය")
        input(' ')
        assertEquals("අර්${zwj}ථය ", text)
    }

    @Test fun `singlish backspace right after the correction restores the transliteration`() {
        singlishWithAutoCorrect()
        chainInput("gedara")
        getAutocorrectedWithSpaceAfter("ගෙදර", "ගෙඩර")
        assertEquals("ගෙදර ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ගෙඩර", text)
    }

    @Test fun `singlish manual pick wins over the prepared correction`() {
        singlishWithAutoCorrect()
        chainInput("kohomada")
        prepareSinglishCorrection("කොහොමඩ", "කොහොමද")
        pickSuggestion("කොහොම")
        assertEquals("කොහොම", text)
    }

    @Test fun `singlish keeps the transliteration without a prepared correction`() {
        singlishWithAutoCorrect()
        chainInput("kohomada") // a valid word: the suggestions don't auto-correct it
        val typedInfo = SuggestedWordInfo("කොහොමඩ", "", 0, SuggestedWordInfo.KIND_TYPED, dict, 0, 0)
        val spelling = SuggestedWordInfo("කොහොමද", "", 0, SuggestedWordInfo.KIND_CORRECTION, dict, 0, 0)
        latinIME.mInputLogic.setSuggestedWords(SuggestedWords(ArrayList(listOf(typedInfo, spelling)), null, typedInfo, true, false, false, InputStyle.NONE, 0))
        input(' ')
        assertEquals("කොහොමඩ ", text)
    }

    @Test fun `singlish does not correct when auto-correction is off`() {
        singlishWithAutoCorrect()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, false) }
        assertFalse(settingsValues.mAutoCorrectEnabled)
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        input(' ')
        assertEquals("ගෙඩර ", text)
    }

    @Test fun `singlish does not correct in fields without auto-correction`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) // no auto-correction for URLs
        switchToSinglish()
        assertFalse(settingsValues.mAutoCorrectEnabled)
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        input(' ')
        assertEquals("ගෙඩර ", text)
    }

    @Test fun `singlish password fields neither look up nor correct`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        switchToSinglish()
        assertFalse(settingsValues.needsToLookupSuggestions())
        assertFalse(settingsValues.mAutoCorrectEnabled)
        ShadowFacilitator2.validWordLookups = 0
        chainInput("gedara ")
        assertFalse("ගෙදර" in text, text)
        assertEquals(0, ShadowFacilitator2.validWordLookups)
    }

    @Test fun `singlish editor action enter does not correct`() {
        singlishWithAutoCorrect()
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        assertFalse("ගෙදර" in text)
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Constants.CODE_ENTER, KeyCode.UNSPECIFIED, 0,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
        assertTrue(ShadowInputMethodService.lastEditorAction != null) // Enter performed the editor action ...
        assertFalse("ගෙදර" in text, text) // ... without correcting the word
    }

    @Test fun `singlish digit after a word does not correct it`() {
        singlishWithAutoCorrect()
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        input('1')
        assertFalse("ගෙදර" in text, text)
        assertTrue(text.startsWith("ගෙඩර"), text)
    }

    @Test fun `singlish correction is learned, but not in incognito mode`() {
        singlishWithAutoCorrect()
        chainInput("gedara")
        getAutocorrectedWithSpaceAfter("ගෙදර", "ගෙඩර")
        assertEquals("ගෙදර", lastAddedWord) // as for any committed word

        lastAddedWord = ""
        latinIME.prefs().edit { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        assertTrue(settingsValues.mIncognitoModeEnabled)
        chainInput("gedara")
        assertEquals("ගෙඩර", composer.typedWord) // still the Singlish keyboard
        getAutocorrectedWithSpaceAfter("ගෙදර", "ගෙඩර")
        assertEquals("ගෙදර ගෙදර ", text) // corrected ...
        assertEquals("", lastAddedWord) // ... but nothing learned
    }

    @Test fun `singlish corrects with the suggestion strip hidden, like other languages`() {
        // HeliBoard keeps looking up and auto-correcting when only the strip is hidden
        singlishWithAutoCorrect()
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHOW_SUGGESTIONS, false) }
        assertTrue(settingsValues.needsToLookupSuggestions())
        chainInput("gedara")
        getAutocorrectedWithSpaceAfter("ගෙදර", "ගෙඩර")
        assertEquals("ගෙදර ", text)
        // with auto-correction off as well, nothing is looked up at all
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, false) }
        assertFalse(settingsValues.needsToLookupSuggestions())
    }

    @Test fun `a correction prepared for another word is not restored`() {
        singlishWithAutoCorrect()
        chainInput("geda")
        prepareSinglishCorrection("ගෙඩ", "ගෙද") // suggestions for an earlier state of the word
        chainInput("ra") // typing on clears it, and no new suggestions arrived
        input(' ')
        assertEquals("ගෙඩර ", text)
    }

    @Test fun `singlish hand-back restores only the very correction object of the current suggestions`() {
        // SuggestedWordInfo is a Kotlin data class since the upstream migration: a copy is equal, but restoring
        // must still check identity (InputLogic.restoreSinglishAutoCorrection uses != in Java)
        singlishWithAutoCorrect()
        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර")
        val prepared = inputLogic.mSuggestedWords.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION)
        val equalCopy = prepared.copy()
        assertEquals(prepared, equalCopy)
        composer.setAutoCorrection(equalCopy) // same content, different object
        input(' ')
        assertEquals("ගෙඩර ", text) // not restored, so the transliteration is committed

        chainInput("gedara")
        prepareSinglishCorrection("ගෙඩර", "ගෙදර") // the composer gets the suggestions' own object
        input(' ')
        assertEquals("ගෙඩර ගෙදර ", text)
    }

    @Test fun `english and native sinhala auto-correction are unchanged`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        assertEquals("hello ", text)

        setText("")
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .first { it.mainLayoutName() == "sinhala" })
        currentScript = ScriptUtils.SCRIPT_SINHALA
        pressKeys("ග", "ෙ", "ඩ", "ර")
        getAutocorrectedWithSpaceAfter("ගෙදර", "ගෙඩර")
        assertEquals("ගෙදර ", text) // the native path, without a combiner, as before
    }

    // ---- RCBBoard: baseline for the upstream Kotlin migration (stage 0) ----
    // Records the current behaviour when the cursor moves into a word (resumed suggestions) and of Khipro,
    // because the upstream refactoring changes the word-at-cursor code (TextRange -> TextAtCursor) and
    // shared suggestion classes. Entries marked "known limitation" are not the intended behaviour.

    private fun resumeAt(position: Int) {
        setCursorPosition(position)
        inputLogic.restartSuggestionsOnWordTouchedByCursor(settingsValues)
        handleMessages()
    }

    private fun switchToNativeSinhala() {
        setText("")
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("si-LK".constructLocale())
            .first { it.mainLayoutName() == "sinhala" })
        currentScript = ScriptUtils.SCRIPT_SINHALA
    }

    @Test fun `singlish word is resumed when the cursor moves into it`() {
        switchToSinglish()
        chainInput("gedhara mama ")
        assertEquals("ගෙදර මම ", text)
        resumeAt(2) // inside ගෙදර
        assertEquals("ගෙදර", composer.typedWord)
        assertEquals("ගෙදර", composingText.toString())
        assertTrue(composer.isResumed)
        assertEquals(SinglishCombiner.COMBINING_SPEC, composer.combiningSpec) // still the Singlish combiner
        assertEquals(2, cursor)
        assertEquals("ගෙදර මම ", text) // nothing changed in the text
    }

    @Test fun `known limitation - singlish letter typed into a resumed word goes to the end of the word`() {
        switchToSinglish()
        chainInput("gedhara mama ")
        resumeAt(2)
        // not via input(): the cursor moves to the end of the word, which input() treats as an error
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource('a'.code))
        handleMessages()
        assertEquals("ගෙදරඅ මම ", text)
        assertEquals("ගෙදරඅ", composer.typedWord)
        assertEquals(5, cursor)
    }

    @Test fun `known limitation - resuming a rakaransaya word stops at the ZWJ`() {
        val zwj = "\u200D"
        switchToSinglish()
        chainInput("kramaya ")
        assertEquals("ක්${zwj}රමය ", text)
        resumeAt(3) // after the ZWJ: only the part after it is resumed
        assertEquals("රමය", composer.typedWord)
        resumeAt(1) // before the ZWJ: only the part before it
        assertEquals("ක්", composer.typedWord)
        assertEquals("ක්${zwj}රමය ", text) // the text itself is unchanged
    }

    @Test fun `native sinhala word is resumed only with the cursor inside it`() {
        switchToNativeSinhala()
        pressKeys("අ", "ප", "ි")
        input(' ')
        assertEquals("අපි ", text)
        resumeAt(3) // right after the word (as for other languages): not resumed
        assertFalse(composer.isComposingWord)
        resumeAt(1)
        assertEquals("අපි", composer.typedWord) // the whole word, including the vowel sign
        assertTrue(composer.isResumed)
        assertEquals("", composer.combiningSpec) // no combiner on the Sinhala keyboard
    }

    private fun khiproSubtype() = SubtypeSettings.getResourceSubtypesForLocale("bn-BD".constructLocale())
        .first { it.getExtraValueOf(Constants.Subtype.ExtraValue.COMBINING_RULES) == "bn_khipro" }

    @Test fun `khipro composes bengali, space commits and delete edits the composing word`() {
        setText("")
        latinIME.switchToSubtype(khiproSubtype())
        currentScript = ScriptUtils.SCRIPT_BENGALI
        chainInput("ami")
        assertEquals("আমি", composer.typedWord)
        assertEquals("bn_khipro", composer.combiningSpec)
        input(' ')
        assertEquals("আমি ", text)
        assertFalse(composer.isComposingWord)
        chainInput("kotha")
        assertEquals("আমি কথা", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("আমি কথ", text)
        assertEquals("কথ", composer.typedWord)
    }

    @Test fun `khipro word survives the word composer copy`() {
        // the copy is what Suggest gets; Khipro must keep its combining spec too (WordComposer copy fix)
        setText("")
        latinIME.switchToSubtype(khiproSubtype())
        currentScript = ScriptUtils.SCRIPT_BENGALI
        chainInput("ami")
        assertEquals("bn_khipro", composer.copy().combiningSpec)
        assertEquals("আমি", composer.copy().typedWord)
    }

    @Test fun `singlish never logs typed text`() {
        val secretLatin = "vasuraa"
        val secretSinhala = "වසුරා"
        switchToSinglish()
        chainInput(secretLatin)
        functionalKeyPress(KeyCode.DELETE)
        chainInput("a ")
        chainInput("$secretLatin.")
        val log = Log.getLog().map { it.toString() } + ShadowLog.getLogs().map { "${it.tag} ${it.msg}" }
        assertTrue(log.none { secretLatin in it || "vasur" in it || secretSinhala in it || "වසුර" in it },
            "typed text was logged: ${log.filter { "vasur" in it || "වසුර" in it }}")
    }

    // emoRegex update to unicode 16.0 was required, https://github.com/HeliBorg/HeliBoard/issues/1760
    @Test fun `emojis deleted one by one`() {
        chainInput("\uD83E\uDEC6\uD83E\uDEC6\uD83E\uDEC6")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("\uD83E\uDEC6\uD83E\uDEC6", text)
    }

    @Test fun `revert autocorrect on delete`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)

        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        latinIME.prefs().edit { putBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, false) }
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `remove glide typing word on delete`() {
        glideTypingInput("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        // todo: now we want some way to disable delete-all on backspace, either per setting or something else
        //  need to avoid getting into the mWordComposer.isBatchMode() part of handleBackspaceEvent
    }

    @Test fun timestamp() {
        chainInput("hello")
        functionalKeyPress(KeyCode.TIMESTAMP)
        assertEquals(Calendar.getInstance().time.time.toDouble(),
            getTimestampFormatter(latinIME).parse(text.substring(5))!!.time.toDouble(), 1000.0)
    }

    @Test fun inlineEmojiSearchStart() {
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, ' '.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch(' '.code, ':'.code, ' '.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '.'.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, "🌍".codePoints().asSequence().last(), settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, 't'.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '3'.code, settingsValues))
    }

    @Test fun inlineEmojiSearchString() {
        assertEquals("test", InputLogic.getInlineEmojiSearchString(":test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(" :test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("t:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("6:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString("🌍:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(",:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString(":test\nt"))
        assertEquals("/48", InputLogic.getInlineEmojiSearchString("2606:127.0.0.1::/48")) // do we want this?
    }

    @Test fun moveCursorHorizontally() {
        chainInput("hello")
        assertEquals(5, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(-2)
        assertEquals(3, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(-5)
        assertEquals(0, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(-1)
        assertEquals(0, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(3)
        assertEquals(3, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(3)
        assertEquals(5, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(1)
        assertEquals(5, cursor)
    }

    // ------- helper functions ---------

    // should be called before every test, so the same state is guaranteed
    @BeforeTest
    fun reset() {
        // reset input connection & facilitator
        currentScript = ScriptUtils.SCRIPT_LATIN
        ShadowInputMethodService.reset()
        lastAddedWord = ""

        // reset settings
        latinIME.prefs().edit { clear() }
        // Inline emoji search opens the bundled emoji dictionary through native code (libjni_latinime),
        // which is not available in JVM tests. The emoji search tests here use the broadcast path instead.
        latinIME.prefs().edit { putBoolean(Settings.PREF_INLINE_EMOJI_SEARCH, false) }

        setText("") // (re)sets selection and composing word
    }

    private fun chainInput(text: String) = text.forEach { input(it.code) }

    private fun input(char: Char) = input(char.code)

    private fun input(codePoint: Int) {
        require(codePoint > 0) { "not a codePoint: $codePoint" }
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val insert = StringUtils.newSingleCodePointString(codePoint)
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""
        val oldIsAtEnd = !composer.isCursorFrontOrMiddleOfComposingWord

        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(codePoint))
        handleMessages()

        if (!latinIME.prefs().getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.contains("CombiningRules") // check fails if combiner merges symbols
            && !(codePoint == Constants.CODE_SPACE && oldBefore.lastOrNull() == ' ') // check fails when 2 spaces are converted into a period
            && !latinIME.mInputLogic.mSuggestedWords.willAutoCorrect // autocorrect obviously creates inconsistencies
            ) {
            if (phantomSpaceToInsert.isEmpty())
                assertEquals(oldBefore + insert, textBeforeCursor)
            else // in some cases autospace might be suppressed
                assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        }
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getTextFromConnection())
        if (composer.isComposingWord) // if we're not composing any more cursor is always at the end
            assertEquals(oldIsAtEnd, !composer.isCursorFrontOrMiddleOfComposingWord)
        checkConnectionConsistency()
    }

    private fun functionalKeyPress(keyCode: Int) {
        require(keyCode < 0) { "not a functional key code: $keyCode" }
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, keyCode, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
        checkConnectionConsistency()
    }

    // almost the same as codePoint input, but calls different latinIME function
    private fun input(insert: String) {
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onTextInput(insert)
        handleMessages()

        if (phantomSpaceToInsert.isEmpty())
            assertEquals(oldBefore + insert, textBeforeCursor)
        else // in some cases autospace might be suppressed
            assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        assert(oldBefore + insert == textBeforeCursor || "$oldBefore $insert" == textBeforeCursor)
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getTextFromConnection())
        checkConnectionConsistency()
    }

    private fun getWordAtCursor() = connection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations)?.mWord

    private fun setCursorPosition(start: Int, end: Int = start, weirdTextField: Boolean = false) {
        val ei = EditorInfo()
        ei.inputType = currentInputType
        ei.imeOptions = ShadowInputMethodService.imeOptions
        ei.initialSelStart = start
        ei.initialSelEnd = end
        // imeOptions should not matter

        // adjust text in inputConnection first, otherwise fixLyingCursorPosition will move cursor
        // to the end of the text
        val fullText = textBeforeCursor + selectedText + textAfterCursor
        assertEquals(fullText, getTextFromConnection())

        // need to update ic before, otherwise when reloading text cache from ic, ric will load wrong text before cursor
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        selectionStart = start
        selectionEnd = end
        assertEquals(fullText, textBeforeCursor + selectedText + textAfterCursor)

        latinIME.onUpdateSelection(oldStart, oldEnd, start, end, composingStart, composingEnd)
        handleMessages()

        if (weirdTextField) {
            latinIME.mHandler.onStartInput(ei, true) // essentially does nothing
            latinIME.mHandler.onStartInputView(ei, true) // does the thing
            handleMessages()
        }

        assertEquals(fullText, getTextFromConnection())
        assertEquals(start, selectionStart)
        assertEquals(end, selectionEnd)
        checkConnectionConsistency()
    }

    // assumes we have nothing selected
    private fun getCursorPosition(): Int {
        assertEquals(cursor, connection.expectedSelectionStart)
        assertEquals(cursor, connection.expectedSelectionEnd)
        return cursor
    }

    // just sets the text and starts input so connection it set up correctly
    private fun setText(newText: String) {
        ShadowInputMethodService.text = newText
        selectionStart = newText.length
        selectionEnd = selectionStart
        composingStart = -1
        composingStart = -1

        // we need to start input to notify that something changed
        // restarting is false, so this is seen as a new text field
        val ei = EditorInfo()
        ei.inputType = currentInputType
        ei.imeOptions = ShadowInputMethodService.imeOptions
        latinIME.mHandler.onStartInput(ei, false)
        latinIME.mHandler.onStartInputView(ei, false)
        handleMessages() // this is important so the composing span is set correctly
        checkConnectionConsistency()
    }

    // like tapping a result in EmojiSearchActivity: it broadcasts the selection from onStop
    private fun emojiSearchDone(emoji: String) {
        val stuffReceiver = LatinIME::class.java.getDeclaredField("mReceiver").apply { isAccessible = true }.get(latinIME)
        val receiver = StuffReceiver::class.java.getDeclaredField("emojiSearchReceiver").apply { isAccessible = true }
            .get(stuffReceiver) as android.content.BroadcastReceiver
        val intent = android.content.Intent(helium314.keyboard.keyboard.emoji.EmojiSearchActivity.EMOJI_SEARCH_DONE_ACTION)
            .putExtra(helium314.keyboard.keyboard.emoji.EmojiSearchActivity.IME_CLOSED_KEY, false)
            .putExtra(helium314.keyboard.keyboard.emoji.EmojiSearchActivity.EMOJI_KEY, emoji)
        receiver.onReceive(latinIME, intent)
        delayedMessages.removeAll { it.callback != null } // posted switch back to emoji view, view-only
        handleMessages()
    }

    private val dict = Dictionary.PhonyDictionary("null")
    // like selecting a suggestion from strip
    private fun pickSuggestion(suggestion: String) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, dict, 0, 0)
        latinIME.pickSuggestionManually(info)
        checkConnectionConsistency()
    }

    // only works when autocorrect is on, separator after word is required
    private fun getAutocorrectedWithSpaceAfter(suggestion: String, typedWord: String) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, dict, 0, 0)
        val typedInfo = SuggestedWordInfo(typedWord, "", 0, 0, dict, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(typedInfo, info)), null, typedInfo, false, true, false, InputStyle.NONE, 0)
        latinIME.mInputLogic.setSuggestedWords(sw) // this prepares for autocorrect
        input(' ')
        checkConnectionConsistency()
    }

    private fun glideTypingInput(word: String) {
        val info = SuggestedWordInfo(word, "", 0, 0, dict, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, InputStyle.NONE, 0)
        latinIME.mInputLogic.onUpdateTailBatchInputCompleted(settingsValues, sw, KeyboardSwitcher.getInstance())
    }

    private fun checkConnectionConsistency() {
        // RichInputConnection only has composing text up to cursor, but InputConnection has full composing text
        val expectedConnectionComposingText = if (composingStart == -1 || composingEnd == -1) ""
        else text.substring(composingStart, min(composingEnd, selectionEnd))
        assert(composingText.startsWith(expectedConnectionComposingText))
        // RichInputConnection only returns text up to cursor
        val textBeforeComposingText = if (composingStart == -1) textBeforeCursor else text.substring(0, composingStart)

        println("consistency: $selectionStart, ${connection.expectedSelectionStart}, $selectionEnd, ${connection.expectedSelectionEnd}, $textBeforeComposingText, " +
                "$connectionTextBeforeComposingText, $composingText, $connectionComposingText, $textBeforeCursor, ${connection.getTextBeforeCursor(textBeforeCursor.length, 0)}" +
                ", $textAfterCursor, ${connection.getTextAfterCursor(textAfterCursor.length, 0)}")
        assertEquals(selectionStart, connection.expectedSelectionStart)
        assertEquals(selectionEnd, connection.expectedSelectionEnd)
        assertEquals(textBeforeComposingText, connectionTextBeforeComposingText)
        assertEquals(expectedConnectionComposingText, connectionComposingText)
        assertEquals(textBeforeCursor, connection.getTextBeforeCursor(textBeforeCursor.length, 0).toString())
        assertEquals(textAfterCursor, connection.getTextAfterCursor(textAfterCursor.length, 0).toString())
    }

    private fun getTextFromConnection() =
        connection.getTextBeforeCursor(100, 0).toString() + (connection.getSelectedText(0) ?: "") + connection.getTextAfterCursor(100, 0)

    private fun setInputType(inputType: Int) {
        // set text to actually apply input type
        currentInputType = inputType
        setText(text)
    }

    // always need to handle messages for proper simulation
    private fun handleMessages() {
        while (messages.isNotEmpty()) {
            latinIME.mHandler.handleMessage(messages.first())
            messages.removeAt(0)
        }
        while (delayedMessages.isNotEmpty()) {
            val msg = delayedMessages.first()
            if (msg.what != 2) // MSG_UPDATE_SUGGESTION_STRIP, we want to ignore it because it's irrelevant and has a 500 ms timeout
                latinIME.mHandler.handleMessage(delayedMessages.first())
            delayedMessages.removeAt(0)
            // delayed messages may post further messages, handle before next delayed message
            while (messages.isNotEmpty()) {
                latinIME.mHandler.handleMessage(messages.first())
                messages.removeAt(0)
            }
        }
        assertEquals(0, messages.size)
        assertEquals(0, delayedMessages.size)
    }

}

private var currentScript = ScriptUtils.SCRIPT_LATIN
private val messages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService
private val delayedMessages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService

// Shadows are handled by Robolectric. @Implementation overrides built-in functionality.
// This is used for avoiding crashes (LocaleManagerCompat, InputMethodManager, KeyboardSwitcher)
// and for simulating system stuff (InputMethodService for controlling the InputConnection, which
// more or less is the contents of the text field), and for setting the current script in
// KeyboardSwitcher without having to care about InputMethodSubtypes

@Implements(Handler::class)
class ShadowHandler {
    @Implementation
    fun sendMessage(message: Message) {
        messages.add(message)
    }
    @Implementation
    fun sendMessageDelayed(message: Message, delay: Long) {
        delayedMessages.add(message)
    }
}

@Implements(KeyboardSwitcher::class)
class ShadowKeyboardSwitcher {
    @Implementation
    // basically only needed for null check
    fun getMainKeyboardView(): MainKeyboardView = Mockito.mock(MainKeyboardView::class.java)
    @Implementation
    // only affects view
    fun setKeyboard(keyboardId: Int, toggleState: KeyboardSwitcher.KeyboardSwitchState) = Unit
    @Implementation
    // only affects view
    fun setOneHandedModeEnabled(enabled: Boolean, force: Boolean) = Unit
    @Implementation
    fun getCurrentKeyboardScript() = currentScript
}

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator2 {
    @Implementation
    fun addToUserHistory(suggestion: String, wasAutoCapitalized: Boolean,
                         ngramContext: NgramContext, timeStampInSeconds: Long,
                         blockPotentiallyOffensive: Boolean) {
        lastAddedWord = suggestion
    }
    @Implementation
    fun isValidSuggestionWord(word: String): Boolean {
        validWordLookups++
        return false
    }
    companion object {
        var lastAddedWord = ""
        var validWordLookups = 0 // RCBBoard Singlish spellings (Suggest)
    }
}

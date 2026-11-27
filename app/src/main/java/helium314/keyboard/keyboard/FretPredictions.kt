// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script

/**
 * RCBBoard: BlackBerry 10 style word predictions for the Fret (RCB Classic) style.
 *
 * Takes the suggestions RCBBoard's prediction engine already produced for the suggestion strip and places
 * up to [MAX_WORDS] of them on the keyboard, like BlackBerry 10:
 * - In-Letter: each word above the key of its next letter, e.g. "BlackBerry" above K after typing "Blac".
 *   Before the first letter of a word (next-word predictions) the word sits above its first letter.
 * - In-Column: the words in three columns above the keyboard, best in the middle.
 * A word is accepted by flicking up from its key (In-Letter) or from anywhere below it (In-Column),
 * see [PointerTracker]; accepting goes through the same path as tapping it in the suggestion strip.
 * Nothing is stored and nothing leaves the device: this only re-arranges suggestions already in memory.
 */
object FretPredictions {
    enum class Mode(val prefValue: String) {
        IN_LETTER("in_letter"), IN_COLUMN("in_column"), OFF("off");

        companion object {
            fun fromPref(value: String?) = entries.firstOrNull { it.prefValue == value } ?: IN_LETTER
        }
    }

    const val MAX_WORDS = 3
    /** the key with the emoji suggestion above it */
    const val EMOJI_KEY = ','.code
    const val COLUMNS = 3

    /** set by LatinIME, accepts a word like tapping it in the suggestion strip */
    @JvmStatic var acceptor: ((SuggestedWordInfo) -> Unit)? = null
    /** implemented by main keyboard views, there can be more than one attached */
    interface Listener {
        /** the words changed, redraw */
        fun onPredictionsChanged()
        /** [word] was flicked up and is being inserted, e.g. to animate it */
        fun onPredictionAccepted(word: SuggestedWordInfo)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<Listener>()

    @JvmStatic fun addListener(listener: Listener) { listeners.add(listener) }
    @JvmStatic fun removeListener(listener: Listener) { listeners.remove(listener) }
    private fun notifyChanged() = listeners.forEach { it.onPredictionsChanged() }

    /** the mode in use, [Mode.OFF] when the keys can't show the words, see [layoutMatchesLanguage] */
    @JvmStatic var mode = Mode.OFF
        private set

    /**
     * Whether the letters of the current keyboard are in the script of its language. Not for layouts like
     * Singlish (Sinhala written with the QWERTY layout): there the words stay in the normal suggestion strip.
     */
    @JvmStatic var layoutMatchesLanguage = true
        internal set

    /** call with each new main keyboard, before deciding whether the suggestion strip is shown */
    @JvmStatic
    fun updateLayout(keyboard: Keyboard) {
        if (!keyboard.mId.element.isAlphabet) return
        val script = keyboard.mId.locale.script()
        val letters = keyboard.sortedKeys.map { it.code }.filter { Character.isLetter(it) }
        layoutMatchesLanguage = letters.isEmpty() || letters.count { ScriptUtils.isLetterPartOfScript(it, script) } * 2 >= letters.size
    }

    /** like BlackBerry 10, no suggestion strip above the keys while the words are shown on the keys */
    @JvmStatic
    fun hidesSuggestionStrip(selectedMode: Mode) = selectedMode != Mode.OFF && layoutMatchesLanguage
    /** the letters of the current word typed so far, shown brighter in the predicted words */
    @JvmStatic var typedPrefix = ""
        private set
    private var byLetter: Map<Int, SuggestedWordInfo> = emptyMap()
    /** In-Letter: the best emoji suggestion, shown above the comma key (emoji have no next letter) */
    private var emoji: SuggestedWordInfo? = null
    private var columns: Array<SuggestedWordInfo?> = arrayOfNulls(COLUMNS)

    @JvmStatic
    fun update(words: SuggestedWords?, newMode: Mode) {
        val typed = words?.typedWordInfo?.word.orEmpty()
        val candidates = if (words == null || newMode == Mode.OFF || !layoutMatchesLanguage
                || words.inputStyle == InputStyle.UPDATE_BATCH) emptyList()
            else words.suggestedWordInfoList.filter {
                it.word.isNotEmpty() && !it.isKindOf(SuggestedWordInfo.KIND_TYPED)
                        && !it.isKindOf(SuggestedWordInfo.KIND_HARDCODED) && !it.word.equals(typed, ignoreCase = true)
            }
        typedPrefix = typed
        mode = if (layoutMatchesLanguage) newMode else Mode.OFF
        byLetter = if (mode == Mode.IN_LETTER) mapToNextLetters(candidates, typed) else emptyMap()
        emoji = if (mode == Mode.IN_LETTER) candidates.firstOrNull { it.isEmoji } else null
        columns = arrayOfNulls(COLUMNS)
        if (mode == Mode.IN_COLUMN) {
            // best word in the middle, then left, then right
            candidates.getOrNull(0)?.let { columns[1] = it }
            candidates.getOrNull(1)?.let { columns[0] = it }
            candidates.getOrNull(2)?.let { columns[2] = it }
        }
        notifyChanged()
    }

    @JvmStatic
    fun clear() {
        if (byLetter.isEmpty() && emoji == null && columns.all { it == null }) return
        byLetter = emptyMap()
        emoji = null
        columns = arrayOfNulls(COLUMNS)
        typedPrefix = ""
        notifyChanged()
    }

    /** In-Letter: letter code to word, best ranked first */
    @JvmStatic
    fun lettersByRank(): List<Pair<Int, SuggestedWordInfo>> = byLetter.entries.map { it.key to it.value }

    /** In-Letter: the word shown above the key with this code, if any */
    @JvmStatic
    fun wordForLetter(code: Int): SuggestedWordInfo? = byLetter[Character.toLowerCase(code)]

    /** In-Letter: the emoji shown above the comma key, if any */
    @JvmStatic
    fun emojiWord(): SuggestedWordInfo? = emoji

    /** In-Column: the word of column 0 (left), 1 (middle) or 2 (right) */
    @JvmStatic
    fun wordForColumn(column: Int): SuggestedWordInfo? = columns.getOrNull(column)

    /** the word a flick up from [key] at [x] accepts, for a keyboard [keyboardWidth] wide */
    @JvmStatic
    fun wordForTouch(key: Key, x: Int, keyboardWidth: Int): SuggestedWordInfo? = when (mode) {
        Mode.IN_LETTER -> if (key.code == EMOJI_KEY) emoji else wordForLetter(key.code)
        Mode.IN_COLUMN -> if (keyboardWidth > 0) wordForColumn((x * COLUMNS / keyboardWidth).coerceIn(0, COLUMNS - 1)) else null
        Mode.OFF -> null
    }

    @JvmStatic
    fun accept(word: SuggestedWordInfo) {
        listeners.forEach { it.onPredictionAccepted(word) }
        acceptor?.invoke(word)
    }

    /** each word goes to the key of the letter after [typed], the best ranked word wins a key */
    internal fun mapToNextLetters(candidates: List<SuggestedWordInfo>, typed: String): Map<Int, SuggestedWordInfo> {
        val result = LinkedHashMap<Int, SuggestedWordInfo>()
        for (info in candidates) {
            if (result.size >= MAX_WORDS) break
            val word = info.word
            if (word.length <= typed.length || !word.regionMatches(0, typed, 0, typed.length, ignoreCase = true)) continue
            val next = Character.toLowerCase(word.codePointAt(typed.length))
            if (!Character.isLetter(next) || next in result) continue
            result[next] = info
        }
        return result
    }
}

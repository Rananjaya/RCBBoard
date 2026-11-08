// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo

/**
 * RCBBoard Singlish (phase 2A): repaya and the bundled Sinhala dictionary.
 *
 * Unicode and Android draw repaya only for ර + ් + ZWJ (U+200D) + consonant; without ZWJ the
 * hal is shown separately. The bundled word list mostly stores repaya without ZWJ (about 2,980
 * words such as කර්ම, against about 30 with it). Singlish therefore always types the ZWJ form, and
 * for suggestions it also looks up the form without ZWJ and shows every suggestion in the ZWJ form,
 * so that picking a suggestion commits correctly rendered text.
 *
 * Used only for the Sinhala (Singlish) subtype (see Suggest); the Sinhala and Wijesekara keyboards
 * and the dictionary itself are not changed.
 */
object SinglishRepaya {
    private const val RA = 'ර'
    private const val HAL = '්'
    private const val ZWJ = '‍'

    private fun isConsonant(c: Char?) = c != null && c in 'ක'..'ෆ'

    /** ර්‍ + consonant → ර් + consonant (the spelling used by most entries of the word list). */
    @JvmStatic
    fun withoutRepayaZwj(word: String): String {
        if (word.indexOf(ZWJ) < 0) return word
        val out = StringBuilder(word.length)
        var i = 0
        while (i < word.length) {
            val c = word[i]
            out.append(c)
            if (c == RA && word.getOrNull(i + 1) == HAL && word.getOrNull(i + 2) == ZWJ && isConsonant(word.getOrNull(i + 3))) {
                out.append(HAL)
                i += 3 // skip ් and ZWJ, the consonant is appended next
                continue
            }
            i++
        }
        return out.toString()
    }

    /** ර් + consonant → ර්‍ + consonant (the form Android renders as repaya). Already correct text is unchanged. */
    @JvmStatic
    fun withRepayaZwj(word: String): String {
        if (word.indexOf(RA) < 0) return word
        val out = StringBuilder(word.length + 2)
        var i = 0
        while (i < word.length) {
            val c = word[i]
            out.append(c)
            if (c == RA && word.getOrNull(i + 1) == HAL && isConsonant(word.getOrNull(i + 2))) {
                out.append(HAL).append(ZWJ)
                i += 2
                continue
            }
            i++
        }
        return out.toString()
    }

    /**
     * Suggestions for a Singlish [typedWord]: [suggestions] from the normal lookup, plus [lookup]
     * of the spelling without repaya ZWJ when it differs. All words are returned in the ZWJ form;
     * a word found by both lookups is kept once, with its best score, ordered by score.
     */
    @JvmStatic
    fun compatibleSuggestions(
        typedWord: String,
        suggestions: Collection<SuggestedWordInfo>,
        lookup: (String) -> Collection<SuggestedWordInfo>
    ): ArrayList<SuggestedWordInfo> {
        val all = ArrayList(suggestions)
        val withoutZwj = withoutRepayaZwj(typedWord)
        if (withoutZwj != typedWord) all.addAll(lookup(withoutZwj))
        val byWord = LinkedHashMap<String, SuggestedWordInfo>()
        for (info in all) {
            val word = withRepayaZwj(info.mWord)
            val converted = if (word == info.mWord) info else SuggestedWordInfo(word, info.mPrevWordsContext,
                info.mScore, info.mKindAndFlags, info.mSourceDict, info.mIndexOfTouchPointOfSecondWord,
                info.mAutoCommitFirstWordConfidence)
            val existing = byWord[word]
            if (existing == null || converted.mScore > existing.mScore) byWord[word] = converted
        }
        return ArrayList(byWord.values.sortedByDescending { it.mScore }) // stable: equal scores keep their order
    }
}

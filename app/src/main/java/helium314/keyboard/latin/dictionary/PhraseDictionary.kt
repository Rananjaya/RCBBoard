// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictionary

import android.content.Context
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.utils.Log
import java.nio.ByteBuffer
import java.util.Locale

/**
 * RCBBoard: trigram next-word predictions for English, from a read-only phrase file in assets/phrases
 * (built by tools/english-dictionaries/build_english_trigrams.py, format below).
 *
 * It answers only for next-word predictions (nothing typed yet) after two words, with the most likely next
 * words and a 0-255 score on the same scale as the main dictionary's bigram predictions. The results are
 * merged with the main dictionary's bigrams and the user history by score, so the dictionary's bigrams back
 * off for contexts it doesn't know. It never contributes to corrections, autocorrect or learning, and has no
 * words of its own ([isInDictionary] is always false).
 *
 * File: "RCBP", u16 version, u16 reserved, u32 vocabulary / context / entry counts; vocabulary: u32 offsets
 * (count + 1) and the UTF-8 words, sorted by their bytes; contexts: u64 keys (id1 << 32 | id2) sorted, u32 entry
 * starts (count + 1); entries: u32 (word id << 8 | score). Big endian.
 */
class PhraseDictionary private constructor(locale: Locale, private val data: ByteBuffer) :
    Dictionary(TYPE_PHRASES, locale) {

    private val vocabularyCount = data.getInt(8)
    private val contextCount = data.getInt(12)
    private val vocabularyOffsets = 20
    private val vocabularyBytes = vocabularyOffsets + 4 * (vocabularyCount + 1)
    private val contextKeys = vocabularyBytes + data.getInt(vocabularyOffsets + 4 * vocabularyCount)
    private val entryStarts = contextKeys + 8 * contextCount
    private val entries = entryStarts + 4 * (contextCount + 1)

    override fun getSuggestions(
        composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>? {
        if (composedData.mIsBatchMode || composedData.mTypedWord.isNotEmpty()) return null
        if (ngramContext.isNthPrevWordBeginningOfSentence(1) || ngramContext.isNthPrevWordBeginningOfSentence(2))
            return null
        val previous = wordId(ngramContext.getNthPrevWord(1)) ?: return null
        val beforePrevious = wordId(ngramContext.getNthPrevWord(2)) ?: return null
        val context = findContext((beforePrevious.toLong() shl 32) or previous.toLong())
        if (context < 0) return null
        val start = data.getInt(entryStarts + 4 * context)
        val end = data.getInt(entryStarts + 4 * (context + 1))
        val result = ArrayList<SuggestedWordInfo>(end - start)
        for (i in start until end) {
            val entry = data.getInt(entries + 4 * i)
            val score = ((entry and 0xFF) * weightForLocale).toInt()
            result.add(SuggestedWordInfo(word(entry ushr 8), "", score, SuggestedWordInfo.KIND_PREDICTION, this,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
        return result
    }

    override fun isInDictionary(word: String) = false

    /** the id of [word], or of its lower case form (a capitalised word at the start of a sentence) */
    private fun wordId(word: CharSequence?): Int? {
        if (word.isNullOrEmpty()) return null
        val text = word.toString()
        return findWord(text.toByteArray()) ?: text.lowercase(mLocale).takeIf { it != text }
            ?.let { findWord(it.toByteArray()) }
    }

    private fun findWord(bytes: ByteArray): Int? {
        var low = 0
        var high = vocabularyCount - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val comparison = compareWord(middle, bytes)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> return middle
            }
        }
        return null
    }

    /** compares the stored word [id] with [bytes], byte by byte (unsigned), as the file is sorted */
    private fun compareWord(id: Int, bytes: ByteArray): Int {
        val start = vocabularyBytes + data.getInt(vocabularyOffsets + 4 * id)
        val length = vocabularyBytes + data.getInt(vocabularyOffsets + 4 * (id + 1)) - start
        for (i in 0 until minOf(length, bytes.size)) {
            val difference = (data.get(start + i).toInt() and 0xFF) - (bytes[i].toInt() and 0xFF)
            if (difference != 0) return difference
        }
        return length - bytes.size
    }

    private fun word(id: Int): String {
        val start = vocabularyBytes + data.getInt(vocabularyOffsets + 4 * id)
        val end = vocabularyBytes + data.getInt(vocabularyOffsets + 4 * (id + 1))
        val bytes = ByteArray(end - start)
        for (i in bytes.indices) bytes[i] = data.get(start + i)
        return String(bytes, Charsets.UTF_8)
    }

    private fun findContext(key: Long): Int {
        var low = 0
        var high = contextCount - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val stored = data.getLong(contextKeys + 8 * middle)
            when {
                stored < key -> low = middle + 1
                stored > key -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    companion object {
        const val TYPE_PHRASES = "phrases"
        private const val TAG = "PhraseDictionary"
        private const val VERSION = 1

        /** the phrase dictionary for [locale] (English only), or null if there is none or it can't be read */
        @JvmStatic
        fun create(context: Context, locale: Locale): PhraseDictionary? {
            if (locale.language != "en") return null
            val name = "phrases/${locale.language}-${locale.country}.phrases"
            return try {
                val bytes = context.assets.open(name).use { it.readBytes() }
                val data = ByteBuffer.wrap(bytes) // big endian, as written by the build script
                if (bytes.size < 20 || bytes[0] != 'R'.code.toByte() || bytes[1] != 'C'.code.toByte()
                        || bytes[2] != 'B'.code.toByte() || bytes[3] != 'P'.code.toByte()
                        || data.getShort(4).toInt() != VERSION) {
                    Log.w(TAG, "$name is not a phrase file of version $VERSION")
                    null
                } else PhraseDictionary(locale, data)
            } catch (_: java.io.FileNotFoundException) {
                null // no phrases for this locale
            } catch (e: Exception) {
                Log.w(TAG, "could not read $name", e)
                null
            }
        }
    }
}

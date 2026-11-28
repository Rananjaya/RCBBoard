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
 * It answers only for next-word predictions (nothing typed yet): after three words from the 4-gram table, and after
 * two words from the trigram table, with a 0-255 score on the same scale as the main dictionary's bigram
 * predictions (the longer context scores higher). The results are merged with the main dictionary's bigrams and
 * the user history by score, so shorter contexts are the back-off for longer ones the file doesn't know. It never
 * contributes to corrections, autocorrect or learning, and has no words of its own ([isInDictionary] is false).
 *
 * File (version 2, big endian): "RCBP", u16 version, u16 reserved, u32 vocabulary count, u32 offsets (count + 1)
 * and the UTF-8 words sorted by their bytes; then two sections, two-word and three-word contexts, each: u32
 * context count, u32 entry count, u64 keys sorted (id1 << 32 | id2, or id1 << 42 | id2 << 21 | id3), u32 entry
 * starts (count + 1), u32 entries (word id << 8 | score).
 */
class PhraseDictionary private constructor(locale: Locale, private val data: ByteBuffer) :
    Dictionary(TYPE_PHRASES, locale) {

    private class Section(data: ByteBuffer, start: Int) {
        val contextCount = data.getInt(start)
        val keys = start + 8
        val starts = keys + 8 * contextCount
        val entries = starts + 4 * (contextCount + 1)
        val end = entries + 4 * data.getInt(start + 4)
    }

    private val vocabularyCount = data.getInt(8)
    private val vocabularyOffsets = 12
    private val vocabularyBytes = vocabularyOffsets + 4 * (vocabularyCount + 1)
    private val twoWords = Section(data, vocabularyBytes + data.getInt(vocabularyOffsets + 4 * vocabularyCount))
    private val threeWords = Section(data, twoWords.end)

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
        val result = ArrayList<SuggestedWordInfo>()
        addEntries(twoWords, (beforePrevious.toLong() shl 32) or previous.toLong(), weightForLocale, result)
        if (!ngramContext.isNthPrevWordBeginningOfSentence(3)) {
            val third = wordId(ngramContext.getNthPrevWord(3))
            if (third != null)
                addEntries(threeWords, (third.toLong() shl 42) or (beforePrevious.toLong() shl 21) or previous.toLong(),
                    weightForLocale, result)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun addEntries(section: Section, key: Long, weightForLocale: Float, result: ArrayList<SuggestedWordInfo>) {
        val context = findContext(section, key)
        if (context < 0) return
        for (i in data.getInt(section.starts + 4 * context) until data.getInt(section.starts + 4 * (context + 1))) {
            val entry = data.getInt(section.entries + 4 * i)
            val score = ((entry and 0xFF) * weightForLocale).toInt()
            result.add(SuggestedWordInfo(word(entry ushr 8), "", score, SuggestedWordInfo.KIND_PREDICTION, this,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
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

    private fun findContext(section: Section, key: Long): Int {
        var low = 0
        var high = section.contextCount - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val stored = data.getLong(section.keys + 8 * middle)
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
        private const val VERSION = 2

        /** the phrase dictionary for [locale] (English only), or null if there is none or it can't be read */
        @JvmStatic
        fun create(context: Context, locale: Locale): PhraseDictionary? {
            if (locale.language != "en") return null
            val name = "${locale.language}-${locale.country}.phrases"
            return try {
                val data = map(context, "phrases/$name") ?: return null
                if (data.capacity() < 12 || data.get(0) != 'R'.code.toByte() || data.get(1) != 'C'.code.toByte()
                        || data.get(2) != 'B'.code.toByte() || data.get(3) != 'P'.code.toByte()
                        || data.getShort(4).toInt() != VERSION) {
                    Log.w(TAG, "$name is not a phrase file of version $VERSION")
                    null
                } else PhraseDictionary(locale, data)
            } catch (e: Exception) {
                Log.w(TAG, "could not read $name", e)
                null
            }
        }

        /**
         * The phrase file, memory-mapped from a copy in the cache (copied once per asset version), so it takes no
         * heap and is shared by every user of it (keyboard and spell checker). Null if there is no such asset.
         */
        private fun map(context: Context, asset: String): java.nio.MappedByteBuffer? {
            val directory = java.io.File(context.cacheDir, "phrases").apply { mkdirs() }
            val baseName = asset.substringAfter('/')
            if (context.assets.list(asset.substringBefore('/'))?.contains(baseName) != true) return null
            // a new copy after every app install or update, when the asset may have changed
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val file = java.io.File(directory, "$baseName.$installed")
            synchronized(LOCK) {
                if (!file.isFile) {
                    directory.listFiles()?.filter { it.name.startsWith("$baseName.") }?.forEach { it.delete() } // old versions
                    val temp = java.io.File(directory, "$baseName.tmp")
                    context.assets.open(asset).use { input -> temp.outputStream().use { input.copyTo(it) } }
                    if (!temp.renameTo(file)) return null
                }
            }
            val length = file.length()
            return java.io.RandomAccessFile(file, "r").use { it.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, length) }
        }

        private val LOCK = Any()
    }
}

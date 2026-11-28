// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictionary.history

import android.content.Context
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import java.io.File

/**
 * RCBBoard: how many words the keyboard has learned, for the settings. Reads the word count the native library
 * writes into the header of each saved user history dictionary (UNIGRAM_COUNT), without loading the dictionaries.
 * Words learned in the last seconds are counted once they are saved.
 */
object LearnedWords {
    private const val COUNT_KEY = "UNIGRAM_COUNT"

    /** learned words of all languages, or 0 if nothing was learned yet */
    fun count(context: Context): Int =
        listOfNotNull(context.filesDir, DeviceProtectedUtils.getFilesDir(context)).distinct()
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.name.startsWith(UserHistoryDictionary.NAME) } }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.name.endsWith(".header") }.toList() }
            .distinctBy { it.canonicalPath }
            .sumOf { headerAttributes(it)[COUNT_KEY]?.toIntOrNull() ?: 0 }

    /** the attributes of a dictionary header: magic, version, flags, size, then key and value strings */
    internal fun headerAttributes(file: File): Map<String, String> = runCatching {
        val bytes = file.readBytes()
        if (bytes.size < 12) return emptyMap()
        val size = ((bytes[8].toInt() and 0xFF) shl 24) or ((bytes[9].toInt() and 0xFF) shl 16) or
            ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        val end = minOf(size, bytes.size)
        val result = HashMap<String, String>()
        var pos = 12
        fun string(): String {
            val text = StringBuilder()
            while (pos < end) {
                val first = bytes[pos].toInt() and 0xFF
                if (first == 0x1F) { pos++; break }
                if (first < 0x20) { // three-byte code point
                    if (pos + 2 >= end) { pos = end; break }
                    text.appendCodePoint((first shl 16) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or (bytes[pos + 2].toInt() and 0xFF))
                    pos += 3
                } else {
                    text.append(first.toChar())
                    pos++
                }
            }
            return text.toString()
        }
        while (pos < end) {
            val key = string()
            result[key] = string()
        }
        result
    }.getOrDefault(emptyMap())
}

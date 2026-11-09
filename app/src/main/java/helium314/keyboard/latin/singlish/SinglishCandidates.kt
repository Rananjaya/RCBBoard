// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

/**
 * RCBBoard Singlish (phase 2B, stage 1): alternative Sinhala spellings for a transliterated word.
 *
 * [SinglishTransliterator] gives exactly one result, but several Singlish letters are ambiguous in
 * everyday typing (`d` for ඩ or ද, `a` for අ or ආ, `sh` for ශ or ෂ, `n` + `h` for න්හ or ංහ, ...).
 * This object takes that Sinhala result and generates spellings that differ only in these
 * ambiguity classes (general letter classes, never corrections for particular words), and keeps
 * the ones the dictionary knows.
 *
 * - Every changed letter, inserted ා or replaced letter sequence costs 1.
 * - At most [MAX_SUBSTITUTIONS] changes per spelling and [MAX_VARIANTS] spellings per word,
 *   including the word itself. Spellings are generated cost by cost (all cost 1 spellings before
 *   any cost 2 spelling), so the limit drops the most expensive spellings first.
 * - A spelling is valid if the dictionary has it as written or without the repaya ZWJ (the word
 *   list mostly stores repaya without ZWJ, see [SinglishRepaya]). Results always use the ZWJ form,
 *   which is how Android draws rakaransaya, yansaya and repaya.
 *
 * Pure and deterministic, no Android and no state: nothing typed is stored or logged.
 * Not used by the keyboard yet.
 */
object SinglishCandidates {
    const val MAX_SUBSTITUTIONS = 3
    const val MAX_VARIANTS = 64

    /** A Sinhala spelling and the number of changes from the transliterated word. */
    data class Candidate(val word: String, val cost: Int)

    private const val AA_SIGN = "ා"

    /** Single letters and the letters they can also stand for. */
    private val LETTER_ALTERNATIVES: Map<Char, List<String>> = mapOf(
        // retroflex and dental: d, t
        'ඩ' to listOf("ද"),
        'ද' to listOf("ධ", "ඩ"),
        'ට' to listOf("ත"),
        'ත' to listOf("ථ", "ට"),
        // sibilants, nasals, laterals: sh, n, l
        'ශ' to listOf("ෂ"),
        'න' to listOf("ණ"),
        'ල' to listOf("ළ"),
        // vowel length: independent vowels
        'අ' to listOf("ආ"),
        'ඉ' to listOf("ඊ"),
        'උ' to listOf("ඌ"),
        'එ' to listOf("ඒ"),
        'ඔ' to listOf("ඕ"),
        // vowel length: vowel signs
        'ි' to listOf("ී"),
        'ු' to listOf("ූ"),
        'ෙ' to listOf("ේ"),
        'ො' to listOf("ෝ"),
    )

    /** Letter sequences and the sequences they can also stand for. */
    private val SEQUENCE_ALTERNATIVES: List<Pair<String, String>> = listOf(
        "න්හ" to "ංහ", // n + h: anusvara before h
        "න්ග" to "ංග", // n + g: anusvara before g
        "ම්බ" to "ඹ", // m + b: prenasalised b
    )

    /** One place in the word that can change: the text in [start, end) can be replaced by each of [replacements]. */
    private class Slot(val start: Int, val end: Int, val replacements: List<String>) {
        // an insertion (start == end) overlaps another insertion at the same place, or a slot it is strictly inside
        fun overlaps(other: Slot): Boolean = when {
            start == end && other.start == other.end -> start == other.start
            start == end -> other.start < start && start < other.end
            other.start == other.end -> start < other.start && other.start < end
            else -> start < other.end && other.start < end
        }
    }

    private fun isConsonant(c: Char) = c in 'ක'..'ෆ'

    /** A consonant that is not followed by a vowel sign or hal kirīma has the inherent vowel a. */
    private fun hasInherentVowel(word: String, index: Int): Boolean {
        if (!isConsonant(word[index])) return false
        val next = word.getOrNull(index + 1) ?: return true
        return !(next in 'ා'..'ෟ' || next == '්' || next == 'ෲ' || next == 'ෳ')
    }

    private fun slots(word: String): List<Slot> {
        val slots = mutableListOf<Slot>()
        for (i in word.indices) {
            LETTER_ALTERNATIVES[word[i]]?.let { slots.add(Slot(i, i + 1, it)) }
            if (hasInherentVowel(word, i)) slots.add(Slot(i + 1, i + 1, listOf(AA_SIGN))) // a -> aa
        }
        for ((sequence, replacement) in SEQUENCE_ALTERNATIVES) {
            var index = word.indexOf(sequence)
            while (index >= 0) {
                slots.add(Slot(index, index + sequence.length, listOf(replacement)))
                index = word.indexOf(sequence, index + 1)
            }
        }
        return slots.sortedWith(compareBy({ it.start }, { it.end }))
    }

    /**
     * Spellings of [word] with at most [maxSubstitutions] changes and at most [maxVariants]
     * spellings, starting with [word] itself (cost 0), ordered by cost. Each spelling is listed
     * once, with its lowest cost.
     */
    @JvmStatic
    @JvmOverloads
    fun variants(word: String, maxSubstitutions: Int = MAX_SUBSTITUTIONS, maxVariants: Int = MAX_VARIANTS): List<Candidate> {
        val result = LinkedHashMap<String, Int>()
        if (maxVariants <= 0) return emptyList()
        result[word] = 0
        val slots = slots(word)
        for (cost in 1..maxSubstitutions) {
            if (result.size >= maxVariants || !addVariantsWithCost(word, slots, cost, result, maxVariants)) break
        }
        return result.map { (w, c) -> Candidate(w, c) }
    }

    /** Adds all spellings with exactly [cost] changes; false once [maxVariants] is reached. */
    private fun addVariantsWithCost(word: String, slots: List<Slot>, cost: Int,
                                    result: LinkedHashMap<String, Int>, maxVariants: Int): Boolean {
        val chosen = ArrayList<Pair<Slot, String>>(cost)
        fun recurse(fromSlot: Int): Boolean {
            if (chosen.size == cost) {
                val variant = apply(word, chosen)
                if (variant !in result) {
                    if (result.size >= maxVariants) return false
                    result[variant] = cost
                }
                return result.size < maxVariants
            }
            for (i in fromSlot until slots.size) {
                val slot = slots[i]
                if (chosen.any { it.first.overlaps(slot) }) continue
                for (replacement in slot.replacements) {
                    chosen.add(slot to replacement)
                    val more = recurse(i + 1)
                    chosen.removeAt(chosen.size - 1)
                    if (!more) return false
                }
            }
            return true
        }
        return recurse(0)
    }

    private fun apply(word: String, changes: List<Pair<Slot, String>>): String {
        val out = StringBuilder(word.length + changes.size)
        var position = 0
        for ((slot, replacement) in changes.sortedWith(compareBy({ it.first.start }, { it.first.end }))) {
            out.append(word, position, slot.start).append(replacement)
            position = slot.end
        }
        return out.append(word, position, word.length).toString()
    }

    /**
     * The spellings of [word] that [isValidWord] accepts, as written or without the repaya ZWJ,
     * in the ZWJ form and ordered by cost (lowest first). [word] itself is included with cost 0
     * when it is valid. Empty when no spelling is valid.
     */
    @JvmStatic
    @JvmOverloads
    fun candidates(
        word: String,
        isValidWord: (String) -> Boolean,
        maxSubstitutions: Int = MAX_SUBSTITUTIONS,
        maxVariants: Int = MAX_VARIANTS,
    ): List<Candidate> {
        if (word.isEmpty()) return emptyList()
        val found = LinkedHashMap<String, Int>()
        for ((variant, cost) in variants(word, maxSubstitutions, maxVariants)) {
            val withoutZwj = SinglishRepaya.withoutRepayaZwj(variant)
            if (!isValidWord(variant) && (withoutZwj == variant || !isValidWord(withoutZwj))) continue
            val shown = SinglishRepaya.withRepayaZwj(variant)
            if (shown !in found) found[shown] = cost // variants are ordered by cost, so the first is the lowest
        }
        return found.map { (w, c) -> Candidate(w, c) }
    }
}

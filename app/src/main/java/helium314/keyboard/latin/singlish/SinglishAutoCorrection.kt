// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import helium314.keyboard.latin.singlish.SinglishCandidates.Candidate

/**
 * RCBBoard Singlish (phase 2B, stage 3): which spelling, if any, Space and punctuation may put in
 * place of the transliterated word.
 *
 * Deliberately conservative, because the Sinhala word list has no word frequencies:
 * - a typed word the dictionaries know (main, personal or learned) is never replaced;
 * - only one spelling may have the lowest cost, otherwise nothing is chosen (no guessing);
 * - at most [MAX_COST] changes, and only for words of at least [MIN_LENGTH] code points;
 * - never for words with digits.
 *
 * Uses the dictionary-checked spellings that the suggestions already found (no extra lookups).
 * Pure and deterministic: nothing typed is stored or logged.
 */
object SinglishAutoCorrection {
    const val MAX_COST = 2
    const val MIN_LENGTH = 3

    /**
     * The spelling to auto-correct [typedWord] to, or null to keep it.
     * [candidates] are the valid spellings of [typedWord] as [SinglishCandidates.candidates] returns
     * them: ordered by cost, in ZWJ form, including [typedWord] itself with cost 0 when it is valid.
     */
    @JvmStatic
    fun choose(typedWord: String, candidates: List<Candidate>): String? {
        if (typedWord.codePointCount(0, typedWord.length) < MIN_LENGTH) return null
        if (typedWord.any { Character.isDigit(it) }) return null
        if (candidates.any { it.cost == 0 }) return null // the typed word is valid: keep it
        val cheapest = candidates.minOfOrNull { it.cost } ?: return null
        if (cheapest > MAX_COST) return null
        val best = candidates.filter { it.cost == cheapest }
        if (best.size != 1) return null // several equally good spellings: don't guess
        if (!isCostLevelComplete(typedWord, cheapest)) return null
        return best.single().word.takeIf { it != typedWord }
    }

    /**
     * Whether all spellings with [cost] changes were generated. The number of generated spellings is
     * limited, so an equally cheap spelling might be missing, and then the best one is not certain.
     */
    private fun isCostLevelComplete(typedWord: String, cost: Int): Boolean {
        val variants = SinglishCandidates.variants(typedWord)
        return variants.size < SinglishCandidates.MAX_VARIANTS || variants.last().cost > cost
    }
}

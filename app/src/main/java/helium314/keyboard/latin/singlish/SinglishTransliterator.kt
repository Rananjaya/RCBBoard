// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.singlish

import java.text.Normalizer

/**
 * RCBBoard Singlish: converts phonetically typed Latin letters to Sinhala.
 *
 * Pure and deterministic (no Android, no state between calls), so the same input always gives the
 * same output. The mapping follows the widely used Helakuru-style convention and is written for
 * RCBBoard; the full table and its references are in docs/singlish.md.
 *
 * How it works:
 *  1. The input is split into tokens by longest match against [SinglishRules] (`zdh` before `zd`
 *     before `z`; `dhh` before `dh` before `d`). An unmapped capital letter falls back to its
 *     lowercase rule (`M` types like `m`), anything else passes through unchanged.
 *  2. Tokens are rendered with context: a consonant followed by a vowel takes that vowel's sign
 *     (`a` keeps the inherent vowel), a consonant followed by another consonant or by the end of
 *     the word gets hal kirīma (්), `Y`/`R` after a consonant give yansaya/rakaransaya, and `R`
 *     before a consonant (not after one) gives repaya.
 *  3. The result is NFC-normalised.
 *
 * Phase 1 gives exactly one result. Spellings that the rules map differently from the intended word
 * (for example `gedara` gives ගෙඩර, the intended ගෙදර needs `gedhara`) are left to the candidate
 * generation planned for phase 2.
 */
object SinglishTransliterator {
    private const val HAL = "්" // ් hal kirīma (virama)
    private const val ZWJ = "‍"
    private const val YA = "ය" // ය
    private const val RA = "ර" // ර

    /** Letters that the Singlish keyboard collects into a word (ASCII letters only). */
    @JvmStatic
    fun isSinglishLetter(codePoint: Int): Boolean =
        codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code

    @JvmStatic
    fun transliterate(input: CharSequence): String {
        if (input.isEmpty()) return ""
        return Normalizer.normalize(render(tokenize(input.toString())), Normalizer.Form.NFC)
    }

    internal sealed interface Token {
        data class Rule(val rule: SinglishRules.Rule) : Token
        data class PassThrough(val text: String) : Token
    }

    internal fun tokenize(input: String): List<Token> {
        val tokens = ArrayList<Token>(input.length)
        var i = 0
        while (i < input.length) {
            val match = longestMatch(input, i)
            if (match != null) {
                tokens.add(Token.Rule(match))
                i += match.key.length
                continue
            }
            val c = input[i]
            val lowercaseRule = if (c in 'A'..'Z') SinglishRules.byKey[c.lowercase()] else null
            if (lowercaseRule != null) {
                tokens.add(Token.Rule(lowercaseRule))
                i += 1
                continue
            }
            val cp = input.codePointAt(i)
            val length = Character.charCount(cp)
            tokens.add(Token.PassThrough(input.substring(i, i + length)))
            i += length
        }
        return tokens
    }

    private fun longestMatch(input: String, start: Int): SinglishRules.Rule? {
        val maxLength = minOf(SinglishRules.maxKeyLength, input.length - start)
        for (length in maxLength downTo 1) {
            SinglishRules.byKey[input.substring(start, start + length)]?.let { return it }
        }
        return null
    }

    private fun render(tokens: List<Token>): String {
        val out = StringBuilder()
        // true while the last output is a consonant (or conjunct) without a vowel yet
        var consonantPending = false
        tokens.forEachIndexed { index, token ->
            if (token is Token.PassThrough) {
                if (consonantPending) out.append(HAL)
                out.append(token.text)
                consonantPending = false
                return@forEachIndexed
            }
            val rule = (token as Token.Rule).rule
            when (rule.kind) {
                SinglishRules.Kind.CONSONANT -> {
                    if (consonantPending) out.append(HAL)
                    out.append(rule.letter)
                    consonantPending = true
                }
                SinglishRules.Kind.VOWEL -> {
                    out.append(if (consonantPending) rule.sign else rule.letter)
                    consonantPending = false
                }
                SinglishRules.Kind.SIGN -> { // ං ඃ follow the consonant with its inherent vowel
                    out.append(rule.letter)
                    consonantPending = false
                }
                SinglishRules.Kind.YANSAYA -> {
                    out.append(if (consonantPending) HAL + ZWJ + YA else YA)
                    consonantPending = true
                }
                SinglishRules.Kind.RAKARANSAYA -> {
                    val next = tokens.getOrNull(index + 1)
                    val nextIsConsonant = next is Token.Rule && next.rule.kind == SinglishRules.Kind.CONSONANT
                    when {
                        consonantPending -> { // rakaransaya: ක්‍ර
                            out.append(HAL + ZWJ + RA)
                            consonantPending = true
                        }
                        nextIsConsonant -> { // repaya: ර්‍ before the next consonant
                            out.append(RA + HAL + ZWJ)
                            consonantPending = false
                        }
                        else -> { // plain ර
                            out.append(RA)
                            consonantPending = true
                        }
                    }
                }
            }
        }
        if (consonantPending) out.append(HAL)
        return out.toString()
    }
}

/**
 * The RCBBoard Singlish mapping table. Keep docs/singlish.md and SinglishTransliteratorTest in sync
 * when changing it. Keys are case-sensitive; capitals select a different letter where listed.
 */
object SinglishRules {
    enum class Kind { VOWEL, CONSONANT, SIGN, YANSAYA, RAKARANSAYA }

    /** [letter] is the independent form, [sign] the vowel sign after a consonant (empty for `a`). */
    class Rule(val key: String, val kind: Kind, val letter: String, val sign: String = "")

    private fun vowel(key: String, letter: String, sign: String) = Rule(key, Kind.VOWEL, letter, sign)
    private fun consonant(key: String, letter: String) = Rule(key, Kind.CONSONANT, letter)

    val rules: List<Rule> = listOf(
        // vowels: independent letter / vowel sign
        vowel("a", "අ", ""), vowel("aa", "ආ", "ා"),
        vowel("A", "ඇ", "ැ"), vowel("Aa", "ඈ", "ෑ"), vowel("AA", "ඈ", "ෑ"),
        vowel("i", "ඉ", "ි"), vowel("ii", "ඊ", "ී"),
        vowel("u", "උ", "ු"), vowel("uu", "ඌ", "ූ"),
        vowel("e", "එ", "ෙ"), vowel("ee", "ඒ", "ේ"),
        vowel("o", "ඔ", "ො"), vowel("oo", "ඕ", "ෝ"),
        vowel("ai", "ඓ", "ෛ"), vowel("au", "ඖ", "ෞ"),

        // consonants
        consonant("k", "ක"), consonant("kh", "ඛ"),
        consonant("g", "ග"), consonant("gh", "ඝ"),
        consonant("ch", "ච"), consonant("chh", "ඡ"),
        consonant("j", "ජ"), consonant("jh", "ඣ"),
        consonant("t", "ට"), consonant("T", "ඨ"), // retroflex
        consonant("d", "ඩ"), consonant("D", "ඪ"), // retroflex
        consonant("N", "ණ"),
        consonant("th", "ත"), consonant("thh", "ථ"), // dental
        consonant("dh", "ද"), consonant("dhh", "ධ"), // dental
        consonant("n", "න"),
        consonant("p", "ප"), consonant("ph", "ඵ"),
        consonant("b", "බ"), consonant("bh", "භ"),
        consonant("m", "ම"),
        consonant("y", "ය"), consonant("r", "ර"),
        consonant("l", "ල"), consonant("L", "ළ"),
        consonant("v", "ව"), consonant("w", "ව"),
        consonant("s", "ස"), consonant("sh", "ශ"), consonant("S", "ෂ"), consonant("Sh", "ෂ"),
        consonant("h", "හ"), consonant("f", "ෆ"),

        // prenasalised (sanyaka) and other nasals
        consonant("zg", "ඟ"), consonant("zj", "ඦ"), consonant("zd", "ඬ"),
        consonant("zdh", "ඳ"), consonant("zb", "ඹ"), consonant("B", "ඹ"),
        consonant("zk", "ඤ"), consonant("zh", "ඥ"), consonant("X", "ඞ"),

        // anusvara and visarga
        Rule("x", Kind.SIGN, "ං"), Rule("H", Kind.SIGN, "ඃ"),

        // yansaya / rakaransaya (repaya before a consonant), see SinglishTransliterator
        Rule("Y", Kind.YANSAYA, "ය"), Rule("R", Kind.RAKARANSAYA, "ර"),
    )

    val byKey: Map<String, Rule> = rules.associateBy { it.key }.also {
        check(it.size == rules.size) { "duplicate Singlish key" }
    }

    val maxKeyLength: Int = rules.maxOf { it.key.length }
}

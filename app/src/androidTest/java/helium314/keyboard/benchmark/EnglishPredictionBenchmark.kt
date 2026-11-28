// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.benchmark

import android.content.Context
import android.os.Debug
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.test.platform.app.InstrumentationRegistry
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.DictionaryFacilitatorProvider
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.event.Event
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.suggestions.Suggest
import helium314.keyboard.latin.utils.SubtypeSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Measures English prediction and autocorrection of the real engine (Suggest, the dictionaries and the native
 * library) on a device or emulator, with a fresh install's state: no personal history, contacts or app names.
 *
 * Inputs are held-out sentences that were not used to build the dictionaries (tools/english-dictionaries), in
 * androidTest assets benchmark/<locale>/. Taps get seeded Gaussian noise so typos are reproducible.
 * Results go to logcat (tag RCBBoardBenchmark) and to files/benchmark-<locale>.json of the app.
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.locale=en-GB
 */
@RunWith(JUnit4::class)
class EnglishPredictionBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val testAssets = instrumentation.context.assets
    private val localeTag = InstrumentationRegistry.getArguments().getString("locale") ?: "en-GB"
    private val locale: Locale = Locale.forLanguageTag(localeTag)

    private lateinit var facilitator: DictionaryFacilitator
    private lateinit var suggest: Suggest
    private lateinit var keyboard: Keyboard
    private val settingsForSuggestion = SettingsValuesForSuggestion(true, false)
    private var sequence = 0

    private fun setUp(personalized: Boolean = false): Long {
        SubtypeSettings.init(context)
        Settings.init(context)
        val editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT }
        Settings.getInstance().loadSettings(context, locale, InputAttributes(editorInfo, false, context.packageName))
        val subtype = SubtypeSettings.getResourceSubtypesForLocale(locale).first()
        val metrics = context.resources.displayMetrics
        instrumentation.runOnMainSync {
            keyboard = KeyboardLayoutSet.Builder(context, editorInfo, Settings.getValues())
                .setKeyboardGeometry(metrics.widthPixels, metrics.heightPixels / 3)
                .setSubtype(RichInputMethodSubtype.get(subtype))
                .build().getKeyboard(KeyboardElement.ALPHABET)
        }
        facilitator = DictionaryFacilitatorProvider.getDictionaryFacilitator(false)
        val start = System.nanoTime()
        facilitator.resetDictionaries(context, locale, false, false, personalized, true, "benchmark", null)
        facilitator.waitForLoadingMainDictionaries(60, TimeUnit.SECONDS)
        val loadMillis = (System.nanoTime() - start) / 1_000_000
        suggest = Suggest(facilitator).apply { setAutoCorrectionThreshold(Settings.getValues().mAutoCorrectionThreshold) }
        return loadMillis
    }

    private fun lines(name: String) =
        testAssets.open("benchmark/$localeTag/$name").bufferedReader().readLines().filter { it.isNotBlank() }

    private fun context(words: List<String>): NgramContext =
        if (words.isEmpty()) NgramContext.BEGINNING_OF_SENTENCE
        else NgramContext(*words.reversed().map { NgramContext.WordInfo(it) }.toTypedArray()) // most recent first

    private fun suggestions(composer: WordComposer, words: List<String>, timings: MutableList<Long>?): SuggestedWords {
        val start = System.nanoTime()
        val result = suggest.getSuggestedWords(composer, context(words), keyboard, settingsForSuggestion, true,
            InputStyle.TYPING, sequence++)
        timings?.add(System.nanoTime() - start)
        return result
    }

    /** next-word predictions, as the strip and Fret show them (no typed word) */
    private fun predictions(words: List<String>, timings: MutableList<Long>?): List<String> {
        suggest.clearNextWordSuggestionsCache() // measure the lookup, not the cache
        return suggestions(WordComposer(), words, timings).map { it.word }.filter { it.isNotEmpty() }
    }

    private fun keyCentre(codePoint: Int): Pair<Float, Float>? {
        val key = keyboard.getKey(codePoint) ?: return null
        return (key.x + key.width / 2f) to (key.y + key.height / 2f)
    }

    /** the composer after tapping [word] key by key, like live typing (setComposingWord would mark the word as
     *  resumed, which never autocorrects); with [random], taps are offset like a thumb and may hit other keys */
    private fun type(word: String, random: Random?): WordComposer {
        val composer = WordComposer()
        word.forEach { c ->
            if (!Character.isLetter(c)) { // like typing them from the symbols keyboard: no proximity correction
                val event = Event.createSoftwareKeypressEvent(c.code, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                composer.applyProcessedEvent(composer.processEvent(event))
                return@forEach
            }
            val (cx, cy) = keyCentre(c.code) ?: (0f to 0f)
            var x = cx
            var y = cy
            if (random != null) {
                x += (random.nextGaussian() * NOISE * keyboard.mMostCommonKeyWidth).toFloat()
                y += (random.nextGaussian() * NOISE * keyboard.mMostCommonKeyHeight).toFloat()
            }
            val hit = keyboard.sortedKeys.minByOrNull { k ->
                val dx = (k.x + k.width / 2f) - x
                val dy = (k.y + k.height / 2f) - y
                if (Character.isLetter(k.code)) dx * dx + dy * dy else Float.MAX_VALUE
            }
            val event = Event.createSoftwareKeypressEvent(hit?.code ?: c.code, 0, x.roundToInt(), y.roundToInt(), false)
            composer.applyProcessedEvent(composer.processEvent(event))
        }
        return composer
    }

    private fun nextWord(file: String, timings: MutableList<Long>?): JSONObject {
        var top1 = 0; var top3 = 0; var top5 = 0; var reciprocal = 0.0; var withAny = 0
        val cases = lines(file)
        cases.forEach { line ->
            val (contextText, expected) = line.split("\t")
            val predicted = predictions(contextText.split(" "), timings)
            if (predicted.isNotEmpty()) withAny++
            val rank = predicted.indexOfFirst { it.equals(expected, ignoreCase = true) }
            if (rank == 0) top1++
            if (rank in 0..2) top3++
            if (rank in 0..4) top5++
            if (rank >= 0) reciprocal += 1.0 / (rank + 1)
        }
        val n = cases.size.toDouble()
        return JSONObject().put("cases", cases.size).put("top1", top1 / n).put("top3", top3 / n).put("top5", top5 / n)
            .put("mrr", reciprocal / n).put("contexts_with_predictions", withAny / n)
    }

    private fun percentile(values: List<Long>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).roundToInt()] / 1_000_000.0
    }

    @Test fun benchmark() {
        val loadMillis = setUp()
        val result = JSONObject().put("locale", localeTag).put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            .put("emulator", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
            .put("dictionary_load_ms", loadMillis)

        // 1. next word on a fresh install: Tatoeba held-out sentences, and independent public domain books
        val nextTimings = ArrayList<Long>()
        val firstCall = ArrayList<Long>()
        predictions(listOf("the"), firstCall)
        result.put("next_word", nextWord("next_word.tsv", nextTimings))
        if (testAssets.list("benchmark/$localeTag")?.contains("independent_next_word.tsv") == true)
            result.put("next_word_independent", nextWord("independent_next_word.tsv", null))

        // 2. typing with noisy taps: autocorrection accuracy and wrong corrections
        val random = Random(SEED)
        val typingTimings = ArrayList<Long>()
        var typos = 0; var typosFixed = 0; var typosInTop3 = 0; var clean = 0; var cleanChanged = 0
        val typing = lines("typing.tsv")
        typing.forEach { line ->
            val (contextText, word) = line.split("\t")
            val composer = type(word, random)
            val result1 = suggestions(composer, contextText.split(" "), typingTimings)
            val typed = composer.typedWord
            val committed = if (result1.willAutoCorrect && result1.size > SuggestedWords.INDEX_OF_AUTO_CORRECTION)
                result1.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION) else typed
            if (typed == word) {
                clean++
                if (!committed.equals(word, ignoreCase = true)) cleanChanged++
            } else {
                typos++
                if (committed.equals(word, ignoreCase = true)) typosFixed++
                if (result1.take(4).any { it.word.equals(word, ignoreCase = true) }) typosInTop3++
            }
        }
        result.put("typing", JSONObject().put("cases", typing.size).put("noise_key_widths", NOISE)
            .put("typos", typos).put("autocorrect_accuracy", typosFixed.toDouble() / typos)
            .put("typo_correct_word_in_top3", typosInTop3.toDouble() / typos)
            .put("correct_words", clean).put("wrong_autocorrect_rate", cleanChanged.toDouble() / clean))

        // 3. keystroke savings: accept the word as soon as it is in the top 3, typing it letter by letter otherwise
        var keystrokes = 0; var withoutHelp = 0
        lines("sentences.txt").forEach { sentence ->
            val words = sentence.split(" ")
            words.forEachIndexed { i, word ->
                val previous = words.subList(maxOf(0, i - 3), i)
                withoutHelp += word.length + 1
                var cost = word.length + 1
                if (predictions(previous, null).take(3).any { it.equals(word, ignoreCase = true) }) cost = 1
                else for (length in 1 until word.length) {
                    val composer = type(word.substring(0, length), null)
                    if (suggestions(composer, previous, null).drop(1).take(3).any { it.word.equals(word, ignoreCase = true) }) {
                        cost = length + 1; break
                    }
                }
                keystrokes += cost
            }
        }
        result.put("keystroke_savings", 1.0 - keystrokes.toDouble() / withoutHelp)

        // 4. the representative examples of the brief (not rules: they show what a user sees)
        val examples = JSONObject()
        EXAMPLES.forEach { examples.put(it, predictions(it.split(" "), null).take(5).joinToString(", ")) }
        result.put("examples", examples)
        val corrections = JSONObject()
        TYPOS.forEach { typo ->
            val r = suggestions(type(typo, null), emptyList(), null)
            corrections.put(typo, (if (r.willAutoCorrect) "autocorrects to " else "no autocorrect, top: ") +
                (r.getOrNull(SuggestedWords.INDEX_OF_AUTO_CORRECTION)?.word ?: ""))
        }
        result.put("typo_examples", corrections)

        // 4b. words typed exactly that must stay as typed: rare words, names, technical terms, spellings, addresses
        val changed = JSONObject()
        var safetyCases = 0
        (MUST_STAY + if (localeTag == "en-GB") MUST_STAY_UK else MUST_STAY_US).forEach { word ->
            safetyCases++
            val r = suggestions(type(word, null), listOf("the"), null)
            val committed = if (r.willAutoCorrect) r.getOrNull(SuggestedWords.INDEX_OF_AUTO_CORRECTION)?.word else null
            if (committed != null && !committed.equals(word, ignoreCase = true)) changed.put(word, committed)
        }
        result.put("must_stay", JSONObject().put("cases", safetyCases).put("changed_count", changed.length()).put("changed", changed))

        // 5. latency and memory (on this device only)
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        result.put("latency_ms", JSONObject()
            .put("first_prediction", firstCall.firstOrNull()?.div(1_000_000.0) ?: 0.0)
            .put("next_word_p50", percentile(nextTimings.drop(WARM_UP), 0.5))
            .put("next_word_p95", percentile(nextTimings.drop(WARM_UP), 0.95))
            .put("next_word_p99", percentile(nextTimings.drop(WARM_UP), 0.99))
            .put("typing_p50", percentile(typingTimings.drop(WARM_UP), 0.5))
            .put("typing_p95", percentile(typingTimings.drop(WARM_UP), 0.95))
            .put("typing_p99", percentile(typingTimings.drop(WARM_UP), 0.99)))
        result.put("memory_kb", JSONObject().put("total_pss", memory.totalPss)
            .put("native_heap", Debug.getNativeHeapAllocatedSize() / 1024))

        val text = result.toString(2)
        text.lines().forEach { Log.i(TAG, it) }
        File(context.filesDir, "benchmark-$localeTag.json").writeText(text)
        facilitator.closeDictionaries()
    }

    /**
     * A phrase the user types must be able to come before the bundled predictions. Teaches "see you <word>" through
     * the normal learning path (DictionaryFacilitator.addToUserHistory, written after its 25 s delay) and records the
     * rank of <word> after each use. Uses its own history file ("benchmark" prefix), deleted at the end.
     */
    @Test fun personalPhrasesComeFirst() {
        setUp(personalized = true)
        facilitator.clearUserHistoryDictionary(context)
        val words = listOf("see", "you")
        val bundled = predictions(words, null)
        val target = listOf("there", "again", "outside", "next", "home").first { it !in bundled.take(3) }
        val result = JSONObject().put("locale", localeTag).put("bundled_top5", bundled.take(5).joinToString(", "))
            .put("taught_word", target)
        for (uses in 1..4) {
            val now = System.currentTimeMillis() / 1000
            facilitator.addToUserHistory("see", false, NgramContext.BEGINNING_OF_SENTENCE, now, true)
            facilitator.addToUserHistory("you", false, context(listOf("see")), now, true)
            facilitator.addToUserHistory(target, false, context(words), now, true)
            Thread.sleep(27_000) // learned words are written 25 s after typing
            val predicted = predictions(words, null)
            val rank = predicted.indexOfFirst { it.equals(target, ignoreCase = true) } + 1 // 0: not predicted
            result.put("after_$uses", "rank $rank: ${predicted.take(5).joinToString(", ")}")
        }
        result.toString(2).lines().forEach { Log.i(TAG, it) }
        facilitator.clearUserHistoryDictionary(context)
        facilitator.closeDictionaries()
    }

    private fun SuggestedWords.getOrNull(index: Int) = if (index < size) getInfo(index) else null

    companion object {
        private const val TAG = "RCBBoardBenchmark"
        private const val SEED = 20261128L
        private const val NOISE = 0.25 // standard deviation of a tap, in key widths / heights
        private const val WARM_UP = 50
        private val EXAMPLES = listOf("good", "how are", "I would like", "thank you very", "see you", "happy birthday",
            "nice to", "please let me", "have a nice", "I will be", "as soon as", "looking forward to", "let me",
            "I don't")
        // typed exactly, these must not be autocorrected to something else (a word not in the dictionary may be,
        // that is existing behaviour; the count must not grow)
        private val MUST_STAY = listOf("quixotic", "zeitgeist", "lugubrious", "perspicacious", "ephemeral", "serendipity",
            "Kasun", "Nimal", "Dilshan", "Siobhan", "Niamh", "Rananjaya", "kotlin", "github", "javascript", "kubernetes",
            "postgres", "json", "npm", "sdk", "android", "linux", "example.com", "www.google.com", "user@example.com",
            "name.surname@mail.lk", "https://github.com")
        private val MUST_STAY_UK = listOf("colour", "organise", "centre", "favourite", "realise", "travelled", "programme")
        private val MUST_STAY_US = listOf("color", "organize", "center", "favorite", "realize", "traveled", "program")
        private val TYPOS = listOf("teh", "thsi", "goign", "becuase", "recieve", "tommorow", "helloworld")
    }
}

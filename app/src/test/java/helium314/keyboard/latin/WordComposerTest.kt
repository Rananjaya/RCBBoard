// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.event.Event
import helium314.keyboard.event.SinglishCombiner
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WordComposer.copy() is what InputLogic passes to Suggest, so Suggest only sees what the copy keeps.
 * RCBBoard: the combining spec must survive, otherwise Suggest never knows that a word is typed with
 * the Sinhala (Singlish) combiner (phase 2A repaya suggestions, phase 2B spellings).
 */
@RunWith(RobolectricTestRunner::class)
class WordComposerTest {
    private fun composer(combiningSpec: String?, text: String): WordComposer {
        val wc = WordComposer()
        if (combiningSpec != null) wc.restartCombining(combiningSpec)
        StringUtils.toCodePointArray(text).forEach {
            val e = Event.createEventForCodePointFromAlreadyTypedText(it, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
            wc.applyProcessedEvent(wc.processEvent(e))
        }
        return wc
    }

    @Test fun `copy keeps the Singlish combining spec`() {
        val wc = composer(SinglishCombiner.COMBINING_SPEC, "gedara")
        assertEquals("ගෙඩර", wc.typedWord) // typed through the Singlish combiner
        assertEquals(SinglishCombiner.COMBINING_SPEC, wc.combiningSpec)
        assertEquals(SinglishCombiner.COMBINING_SPEC, wc.copy().combiningSpec)
    }

    @Test fun `copy keeps other combining specs and no spec`() {
        assertEquals("hangul", composer("hangul", "").copy().combiningSpec)
        assertEquals("bn_khipro", composer("bn_khipro", "").copy().combiningSpec)
        assertEquals("", composer("", "abc").copy().combiningSpec) // restartCombining stores "" for no combiner
        assertNull(composer(null, "abc").copy().combiningSpec) // never restarted: still unset, as before
    }

    @Test fun `copy keeps the other composing state`() {
        val wc = composer(SinglishCombiner.COMBINING_SPEC, "kaarya")
        wc.setAutoCorrection(SuggestedWords.SuggestedWordInfo("කාර්ය", "", 1, SuggestedWords.SuggestedWordInfo.KIND_CORRECTION,
            null, SuggestedWords.SuggestedWordInfo.NOT_AN_INDEX, SuggestedWords.SuggestedWordInfo.NOT_A_CONFIDENCE))
        val copy = wc.copy()
        assertEquals(wc.typedWord, copy.typedWord)
        assertEquals(wc.isComposingWord, copy.isComposingWord)
        assertEquals(wc.size(), copy.size())
        assertEquals(wc.isBatchMode, copy.isBatchMode)
        assertEquals(wc.isResumed, copy.isResumed)
        assertEquals(wc.isCursorFrontOrMiddleOfComposingWord, copy.isCursorFrontOrMiddleOfComposingWord)
        assertEquals(wc.isAllUpperCase, copy.isAllUpperCase)
        assertEquals(wc.hasDigits(), copy.hasDigits())
        assertTrue(wc.autoCorrectionOrNull === copy.autoCorrectionOrNull)
        assertEquals(wc.composedDataSnapshot.mTypedWord, copy.composedDataSnapshot.mTypedWord)
        assertTrue(wc.inputPointers === copy.inputPointers) // shared on purpose, see WordComposer.copy
    }

    @Test fun `copying does not change the original`() {
        val wc = composer(SinglishCombiner.COMBINING_SPEC, "gedara")
        wc.copy()
        assertEquals(SinglishCombiner.COMBINING_SPEC, wc.combiningSpec)
        assertEquals("ගෙඩර", wc.typedWord)
        wc.applyProcessedEvent(wc.processEvent(Event.createEventForCodePointFromAlreadyTypedText('t'.code,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)))
        assertEquals("ගෙඩරට්", wc.typedWord) // the original keeps working with its combiner
    }
}

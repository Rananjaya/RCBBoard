// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.singlish.SinglishTransliterator
import java.util.ArrayList

/**
 * RCBBoard Sinhala (Singlish) combiner, selected by `CombiningRules=si_singlish` on the subtype.
 * Built like [BnKhiproCombiner]:
 *  - Latin letters are kept in [latin] and the whole word is converted again after every key;
 *    the converted text is the composing word, so the Sinhala dictionary gives suggestions for it.
 *  - Backspace removes one Latin letter and converts again.
 *  - Space, punctuation, digits and other keys commit the converted word first, then are handled
 *    normally.
 *
 * Privacy: the Latin letters live only in this in-memory buffer, which is cleared on commit and
 * reset. Nothing here logs typed text (Latin or Sinhala); InputLogicTest checks this.
 */
class SinglishCombiner : Combiner {
    private val latin = StringBuilder()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        // Shift and caps lock only change the next letter, they do not end the word
        if (event.keyCode == KeyCode.SHIFT || event.keyCode == KeyCode.CAPS_LOCK) return event

        if (event.keyCode == KeyCode.DELETE) {
            if (latin.isEmpty()) return event // nothing composed: delete text before the cursor as usual
            latin.deleteCharAt(latin.length - 1) // only ASCII letters are stored
            if (latin.isEmpty()) {
                // Same as BnKhiproCombiner / HangulCombiner: end the composition with a space event
                // chained with the delete, so the delete does not remove committed text.
                reset()
                return Event.createHardwareKeypressEvent(Constants.CODE_SPACE, Constants.CODE_SPACE, 0, event, event.isKeyRepeat)
            }
            return Event.createConsumedEvent(event)
        }

        if (!event.isFunctionalKeyEvent && SinglishTransliterator.isSinglishLetter(event.codePoint)) {
            latin.appendCodePoint(event.codePoint)
            return Event.createConsumedEvent(event)
        }

        // anything else ends the word: commit the Sinhala text, then let the key do its normal work
        if (latin.isEmpty()) return event
        val converted = combiningStateFeedback
        reset()
        return Event.createSoftwareTextEvent(converted, KeyCode.MULTIPLE_CODE_POINTS, event)
    }

    override val combiningStateFeedback: CharSequence
        get() = SinglishTransliterator.transliterate(latin)

    override fun reset() {
        latin.setLength(0)
    }

    companion object {
        /** Value of the CombiningRules extra value of the Sinhala (Singlish) subtype. */
        const val COMBINING_SPEC = "si_singlish"
    }
}

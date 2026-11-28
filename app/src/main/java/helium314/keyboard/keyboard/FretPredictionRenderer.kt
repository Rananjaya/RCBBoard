// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import helium314.keyboard.latin.utils.TypefaceUtils
import kotlin.math.max
import kotlin.math.min

/**
 * RCBBoard: draws the BlackBerry 10 style predicted words of the Fret (RCB Classic) style, see [FretPredictions].
 *
 * Measured on the BlackBerry Z30 (row pitch 117 px): regular text with a cap height of 20 px, centred 4 px above
 * the middle of the fret above the key, on a dark backing that dims the fret. Letters already typed are white,
 * the rest light grey. Words in one fret row never overlap: they are pushed apart, kept inside the keyboard and
 * condensed only if a row is too full. A flicked word flies up out of the keyboard, see [FretFlyUpView].
 */
class FretPredictionRenderer {
    private class Placed(val word: String, val prefixLength: Int, var x: Float, var width: Float, var scaleX: Float,
                         val centerX: Float, val rowTop: Float, val maxWidth: Float)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val placed = ArrayList<Placed>()
    /** a flicked word is not drawn in place while it flies away, see [FretFlyUpView] */
    private var hiddenWord: String? = null
    private var hiddenUntil = 0L

    /** where a word is drawn, in keyboard view coordinates, for animating it elsewhere */
    class Snapshot(val word: String, val prefixLength: Int, val x: Float, val width: Float, val scaleX: Float,
                   val centerY: Float, val textSize: Float, val capHeight: Float, val pitch: Float)

    /** draws the words of the current mode */
    fun draw(canvas: Canvas, keyboard: Keyboard, viewWidth: Int, paddingLeft: Int, paddingTop: Int) {
        placed.clear()
        val pitch = keyboard.mMostCommonKeyHeight.toFloat()
        val mode = FretPredictions.mode
        if (pitch > 0 && mode != FretPredictions.Mode.OFF && keyboard.mId.element.isAlphabet) {
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = pitch * TEXT_SIZE
            textPaint.textScaleX = 1f
            layout(keyboard, mode, viewWidth.toFloat(), paddingLeft, paddingTop, pitch)
            val capHeight = TypefaceUtils.getReferenceCharHeight(textPaint)
            val hidden = if (SystemClock.uptimeMillis() < hiddenUntil) hiddenWord else null
            for (p in placed) if (p.word != hidden) drawWord(canvas, p, pitch, capHeight, 0f, 255) // no duplicate
        }
    }

    /** the drawn [word] for the fly-up animation, which also hides it here meanwhile; null if not on screen */
    fun takeSnapshot(word: String, pitch: Float): Snapshot? {
        val p = placed.firstOrNull { it.word == word } ?: return null
        textPaint.typeface = KeyboardTypeface.resolve(word, Typeface.DEFAULT)
        textPaint.textSize = pitch * TEXT_SIZE
        val capHeight = TypefaceUtils.getReferenceCharHeight(textPaint)
        hiddenWord = word
        hiddenUntil = SystemClock.uptimeMillis() + FretFlyUpView.DURATION_MS
        return Snapshot(word, p.prefixLength, p.x, p.width, p.scaleX, wordCenterY(p.rowTop, pitch, capHeight),
            textPaint.textSize, capHeight, pitch)
    }

    private fun layout(keyboard: Keyboard, mode: FretPredictions.Mode, viewWidth: Float, paddingLeft: Int,
                       paddingTop: Int, pitch: Float) {
        val pad = pitch * PAD
        val prefix = FretPredictions.typedPrefix
        fun add(word: String, centerX: Float, rowTop: Float, maxWidth: Float) {
            val prefixLength = if (prefix.isNotEmpty() && word.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true))
                prefix.length else 0
            textPaint.typeface = KeyboardTypeface.resolve(word, Typeface.DEFAULT) // emoji font for emoji words
            val natural = textPaint.measureText(word)
            val scaleX = if (natural > maxWidth) max(MIN_SCALE_X, maxWidth / natural) else 1f
            placed.add(Placed(word, prefixLength, centerX - natural * scaleX / 2, natural * scaleX, scaleX, centerX, rowTop, maxWidth))
        }
        if (mode == FretPredictions.Mode.IN_LETTER) {
            val keysByLetter = HashMap<Int, Key>()
            for (key in keyboard.sortedKeys) if (!key.isSpacer && Character.isLetter(key.code))
                keysByLetter.putIfAbsent(Character.toLowerCase(key.code), key)
            for ((code, info) in FretPredictions.lettersByRank()) { // best ranked first
                val key = keysByLetter[code] ?: continue
                add(info.word, key.x + paddingLeft + key.width / 2f, (key.y + paddingTop).toFloat(), viewWidth - 2 * pad)
            }
            // the best emoji suggestion above the comma key
            val emoji = FretPredictions.emojiWord()
            val comma = keyboard.sortedKeys.firstOrNull { it.code == FretPredictions.EMOJI_KEY }
            if (emoji != null && comma != null)
                add(emoji.word, comma.x + paddingLeft + comma.width / 2f, (comma.y + paddingTop).toFloat(), viewWidth - 2 * pad)
        } else {
            val top = (keyboard.sortedKeys.minOfOrNull { it.y } ?: 0) + paddingTop
            val columnWidth = keyboard.mOccupiedWidth / FretPredictions.COLUMNS.toFloat()
            for (column in 0 until FretPredictions.COLUMNS) {
                val info = FretPredictions.wordForColumn(column) ?: continue
                add(info.word, paddingLeft + columnWidth * (column + 0.5f), top.toFloat(), columnWidth - 3 * pad)
            }
        }
        // resolve overlaps per fret row
        for (row in placed.groupBy { it.rowTop }.values) resolveRow(row.sortedBy { it.centerX }, viewWidth, pad)
    }

    /** pushes the words of one row apart and back inside the keyboard, condenses them only if the row is too full */
    private fun resolveRow(row: List<Placed>, viewWidth: Float, pad: Float) {
        val (xs, factor) = packRow(FloatArray(row.size) { row[it].centerX }, FloatArray(row.size) { row[it].width },
            pad, viewWidth - pad, pad * 2)
        row.forEachIndexed { i, p ->
            p.scaleX *= factor
            p.width *= factor
            p.x = xs[i]
        }
    }

    private fun drawWord(canvas: Canvas, p: Placed, pitch: Float, capHeight: Float, offsetY: Float, alpha: Int) {
        drawWordAt(canvas, textPaint, backingPaint, rect, p.word, p.prefixLength, p.x, p.width, p.scaleX,
            wordCenterY(p.rowTop, pitch, capHeight) + offsetY, capHeight, pitch * PAD, alpha)
    }

    companion object {
        /** centred 4 px above the middle of the 12 px fret, but never cut off at the top of the keyboard */
        internal fun wordCenterY(rowTop: Float, pitch: Float, capHeight: Float) =
            max(rowTop + pitch * 2f / 117f, capHeight * 0.75f + pitch * 2f / 117f)

        /** draws one word on its dark backing, the typed part white and the rest light grey */
        internal fun drawWordAt(canvas: Canvas, textPaint: Paint, backingPaint: Paint, rect: RectF, word: String,
                                prefixLength: Int, x: Float, width: Float, scaleX: Float, centerY: Float,
                                capHeight: Float, pad: Float, alpha: Int) {
            val baseline = centerY + capHeight / 2f
            val half = capHeight * 0.85f
            rect.set(x - pad, centerY - half, x + width + pad, centerY + half)
            textPaint.typeface = KeyboardTypeface.resolve(word, Typeface.DEFAULT)
            textPaint.textScaleX = scaleX
            // no box behind the word (owner's request): a thin dark outline keeps it readable on the bright fret
            backingPaint.set(textPaint)
            backingPaint.style = Paint.Style.STROKE
            backingPaint.strokeWidth = capHeight * 0.22f
            backingPaint.strokeJoin = Paint.Join.ROUND
            backingPaint.color = BACKING
            backingPaint.alpha = alpha
            canvas.drawText(word, x, baseline, backingPaint)
            textPaint.color = TYPED
            textPaint.alpha = alpha
            canvas.drawText(word, 0, prefixLength, x, baseline, textPaint)
            textPaint.color = REST
            textPaint.alpha = alpha
            canvas.drawText(word, prefixLength, word.length, x + textPaint.measureText(word, 0, prefixLength), baseline, textPaint)
            textPaint.textScaleX = 1f
        }

        /**
         * Lays out words of one row, given their wished [centers] (sorted left to right) and [widths]:
         * returns their left x and a common horizontal scale factor (1 unless the row is too full).
         * The words do not overlap (at least [gap] apart) and stay between [left] and [right] where possible.
         */
        internal fun packRow(centers: FloatArray, widths: FloatArray, left: Float, right: Float, gap: Float): Pair<FloatArray, Float> {
            val n = centers.size
            val w = widths.copyOf()
            val gaps = gap * (n - 1).coerceAtLeast(0)
            val total = w.sum() + gaps
            var factor = 1f
            if (total > right - left && w.sum() > 0f) {
                factor = max(MIN_SCALE_X, (right - left - gaps) / w.sum())
                for (i in 0 until n) w[i] *= factor
            }
            val xs = FloatArray(n) { (centers[it] - w[it] / 2).coerceIn(left, max(left, right - w[it])) }
            for (i in 1 until n) xs[i] = max(xs[i], xs[i - 1] + w[i - 1] + gap) // left to right: no overlap
            for (i in n - 1 downTo 0) { // right to left: back inside the right edge
                val limit = if (i == n - 1) right else xs[i + 1] - gap
                xs[i] = min(xs[i], limit - w[i])
            }
            for (i in 0 until n) xs[i] = max(xs[i], left)
            return xs to factor
        }

        /** text size for a cap height of about 20 / 117 of the row pitch */
        private const val TEXT_SIZE = 28f / 117f
        internal const val PAD = 4f / 117f
        private const val MIN_SCALE_X = 0.7f
        private const val TYPED = 0xFFFFFFFF.toInt()
        private const val REST = 0xFFC4C4C4.toInt()
        private const val BACKING = 0xFF000000.toInt()
    }
}

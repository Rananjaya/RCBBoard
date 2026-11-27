// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

/**
 * RCBBoard: the BlackBerry 10 style animation of a predicted word that was flicked up in the Fret style.
 * The word lifts off its fret, glides up out of the keyboard towards the text field and fades away.
 * This view lives on the preview layer above the keyboard (like key previews), so the word can leave the
 * keyboard instead of being cut off at its top edge.
 */
class FretFlyUpView(context: Context) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var snapshot: FretPredictionRenderer.Snapshot? = null
    private var originX = 0f
    private var originY = 0f
    private var start = 0L

    init {
        isClickable = false
        isFocusable = false
    }

    /** starts the animation of [word], drawn by the keyboard view whose top left is at ([x], [y]) on this layer */
    fun start(word: FretPredictionRenderer.Snapshot, x: Int, y: Int) {
        snapshot = word
        originX = x.toFloat()
        originY = y.toFloat()
        start = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val s = snapshot ?: return
        val t = (SystemClock.uptimeMillis() - start) / DURATION_MS.toFloat()
        if (t >= 1f) {
            snapshot = null
            return
        }
        val inv = 1f - t
        val eased = 1f - inv * inv * inv // cubic ease-out: glides up and settles
        // from the fret to above the keyboard, where the text field usually is
        val rise = (s.centerY + s.pitch * RISE_ABOVE_KEYBOARD) * eased
        val scale = 1f + 0.15f * eased
        val alpha = (255 * (1f - t * t)).toInt().coerceIn(0, 255) // fades slowly at first, then away
        val centerY = originY + s.centerY - rise
        val left = originX + s.x
        canvas.save()
        canvas.scale(scale, scale, left + s.width / 2, centerY)
        textPaint.textSize = s.textSize
        FretPredictionRenderer.drawWordAt(canvas, textPaint, backingPaint, rect, s.word, s.prefixLength, left, s.width,
            s.scaleX, centerY, s.capHeight, s.pitch * FretPredictionRenderer.PAD, alpha)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    companion object {
        const val DURATION_MS = 420L
        /** rows above the keyboard the word reaches */
        private const val RISE_ABOVE_KEYBOARD = 1.3f
    }
}

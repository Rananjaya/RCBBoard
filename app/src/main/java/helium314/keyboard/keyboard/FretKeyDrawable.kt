// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * RCBBoard: key tile of the Fret (RCB Classic) style, an original drawing modelled on the BlackBerry Z30
 * keyboard. All sizes are fractions of the key cell height, which is the row pitch, so the tile keeps the
 * BlackBerry proportions on every screen size and keyboard height setting.
 *
 * Measured on a Z30 reference (keyboard 717 px wide, row pitch 117 px):
 * fret 12 px, black gap 6 px, tile 93 px, black gap 6 px; tiles 6 px apart (3 px each side).
 * The fret itself is drawn by [FretBackgroundDrawable], this drawable leaves that area transparent.
 */
class FretKeyDrawable(private val functional: Boolean) : Drawable() {
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var pressed = false
    private var shaderKey = 0L

    override fun draw(canvas: Canvas) {
        val b = bounds
        val pitch = b.height().toFloat()
        if (pitch <= 0f || b.width() <= 0) return
        val top = b.top + pitch * TOP_INSET
        val bottom = b.bottom - pitch * BOTTOM_INSET
        val side = pitch * SIDE_INSET
        rect.set(b.left + side, top, b.right - side, bottom)
        val key = (top.toLong() shl 32) or (bottom.toLong() shl 1) or (if (pressed) 1L else 0L)
        if (key != shaderKey) {
            shaderKey = key
            val colors = when {
                pressed -> PRESSED_COLORS
                functional -> FUNCTIONAL_COLORS
                else -> LETTER_COLORS
            }
            tilePaint.shader = LinearGradient(0f, top, 0f, bottom, colors, POSITIONS, Shader.TileMode.CLAMP)
            rimPaint.color = when {
                pressed -> PRESSED_RIM
                functional -> FUNCTIONAL_RIM
                else -> LETTER_RIM
            }
        }
        val radius = pitch * CORNER_RADIUS
        canvas.drawRoundRect(rect, radius, radius, tilePaint)
        val rimWidth = max(1f, pitch * RIM_WIDTH)
        rimPaint.strokeWidth = rimWidth
        rect.inset(rimWidth / 2, rimWidth / 2)
        canvas.drawRoundRect(rect, radius, radius, rimPaint)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val newPressed = state.any { it == android.R.attr.state_pressed }
        if (newPressed == pressed) return false
        pressed = newPressed
        shaderKey = 0L
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private const val PITCH = 117f
        /** fret 12 px + black gap 6 px */
        const val TOP_INSET = 18f / PITCH
        const val BOTTOM_INSET = 6f / PITCH
        const val SIDE_INSET = 3f / PITCH
        private const val CORNER_RADIUS = 4f / PITCH
        private const val RIM_WIDTH = 1f / PITCH
        /** vertical centre of letters and icons: 3 px above the tile centre */
        const val CONTENT_CENTER = (18f + 93f / 2f - 3f) / PITCH

        private val POSITIONS = floatArrayOf(0f, 0.012f, 0.05f, 0.3f, 1f)
        // colours sampled from the Z30 reference
        private val LETTER_COLORS = intArrayOf(0xFF505052.toInt(), 0xFF404042.toInt(), 0xFF353535.toInt(), 0xFF2F2F2F.toInt(), 0xFF2B2B2B.toInt())
        private val FUNCTIONAL_COLORS = intArrayOf(0xFF2E2E2E.toInt(), 0xFF1A1A1A.toInt(), 0xFF181818.toInt(), 0xFF141414.toInt(), 0xFF121212.toInt())
        // pressed state: no clear reference available, a lighter tile in the same family
        private val PRESSED_COLORS = intArrayOf(0xFF7A7A7E.toInt(), 0xFF68686C.toInt(), 0xFF5A5A5E.toInt(), 0xFF4E4E52.toInt(), 0xFF444448.toInt())
        private const val LETTER_RIM = 0xFF404040.toInt()
        private const val FUNCTIONAL_RIM = 0xFF262626.toInt()
        private const val PRESSED_RIM = 0xFF8A8A8E.toInt()
    }
}

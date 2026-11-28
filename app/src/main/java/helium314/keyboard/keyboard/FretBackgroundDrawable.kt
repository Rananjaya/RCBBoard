// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import helium314.keyboard.latin.R

/**
 * RCBBoard: background of the main keyboard in the Fret (RCB Classic) style.
 * Black, with one metal fret across the full width at the top of every key row, like the BlackBerry Z30
 * keyboard. 12/117 of the row pitch thick, as on the Z30 reference; owner's design for the profile: a metal bar
 * with dark edges at top and bottom, a bright highlight and a grey body between, darkening towards both ends.
 * Original drawing code, no BlackBerry assets.
 */
class FretBackgroundDrawable(res: Resources, private val rowTops: IntArray) : Drawable() {
    private val background = ResourcesCompat.getColor(res, R.color.rcb_classic_background, null)
    private val paint = Paint()
    private val endsPaint = Paint()

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(background)
        if (b.width() <= 0 || rowTops.isEmpty()) return
        for (i in rowTops.indices) {
            val top = rowTops[i].toFloat()
            val pitch = when {
                i + 1 < rowTops.size -> rowTops[i + 1] - top
                i > 0 -> top - rowTops[i - 1]
                else -> b.bottom - top
            }
            val bottom = top + pitch * FRET_HEIGHT
            paint.shader = LinearGradient(0f, top, 0f, bottom, PROFILE, POSITIONS, Shader.TileMode.CLAMP)
            canvas.drawRect(b.left.toFloat(), top, b.right.toFloat(), bottom, paint)
            // dark metal shading towards the left and right ends of the bar
            endsPaint.shader = LinearGradient(b.left.toFloat(), 0f, b.right.toFloat(), 0f, ENDS, ENDS_POSITIONS, Shader.TileMode.CLAMP)
            canvas.drawRect(b.left.toFloat(), top, b.right.toFloat(), bottom, endsPaint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    companion object {
        /** 12 px of a 117 px row pitch on the Z30 reference */
        const val FRET_HEIGHT = 12f / 117f
        // metal bar, top to bottom: dark edge, bright highlight, grey body, lighter lip, dark edge
        private val PROFILE = intArrayOf(
            0xFF3C3B39.toInt(), 0xFFF4F4F2.toInt(), 0xFFC4C2BE.toInt(), 0xFF8E8A85.toInt(),
            0xFF9F9C97.toInt(), 0xFF64615D.toInt(), 0xFF2E2D2B.toInt(),
        )
        private val POSITIONS = floatArrayOf(0f, 0.16f, 0.32f, 0.55f, 0.72f, 0.88f, 1f)
        // left and right ends: darkened over the outer 7 % of the width, clear in the middle
        private val ENDS = intArrayOf(0xC0000000.toInt(), 0x00000000, 0x00000000, 0xC0000000.toInt())
        private val ENDS_POSITIONS = floatArrayOf(0f, 0.07f, 0.93f, 1f)
    }
}

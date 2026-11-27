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
 * keyboard. The fret is 12/117 of the row pitch thick and uses the colour profile sampled from the
 * reference: a 2 px white highlight, a warm grey body and a lighter lower lip. It is uniform across the
 * width, like on the original. Original drawing code, no BlackBerry assets.
 */
class FretBackgroundDrawable(res: Resources, private val rowTops: IntArray) : Drawable() {
    private val background = ResourcesCompat.getColor(res, R.color.rcb_classic_background, null)
    private val paint = Paint()

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
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    companion object {
        /** 12 px of a 117 px row pitch on the Z30 reference */
        const val FRET_HEIGHT = 12f / 117f
        // the 12 rows of the reference fret, top to bottom
        private val PROFILE = intArrayOf(
            0xFFFEFFFF.toInt(), 0xFFEDEDEB.toInt(), 0xFF989894.toInt(), 0xFF918E8A.toInt(), 0xFF8D8882.toInt(),
            0xFF8A857F.toInt(), 0xFF84817C.toInt(), 0xFF999692.toInt(), 0xFF959591.toInt(),
        )
        private val POSITIONS = floatArrayOf(0f, 1f / 12, 2f / 12, 3f / 12, 5f / 12, 8f / 12, 9f / 12, 10f / 12, 1f)
    }
}

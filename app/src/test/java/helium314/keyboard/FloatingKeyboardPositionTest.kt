// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FloatingKeyboardUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RCBBoard: baseline (stage 0 of the upstream Kotlin migration) for the stored floating keyboard position.
 * The floating and toolbar-only views themselves are not unit-testable here; they are on the emulator checklist.
 */
@RunWith(RobolectricTestRunner::class)
class FloatingKeyboardPositionTest {
    private val context = ApplicationProvider.getApplicationContext<App>()
    private val width get() = context.resources.displayMetrics.widthPixels
    private val height get() = context.resources.displayMetrics.heightPixels

    @BeforeTest fun clear() = context.prefs().edit { clear() }

    @Test fun `default position is the middle of the screen`() {
        assertEquals(width / 2 to height / 2, FloatingKeyboardUtils.readPosition(context, width, height))
    }

    @Test fun `stored position is used when it fits`() {
        context.prefs().edit {
            putInt(Settings.PREF_FLOATING_POS_X_PREFIX + width, 10)
            putInt(Settings.PREF_FLOATING_POS_Y_PREFIX + width, 20)
        }
        assertEquals(10 to 20, FloatingKeyboardUtils.readPosition(context, width, height))
    }

    @Test fun `position outside the allowed area is clamped and stored`() {
        context.prefs().edit {
            putInt(Settings.PREF_FLOATING_POS_X_PREFIX + width, 5000)
            putInt(Settings.PREF_FLOATING_POS_Y_PREFIX + width, 20)
        }
        assertEquals(100 to 20, FloatingKeyboardUtils.readPosition(context, 100, 200))
        // both values are stored as the maximum (current behaviour of savePosition)
        assertEquals(100, context.prefs().getInt(Settings.PREF_FLOATING_POS_X_PREFIX + width, -1))
        assertEquals(200, context.prefs().getInt(Settings.PREF_FLOATING_POS_Y_PREFIX + width, -1))
    }

    @Test fun `negative maximum is treated as zero`() {
        assertEquals(0 to 0, FloatingKeyboardUtils.readPosition(context, -5, -1))
    }
}

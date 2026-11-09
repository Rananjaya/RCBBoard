// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RCBBoard: the application ID is registered with Google and must stay `com.rananjaya.rcbboard`
 * (`.debug` for debug builds). The content provider authorities are separate strings and must
 * start with it; otherwise the app conflicts with another install that declares the same authority.
 */
@RunWith(RobolectricTestRunner::class)
class ApplicationIdTest {
    private val context = ApplicationProvider.getApplicationContext<App>()

    @Test fun `application id is the registered one`() {
        val expected = if (BuildConfig.APPLICATION_ID.endsWith(".debug")) "com.rananjaya.rcbboard.debug"
            else "com.rananjaya.rcbboard"
        assertEquals(expected, BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
    }

    @Test fun `content provider authorities belong to the application id`() {
        assertEquals("${context.packageName}.clipprovider", context.getString(R.string.clipboard_provider_authority))
        assertEquals("${context.packageName}.provider", context.getString(R.string.gesture_data_provider_authority))
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.ClipDescription
import android.os.PersistableBundle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ClipboardSensitivityTest {

    private fun description(sensitive: Boolean?): ClipDescription {
        val description = ClipDescription("label", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))
        if (sensitive != null)
            description.extras = PersistableBundle().apply { putBoolean(IS_SENSITIVE, sensitive) }
        return description
    }

    @Test fun sensitiveClipIsNotStored() {
        assertFalse(ClipboardHistoryManager.mayStoreInHistory(description(true)))
    }

    @Test fun clipMarkedNotSensitiveIsStored() {
        assertTrue(ClipboardHistoryManager.mayStoreInHistory(description(false)))
    }

    @Test fun clipWithoutExtrasIsStored() {
        assertTrue(ClipboardHistoryManager.mayStoreInHistory(description(null)))
    }

    @Test fun missingDescriptionIsStored() {
        // fetchPrimaryClip returns earlier for a missing description, this only checks the helper is null-safe
        assertTrue(ClipboardHistoryManager.mayStoreInHistory(null))
    }

    companion object {
        // ClipDescription.EXTRA_IS_SENSITIVE (API 33), spelled out so the test does not depend on the API level
        private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

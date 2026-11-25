// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.onboarding

import helium314.keyboard.settings.onboarding.Onboarding.PAGE_COUNT
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_DONE
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_ENABLE
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_FEATURES
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_LANGUAGES
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_SELECT
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_WELCOME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingTest {
    @Test fun `fresh install shows the tutorial`() {
        assertFalse(Onboarding.isUpgradeInstall(1000, 1000))
        assertTrue(Onboarding.shouldShowOnStart(completed = null, upgrade = false))
    }

    @Test fun `update from a version without the tutorial does not show it`() {
        assertTrue(Onboarding.isUpgradeInstall(1000, 2000))
        assertFalse(Onboarding.shouldShowOnStart(completed = null, upgrade = true))
    }

    @Test fun `finished or skipped tutorial is not shown again`() {
        assertFalse(Onboarding.shouldShowOnStart(completed = true, upgrade = false))
        assertFalse(Onboarding.shouldShowOnStart(completed = true, upgrade = true))
    }

    @Test fun `set as default keyboard is in the middle, not first or last`() {
        assertEquals(7, PAGE_COUNT)
        assertTrue(PAGE_SELECT in 1 until PAGE_COUNT - 1)
        assertEquals(listOf(PAGE_WELCOME, PAGE_ENABLE, PAGE_SELECT, PAGE_LANGUAGES, 4, PAGE_FEATURES, PAGE_DONE), (0 until PAGE_COUNT).toList())
    }

    @Test fun `setup starts at the first missing step`() {
        assertEquals(PAGE_ENABLE, Onboarding.setupStartPage(enabled = false, current = false))
        assertEquals(PAGE_SELECT, Onboarding.setupStartPage(enabled = true, current = false))
        assertEquals(PAGE_WELCOME, Onboarding.setupStartPage(enabled = true, current = true))
        assertTrue(Onboarding.needsSetup(enabled = true, current = false))
        assertFalse(Onboarding.needsSetup(enabled = true, current = true))
    }

    @Test fun `moves on after turning on or choosing RCBBoard on that page`() {
        assertEquals(PAGE_SELECT, Onboarding.pageAfterSetupChange(PAGE_ENABLE, false, true, false, false))
        assertEquals(PAGE_LANGUAGES, Onboarding.pageAfterSetupChange(PAGE_SELECT, true, true, false, true))
    }

    @Test fun `does not move when nothing changed or on other pages`() {
        assertNull(Onboarding.pageAfterSetupChange(PAGE_ENABLE, true, true, false, false)) // already enabled before
        assertNull(Onboarding.pageAfterSetupChange(PAGE_SELECT, true, true, true, true))   // already current before
        assertNull(Onboarding.pageAfterSetupChange(PAGE_LANGUAGES, false, true, false, true))
    }

    @Test fun `quick themes match the keyboard colour settings`() {
        for (theme in Onboarding.QuickTheme.entries) {
            val (dayNight, colors, night) = Onboarding.quickThemeSettings(theme)
            assertEquals(theme, Onboarding.quickTheme(dayNight, colors, night))
        }
        // defaults on Android 10+: follow the system with light and dark
        assertEquals(Onboarding.QuickTheme.SYSTEM, Onboarding.quickTheme(true, "light", "dark"))
        // any other theme (e.g. chosen in Appearance) selects no chip
        assertNull(Onboarding.quickTheme(false, "ocean", "dark"))
        assertNull(Onboarding.quickTheme(true, "light", "black"))
    }

    @Test fun `quick themes need the same Android versions as Appearance`() {
        assertEquals(listOf(Onboarding.QuickTheme.LIGHT, Onboarding.QuickTheme.DARK), Onboarding.availableQuickThemes(28))
        assertEquals(4, Onboarding.availableQuickThemes(31).size)
        assertFalse(Onboarding.QuickTheme.DYNAMIC in Onboarding.availableQuickThemes(30))
    }
}

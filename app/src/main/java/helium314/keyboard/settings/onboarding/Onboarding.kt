// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils

/**
 * RCBBoard first-launch tutorial: rules (pure, unit-tested) and the stored completion state.
 * The screen is [OnboardingScreen].
 */
object Onboarding {
    /** Set when the tutorial was finished or skipped. Stored in the normal preferences, so backups keep it. */
    const val PREF_COMPLETED = "rcbboard_onboarding_completed"

    const val PAGE_WELCOME = 0
    const val PAGE_ENABLE = 1
    const val PAGE_SELECT = 2 // "make RCBBoard your keyboard", in the middle of the tutorial
    const val PAGE_LANGUAGES = 3
    const val PAGE_SWITCH = 4
    const val PAGE_FEATURES = 5
    const val PAGE_DONE = 6
    const val PAGE_COUNT = 7

    /** A fresh install has lastUpdateTime == firstInstallTime. An update has a later lastUpdateTime. */
    fun isUpgradeInstall(firstInstallTime: Long, lastUpdateTime: Long): Boolean = lastUpdateTime > firstInstallTime

    /**
     * Whether to show the tutorial when the app starts. [completed] is null if it was never finished or skipped.
     * Someone updating from a version without the tutorial already uses RCBBoard, so they don't get it
     * (they can open it from the main menu, and the setup card shows if RCBBoard is not set up).
     */
    fun shouldShowOnStart(completed: Boolean?, upgrade: Boolean): Boolean = completed?.not() ?: !upgrade

    /** RCBBoard still has to be turned on or chosen as the current keyboard. */
    fun needsSetup(enabled: Boolean, current: Boolean): Boolean = !enabled || !current

    /** Page to open when the tutorial is started for setting up RCBBoard (e.g. from the setup card). */
    fun setupStartPage(enabled: Boolean, current: Boolean): Int = when {
        !enabled -> PAGE_ENABLE
        !current -> PAGE_SELECT
        else -> PAGE_WELCOME
    }

    /** Page after [page] when RCBBoard was just turned on or chosen while the user was on that page, or null. */
    fun pageAfterSetupChange(page: Int, enabledBefore: Boolean, enabled: Boolean, currentBefore: Boolean, current: Boolean): Int? = when {
        page == PAGE_ENABLE && !enabledBefore && enabled -> PAGE_SELECT
        page == PAGE_SELECT && !currentBefore && current -> PAGE_LANGUAGES
        else -> null
    }

    /** Quick keyboard themes offered on the last page. They set the same settings as Appearance. */
    enum class QuickTheme { SYSTEM, LIGHT, DARK, DYNAMIC }

    /** The quick theme matching the keyboard colour settings, or null for any other (e.g. a custom) theme. */
    fun quickTheme(dayNight: Boolean, colors: String, colorsNight: String): QuickTheme? = when {
        colors == KeyboardTheme.THEME_DYNAMIC && (!dayNight || colorsNight == KeyboardTheme.THEME_DYNAMIC) -> QuickTheme.DYNAMIC
        dayNight && colors == KeyboardTheme.THEME_LIGHT && colorsNight == KeyboardTheme.THEME_DARK -> QuickTheme.SYSTEM
        !dayNight && colors == KeyboardTheme.THEME_LIGHT -> QuickTheme.LIGHT
        !dayNight && colors == KeyboardTheme.THEME_DARK -> QuickTheme.DARK
        else -> null
    }

    /** Settings for [theme]: day/night mode, colours, night colours. */
    fun quickThemeSettings(theme: QuickTheme): Triple<Boolean, String, String> = when (theme) {
        QuickTheme.SYSTEM -> Triple(true, KeyboardTheme.THEME_LIGHT, KeyboardTheme.THEME_DARK)
        QuickTheme.LIGHT -> Triple(false, KeyboardTheme.THEME_LIGHT, KeyboardTheme.THEME_DARK)
        QuickTheme.DARK -> Triple(false, KeyboardTheme.THEME_DARK, KeyboardTheme.THEME_DARK)
        QuickTheme.DYNAMIC -> Triple(true, KeyboardTheme.THEME_DYNAMIC, KeyboardTheme.THEME_DYNAMIC)
    }

    /** Following the system needs Android 10, dynamic colours need Android 12 (as in Appearance). */
    fun availableQuickThemes(sdk: Int): List<QuickTheme> = QuickTheme.entries.filter {
        when (it) {
            QuickTheme.SYSTEM -> sdk >= Build.VERSION_CODES.Q
            QuickTheme.DYNAMIC -> sdk >= Build.VERSION_CODES.S
            else -> true
        }
    }

    fun quickTheme(prefs: SharedPreferences): QuickTheme? = quickTheme(
        prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT),
        prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS)!!,
        prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT)!!,
    )

    fun setQuickTheme(prefs: SharedPreferences, theme: QuickTheme) {
        val (dayNight, colors, colorsNight) = quickThemeSettings(theme)
        prefs.edit {
            putBoolean(Settings.PREF_THEME_DAY_NIGHT, dayNight)
            putString(Settings.PREF_THEME_COLORS, colors)
            putString(Settings.PREF_THEME_COLORS_NIGHT, colorsNight)
        }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    fun isCompleted(prefs: SharedPreferences): Boolean? =
        if (prefs.contains(PREF_COMPLETED)) prefs.getBoolean(PREF_COMPLETED, false) else null

    fun setCompleted(prefs: SharedPreferences) = prefs.edit { putBoolean(PREF_COMPLETED, true) }

    fun shouldShowOnStart(context: Context, prefs: SharedPreferences): Boolean {
        val upgrade = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            isUpgradeInstall(info.firstInstallTime, info.lastUpdateTime)
        } catch (e: Exception) {
            Log.w("Onboarding", "could not read install times", e)
            false
        }
        return shouldShowOnStart(isCompleted(prefs), upgrade)
    }

    fun isEnabled(context: Context): Boolean =
        UncachedInputMethodManagerUtils.isThisImeEnabled(context, imm(context))

    fun isCurrent(context: Context): Boolean =
        UncachedInputMethodManagerUtils.isThisImeCurrent(context, imm(context))

    /** getSystemService(Class) needs API 23, RCBBoard supports API 21 */
    fun imm(context: Context): InputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
}

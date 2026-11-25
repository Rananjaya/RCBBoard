// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.define

import helium314.keyboard.latin.BuildConfig

/**
 * RCBBoard features that developers switch on or off when building the app. Users can't change them:
 * there is no setting for them in the app.
 * See docs/MAIN_MENU_AND_FEATURE_CONFIGURATION.md.
 */
object RcbFeatures {
    /**
     * Glide (gesture) typing. False by default.
     *
     * Set by the GLIDE_TYPING_ENABLED build config field in app/build.gradle.kts: change the default
     * there, or build with `-PrcbboardGlideTyping=true`.
     *
     * When false, no glide typing library is loaded (not the user-supplied one, not the system one),
     * so JniUtils.sHaveGestureLib stays false. Glide typing then can't be switched on, and its settings,
     * the gesture data screens and the "Load gesture typing library" option are not shown.
     * The glide typing code itself is unchanged.
     */
    const val GLIDE_TYPING_ENABLED: Boolean = BuildConfig.GLIDE_TYPING_ENABLED

    /**
     * Whether users may load a glide typing library from a file (Advanced settings).
     * Only with glide typing enabled, and never in the nouserlib build type.
     */
    @JvmStatic
    fun isGestureLibraryLoadingEnabled(): Boolean = GLIDE_TYPING_ENABLED && BuildConfig.BUILD_TYPE != "nouserlib"

    /**
     * Adding dictionaries ("language libraries") from files: the "Add dictionary from file" options,
     * links to download dictionaries, and opening .dict files from other apps. False: RCBBoard only
     * uses its bundled dictionaries (and the user's own learned words and personal dictionary).
     * To allow it again, set this to true and add the .dict intent filter back to SettingsActivity2
     * in AndroidManifest.xml.
     */
    const val DICTIONARY_IMPORT_ENABLED: Boolean = false
}

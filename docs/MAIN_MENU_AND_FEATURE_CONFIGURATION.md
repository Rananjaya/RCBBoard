# Main menu and feature configuration

This page describes RCBBoard's main menu, the languages it offers, and two features that developers switch on or off in the build: **glide typing** and **adding dictionaries from files**. Users can't change these features in the app.

## Main menu

The main menu (`app/src/main/java/helium314/keyboard/settings/screens/MainSettingsScreen.kt`) has three Material 3 cards. Each entry has an icon in a tonal container, a title, a one-line summary and a chevron, and is at least 72 dp high. Colours come from the theme (light, dark and dynamic colours). The entries, the screens they open and the settings search are the same as before.

| Group | Entry | Icon (Material Symbols Rounded) | Drawable |
| --- | --- | --- | --- |
| Typing | Languages & Layouts (summary: enabled languages) | `language` | `ic_menu_languages` |
| Typing | Text correction | `spellcheck` | `ic_menu_text_correction` |
| Typing | Dictionaries | `dictionary` | `ic_menu_dictionaries` |
| Typing | Gesture typing (only with glide typing) | `gesture` | `ic_menu_gesture_typing` |
| Typing | Gesture data (only with glide typing, until 2027-01-15) | `swipe` | `ic_menu_gesture_data` |
| Look and feel | Appearance | `palette` | `ic_menu_appearance` |
| Look and feel | Toolbar | `toolbar` | `ic_menu_toolbar` |
| Look and feel | Secondary layouts | `keyboard` | `ic_menu_secondary_layouts` |
| General | Preferences | `tune` | `ic_menu_preferences` |
| General | Advanced | `build` | `ic_menu_advanced` |
| General | About | `info` | `ic_menu_about` |
| (all) | chevron | `chevron_right` | `ic_menu_chevron` |

The icons are Material Symbols Rounded by Google (weight 400, unfilled, 24 dp), Apache-2.0, from https://github.com/google/material-design-icons. They are listed in `licenses/THIRD_PARTY_NOTICES.md`, and each file names its symbol. The summaries are in `res/values/rcbboard_main_menu_strings.xml`. The old menu icons `ic_settings_languages`, `_preferences`, `_appearance`, `_toolbar`, `_correction`, `_layout` and `_advanced` were removed because nothing used them any more.

## Languages

RCBBoard offers only these languages, set in `res/values/rcbboard_languages.xml` (`rcbboard_enabled_locales`) and enforced by `SubtypeSettings.getSelectableSubtypes()`:

| Language | Layout | Dictionary |
| --- | --- | --- |
| English (UK) | QWERTY | `main_en-GB.dict` |
| English (US) | QWERTY | `main_en-US.dict` |
| Sinhala | Sinhala | `main_si.dict` |
| Sinhala (Wijesekara) | Wijesekara | `main_si.dict` |
| Sinhala (Singlish) | QWERTY, phonetic (`si_singlish`) | `main_si.dict` |

**First start.** A new install, or one where no language was chosen yet, starts with **English (UK)** as the default keyboard and **Sinhala (Singlish)** enabled, whatever the system language (`SubtypeSettings.rcbBoardDefaultSubtypes()`). The other languages can be enabled in *Languages & Layouts*. Languages the user already chose are not changed. These defaults are not saved, so someone who never chose a language also gets them after updating.

Not changed: multilingual typing (only with the languages above), custom layouts (the "+" next to a layout), the personal dictionary, learned words and blocked suggestions.

## Adding dictionaries ("language libraries") – removed

Users can't add dictionaries from files any more. The switch is `RcbFeatures.DICTIONARY_IMPORT_ENABLED` (`false`) in `app/src/main/java/helium314/keyboard/latin/define/RcbFeatures.kt`.

| Former way to add a dictionary | Where | Now |
| --- | --- | --- |
| "Add dictionary from file" | `DictionaryScreen.kt` | not shown |
| "Add dictionary" button, download links | `DictionaryDialog.kt` | not shown |
| "Missing dictionary" dialog with download links | `LanguageScreen.kt` | not shown (all offered languages have bundled dictionaries) |
| Opening a `.dict` file from another app | `AndroidManifest.xml` (`SettingsActivity2`), `SettingsActivity.kt` | intent filter removed (left as a comment); `ACTION_VIEW` sent directly is ignored |
| Restoring dictionaries added from files from a backup | `BackupRestorePreference.kt` | not restored (still included in new backups) |

The bundled dictionaries are loaded as before. To allow adding dictionaries again, set the constant to `true` and put the intent filter back in the manifest.

## Glide typing – off by default

Glide (gesture) typing needs a closed-source native library. RCBBoard does not include it. Before, the user could load it from a file (*Advanced → Load gesture typing library*), or the system library was used when available. That library replaces RCBBoard's own native library (`libjni_latinime`), the dictionary engine included.

### The flag

| | |
| --- | --- |
| Constant | `RcbFeatures.GLIDE_TYPING_ENABLED` in `app/src/main/java/helium314/keyboard/latin/define/RcbFeatures.kt` |
| Set by | the build config field `GLIDE_TYPING_ENABLED` in `app/build.gradle.kts` (`defaultConfig`) |
| Default | `false` |
| Turn on | change the default in `app/build.gradle.kts`, or build with `./gradlew assembleRelease -PrcbboardGlideTyping=true` |

There is no setting for it in the app, and users can't change it.

### What the flag controls

When `false`:

- `JniUtils` loads neither the user-supplied library (`files/libjni_latinime.so`) nor the system glide library, only RCBBoard's own native library. `JniUtils.sHaveGestureLib` therefore stays `false`. A library file loaded earlier is left in place, but not loaded.
- `SettingsValues.mGestureInputEnabled` is always `false`, so the keyboard never starts glide input.
- *Load gesture typing library* is not shown in Advanced, and it is not registered at all, so the settings search can't find it (`AdvancedScreen.kt`, `RcbFeatures.isGestureLibraryLoadingEnabled()`).
- The *Gesture typing* and *Gesture data* menu entries, all gesture typing settings, the glide-only text correction settings ("Autospace before/after gesture typing a word"), the gesture data reminder and the welcome wizard's glide typing hint are hidden, also from search. These depend on `sHaveGestureLib` (`MainSettingsScreen.kt`, `SettingsContainer.kt`, `TextCorrectionScreen.kt`, `SettingsActivity.kt`, `WelcomeWizard.kt`, `GestureDataGathering*.kt`).

When `true`, everything works as before, including loading a library from a file. The `nouserlib` build type still never loads a user-supplied library.

No glide typing code was deleted: the gesture detection (`PointerTracker`, `GestureStrokeRecognitionPoints`, `BatchInputArbiter`), the gesture typing settings screen, the gesture data code and `LoadGestureLibPreference.kt` are all still there.

## Verification (2026-11-25, Android 15 emulator, debug build)

| Check | Result |
| --- | --- |
| Main menu shows the new icons, light and dark theme | ✅ screenshots checked |
| All 9 menu entries open the right screen | ✅ (scripted tap and title check) |
| No "Add dictionary from file" entry; no add button in a language's dictionary dialog | ✅ |
| Only the five languages above in Languages & Layouts | ✅ |
| First start after clearing data: English (UK), Sinhala (Singlish) | ✅ |
| English typing, language switching with the globe key, Singlish `mama` → මම | ✅ typed by tapping keys |
| Flag `false`: no *Load gesture typing library*; search for "gesture" finds no glide setting | ✅ |
| Flag `true` (`-PrcbboardGlideTyping=true`): *Load gesture typing library* is back | ✅ |
| Glide typing itself with the flag `true` | ❌ not tested: the closed-source library is not available here |
| Unit tests (`testRunTestsUnitTest`, CI setup, JDK 25) | ✅ 410 tests, 0 failures. The first-start tests in `LanguageAllowListTest` were changed to the new defaults. |
| Debug and release builds | ✅ build (release unsigned locally) |
| Lint | Same 68 inherited `MissingTranslation` errors as before, no new issues |

## Emoji row

*Settings → Preferences → Emoji row* (`show_emoji_row`, **off by default**) adds a row of 10 emoji above the letter and symbol keys.

- **Content:** the user's recently used emoji, most recent first, filled up with popular defaults (`EmojiRow.DEFAULT_EMOJIS`). Recent emoji come from the emoji palette, emoji search, emoji suggestions and emoji tapped in the row itself (`KeyboardActionListenerImpl.rememberTypedEmoji`). Nothing is remembered in incognito mode.
- **Updating:** the row content is part of `KeyboardId`, so the keyboard is rebuilt with the new order the next time it is shown, not while typing (keys don't move under the finger).
- **Not shown** in password fields, or on number pads, the phone pad and the emoji and clipboard views.
- **Height:** like the number row, the row takes space from the other rows, so the keyboard keeps its height. The emoji and clipboard views count it too, so their bottom rows line up.
- **Code:** `keyboard/emoji/EmojiRow.kt` (rules, unit-tested in `EmojiRowTest`), `KeyboardParser.createRows` (adds the row), `PreferencesScreen.kt` (setting).

Verified on the Android 15 emulator: off and on in Preferences, emoji inserted by tapping (single and multi-code-point emoji such as ❤️), recent emoji moving to the front after the keyboard is reopened, symbols page, Sinhala (Singlish) layout, light and dark theme.

## RCBBoard branding

- APK files are named `RCBBoard_<version>-<build type>.apk`.
- The app name, spell checker name and settings titles say RCBBoard in all languages, including transliterated forms in translations. Translation updates taken from HeliBoard can bring back the old name in `ime_settings`, `spell_checker_service_name` and `android_spell_checker_settings`: check these after each translation update.
- About shows the RCBBoard slogan, the source code link and the licence. The licence entry opens RCBBoard's `LICENSE` and names HeliBoard, OpenBoard and the AOSP keyboard: this attribution is required by GPL-3.0 and the inherited copyright notices and must stay.
- Links to the HeliBoard wiki and HeliBoard discussions were removed from the app (custom layouts link to RCBBoard's `layouts.md`).
- Intentionally unchanged: copyright and licence headers, `LICENSE*`, `licenses/`, the third-party licences screen, the internal `helium314.keyboard` package, code comments with HeliBoard issue links, internal file names such as `heliboard.db` (renaming would lose user data), and the gesture data texts (they describe HeliBoard's gesture data project and are only shown with glide typing).

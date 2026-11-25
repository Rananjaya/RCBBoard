# RCBBoard 5.1.0 release report

| | |
| --- | --- |
| Version | **5.1.0**, versionCode **50100** (previous release: 5.0.0 / 50000) |
| Application ID | `com.rananjaya.rcbboard` |
| Source | `develop` at `dd82a518` (= the six feature branches merged); pushed as `release/5.1.0` |
| Version source | `.github/scripts/next_version.py`: new features since `rcbboard-v5.0.0` → minor bump |
| Date | 2026-11-26 |
| Signing | GitHub release workflow with the existing production key (see section 6) |

## 1. What's new

**Features**
- First-launch tutorial (7 pages): turn on and choose RCBBoard with Android's own screens, English and Sinhala, switching languages, handy features, choose a look (theme). Replay from General → Tutorial; a setup card appears while RCBBoard is not set up.
- Redesigned main menu (Material 3, Material Symbols Rounded icons) and new toolbar icons.
- New app icon with Sinhala අ and Latin A.
- New installs start with English (UK) and Sinhala (Singlish).
- Optional emoji row (off by default): your recently used emoji above the keys.

**Removed or hidden by design**
- Adding dictionaries from files and opening `.dict` files from other apps.
- Glide typing and loading a glide typing library (build flag `GLIDE_TYPING_ENABLED`, off).
- HeliBoard wiki and community links in the app; the app and spell checker are named RCBBoard in all languages.

**Bug fixes**
- The app closed itself when opened right after installing or a force stop while RCBBoard was not the current keyboard (Android 15). Also affects HeliBoard.
- Floating toolbar-only mode: touch area, emoji and clipboard views (ported from HeliBoard).
- Translations updated from HeliBoard 4.3-beta2.

**Security and privacy**
- Passwords and codes marked sensitive by password managers are no longer kept in the clipboard history.
- The dictionary file handler only accepts other apps' content URIs; unreadable files no longer crash the app.
- No typed words, blocked words or inserted emoji in logs.
- Unused JitPack repository removed from the build.

## 2. Build and test results

Run on 2026-11-26 on `dd82a518` (macOS, Gradle 9.7.1, Android Studio JBR 25).

| Check | Result |
| --- | --- |
| Unit tests (CI command) | ✅ 430 tests, 0 failures, 0 errors, 0 skipped |
| Native dictionary tests (ASan/UBSan) | ✅ 115 tests passed |
| Lint (`lintRelease`) | 68 errors (all inherited `MissingTranslation`, see below), 119 warnings, no new errors |
| Debug build | ✅ builds |
| Release build (5.1.0 / 50100, unsigned) | ✅ builds: `RCBBoard_5.1.0-release.apk`, 22,952,188 bytes, SHA-256 `1063c11155c8a361ec42b488e20276dcb38cc9e7d00042d034509e4227017805` (unsigned; identical across two builds) |
| Instrumentation tests | Not run: the project has no instrumentation tests |

**Lint.** All 68 errors are `MissingTranslation` in `strings-talkback-descriptions.xml` (TalkBack spoken descriptions), inherited from HeliBoard: untranslated in 17 small locales (e.g. Asturian, Filipino, Welsh) and partly in others. Missing strings fall back to English; nothing breaks and typing is not affected. **Not release-blocking.** Sinhala lacks 6 of them, for a Sinhala speaker to translate: `spoken_clipboard_suggestion` (Clipboard suggestion), `spoken_description_mode_dpad` (D-pad mode), `spoken_description_mode_numpad` (Numpad mode), `spoken_description_mode_number` (Numbers mode), `spoken_description_mode_clipboard` (Clipboard mode), `spoken_description_emoji_category_eight_smiley` (Smiley). Ten strings and some resources left unused by the HeliBoard link removal show as warnings only; their translations are kept.

## 3. Device tests

Android 15 emulator (Pixel, arm64), debug build of the same code unless noted. No physical phone was connected.

| Area | Result |
| --- | --- |
| English typing, auto-capitalisation | ✅ |
| Sinhala (standard) typing | ✅ e.g. `ැයස` |
| Sinhala (Wijesekara) | ✅ ක + ා + ම → `කාම` |
| Singlish | ✅ `mama` → මම |
| Language switching (long-press space, globe key) | ✅ |
| Learning: new words suggested after use | ✅ English `zorbak`, Sinhala `ගොරබක`, after the designed 25 s delay |
| Toolbar actions | ✅ undo, select word, copy, paste, cursor keys, clipboard, settings. Redo had no visible effect in Contacts; voice not testable (no voice keyboard enabled) |
| Main menu: all 9 entries open the right screen | ✅ |
| Tutorial: 7 pages, Android's keyboard settings and picker, auto-advance, Skip, Get started, Back key, replay, setup card, already set up | ✅ |
| First launch (fresh install) and no tutorial after an update | ✅ |
| Emoji row off/on, letters and symbols | ✅ |
| Recent emoji order (picker, suggestion, row taps) | ✅ |
| Emoji row in incognito mode: nothing remembered | ✅ |
| Typed text and emoji not in logcat | ✅ |
| `.dict` files: no handler in 5.1.0, direct intent ignored | ✅ (the handler listed on the emulator belongs to 5.0.0) |
| Glide typing: no option in Advanced or search | ✅ (flag off in debug and release `BuildConfig`) |
| Light, dark, dynamic colours | ✅ |
| Portrait, landscape, small screen (720×1280), font 130 % | ✅ |
| About: licence attribution, Source code opens the GitHub link | ✅ |
| Keyboard performance (60 taps, frame times) | ✅ 17 ms (60 Hz), same as 5.0.0 (16–17 ms); no regression |
| Clipboard: sensitive clips not stored | Unit tests only (no app on the emulator can mark a clip as sensitive) |
| Password fields: no emoji row, no learning | Unit tests only (Chrome needed first-run setup, which was not accepted) |
| TalkBack | Not tested (owner decision); turning TalkBack on caused no crash |
| Physical phone | Not tested: no phone connected |

## 4. Security and privacy (release APK)

- Permissions: `READ_USER_DICTIONARY`, `WRITE_USER_DICTIONARY`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `READ_CONTACTS` (only when contact suggestions are turned on), plus AndroidX's signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No `INTERNET`.**
- No `android:debuggable` in the manifest.
- No analytics, crash reporting, HTTP libraries, sockets or WebView in the code (dex scan for Firebase, Crashlytics, Sentry, AppCenter, Amplitude, Mixpanel, OkHttp, Retrofit, `HttpURLConnection`, `Socket`, `WebView`: none).
- Exported components: the keyboard and spell checker services (protected by system permissions), the launcher/settings activities, `SystemBroadcastReceiver` (system broadcasts only) and AndroidX `ProfileInstallReceiver` (protected by `DUMP`). Providers are not exported.
- No signing material in the repository or the APK; the release is signed by the GitHub workflow from protected secrets.
- Earlier security fixes are present (clipboard, dictionary URIs, logging, native dictionary hardening, backup restore).

## 5. GPL-3.0 and attribution

- No copyright, SPDX or licence line was removed from any file since the fork point; Java files converted to Kotlin kept their AOSP notices (same as upstream).
- `LICENSE` (GPL-3.0), `LICENSE-Apache-2.0`, `LICENSE-CC-BY-SA-4.0`, `licenses/` present. The APK contains `assets/third_party_licenses/` (GPL-3.0, AGPL-3.0, Unicode, Sinhala dictionary licences, `THIRD_PARTY_NOTICES.md`).
- `THIRD_PARTY_NOTICES.md` credits the emoji and Sinhala dictionaries, the Wijesekara references, Material Symbols (Apache-2.0) and the Noto fonts used for the icon letters (OFL-1.1).
- About shows "RCBBoard is based on HeliBoard, OpenBoard and the Android Open Source Project keyboard" and a working Source code link.
- **Corresponding source:** the exact release commit is on GitHub as `release/5.1.0` (and in the six PR branches). The About link points to `develop`, so merge the PRs before or when publishing.
- The signing certificate's name is `CN=HeliBoard Personal`. It cannot be changed without breaking updates for existing users.

## 6. Signing and upgrade compatibility

- Official 5.0.0 (`RCBBoard-5.0.0.apk`, SHA-256 `11543c07…8d`, matches its `SHA256SUMS.txt`): certificate SHA-256 `0b01373116d4a7b57646c4c7e2ffc28af91870bdbeabb4b8c05c00f313e2291b`, package `com.rananjaya.rcbboard`, versionCode 50000.
- 5.1.0 is signed by the release workflow with the same production key; `sign_release.py` refuses any other certificate. Same package, higher versionCode (50100), so it installs as an update.
- **Pending:** the production-signed `RCBBoard-5.1.0.apk` is produced by *Actions → Release* (dry_run off) after the PRs are merged. Its SHA-256 is published in the release's `SHA256SUMS.txt`. It is not built from `dd82a518` itself but from the merge commit GitHub creates, with the same code.

**Upgrade test** (official 5.0.0 → 5.1.0 on the emulator): 5.0.0 was installed, enabled and selected, with the number row and language key turned on and English (US) + Sinhala (Wijesekara) enabled. **Pending:** installing the signed 5.1.0 over it, then checking the settings, languages, keyboard selection, that no tutorial appears (update rule), and that the `.dict` handler is gone. The equivalent behaviour is covered by unit tests and the fresh/update-install tests in section 3.

## 7. Known limitations

- The 68 inherited TalkBack translation gaps (section 2).
- Learning stores a word 25 s after it is typed (so corrections are not learned), and not in incognito mode or fields that ask keyboards not to learn, such as auto-capitalised name fields.
- Translation updates from HeliBoard can bring back the old name in three strings; check after each update.
- The README screenshot still shows the old icon.

## 8. Remaining manual checks

- On a real phone: install the signed 5.1.0 over 5.0.0, type in English and Sinhala, check the tutorial does not appear and settings are kept.
- Password managers: copy a password and check it does not appear in the clipboard history.

## 9. Recommendation

**GO, on these conditions:** (1) merge the six PRs into `develop` so the published source matches the release; (2) run Development → QA → Release (dry_run off) on the merged commit, which signs with the existing key and checks the certificate; (3) the upgrade test over 5.0.0 passes with the signed APK. Not release-blocking: the inherited TalkBack translation gaps, the untested TalkBack and the physical-phone check (recommended before announcing the release).

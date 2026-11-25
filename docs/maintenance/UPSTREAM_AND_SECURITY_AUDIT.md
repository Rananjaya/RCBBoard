# Upstream synchronisation and security audit – November 2026

| | |
| --- | --- |
| Date | 2026-11-25 |
| RCBBoard branch audited | `develop` at `85922262` (Merge pull request #29) |
| Work branch | `chore/upstream-sync-security-2026-11` (not pushed) |
| Upstream | [HeliBorg/HeliBoard](https://github.com/HeliBorg/HeliBoard), `main` at `10638635` = release **v4.3-beta2** (2026-11-15) |

This report covers the comparison of RCBBoard with the original HeliBoard project, a security and privacy review of RCBBoard, a dependency review, the changes made, and what is left to decide.

## 1. Current RCBBoard project status

- **Repository:** `Rananjaya/RCBBoard`, a GitHub fork of HeliBoard. The working tree was clean on `develop`, up to date with `origin/develop`.
- **Build:** Gradle 9.7.1 (wrapper checksum pinned), Android Gradle Plugin 9.1.1, Kotlin 2.4.0, `compileSdk`/`targetSdk` 37, `minSdk` 21, NDK 28.0.13004108, Java 17 bytecode.
- **Application ID:** `com.rananjaya.rcbboard` (unchanged by this work). Version in source: `4.3-beta1` / 4300; releases set the version from the release workflow (latest tag `rcbboard-v5.0.0`).
- **History with upstream:**
  - On 2026-11-04 upstream `main` up to `ba2500bb` was merged (`1d6e12dc`), and right after that the keyboard code was returned to its state before that merge (`7214a540`, "return to the keyboard from before the latest upstream merge"). The code base is therefore upstream `5790f31a` (2026-10-31) plus RCBBoard's work, although Git sees `ba2500bb` as the merge base.
  - Since then, parts of the reverted upstream work were ported back one at a time with tests (pull requests #25–#27).
  - `origin/main` mirrors upstream up to `3e91b4f1`; one RCBBoard branch, `fix/upstream-floating-toolbar-height`, was pushed but never merged.
- **Upstream fetch:** upstream was fetched into `refs/remotes/upstream/main` (and two tags under `refs/upstream-tags/`). No remote was added and no local branch was changed.

### RCBBoard-specific work (all preserved)

| Area | What it is |
| --- | --- |
| Branding | Name, icon, About screen (developer credit, source link), settings UI refresh, application ID `com.rananjaya.rcbboard` |
| Sinhala dictionary | `assets/dicts/main_si.dict`, built from `third_party/sinhala-dictionary/` (GPL-3.0); `SinhalaLookup.kt`, Sinhala handling in `DictionaryInfoUtils`, `ReadOnlyBinaryDictionary`, `DictionaryFactory` |
| Emoji dictionaries | `emoji_en.dict`, `emoji_si.dict` with sources in `third_party/emoji-dictionaries/` |
| Layouts and languages | Sinhala (Wijesekara) layout, Sinhala (Singlish) phonetic input with conjuncts, smart space and dictionary-checked candidates, English (UK), language allow-list (English US/UK and Sinhala) |
| Security fixes in native code | Patricia trie stack buffer overflow (`2926a31f`), v2 dictionary out-of-bounds reads (`1efa248f`), `IntArrayView` null `memmove` (`7a4e2923`), with a sanitizer host test suite. **Upstream does not have these fixes.** |
| Other fixes | Safe backup restore (`41ca2be6`), no typed text in `RichInputConnection` logs (`03982a41`), emoji search insert, emoji suggestions setting, `WordComposer` copy |
| Licensing | `licenses/`, `third_party/`, in-app third-party licences screen, licence asset sync task in `app/build.gradle.kts` |
| CI/CD | Three-stage development/QA/release workflows with pinned actions and dry-run release default |

## 2. Upstream repository and latest release

- Official repository: `https://github.com/HeliBorg/HeliBoard` (the project moved from `Helium314/HeliBoard`; the README, CONTRIBUTING.md and the About screen already point to `HeliBorg`). Default branch `main`.
- Latest tag and release: **v4.3-beta2** (`10638635`, 2026-11-15), version code 4301. The previous release, v4.3-beta1 (4300), is the base RCBBoard's source version comes from.
- No security advisories are published for HeliBoard, and none of the reviewed upstream commits is a security fix.

## 3. Upstream commits reviewed

All 21 upstream commits that RCBBoard does not have in their upstream form were reviewed: the 14 reverted on 2026-11-04 and the 7 that came after. "Already in RCBBoard" means the change was ported earlier, possibly in a different form.

| Upstream commit | Subject | Category | Decision |
| --- | --- | --- | --- |
| `6c36d91d` | move SuggestedWords to suggestions folder | Refactoring | Already in RCBBoard (`cdbc3942`, #27) |
| `a74cd059` | SuggestedWordInfo to kotlin | Refactoring | Already in RCBBoard (`cbb41483`, #27) |
| `7a106de3` | SuggestionResults to suggestions folder and to kotlin | Refactoring | Already in RCBBoard (`a657f3af`, #27) |
| `aec7f831` | suggestedWords to kotlin | Refactoring | Already in RCBBoard (`86b46cb9`, #27) |
| `87b6e5dd` | more consistent behavior or default getTouchedWordBounds | Bug fix | Already in RCBBoard (`3a3aa605`, #26) |
| `ba2500bb` | fix broken emoji search insert | Bug fix | Already fixed in RCBBoard, with a test (`d9eb4831`) |
| `b7074d90` | fix problems with floating toolbar-only mode | Bug fix | **Integrated** (RCBBoard's own port, branch `fix/upstream-floating-toolbar-height`) |
| `e926aeba` | fix emoji and other special views when using toobar-only mode | Bug fix | **Integrated** (ported to `KeyboardSwitcher.java`) |
| `10638635` | upgrade version and translations | Translations | **Integrated: translations only.** The version bump and HeliBoard's store changelogs do not apply. |
| `3e91b4f1` | fix repeated next word suggestions | Bug fix | Not applicable: it fixes code added by `d08e05d5`, which RCBBoard does not have |
| `f3e772b5` | reduce log level for keyboard creation | Cosmetic | Not integrated: only changes debug-mode log levels |
| `26d299a3` | simplify getFromApplicationSpecifiedCompletions | Refactoring | Not integrated (no behaviour change) |
| `4f15ec84` | TextRange -> TextAtCursor / WordAtCursor | Refactoring | **Needs a decision** (see below) |
| `d08e05d5` | get suggestions for some texts that are not detected as words | Improvement | **Needs a decision** (see below) |
| `b6b62e83` | do emoji search in secondary locales too | Optional enhancement | **Needs a decision** (see below) |
| `db5e834e` | show emoji search button only if available for current locales | Optional enhancement | **Needs a decision**, depends on `b6b62e83` |
| `92df2740` | keyboardSwitcher to kotlin | Refactoring | Not integrated: RCBBoard went back to the Java version on purpose |
| `50628345` | improve startup times | Performance | Not integrated: built on the Kotlin `KeyboardSwitcher` and new view stubs |
| `864cf09e` | make inputView nullable again | Refactoring | Not integrated: Kotlin `KeyboardSwitcher` follow-up |
| `a14de984` | comments and readability stuff in latinIME | Refactoring | Not integrated |
| `2d9869b2` | remove inputView from LatinIME | Refactoring | Not integrated: Kotlin `KeyboardSwitcher` follow-up |

### Changes that conflict with RCBBoard's customisations and need approval

These were not integrated, because they touch code that RCBBoard changed for Sinhala and Singlish, or code that was reverted on purpose:

1. **`4f15ec84` + `d08e05d5` (+ its fix `3e91b4f1`): suggestions for text that is not detected as a word.** This rewrites how the word at the cursor is found (`TextRange` → `TextAtCursor`) and adds a second "extended word" suggestion pass in `Suggest.kt`. RCBBoard relies on the current word boundary behaviour for Sinhala (combining vowel signs, ZWJ conjuncts) and for Singlish candidates (see `SinhalaWordBoundaryTest`, `WordIteratorTest` and the Singlish tests). Upstream also needed a follow-up fix (`3e91b4f1`) for repeated next-word suggestions. **Recommendation:** port it as its own pull request, in the same way as #25–#27, with Sinhala and Singlish baseline tests first.
2. **`b6b62e83` + `db5e834e`: emoji search in secondary languages.** This would let the emoji search also use the Sinhala emoji dictionary when Sinhala is a secondary language. It changes `DictionaryFactory` and `EmojiSearchActivity`, which RCBBoard changed for Sinhala emoji. **Recommendation:** a useful feature for English + Sinhala users; port it separately after checking it together with the Sinhala emoji dictionary.
3. **`92df2740`, `50628345`, `864cf09e`, `a14de984`, `2d9869b2`: Kotlin `KeyboardSwitcher` and start-up work.** RCBBoard returned to the Java `KeyboardSwitcher` on 2026-11-04. Taking these means taking that rewrite again. The only user-visible gain is faster start-up (emoji and clipboard views are created when first opened). **Recommendation:** decide whether to move to the Kotlin `KeyboardSwitcher` again; until then, port upstream fixes to the Java version, as done here for `e926aeba`.

## 4. Relevant fixes found

| Category | Item |
| --- | --- |
| Security fixes | None from upstream. Two hardening fixes and two privacy fixes from this audit (section 5). |
| Critical bug fixes | None outstanding. The emoji search insert bug (`ba2500bb`) was already fixed in RCBBoard. |
| Important improvements | Floating toolbar-only mode fixes (`b7074d90`, `e926aeba`), integrated. |
| Dependency updates | None needed for security (section 6). JitPack repository removed. |
| Optional enhancements | Emoji search in secondary languages; suggestions for text not detected as words; faster start-up; updated translations (integrated). |
| Incompatible with RCBBoard customisations without more work | The Kotlin `KeyboardSwitcher` series, and the `TextAtCursor` word detection rewrite (section 3). |

## 5. Security audit findings

### Fixed in this work

| # | Severity | Finding | Fix |
| --- | --- | --- | --- |
| S1 | Medium (privacy) | **Sensitive clips stored in the clipboard history.** Passwords and one-time codes that password managers mark as sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`, Android 13+) were hidden from the clipboard suggestion, but still saved in the clipboard history database. That history stays on disk until the retention time ends and is included in RCBBoard backups and in Android's device backup. | Do not store clips marked as sensitive (`ClipboardHistoryManager.mayStoreInHistory`), with tests. |
| S2 | Low–medium | **Exported dictionary `VIEW` handler accepted any URI.** `SettingsActivity`/`SettingsActivity2` are exported and copy `intent.data` into the dictionary cache. Explicit intents skip the `content://` filter, so another app could make RCBBoard read its own private files (`file://` or its own non-exported providers) into the cache, or crash the settings screen with an unreadable URI. A `SecurityException` on the background copy thread crashed the whole process, keyboard included, because only `IOException` was caught. No way to get the data out was found. | Only accept `content://` URIs of other apps; handle read errors; catch runtime exceptions in `FileUtils.copyContentUriToNewFile`. |
| S3 | Low (privacy) | **User text in logs.** Three log messages that are not debug-only contained user text: the word when adding to a learning dictionary failed (`ExpandableBinaryDictionary`), the word when updating the blocked-suggestions file failed (`DictionaryFacilitatorImpl`), and the emoji picked in emoji search (`StuffReceiver`). They go to logcat and to the in-app log that *Save log* exports. | Log the length or a flag instead (continues `03982a41`). |
| S4 | Low (supply chain) | **Unused JitPack repository** in `build.gradle.kts`. No dependency comes from it, but JitPack builds artifacts on demand from any GitHub repository. | Removed; all configurations resolve without it. |

### Reviewed, no change needed

- **Manifest permissions:** `READ_USER_DICTIONARY`, `WRITE_USER_DICTIONARY`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `READ_CONTACTS` (runtime permission, only requested when contact suggestions are turned on). **No `INTERNET` permission**, and none of the dependencies adds one (checked in the merged manifest of the release APK, section 9).
- **Exported components:** `LatinIME` (protected by `BIND_INPUT_METHOD`), `AndroidSpellCheckerService` (`BIND_TEXT_SERVICE`), the launcher activities, `SpellCheckerSettingsActivity`, and `SystemBroadcastReceiver`. The receiver only acts on protected system broadcasts, which other apps cannot send; any other intent only ends the process when RCBBoard is not the current keyboard. Both `FileProvider`s are not exported. The emoji search result receiver is registered as `RECEIVER_NOT_EXPORTED`.
- **PendingIntent and WebView:** none used.
- **Network:** no network code (`URL`, sockets, HTTP clients) in the app.
- **Passwords:** password fields are detected (`InputTypeUtils.isPasswordInputType`); learning, suggestions and the clipboard suggestion are turned off for them, as in upstream.
- **Dictionary and file parsing:** native dictionary reading is covered by RCBBoard's sanitizer tests (115 passing). Backup restore checks entry paths (`restoreEntryToDir`, `41ca2be6`).
- **Native code loading:** the optional user-supplied glide typing library is only loaded from the app's private files folder and only when its SHA-256 matches the checksum stored when the user added it. The `nouserlib` build type turns this off.
- **Secrets:** no keys, tokens or keystores in the repository. Signing happens in the release workflow from GitHub secrets.
- **Logging in general:** other log statements that contain words or suggestions are only active in debug mode or behind compile-time `DEBUG` constants.
- **Logcat export:** *Save log* (About screen) writes `logcat -d` and the in-app log to a file the user picks. Only the user can start it; with S3 fixed, it contains no typed text from RCBBoard's own logging.

### Privacy points that need a decision (not changed)

- **P1 – Android device backup (`android:allowBackup="true"`, no backup rules).** Android's cloud backup includes learned words, the clipboard history and settings. The README mentions this ("or in Android's own device backup if you have that turned on"), so it was left as it is. Option: add `dataExtractionRules`/`fullBackupContent` to keep the clipboard history (and maybe the learned words) out of cloud backup, while keeping RCBBoard's own backup and restore.
- **P2 – Gesture data sharing (inherited from HeliBoard).** With a glide typing library installed, the *Gesture data* screen lets the user share gesture data by email with HeliBoard's research address, for upstream research. It only runs when the user starts it, and the promotion stops after 2027-01-01. Option: remove the screen and `GestureFileProvider` from RCBBoard, since RCBBoard users may not expect data to go to the upstream project.
- **P3 – Clipboard history on by default** and stored in device-protected storage, as in upstream. S1 removes the most sensitive content; the retention time setting limits the rest.

## 6. Dependency audit

- **Known vulnerabilities:** none. All 14 direct dependencies and plugins, and all 232 artifacts of the resolved release runtime classpath, were checked against [OSV](https://osv.dev) (which includes the GitHub Advisory Database). There were no matches. Only public package names and versions were sent.
- **Toolchain:** Gradle 9.7.1, AGP 9.1.1 and Kotlin 2.4.0 are recent. `targetSdk` 37 is above Google Play's current requirement (API 35 for new apps and updates; API 36 is required from August 2026).
- **Versions are the same as upstream v4.3-beta2.** No dependency was upgraded: none has a known vulnerability, and several newer versions need `minSdk` 23 (upstream deliberately pins them for `minSdk` 21).

| Dependency | In use | Latest stable | Note |
| --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.17.0 | 1.19.1 | 1.18+ requires minSdk 23 (comment in build file) |
| Compose BOM | 2025.11.01 | 2026.11.01 | newer requires minSdk 23 (comment in build file) |
| `com.github.skydoves:colorpicker-compose` | 1.1.3 | 1.3.0 | newer requires minSdk 23 (comment in build file) |
| `androidx.navigation:navigation-compose` | 2.9.8 | 2.10.2 | optional; check minSdk before upgrading |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | 1.13.0 | optional; check the Kotlin version it needs |
| Kotlin | 2.4.0 | 2.4.21 | optional patch upgrade (three places: `build.gradle.kts`, `app/build.gradle.kts` plugins, `tools/make-emoji-keys`) |
| AGP | 9.1.1 | 9.5.2 | optional; larger upgrade, do it together with upstream |
| `org.robolectric:robolectric` (test) | 4.16.1 | 4.18.1 | optional |
| `org.mockito:mockito-core` (test) | 5.23.0 | 5.24.0 | optional |
| recyclerview, autofill, viewpager2, desugar_jdk_libs, reorderable, junit | current | – | up to date |

- **Deprecated APIs:** the compiler reports deprecation notes for inherited Java code (same as upstream). Gradle reports deprecated features that will break with Gradle 10; this needs AGP and plugin updates, ideally together with upstream.
- **Build hardening ideas (not done):** Gradle dependency verification (`gradle/verification-metadata.xml`).

## 7. Changes made

All on branch `chore/upstream-sync-security-2026-11`, based on `develop` (`85922262`):

| Commit | Message | Kind |
| --- | --- | --- |
| `e593b3c8` | fix(privacy): stop logging learned words, blocked words and inserted emoji | Audit (S3) |
| `8024fecb` | fix(privacy): keep sensitive clips out of the clipboard history | Audit (S1), with tests |
| `f8c7a9ec` | fix(security): only accept other apps' content URIs for dictionary files | Audit (S2) |
| `188c2b66` | fix(keyboard): fix floating toolbar-only touch area | Upstream `b7074d90` (RCBBoard's existing port, cherry-picked) |
| `6d7a40b5` | fix(keyboard): show emoji and clipboard views in toolbar-only mode | Upstream `e926aeba`, ported to Java |
| `a597a5f4` | build(deps): remove the unused JitPack repository | Audit (S4) |
| `c7e85376` | chore(upstream): update inherited translations from HeliBoard 4.3-beta2 | Upstream `10638635` (translations only) |
| the commit that adds this file | docs(maintenance): add the upstream and security audit report | This report |

Not changed: application ID, version, dictionaries, layouts, licence files, CI workflows, the Sinhala and Singlish code.

## 8. RCBBoard customisations preserved

- **Dictionaries and layouts unchanged:** SHA-256 of `main_si.dict`, `emoji_si.dict`, `emoji_en.dict`, `sinhala_wijesekara.json` and all files in `third_party/sinhala-dictionary/` and `third_party/emoji-dictionaries/` match before and after. The branch does not touch `app/src/main/assets/` or `third_party/`.
- **Licences:** no copyright or SPDX header, `LICENSE*`, `licenses/` or `third_party/` file was removed or changed. The ported upstream code credits HeliBoard and Helium314 in the commit messages.
- **Branding, application ID, Sinhala/Singlish code, language allow-list:** untouched (see the diff summary in section 9).

## 9. Tests and build results

All results are from this machine (macOS, Gradle 9.7.1) on 2026-11-25. Nothing is reported as passing unless it ran.

| Check | Before (`develop`) | After (this branch) |
| --- | --- | --- |
| Unit tests (CI command: `testRunTestsUnitTest` with `.github/ci-unit-tests.init.gradle`, `XLinkTest` excluded as in CI) | 410 tests, 0 failures | **414 tests, 0 failures** (4 new in `ClipboardSensitivityTest`) |
| Native host tests (`run_host_tests.sh`, ASan + UBSan) | – | **115 tests passed** (native code unchanged) |
| Android lint (`lintRelease`) | – | Fails with 68 `MissingTranslation` errors and 108 warnings, identical before and after the translation commit; no issue in any changed source file. These errors are inherited, because this branch changes no base strings; CI does not run lint. |
| Dependency vulnerability check (OSV) | – | 0 known vulnerabilities (direct dependencies and 232 resolved release artifacts) |
| Debug APK (`assembleDebug`) | – | **Builds** |
| Release APK (`assembleRelease`) | – | **Builds**, unsigned: there is no signing configuration locally, and signing happens in `release.yml`. A signed release was not built. |
| Instrumentation tests | – | **Not run:** the project has no `androidTest` sources. |

**Java version for unit tests.** With JDK 17 (what README/CONTRIBUTING mention for building), every Robolectric test fails at start with "Android SDK 36 requires Java 21". The tests above ran with Android Studio's JBR 25; CI uses JDK 21. Building works with JDK 17.

**Checks on the Android 15 (API 35) emulator with the debug APK:**

- Permissions of the release APK (`aapt2 dump permissions`): `READ_USER_DICTIONARY`, `WRITE_USER_DICTIONARY`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `READ_CONTACTS`, and AndroidX's own signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No `INTERNET`.**
- Dictionary `VIEW` intent (S2): a `file://` URI to RCBBoard's own preferences and `content://` URIs of both of RCBBoard's own providers were rejected without reading; URIs of unknown providers were handled with a warning; the app did not crash in any case. A real `.dict` shared through MediaStore still opens the *Add dictionary from file* dialog with the correct dictionary information.
- The bundled `main_si.dict` in the release APK has the same SHA-256 as the source file.

**Sinhala, language and layout checks.** These are covered by the unit tests, which all pass: `SinhalaDictionaryTest`, `SinhalaNormalizationTest`, `SinhalaWijesekaraLayoutTest`, `SinhalaWordBoundaryTest`, the Singlish tests in `InputLogicTest`, `LanguageAllowListTest`, `LanguageAllowListCustomLayoutTest`, `SubtypeTest`, `ParserTest` and `ApplicationIdTest`. Typing Sinhala on the emulator and switching languages by hand was **not** done in this session.

**Floating toolbar-only mode** (`188c2b66`, `6d7a40b5`) needs a floating keyboard and a hardware keyboard. It was checked by compiling, by review against upstream and by the full test suite, but **not by hand on a device**.

## 10. Outstanding issues

1. **Decisions on the upstream work not integrated** (section 3): the `TextAtCursor`/extended-word suggestions, emoji search in secondary languages, and the Kotlin `KeyboardSwitcher` series.
2. **Privacy decisions** P1 (cloud backup rules), P2 (gesture data sharing with HeliBoard) and P3 (clipboard history default) (section 5).
3. **Test by hand on a device:** floating keyboard with a hardware keyboard and "show only the toolbar"; Sinhala/Singlish typing and language switching on a real build.
4. **Lint:** the 68 inherited `MissingTranslation` errors make `lintRelease` fail. Options: a lint baseline, or `MissingTranslation` as a warning, and then running lint in CI.
5. **Documentation:** say that unit tests need JDK 21 or newer (README/CONTRIBUTING mention JDK 17).
6. **Outdated dependencies:** optional upgrades in section 6, preferably together with upstream.
7. **Gradle 10 deprecations** reported by the build.
8. **Upstream contribution:** RCBBoard's native dictionary security fixes (and S2) also apply to HeliBoard. Consider reporting them to HeliBoard privately, following its security policy.
9. The other callers of `FileUtils.copyContentUriToNewFile` (dictionary file picker, custom font) do not catch read errors. They only get URIs that the user picked, so the risk is low, but they could show an error instead of crashing.

## 11. Git commits

Branch `chore/upstream-sync-security-2026-11`, based on `develop` at `85922262`. Not pushed. Commit messages have no AI attribution, as CLAUDE.md requires.

```
e593b3c8 fix(privacy): stop logging learned words, blocked words and inserted emoji
8024fecb fix(privacy): keep sensitive clips out of the clipboard history
f8c7a9ec fix(security): only accept other apps' content URIs for dictionary files
188c2b66 fix(keyboard): fix floating toolbar-only touch area        (author: Rananjaya, cherry-picked from a7df5a36)
6d7a40b5 fix(keyboard): show emoji and clipboard views in toolbar-only mode
a597a5f4 build(deps): remove the unused JitPack repository
c7e85376 chore(upstream): update inherited translations from HeliBoard 4.3-beta2
```

The commit that adds this report comes after these. Its hash is in `git log`.

The upstream refs fetched for this review (`refs/remotes/upstream/main`, `refs/upstream-tags/v4.3-beta1`, `refs/upstream-tags/v4.3-beta2`) are local only. Remove them with `git update-ref -d <ref>` if they are not needed.

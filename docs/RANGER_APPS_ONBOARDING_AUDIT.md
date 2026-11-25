# Ranger Apps onboarding audit

Date: 2026-11-25. This document covers the onboarding of RCBBoard (redesigned in this change) and an assessment of the other Ranger Apps projects in the workspace. **The other apps were only read, not changed.** Their findings are based on their source code and READMEs at the commits listed below.

## 1. Shared design language

RCB Music's tutorial (`rcb-music`, `ui/screens/OnboardingScreen.kt`) is the reference. RCB Calendar and RCB Wake already follow the same pattern, and RCBBoard now does too.

| Element | Pattern |
| --- | --- |
| Structure | Swipeable pages (`HorizontalPager`), 5–7 pages |
| Top bar | "n of N" counter (read as "Page n of N") and **Skip**; **Close** when replayed |
| Page | Illustration in a 140 dp circle (`primaryContainer`), centred title (`headlineMedium`, marked as heading), body text, short bullet list |
| Layout | Portrait: picture above text, max 520 dp wide. Landscape: picture beside text. Text scrolls for large fonts. |
| Bottom bar | **Back**, animated pill dots, **Next** → **Get started** (**Done** when replayed) |
| Last page | "Make it yours": theme choices that apply immediately |
| Rules | Shown on a fresh install only; an update from a version without the tutorial counts as completed; Skip and Get started both store completion; replay from Settings never asks again. Permissions are explained in the tutorial but only requested afterwards. |
| Identity | Each app keeps its own colours, icon and wording; only the structure is shared |

Reusable parts: the page/dots/bottom-bar scaffold, the `isUpgradeInstall` / `shouldShowOnStart` rules (unit-tested in RCB Music and RCBBoard), and the "replay from Settings" entry. A shared library is not recommended (Compose and React Native apps, different repositories); copying the pattern per app is simpler.

## 2. RCBBoard (implemented)

**Before:** a three-step setup wizard (enable, select, done) with old setup images. It appeared on **every** start until RCBBoard was enabled and selected, did not remember completion, could not be replayed, and explained no features.

**Now:** a seven-page tutorial in the RCB Music style (`settings/onboarding/OnboardingScreen.kt`, rules in `Onboarding.kt`):

| # | Page | What it does |
| --- | --- | --- |
| 1 | Welcome to RCBBoard | App icon; private, offline, English and Sinhala |
| 2 | Turn on RCBBoard | Opens Android's own keyboard settings; explains Android's "may collect text" warning; shows ✓ when done and moves on by itself |
| 3 | **Make RCBBoard your keyboard** (middle step) | Opens Android's own keyboard picker; ✓ when done, moves on by itself. If RCBBoard is not turned on yet, it says so and links back to page 2. |
| 4 | English and Sinhala | The two default languages, Singlish examples (`mama` → මම, `sixhala` → සිංහල), where to turn on English (US), Sinhala and Wijesekara, and a **Try it here** field |
| 5 | Switch languages | Long-press the space bar (the default), the comma key's language button, and a switch for the real "Show a language key" setting |
| 6 | Handy features | Toolbar, comma long-press (clipboard, emoji, one-handed mode, settings), emoji suggestions, on-device learning, appearance settings |
| 7 | Ready to type | Live ✓ status for "Turned on" and "Your current keyboard" with buttons if not; **Choose a look**: Follow system / Light / Dark / Dynamic colours chips, the full Colours setting of Appearance, and a field to see the keyboard |

Behaviour:
- Shown on the first start of a fresh install. Not shown again after **Skip** or **Get started**. Updates from older versions don't get it.
- The back key goes to the previous page. On page 1 of the first start it leaves the app without storing completion, so the tutorial comes back next time.
- Replay: main menu → General → **Tutorial** (Close/Done, completion unchanged).
- When RCBBoard is not turned on or not the current keyboard (e.g. after Skip), the main menu shows a **Finish setting up RCBBoard** card. It never blocks the settings; **Set up** opens the tutorial at the missing step. The keyboard itself never depends on the tutorial.
- The tutorial requests no permissions.
- Fixed with it: since Android 15 the app was killed when opened from a stopped state (fresh install, force stop) while RCBBoard was not the current keyboard, so the first tap on the icon closed the app. See `SystemBroadcastReceiver.isInForeground()`.

## 3. Other apps

| App | Folder (commit) | Existing welcome experience | Priority |
| --- | --- | --- | --- |
| RCB Music | `rcb-music` (`a30b947`) | 5-page tutorial with theme page, replay in Settings | Reference – low |
| RCB Calendar | `rcb-calendar` (`47bafcc`) | Privacy agreement + 5-page tutorial, replay in Settings | Low |
| RCB Wake | `RCB-Wake` (`3ca88ad`, React Native) | Privacy agreement + 5-page tutorial, replay in Settings, translated | Low–medium |
| RCB Video Player | `RCB-video-player` (`3f95c3d`) | **None**; only a media permission prompt | **High** |
| RCB Vault | `rcb-vault` (`c297877`, React Native + Kotlin) | One welcome screen ("Create vault") | **High** |
| RCB SMS | `RCB-Messages` (`11a412d`) | One **blocking** "set as default SMS app" screen | Medium |
| RCB Phone | `RCB-Phone` (`8a894ca`) | Dismissible default-phone banner, then full-screen-intent and notification checks | Medium |
| RCB Calculator | `rcb Cal` (`f3372bc`) | No first-run tutorial; a **Guide & Help** tile on the home screen | Low |

### RCB Video Player – high priority
- **Today:** the library asks for video access ("RCB Video Player reads the videos on this device…"); nothing explains the player.
- **Easy to miss:** player gestures, Picture-in-Picture, speed, aspect/zoom, resume, history and favourites, subtitle styling and sync (Sinhala, Tamil, English), the folder picker when access is denied.
- **Suggested tutorial (5 pages):** Welcome (offline, no account) → Your videos (library, folders, history, favourites; explains video access, which is requested after the tutorial) → Player gestures (with small animated demos) → Subtitles and audio (tracks, styling, sync) → Make it yours (theme) + Get started.
- **Note:** commit `3f95c3d` adds online subtitle download for debug and personal builds. The tutorial must not mention it for builds without internet access.

### RCB Vault – high priority
- **Today:** a single welcome screen that leads to creating the master password. It already warns that the password can't be recovered.
- **Easy to miss:** fingerprint unlock, autofill (needs a system setting), browser password import (CSV), encrypted backup, the password generator, screenshot protection.
- **Suggested tutorial:** keep the welcome screen as page 1. After creating the vault: Unlock (fingerprint) → Autofill (explain, then open Android's autofill setting) → Import from your browser → Backups (encrypted backup file; **no recovery without the master password**) → Get started. Show it after the vault exists, never before the master password. Do not change any cryptography, storage or session code for this.

### RCB SMS – medium priority
- **Today:** the app is blocked until it is the default SMS app. That is required for an SMS app, but there is no explanation beyond one screen and no tutorial. The README still says "Phase 0 … no application code yet", which is outdated.
- **Suggested tutorial:** Welcome (on-device, no internet) → **Set as default SMS app** (system role dialog; explain why) → Notifications (explain, then request on Android 13+) → Conversations features → Done. Keep the role requirement, but make page 1 explain it before the role dialog.

### RCB Phone – medium priority
- **Today:** a dismissible banner asks to set RCB Phone as the default phone app, then checks full-screen intent access (Android 14+) and notifications. The explanation acknowledgement is stored.
- **Easy to miss:** swipe between tabs, Classic/glass appearance, call blocking and history, voicemail, Messages opening RCB SMS.
- **Suggested tutorial:** Welcome → **Default phone app** (role dialog) → Calls on the lock screen (full-screen intent, Android 14+) → Notifications → Tabs and appearance → Done, reusing the existing checks.

### RCB Wake – low to medium
- **Today:** a 5-page tutorial (Welcome, Alarms your way, Timers and focus, Clocks and widget, Make it yours), translated, with replay.
- **Improvement:** add a page or contextual hint for reliability: exact alarm permission and battery optimisation on Samsung, Xiaomi and similar devices (the README asks for testers there), and the wake-up math challenge.

### RCB Calendar – low
- **Today:** privacy agreement plus a 5-page tutorial (Welcome, Your calendar, Sri Lankan holidays, Events and reminders, Make it yours) with replay.
- **Improvement:** explain the optional "holidays from your phone calendar" permission at the moment it is turned on (contextual help), not only in the tutorial.

### RCB Calculator – low
- **Today:** no first-run tutorial, but a Guide & Help screen with tutorials and examples.
- **Improvement:** a one-time hint pointing to Guide & Help is enough; a full tutorial is not needed.

### RCB Music – reference
- Keep it as the reference. One gap: the last page explains audio access, but the permission screen that follows could link back to the tutorial.

## 4. Recommended order

1. RCB Video Player – new tutorial (no onboarding, many hidden features).
2. RCB Vault – post-setup tutorial (security features users must find themselves).
3. RCB SMS – explain the default-app requirement before the role dialog; update the README.
4. RCB Phone – turn the banner and checks into a short tutorial.
5. RCB Wake – reliability page.
6. RCB Calendar, RCB Calculator, RCB Music – small contextual improvements.

Each app should be done in its own repository and branch, one at a time, with its own tests (first launch, skip, completion, relaunch, replay, themes, screen sizes, accessibility).

## 5. RCBBoard test results

Tested on an Android 15 emulator (Pixel, 1080×2400) with the debug build, using `adb`, UI Automator dumps and screenshots.

| Test | Result |
| --- | --- |
| Fresh install → first launch shows page 1 | ✅ |
| Navigation: Next, Back, swiping, dots, counter | ✅ |
| Page 2 → Android keyboard settings → turn on (with Android's warning) → back: ✓ and moves on to page 3 | ✅ |
| Page 3 → Android keyboard picker → choose RCBBoard: ✓ and moves on to page 4 | ✅ |
| "Try it here": English typing, long-press space → language list, Singlish `mama` → මම | ✅ |
| "Show a language key" switch changes the real setting | ✅ (`show_language_switch_key` set to true, then false) |
| Theme chips change the keyboard theme immediately (Dark verified on the keyboard; Follow system restores the defaults) | ✅ |
| Skip → main menu with "Finish setting up RCBBoard" card; completion stored | ✅ |
| Relaunch after Skip / after Get started: no tutorial | ✅ |
| Setup card "Set up" → tutorial opens at the missing step (page 2) | ✅ |
| Back key on page 1 of the first start: leaves, not completed, shown again next time | ✅ |
| Replay from General → Tutorial: Close / Done | ✅ |
| RCBBoard already on and selected before first launch: pages 2 and 3 show ✓ | ✅ |
| Update install (`adb install -r`) does not show the tutorial | ✅ (by design) |
| Dark theme, landscape (picture beside text), small screen 720×1280 at 320 dpi | ✅ screenshots |
| Opening the app after a force stop when RCBBoard is not the current keyboard | ❌ before the fix (app killed) → ✅ after |
| Unit tests (CI setup, JDK 25) | ✅ 423 tests, 0 failures (9 new in `OnboardingTest`) |
| Accessibility | Headings, page counter description, live status regions, 48 dp+ buttons. TalkBack itself was not run. |

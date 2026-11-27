# Fret keyboard style: visual validation

Fret (internal style id `RCBClassic`, resources `rcb_classic_*`) is an RCBBoard keyboard style
modelled on the BlackBerry 10 virtual keyboard, using the BlackBerry Z30 as the primary reference.
It is an original re-creation: no BlackBerry source code, fonts, logos or graphics are used.
The key tiles are drawn by `FretKeyDrawable`, the frets by `FretBackgroundDrawable`, and the icons
are new vector drawings.

All screenshots in this folder come from the RCBBoard debug build on an Android emulator:
Pixel 9a, 1080 × 2424 px at 420 dpi, Android 17 (API 37.1, 16 KB pages), English (UK).
Other screen sizes were set with `adb shell wm size` and `wm density`.

## Reference

The primary reference is a screenshot of the BlackBerry Z30 keyboard (BlackBerry 10.2). The Z30
screen is 720 × 1280 px at 295 ppi, and BlackBerry's design unit there is 8 px. The reference image
is 717 px wide and shows the whole keyboard, from the first fret to below the bottom row. It is not
stored in this repository, because it is copyrighted.

BlackBerry never published the keyboard's dimensions, so every value below was measured on that
image with a pixel script:
- frets are found in the row brightness profile
- key tiles are found from the black gaps between them
- the letter cap height comes from the white glyph pixels
- colours are averaged pixel samples

Each value is normalised to the keyboard width W, or to the row pitch, and Fret scales it to any
screen.

## Z30 anatomy (717 px wide reference)

| Element | Pixels | Normalised |
|---|---|---|
| Row pitch (fret to fret) | 117 | 0.1632 W |
| Keyboard, 4 rows | 468 | 0.6527 W |
| Fret | 12 | 0.0167 W, 12/117 of the pitch |
| Black gap above a tile | 6 | 6/117 of the pitch |
| Key tile height | 93 | 0.1297 W, 93/117 of the pitch |
| Black gap below a tile | 6 | 6/117 of the pitch |
| Gap between tiles | 6–7 | about 0.0087 W, 3 px each side |
| Tile corner radius | about 4 | 4/117 of the pitch |
| Letter cap height | 30 | 0.0418 W, 0.256 of the pitch |
| Letter vertical centre | 3 px above the tile centre | 0.526 of the pitch from the row top |
| Letter stem (bold) | 6 | 9 px at 1080 px width |
| Shift icon | 27 × 30 | |
| Delete icon | 34 × 30 | |
| Enter icon | 30 × 26 | |
| Space bracket | 101 × 16, 5 px stroke | centred in the space bar |
| Microphone hint on the period key, grey `#898983` | 25 × 31 | 14 px right of the key centre, top 23 px below the row top |
| Keyboard hint on the space bar, grey | 32 × 24 | right edge 12.5 px inside the key, top 28 px below the row top |

## Measured comparison: Z30 and Fret

The same script measured the Z30 reference and the Fret screenshots. Key widths are fractions of the
keyboard width.

| Measurement | Z30 reference | Fret, 1080 × 2424 @ 420 dpi | Fret, 1440 × 3120 @ 560 dpi | Fret, 720 × 1280 @ 320 dpi |
|---|---|---|---|---|
| Row pitch | 0.1632 W | 0.1633 W | 0.1632 W | 0.1630 W (117.3 px) |
| 4-row keyboard height | 0.6527 W | 0.6531 W | 0.6528 W | 0.652 W |
| Fret thickness | 0.0167 W | 0.0167 W | 0.0167 W | 0.0167 W |
| Tile height (detected) | 0.1548 W | 0.1519 W | 0.1521 W | |
| Gap between tiles | 0.0084 W | 0.0074 W | 0.0083 W | 0.0083 W |
| Letter cap height | 0.0418 W | 0.0417 W | 0.0417 W | 0.0417 W |
| Letter centre (of the pitch) | 0.526 | 0.522 | 0.526 | |
| Q–P | 10 × 0.100 | 10 × 0.100 | 10 × 0.100 | 10 × 0.100 |
| A–L, edge to edge | 9 × 0.111 | 9 × 0.111 | 9 × 0.111 | 9 × 0.111 |
| Shift / Z–M / delete | 0.125 / 0.1074 / 0.123 | 0.124 / 0.1074 / 0.124 | 0.124 / 0.1074 / 0.124 | 0.123 / 0.107 / 0.125 |
| ?123 / , / space / . / enter | 0.179 / 0.107 / 0.428 / 0.107 / 0.178 | 0.178 / 0.107 / 0.428 / 0.107 / 0.179 | 0.178 / 0.108 / 0.428 / 0.107 / 0.179 | 0.179 / 0.107 / 0.428 / 0.107 / 0.180 |

At 720 px width, the Z30's own resolution, Fret's row pitch is 117.3 px, against 117 px on the Z30.

How the proportions are applied:
- **Default height:** 4 rows = 0.653 × screen width (`rcb_classic_keyboard_height_to_width`), plus a
  black band of 6/117 of a row above the top fret. An extra number row keeps the same row height. It is capped at 46 % of the screen height. The
  "Keyboard height" setting still scales it. Landscape and tablets use RCBBoard's normal height.
- **Tiles, gaps, frets, letter position and icon sizes** are fractions of the row pitch, so they
  follow the height setting and every screen size.
- **Key widths** come from the layout parser (`KeyboardParser.applyRcbClassicKeyWidths`).

## Long press, as on BlackBerry 10

- **Holding the period key starts voice input**, and the key shows the grey microphone. This only
  applies when voice input is available, so not in password or email fields and not when no voice
  service is installed. Otherwise the key keeps its normal punctuation popup and shows no microphone.
- **Holding the space bar hides the keyboard**, and the key shows the grey keyboard with a down arrow.
  In the Fret style this replaces the language picker on a long press of space. Languages can still
  be switched by swiping on the space bar, with the language key, or with the system keyboard switcher.

Both were checked on the emulator: holding space hid the keyboard, and holding the period key opened
voice typing.

## Word predictions, as on BlackBerry 10

BlackBerry 10 shows predicted words on the keyboard itself, sitting on the frets, and a word is
accepted by flicking it up into the text. Sources:
[GSMArena BlackBerry Z30 review](https://www.gsmarena.com/blackberry_z30-review-1009p5.php) (suggestions
appear on the frets over the next letter, swipe up to push the word into the text field),
[Laptop Mag](https://www.laptopmag.com/articles/how-to-move-predictive-text-on-blackberry-10s-keyboard)
(In-Letter, In-Column and Off) and
[CrackBerry](https://crackberry.com/10-weeks-blackberry-10-keyboard) (after "Blac", "BlackBerry"
appears over K).

The setting is **Appearance › Word predictions on the keyboard**. It is shown while Fret is selected,
and the default is In-Letter.

| Mode | What it shows | How to accept a word |
|---|---|---|
| In-Letter | up to 3 words, each above the key of its next letter. Before the first letter of a word, next-word predictions sit above their first letter | flick up from that key |
| In-Column | up to 3 words in three columns above the keyboard, the best word in the middle | flick up from any key below the word |
| Off | no words on the keyboard | – |

- **Source of the words.** They are the suggestions RCBBoard's prediction engine already makes
  (`FretPredictions`). There is no new engine, no new storage, and nothing leaves the device.
  Next-word predictions (before the first letter) come from the personal history dictionary. The
  bundled dictionaries have no word-pair data, so a fresh install shows next words only after a
  phrase has been typed a few times. This is the same for every style.
- **No top bar.** While predictions are on, Fret shows no suggestion strip or toolbar above the
  keys, like BlackBerry 10. Toolbar functions are reached by holding the comma key: clipboard, emoji,
  language, one-handed mode, floating keyboard and settings. Inline autofill suggestions are left to
  the app, as with RCBBoard's hidden-toolbar mode. Suggestions, autocorrection and learning stay on.
  With Off, the normal strip is back.
- **Singlish and other layouts in another script than their language** (Latin keys for Sinhala
  words) keep the normal suggestion strip with their suggestions, because those words cannot sit
  above Latin letter keys. Layouts in the language's own script, such as English QWERTY or Sinhala
  Wijesekara, get the words on the keys.
- **Emoji suggestions** (In-Letter) appear above the comma key and are flicked up from it. Holding
  comma still opens its popup.
- **Accepting a word** goes through the same path as tapping a word in the suggestion strip, so
  autocorrection, learning (none in incognito mode or password fields), automatic spaces and
  punctuation behave as before.
- **A flick** is a stroke from a key that shows a word, straight up (at least twice as far up as
  sideways) by more than 0.4 of the row height. The word is accepted when the finger is lifted. While
  a stroke goes straight up, glide typing records the points but does not start. If the stroke bends,
  it is no flick: glide typing continues with the whole path and normal key handling resumes. Space,
  delete (which have their own swipes) and modifier keys never start a flick.
- **Animation.** The flicked word lifts off its fret, glides up out of the keyboard towards the text
  field, grows slightly and fades away (360 to 420 ms, cubic ease-out). It is drawn on the preview
  layer above the keyboard, so it is not cut off at the keyboard's top edge.
- **Appearance.** Measured on the Z30 screenshot, a word has a 20 px cap height (0.17 of the row
  pitch) in regular weight. It is centred on the fret 4 px above the fret's middle, on a dark backing
  that dims the fret. The letters already typed are white and the rest is light grey. Words in one row
  never overlap: they are pushed apart, kept inside the keyboard edges and condensed horizontally (to
  70 % at most) only when a row is too full. Key labels stay capital letters, and the words keep their
  own casing ("Her", "Head" after typing "He").

## Colours: Z30 compared with Fret

Average pixel colours at the same places on the Z30 reference and on Fret (Pixel 9a), measured with
the same script. All differences are within 2 brightness levels out of 255, except the background
gap, where the reference has JPEG noise around pure black.

| Element | Z30 | Fret |
|---|---|---|
| Letter tile, top edge | `#3F3F3F` | `#414142` |
| Letter tile, upper / middle / lower | `#323232` / `#2E2E2E` / `#2B2B2B` | `#333333` / `#2E2E2E` / `#2C2C2C` |
| Shift / delete / ?123 / enter tile | `#131313` / `#121212` / `#131313` / `#131313` | `#131313` / `#141414` / `#131313` / `#131313` |
| Space tile | `#2F2F2F` | `#2E2E2E` |
| Fret, rows 0 / 1 / 3 / 6 / 9 / 11 of 12 | `#FEFFFF` `#EDEDEB` `#8F8C88` `#8D8882` `#827F7A` `#939390` | `#F8F9F8` `#EDEDEB` `#918E8A` `#8C8781` `#84817C` `#979692` |
| Gap below a tile | `#060605` | `#000000` |

The top fret has a black band above it (6/117 of the row pitch, like the gaps between rows), so it
stands out from the app above like the other frets.

## Verification

Emulator Pixel 9a (1080 × 2424, 420 dpi), Android 17 (API 37.1, 16 KB pages), debug build, English
(UK) unless noted.

| Check | Result |
|---|---|
| In-Letter after "hel" | held above D, help above P, hello above L |
| Flick up from L | "hello" inserted, the words cleared, fly-up animation shown |
| Top-row word "her" flicked from R | leaves the keyboard, rises over the app and fades, no clipping |
| Capitals after "He" | Her, Head, Held, key labels stay capital |
| In-Column | held, he'll, help above the keyboard; a flick from Z inserts held |
| Off | normal strip with held, he'll, help; no words on the keys |
| Neighbouring words ("st": still above I, story above O) | pushed apart, no overlap |
| Emoji after "thank" | 🙏 above comma, a flick inserts it |
| Singlish "mama" | normal strip with Sinhala suggestions, no words on the keys |
| Next-word after learned "thank you" | "thank " shows you above Y |
| Autocorrection, spacing, punctuation | "teh" + space → "the ", a flicked "hello" gets a space, "." after it gives "hello." |
| Password field (Wi-Fi) | no words, no microphone hint, holding "." opens punctuation |
| Incognito | flicked words use the same path as strip taps, which does not learn in incognito |
| Rapid typing | 22 letters in 0.43 s, all typed, none doubled |
| Multitouch rollover (two fingers, both release orders) | "as", "df", "kl" typed in press order |
| Keyboard height 80 % / 120 %, landscape | words, frets and tiles scale with the rows |
| Material, Holo, Rounded | keys pixel-identical to the build before Fret; Material strip unchanged |
| Unit tests | 444 passed, 0 failed (JDK 21+, see CI) |
| Minified release build | builds; no INTERNET permission; 5.1.1 (50101); 16 KB alignment verified |

Rendering and input cost, measured on the emulator while typing and flicking:

| Measurement | Median | 95th percentile | Max |
|---|---|---|---|
| Mapping suggestions to keys, per update | 0.09 ms | 0.22 ms | 0.35 ms |
| Drawing the predicted words, per frame | 0.22 ms | 0.67 ms | 1.03 ms |
| Whole keyboard frame | 0.96 ms | 1.68 ms | 1.74 ms |

Frame timing (`dumpsys gfxinfo`): Fret 116 frames, 0 % janky (legacy), 17 / 19 / 20 ms at the
50th / 90th / 99th percentile; Material with the same typing 41 frames, 2.4 % janky, 17 / 19 / 32 ms.

## Screenshots

| File | Content |
|---|---|
| `before_material_pixel9a.png` | Before: the default Material style, same field and device |
| `fret_portrait_pixel9a.png` | Fret, letters, no top bar |
| `fret_shifted_pixel9a.png` | Shift active (filled arrow) |
| `fret_symbols_pixel9a.png` | Symbols layout, edge to edge |
| `fret_pressed_preview_pixel9a.png` | Pressed key and the key preview above it |
| `fret_longpress_pixel9a.png` | Long-press popup panel |
| `fret_predictions_in_letter_pixel9a.png` | In-Letter after "hel" |
| `fret_predictions_capitalised_pixel9a.png` | Words keep their capital letter after "He" |
| `fret_predictions_in_column_pixel9a.png` | In-Column after "hel" |
| `fret_predictions_off_strip_pixel9a.png` | Predictions off: the normal strip is back |
| `fret_predictions_no_overlap_pixel9a.png` | Words on neighbouring keys pushed apart |
| `fret_predictions_emoji_above_comma_pixel9a.png` | Emoji suggestion above the comma key |
| `fret_flick_flyup_sequence.png` | Every second frame (60 fps) of "her" flicked up from R |
| `fret_singlish_suggestion_strip_pixel9a.png` | Singlish: Sinhala suggestions in the normal strip |
| `fret_password_field_pixel9a.png` | Password field: no words, no microphone hint |
| `fret_predictions_height_80_pixel9a.png`, `fret_predictions_height_120_pixel9a.png` | Keyboard height 80 % and 120 % |
| `fret_predictions_landscape_pixel9a.png`, `fret_landscape_pixel9a.png` | Landscape |
| `fret_portrait_720x1280_320dpi.png` | The Z30's resolution, 720 × 1280 |
| `fret_portrait_1440x3120_560dpi.png` | Large screen, 1440 × 3120 |
| `fret_style_picker.png` | Style picker in the settings, with keyboard thumbnails |
| `comparison_before_after.png` | Material before, Fret after |
| `comparison_reference_grid.png` | Fret scaled to 717 px. Red: the Z30 tile rectangles from the measurements. Blue: the bottom of the Z30 fret |

## Remaining differences and limitations

- **Font.** BlackBerry used its own Slate Pro typeface, which is proprietary. Fret uses the system
  sans-serif in bold, with the same cap height and stem weight, so the letter shapes differ slightly.
- **Toolbar keys not in the comma popup** (for example undo, redo, cursor keys and incognito) are not
  reachable while the bar is hidden. Incognito can still be set in the settings.
- **Clipboard suggestion chip.** With no strip, the chip that offers recently copied text does not
  appear. The clipboard itself is reached by holding comma.
- **Glide typing** is not part of this build, so the flick and glide interplay was checked in code
  and unit tests, not on the device. A glide that is a straight vertical stroke from a key with a word
  is read as a flick.
- **The animation ends above the keyboard.** The word cannot be drawn into the app's text field, so
  it fades about a row above the keyboard.
- **One setting for both orientations.** BlackBerry 10 had separate prediction settings for portrait
  and landscape.
- **Pressed state and key preview.** No clear reference was available. The pressed tile is lighter
  grey and the preview balloon is an original design.
- **Languages.** Fret is tuned for English QWERTY. Other layouts use the same colours and geometry
  but keep their own font settings.
- **Landscape and tablets** use RCBBoard's normal height, because the BlackBerry 10 phones the
  style is modelled on were portrait phones.

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
- **Default height:** 4 rows = 0.653 × screen width (`rcb_classic_keyboard_height_to_width`). An
  extra number row keeps the same row height. It is capped at 46 % of the screen height. The
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

## Colours sampled from the Z30

| Element | Colour |
|---|---|
| Background and gaps | `#000000` |
| Letter, space, comma and period tiles | `#505052` top edge → `#404042` → `#353535` → `#2F2F2F` → `#2B2B2B`, rim `#404040` |
| Shift, delete, ?123 and enter tiles | `#2E2E2E` top edge → `#1A1A1A` → `#181818` → `#121212` |
| Fret, top to bottom | `#FEFFFF`, `#EDEDEB` highlight → `#989894` → `#8D8882` → `#84817C` → `#999692` lip, uniform across the width |
| Letters and icons | `#FFFFFF` |

## Screenshots

| File | Content |
|---|---|
| `before_material_pixel9a.png` | Before: the default Material style, same field and device |
| `fret_portrait_pixel9a.png` | Fret, letters |
| `fret_shifted_pixel9a.png` | Shift active (filled arrow) |
| `fret_symbols_pixel9a.png` | Symbols layout, edge to edge |
| `fret_pressed_preview_pixel9a.png` | Pressed key and the key preview above it |
| `fret_longpress_pixel9a.png` | Long-press popup panel |
| `fret_typing_suggestions_pixel9a.png` | Typing with suggestions in the strip |
| `fret_landscape_pixel9a.png` | Landscape |
| `fret_portrait_720x1280_320dpi.png` | The Z30's resolution, 720 × 1280 |
| `fret_portrait_1440x3120_560dpi.png` | Large screen, 1440 × 3120 |
| `fret_style_picker.png` | Style picker in the settings, with keyboard thumbnails |
| `comparison_before_after.png` | Material before, Fret after |
| `comparison_reference_grid.png` | Fret scaled to 717 px. Red: the Z30 tile rectangles from the measurements. Blue: the bottom of the Z30 fret |

## Remaining differences to the original

- **Font.** BlackBerry used its own Slate Pro typeface, which is proprietary. Fret uses the system
  sans-serif in bold, with the same cap height and stem weight, so the letter shapes differ slightly.
- **Flick typing.** BlackBerry 10 showed suggestions floating above the keys, to be flicked up.
  That is outside the scope of a visual theme, so suggestions stay in the black strip above the
  keyboard. BlackBerry 10 had no such strip.
- **Pressed state and key preview.** No clear reference was available. The pressed tile is lighter
  grey and the preview balloon is an original design.
- **Languages.** Fret is tuned for the English QWERTY layout. Other layouts, such as Sinhala, use the
  same colours and geometry but keep their own font settings.
- **Landscape and tablets** use RCBBoard's normal height, because the BlackBerry 10 phones the
  style is modelled on were portrait phones.

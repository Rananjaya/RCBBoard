# App icon

RCBBoard's icon is a keyboard key showing the Sinhala letter **අ** with the Latin letter **A** in the corner, as on a key with two labels. This shows that the keyboard is for Sinhala and English. The key is white on RCBBoard's navy background (`#0F1835`).

## Files

| File | Used for |
| --- | --- |
| `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive icon (Android 8+) |
| `res/drawable/ic_launcher_rcb_background.xml` | Adaptive icon background |
| `res/drawable/ic_launcher_rcb_foreground.xml` | Adaptive icon foreground: the key and the letters |
| `res/drawable/ic_launcher_rcb_monochrome.xml` | Themed icons (Android 13+): the key with the letters cut out |
| `res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | Android 5–7.1 (no adaptive icons) |
| `res/drawable/ic_launcher_rcb_full.xml`, `ic_launcher_rcb_full_round.xml` | The whole icon in one drawable: for Android 5–7.1 and in the app (main menu title) |

All of them are vector drawables, so there are no PNG files per screen density. The previous raster icon (`mipmap-*dpi/ic_launcher*.png`, `drawable-nodpi/ic_brand_mark.png`) was removed. The screenshot `docs/images/rcbboard-icon.png` in the README still shows the old icon.

## How it is made

Vector drawables can't contain text, so the letters are outlines from **Noto Sans Sinhala** and **Noto Sans** (bold), © The Noto Project Authors, licensed under the SIL Open Font License 1.1 (see `licenses/THIRD_PARTY_NOTICES.md`). No font file is included in the app.

`tools/app-icon/make_icon.py` generates all the files above. To change colours, sizes or letters, edit the constants at the top of the script and run it from the repository root:

```sh
python3 -m venv /tmp/icon-venv
/tmp/icon-venv/bin/pip install fonttools skia-pathops
curl -LO 'https://github.com/google/fonts/raw/main/ofl/notosanssinhala/NotoSansSinhala%5Bwdth,wght%5D.ttf'
curl -LO 'https://github.com/google/fonts/raw/main/ofl/notosans/NotoSans%5Bwdth,wght%5D.ttf'
/tmp/icon-venv/bin/python tools/app-icon/make_icon.py 'NotoSansSinhala[wdth,wght].ttf' 'NotoSans[wdth,wght].ttf'
```

Keep everything inside the 66 dp safe zone of the 108 dp adaptive icon canvas (a circle with radius 33 around the centre), so no launcher mask cuts it off.

## Checked

On an Android 15 emulator: the system App info page, the launcher and the main menu title show the icon, with the key inside the circular mask. On that emulator, Pixel Launcher draws a light ring around this debug build's icon. The old icon got the same ring, so it is not caused by the new icon. Themed (monochrome) icons and Android 5–7.1 were not checked on a device.

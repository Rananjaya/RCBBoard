# M6 – English vocabulary and correction ranking

Branch `feature/english-prediction-engine` on top of M5 (`9c4af6f8`). **Not committed**, waiting for approval.
English only; Sinhala data and behaviour unchanged.

## Summary

- About **2,700 modern words** added to each English dictionary from SCOWL (GitHub, API, COVID, Kotlin, selfie,
  hashtag, blockchain…), American spellings only in en-US, British only in en-GB, capitals as SCOWL writes them.
- **json → Jason fixed** by a general rule, no exception list: a word typed all in lower case is not autocorrected
  to a capitalised word that needs other letters (it stays a suggestion).
- No regression against M5: same next-word accuracy, same autocorrect accuracy, still 0 wrong autocorrects; the
  safety list is now fully kept, and more modern words are offered while typing.

## Sources

| Source | Use | Licence |
| --- | --- | --- |
| SCOWL 2020.12.07 (wordlist.aspell.net), lists up to size 50 | New words | Permissive; notice in `THIRD_PARTY_NOTICES.md` (shown in the app) and `third_party/scowl/Copyright` |
| LDNOOBW English list | Marks new offensive words possibly offensive (blocked by default); not shipped | CC BY 4.0 |
| wordfreq | **Rejected**: partly derived from OpenSubtitles and Twitter, excluded by `dataset-licensing.md`; its author also asks not to convert it | – |

## How words are chosen (`tools/english-dictionaries/add_vocabulary.py`)

- SCOWL common lists for both locales, plus American lists (en-US) or British -ise lists (en-GB); sizes ≤ 50.
- Letters only; lower case words need 4+ letters; abbreviations only in capitals with 3+ letters (API, COVID).
- Frequency: median frequency of the words of the same SCOWL list and size the dictionary already has, minus 10.
- **Typo-trap filter**: a new word is skipped when a dictionary word one typo away is at least 40 points more
  frequent. The first build without it added "io", "tm", "ot"… and autocorrect accuracy fell from 78.2 % to
  75.4 %; the filter skipped about 505 such words and restored it.
- All existing words, frequencies, flags, shortcuts and bigrams are kept. UK +2,718 words, US +2,644; each
  dictionary grows by about 17 KB.

## Benchmark (emulator, same harness and data as M5)

| | UK M5 | UK M6 | US M5 | US M6 |
| --- | --- | --- | --- | --- |
| Next word top-1 / top-3 / top-5 | 21.8 / 37.0 / 44.5 % | 21.8 / 37.0 / 44.5 % | 22.7 / 37.5 / 43.8 % | 22.7 / 37.5 / 43.8 % |
| Independent books top-3 | 25.2 % | 25.2 % | 23.9 % | 23.9 % |
| Keystroke savings | 42.3 % | 42.3 % | 41.1 % | 41.1 % |
| Typos autocorrected | 78.2 % | 78.2 % | 79.5 % | 79.5 % |
| Correct words wrongly autocorrected | 0 % | 0 % | 0 % | 0 % |
| Safety list (34 words) changed | json → Jason | **none** | json → Jason | **none** |
| Modern words (27) kept when typed | 26 | **27** | 26 | **27** |
| Modern words offered after 4 letters (top 3) | 10 | **13** | 11 | **14** |
| Next-word lookup P95 | 0.49 ms | 0.44 ms | 0.43 ms | 0.46 ms |
| Typing lookup P95 | 25.6 ms | 26.2 ms | 26.2 ms | 27.3 ms |
| Memory (PSS) | 118.2 MB | 118.6 MB | 114.1 MB | 113.9 MB |

Typing latency and memory differences are within run-to-run variation. Minified release APK 27.07 MB (M5 27.04 MB).

## Validation

| Check | Result |
| --- | --- |
| Unit tests | 454 passed, 0 failed, 1 skipped (build tool) |
| Script tests | passed |
| Upgrade M5 → M6 (minified, same key) | installed, no crash, Fret style kept |
| Fret | "json" typed stays "json"; "have a nice" + flick on D gives "day" |
| Sinhala (Singlish) | suggestion strip pixel-identical to M2 |
| R8 / 16 KB / INTERNET | 689/719 classes renamed, JNI kept / aligned / none |
| Privacy | no change to learning, storage or input handling |

No physical phone was used.

## Remaining limitations

- Many modern words are still not in the top 3 after four letters (selfie, hashtag, password…): new words get
  conservative frequencies so they never outrank established ones; raising them needs real usage frequencies
  from a source that meets the licensing rules.
- SCOWL stops at 2020; newer words still need learning from the user.
- The lower-case rule also stops "londn" → "London" from being automatic (still suggested).

## Recommendation

**GO** to commit M6. Next candidates: rank completions with the phrase context while typing (largest remaining
keystroke gain), and a measured comparison with FUTO Keyboard on the same test data.

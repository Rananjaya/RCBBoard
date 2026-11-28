# M4 – Next-word prediction from three previous words (English)

Built after M7 on the same branch (`feature/english-prediction-engine`). **Not committed**, waiting for approval.

## Summary

- The phrase files now also hold 4-grams: after three words the keyboard looks up the next word, then falls back
  to two words (trigrams), then to the dictionary's word pairs. Same score scale, so personal history keeps its
  priority. Sinhala gets no phrase file (unchanged).
- Top-3 next-word accuracy: **37.0 % → 38.6 % (UK)**, **37.5 % → 38.9 % (US)**; "have a nice" now predicts
  **day** first. Autocorrection unchanged, still 0 wrong autocorrects.
- APK +1.3 MB (27.07 → 28.38 MB); next-word lookup P95 0.47 ms.

## Design

- `build_english_trigrams.py`: counts 4-grams from the same Tatoeba build sentences (CC BY 2.0 FR, test
  sentences never used), absolute discounting, same word rules (no names, offensive words or single letters,
  British spellings in en-GB).
- File format version 2: one vocabulary, a two-word section and a three-word section (keys
  `id1 << 42 | id2 << 21 | id3`), binary search in each. `PhraseDictionary` reads it; three-word results are added
  only when the three previous words are in the same sentence.
- Tuning on the development set (buckets 10–14, cases with three words of context, UK):

| 4-gram setting | Top-3 | File |
| --- | --- | --- |
| none (M3/M7) | 39.8 % | 2.5 MB |
| seen ≥ 5 times, 4 per context, bonus 20 (**chosen**) | 42.5 % | 3.8 MB |
| seen ≥ 3 times, 4–8 per context, bonus 20 | 43.3 % | 5.4 MB |

Bonus 20 was best (10 gives less, 30 lowers top-1). Final files: 4.0 MB (UK) and 4.1 MB (US), 80,209 / 83,426
three-word contexts.

## Benchmark (emulator, same harness and data; M7 = M6 for fresh-install prediction)

| | UK M7 | UK M4 | US M7 | US M4 |
| --- | --- | --- | --- | --- |
| Next word top-1 / top-3 / top-5 | 21.8 / 37.0 / 44.5 % | 22.7 / **38.6** / 45.8 % | 22.7 / 37.5 / 43.8 % | 22.7 / **38.9** / 45.1 % |
| Mean reciprocal rank | 0.316 | 0.326 | 0.318 | 0.323 |
| Independent books top-3 | 25.2 % | 25.2 % | 23.9 % | 24.0 % |
| Keystroke savings | 42.3 % | 42.9 % | 41.1 % | 41.9 % |
| Typos autocorrected / wrong autocorrects | 78.2 % / 0 % | 78.2 % / 0 % | 79.5 % / 0 % | 79.5 % / 0 % |
| Safety list changed | none | none | none | none |
| Next-word P50 / P95 / P99 | 0.28 / 0.44 / 0.55 ms | 0.28 / 0.47 / 0.58 ms | 0.28 / 0.46 / 0.60 ms | 0.28 / 0.48 / 0.61 ms |
| Typing P95 | 26.2 ms | 26.3 ms | 27.3 ms | 27.7 ms |
| Memory (PSS) | 118.6 MB | 115.1 MB | 113.9 MB | 120.3 MB (run-to-run variation; the files add ≈ 1.5 MB) |

Examples (UK, first five): have a nice → **day**, time, guy, trip, house; I will be → able, in, back, very, here;
please let me → **know**, do, go, finish, tell; looking forward to → **seeing**.

The gain is smaller than the offline estimate (+2.8 points) because many test contexts have fewer than three words
or unseen three-word contexts; the independent books gain nothing (old literary English).

## Owner's Fret requests made during M7/M4 (UI)

| Change | Where |
| --- | --- |
| No black box behind In-Letter / In-Column words; thin dark outline instead | `FretPredictionRenderer.kt` |
| Frets: original 12/117 thickness, metal profile with dark top and bottom edges, darker towards the left and right ends | `FretBackgroundDrawable.kt` |
| Easier flicks: direction judged only after a small move (wobble ignored), up to 45° from vertical, 0.3 key height (was 0.4 and ≈ 27°), a started flick may curve | `PointerTracker.java` |

Flick checks on the emulator (Fret, "have a nice", flick on D): straight, 35° angled, short (0.32 key) and wobbly
start all insert "day"; a plain tap on D still types "d". Before, the angled and short flicks were outside the
limits.

## Validation

| Check | Result |
| --- | --- |
| Unit tests | 455 passed, 0 failed, 1 skipped (incl. a new 4-gram test) |
| Minified release | 28.38 MB; R8 689/719 renamed, JNI kept; 16 KB aligned; no INTERNET permission |
| Upgrade M7 → M4 build (same key) | installed, no crash |
| Fret In-Letter / In-Column / Off | predictions shown (M7 checks; layout code unchanged in M4) |
| Sinhala | no phrase file for Sinhala (unit test); data unchanged; emulator check identical in M7 |
| Personal learning | unchanged; phrase scores stay on the same scale, so learned phrases keep priority |

Emulator only, no physical phone.

## Recommendation

**GO**, with the size trade-off stated: +1.3 MB for about +1.5 points top-3. Commit order proposal: M7 (learning),
Fret UI (box, frets, flicks), M4 (4-grams).

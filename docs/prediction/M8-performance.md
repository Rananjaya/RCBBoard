# M8 – Performance

Branch `feature/english-prediction-engine` on top of M4 (`7a4287ff`). **Not committed**, waiting for approval.
Battery measurement deferred by the owner.

## Summary

| Emulator, release-like data | UK M4 | UK M8 | US M4 | US M8 |
| --- | --- | --- | --- | --- |
| Typing lookup P50 / P95 / P99 | 17.9 / 26.3 / 38.1 ms | **9.4 / 18.3 / 29.2 ms** | 18.1 / 27.7 / 38.4 ms | **9.4 / 19.0 / 29.8 ms** |
| Next-word lookup P95 | 0.47 ms | 0.45 ms | 0.48 ms | 0.46 ms |
| Next word top-1 / 3 / 5 | 22.7 / 38.6 / 45.8 % | identical | 22.7 / 38.9 / 45.1 % | identical |
| Autocorrect / wrong autocorrects | 78.2 % / 0 | identical | 79.5 % / 0 | identical |
| Keystroke savings, modern words, safety list | 42.9 %, 13, none | identical | 41.9 %, 14, none | identical |

Typing P95 is now below the 20 ms target on this emulator (before: 26–28 ms). Accuracy, learning, Sinhala, Fret and
gestures are unchanged.

## Where the time went

A profiling test (`profile` in the benchmark) split a typing lookup into the dictionary search (native) and the rest.
The native search was almost all of it, and the emoji dictionary search, done after the main dictionary search, was
about 40 %: P50/P95 13.7/20.0 ms with it, 8.2/12.0 ms without it.

## Changes

| File | Change |
| --- | --- |
| `DictionaryCollection.java` | The other native dictionaries of a collection (the emoji dictionary) are searched on one background thread while the first (main) dictionary is searched; results are added in the same order, and the in/out language-model weight is passed on as in the sequential order. Only while typing a word (predictions stay sequential). Falls back to a normal search if the background search fails. |
| `PhraseDictionary.kt` | The phrase file is copied once per app install or update to the cache and memory-mapped, instead of being read into the Java heap each time dictionaries load (4 MB per English locale; the keyboard and the spell checker each held a copy). |
| `androidTest/.../EnglishPredictionBenchmark.kt` | `profile` (time split) and `emojiOutput` (emoji suggestions for 34 words and prefixes, to compare implementations) |

**Same output:** the emoji suggestions (emoji and positions) for the 34 test words and prefixes are byte-identical
before and after; all benchmark accuracy numbers are identical; the Sinhala suggestion strip is pixel-identical.

## Memory and loading

The phrase file no longer occupies 4 MB of Java heap per user; mapped pages are loaded on demand, can be reclaimed
by Android and are shared. Measured PSS did not drop in the benchmark (117–118 MB, run-to-run spread ±3 MB),
because the benchmark reads many phrase pages and mapped pages count towards PSS. Dictionary load time is
unchanged on a fresh install (64–70 ms, which includes the one-time copy).

## Rapid typing

40 taps sent without pauses (0.6 s) on the Fret keyboard: all 40 letters arrived, no crash, no ANR. The parallel
search runs while the main search runs, so it never adds waiting on the input thread.

## Persistence timings (reconciled)

All measured from the moment the word is typed, so they include the learning delay:

| Build | Learning delay | Save delay | Measured "saved after" |
| --- | --- | --- | --- |
| M3 | 25 s | only when leaving the field | never during the test |
| M5 | 10 s | 15 s after the last word (debounced) | 25.2 s (= 10 + 15) |
| M7 first build | 1.5 s | 4 s, throttled | 5.7 s (= 1.5 + 4 + polling) |
| M7 final / M8 | 1.5 s | 3 s, throttled | **4.7 s** (= 1.5 + 3 + polling) |

The M5 report's "15 s" was the save delay alone; the M7 report's 4.7 s is from typing. Both are consistent with the
measurements above.

## The skipped unit test

`helium314.keyboard.tools.CompileDictionaryTool` is not a test: it is the tool that compiles `.combined` word lists
into the bundled `.dict` files with the app's encoder. It only runs when `RCBBOARD_COMPILE_DICTS` names files to
compile, otherwise it is skipped by design (`tools/english-dictionaries/README.md`).

## Validation

| Check | Result |
| --- | --- |
| Unit tests | 455 passed, 0 failed, 1 skipped (the tool above) |
| Learning test | unchanged: phrase ≈ 1.5 s after its last word, saved after 4.7 s, typo protection as before |
| Upgrade M4 → M8 (minified, same key) | no crash; Fret kept; flick inserts "day" |
| Sinhala (Singlish) | pixel-identical to M2 |
| Minified release | 28.32 MB; R8 690/720 renamed, JNI kept; 16 KB aligned; no INTERNET permission |

Emulator only (Pixel 9a / Pixel 8 images on an Apple Silicon host); real-phone timings will differ.

## Recommendation

**GO** to commit M8. Battery measurement remains open (deferred).

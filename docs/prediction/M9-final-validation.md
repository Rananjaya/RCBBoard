# M9 – Final validation of M1–M8

Branch `feature/english-prediction-engine`, HEAD `0045f5cd` (M8) plus benchmark-test changes only. Nothing pushed,
merged or published. Emulator only (Pixel 9a and Pixel 8 images, Apple Silicon host); no physical phone.

## Verdict

**GO for merge to `develop` and QA, with one recommended data fix first** (names in word pairs, below).
Not yet release-ready for Play: needs a physical-phone test of the production-signed build and the battery
measurement the owner deferred.

## What changed (M1–M8)

| Milestone | Commit | Change |
| --- | --- | --- |
| M2 | `03371482` | English UK/US word pairs (Tatoeba), encoder fixes, benchmark |
| – | `21b5def7` | Key hints off by default |
| M3 | `1e281baf` | Phrase files: next word after two words |
| M5 | `9c4af6f8` | Faster learning, prompt saving, learned-words counter |
| M6 | `87512d33` | ~2,700 modern words (SCOWL), lower-case words not autocorrected to names |
| M7 | `e35e3aaa` | Learning in ~1.5 s, saving in ~4.7 s, accepted words, corrected typos removed |
| Fret | `6e17487e` | No black box behind predictions, metal frets, easier flicks |
| M4 | `7a4287ff` | Next word after three words |
| M8 | `0045f5cd` | Parallel emoji search, memory-mapped phrase files |

## Final numbers (emulator benchmark, held-out Tatoeba sentences, fresh install)

| | UK before (M1) | UK final | US before (M1) | US final |
| --- | --- | --- | --- | --- |
| Next word top-3 | 0 % | **38.6 %** | 16.9 % | **38.9 %** |
| Next word top-1 | 0 % | 22.7 % | 5.9 % | 22.7 % |
| Keystroke savings | 28.4 % | **42.9 %** | 32.4 % | **41.9 %** |
| Typos autocorrected | 75.2 % | 78.2 % | 78.1 % | 79.5 % |
| Correct words wrongly autocorrected | 0 | **0** | 0 | **0** |
| Safety list (34 words) changed | json → Jason | **none** | json → Jason | **none** |
| Typing lookup P95 | 26.4 ms | **18.3 ms** | 26.6 ms | **19.0 ms** |
| Next-word lookup P95 | 0.37 ms | 0.45 ms | 0.22 ms | 0.46 ms |
| Minified release APK | 22.5 MB | 28.3 MB | | |

Learning (UK): a phrase is predicted ≈ 1.5 s after its last word; learned words are saved 4.7 s after typing;
new words need two uses; corrected typos are not learned or are removed.

## Audit results

| Area | Result |
| --- | --- |
| **Concurrency (M8 parallel search)** | Real use: all typing lookups come from the single `InputLogicHandler` thread; the spell checker uses its own facilitator and a separate native session per request (semaphore + session pool). Each read-only dictionary holds a read lock during a search and closing takes the write lock. The parallel search is joined before returning (no late or stale results), runs only while a word is typed, on one daemon thread. Stress test: 800 typing lookups on the input thread while a second facilitator ran 823 lookups and reloaded its dictionaries 107 times: **0 mismatches, 0 errors, no native crash, 1 search thread**. |
| Concurrency, unsupported pattern | Several threads using the **same** facilitator and session at once crashed in the main dictionary's native search (`std::length_error`). The app never does this, and the main dictionary path is unchanged since before M1, so it is pre-existing, not caused by M8. Noted for anyone embedding the engine. |
| Prediction accuracy | Identical results from M4 to M8 (benchmark and emoji output byte-identical); no autocorrect regression since M1 |
| Learning persistence | Timings reconciled in M8; saving throttled (≤ 1 write per 3 s while typing, none when idle) |
| Licensing | AOSP (Apache-2.0), Tatoeba (CC BY 2.0 FR), SCOWL (permissive notice) credited in `THIRD_PARTY_NOTICES.md`, which is shipped and shown in the app (verified in the APK); LDNOOBW and Gutenberg not shipped; wordfreq rejected |
| Privacy | No network code added since `develop` (diff scan: 0 matches); no INTERNET permission; manifest unchanged; password fields and incognito still never learned from |
| Sinhala | `main_si.dict`, layouts and Sinhala code untouched; no phrase file for Sinhala; suggestion strip pixel-identical to M2 in every milestone (incl. M8 parallel search) |
| Fret | In-Letter, In-Column, Off show predictions; flicks (straight, 35°, short, wobbly) insert the word; taps type letters; style kept over upgrades |
| Upgrade 5.1.1 → final | Installs, no crash, settings kept ("Toggle numpad" space swipe). Learned history kept was shown in M2 (5.1.3 → M2) and the history file name and format are unchanged since; the M9 rerun could not re-check it because its 5.1.1 typing step did not reach the field (test harness) |
| Release build | Minified; R8 690/720 classes renamed, JNI classes kept; 16 KB aligned; no INTERNET permission |
| Tests | 455 unit tests pass (1 skipped: `CompileDictionaryTool`, a build tool, by design); script tests pass |

## Remaining risks

1. **Names in word pairs (recommended fix before release):** the M2 word pairs still predict Tatoeba's frequent
   name "Tom" as the word "tom" (522 UK entries, e.g. "thank → tom"). The phrase files were fixed in M3; the same
   name filter should be applied to the bigram build and the dictionaries rebuilt (data only, ~1 hour).
2. **Real phones:** all timings are from an emulator; typing P95 on a low-end phone is unknown.
3. **Battery:** not measured (deferred by the owner).
4. **APK size:** +5.8 MB since M1, mostly phrase files.
5. **Tatoeba style:** predictions lean to simple, conversational English.
6. **Engine licence:** the engine is part of a GPL-3.0 app; a non-commercial or "no copying" licence is not possible
   inside RCBBoard. Owner-only parts (build scripts, phrase data, a fresh reader) could be kept or licensed
   separately; to be decided with legal advice.

## Release readiness

1. Fix risk 1, rerun the benchmark (expected: same or better accuracy).
2. Merge `feature/english-prediction-engine` into `develop` (owner approval), and `ci/remove-release-smoke-test`
   (remove "Release smoke test" from the ruleset).
3. QA run, then a Release dry run for the production-signed APK, tested on the owner's phone.
4. Publish only after that test.

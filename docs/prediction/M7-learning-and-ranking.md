# M7 – Faster learning, prompt saving, ranking

Branch `feature/english-prediction-engine` on top of M6 (`87512d33`). **Not committed**, waiting for approval.

## Summary

| Learning (emulator, English UK, same test) | M6 | M7 |
| --- | --- | --- |
| Phrase predicted, from the start of typing it | 11.3 s | **2.7 s** (≈ 1.5 s after its last word) |
| Learned words written to disk, from typing | 25.2 s (only after a pause) | **4.7 s** |
| Disk writes while typing 20 words in 20 s | 0 (saving waited for a pause) | 7 (at most one per 3 s) |
| New word the user deliberately kept, offered after 1 use | no | **yes** |
| Typo corrected after it was learned | stays half learned | **removed** |
| New word typed normally | 2 uses | 2 uses (typo protection kept) |
| Typo corrected at once / typed once | not learned | not learned |
| Name capitalised by the user at a sentence start | learned | learned |

Prediction accuracy, autocorrection and Sinhala are unchanged (M6 dictionaries kept, see Ranking).

## Changes

| File | Change |
| --- | --- |
| `DictionaryFacilitatorImpl.kt` | Learning in memory 1.5 s after a word (was 10 s); disk save throttled: at most 3 s after learning and at most once per 3 s, never cancelled by further typing; a word unknown to the dictionaries that is corrected within a minute after being learned is removed again; deliberately kept words are learned at once as valid |
| `DictionaryFacilitator.java` | `markAccepted(word)` (default: nothing) |
| `InputLogic.java` | Picking exactly the typed word from the strip marks it accepted |
| `LatinIME.java` | Saves learned words when the keyboard is hidden (writes only if something changed) |
| `FretPredictionRenderer.kt` | Owner's request during M7: no black box behind In-Letter / In-Column words; a thin dark outline keeps them readable on the fret (also used by the fly-up animation) |
| `androidTest/.../EnglishPredictionBenchmark.kt` | Learning test: save time, accepted words, late corrections, write count |

In-memory learning and disk saving are separate: learning updates the dictionary in memory on a background
coroutine; saving is a separate throttled job on the dictionary's background executor. Nothing runs on the main
thread, typing is never delayed.

## Ranking

Raising the frequencies of the M6 words (median of their SCOWL group instead of median − 10) changed nothing
measurable: modern words offered after 4 letters stayed at 13/27, autocorrect accuracy stayed 78.2 %. They lose to
common words with the same first letters, not to their own frequency. The M6 dictionaries are kept. The next real
gain is ranking completions with the phrase context (see M4/M8).

## Battery and writes

Saving writes only when something changed and at most once per 3 s while typing (7 small writes in 20 s of
continuous typing, none when idle). Before M7, nothing was saved during continuous typing, so a process end lost it.

## Validation

| Check | Result |
| --- | --- |
| Unit tests | 454 passed, 0 failed, 1 skipped |
| Upgrade M6 → M7 (minified, same key) | no crash |
| Fret In-Letter / In-Column / Off | predictions shown ("as soon as" → possible, I, he), now without boxes; flick inserts "day" |
| Sinhala (Singlish) | suggestion strip pixel-identical to M2 |
| Privacy | password fields and incognito are checked before learning (unchanged); accepted words only from a manual pick; history stays private |
| Minified release | 27.07 MB; R8 689/719 renamed, JNI kept; 16 KB aligned; no INTERNET permission |

Emulator only, no physical phone.

## Limitations

- A word corrected more than a minute after it was learned is not removed (it is then treated as accepted).
- Battery cost was estimated from write counts, not measured with a power profiler.

## Recommendation

**GO** to commit M7 (and the Fret box removal as its own commit).

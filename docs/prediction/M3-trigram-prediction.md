# M3 – Trigram next-word prediction (English)

Branch `feature/english-prediction-engine` (M2 committed as `03371482`, key hints `21b5def7`). M3 is **not
committed**, waiting for approval. English only: Sinhala, Wijesekara and Singlish are unchanged.

## Summary

- Next-word predictions now use the previous **two** words when the phrase is known, and fall back to the M2
  bigrams (one word) otherwise.
- Top-3 next-word accuracy on held-out sentences: **37.0 % (UK) and 37.5 % (US)**, up from 29.4 % / 29.0 % in M2
  (estimate before M3: 38 %). On independent public-domain books: 20.5 % → **25.2 %** (UK).
- Keystroke savings 39.2 % → **42.3 %** (UK), 37.5 % → **41.1 %** (US).
- Autocorrection unchanged: same accuracy, **0 wrong autocorrects**, same safety-list result.
- A phrase the user types reaches first place after 3 uses, ahead of the bundled phrases.
- Next-word lookup P95 **0.43 ms** (target < 2 ms); typing latency unchanged.
- APK +2.6 MB (24.4 → 27.0 MB). No native code change, no new dependency, no INTERNET permission.

## Architecture

**Evaluated first:**

| Option | Verdict |
| --- | --- |
| Native ver4 dictionary as the bundled main dictionary (supports n-grams) | Rejected. Ver4 is a multi-file directory format (needs new asset extraction), can only be generated through the native library on a device (not reproducible on a computer), and would change the typing scores used by autocorrect. |
| Compact phrase file next to the existing dictionary | **Chosen.** Read-only, built reproducibly by a script, used only for next-word predictions, no JNI or R8 changes. |

**How it works**

- `assets/phrases/en-GB.phrases`, `en-US.phrases`: for each two-word context, up to 8 next words with a 0–255
  score. Sorted vocabulary and sorted context keys, looked up by binary search (format in `PhraseDictionary.kt`).
- `PhraseDictionary` (new, Kotlin) is added to the main dictionary collection for English locales by
  `DictionaryFactory`. It answers only when nothing is typed and there are two previous words in the same
  sentence; otherwise it returns nothing. It has no words of its own, so it never affects spell checking,
  autocorrection or learning.
- **Backoff and ranking:** its results are merged with the main dictionary's bigram predictions and the user
  history **by score**, on the same 0–255 scale. Known two-word context → phrase scores (plus a 12-point bonus,
  tuned, because two words are more specific than one); unknown context → the bigrams; unknown previous word →
  the dictionary's existing behaviour.
- **Smoothing / confidence:** absolute discounting (a phrase seen c times counts as c − 0.5); a phrase must be
  seen at least 3 times with a probability of at least 2 %. Scores use the dictionary's own log scale (fitted
  slope ≈ 20 points per factor of ten).
- **Personal history** is unchanged; its predictions compete by score and, once a phrase is used a few times,
  come first (measured below).
- Loading: about 2.6 MB per English locale, read into memory when the dictionaries load (background thread, part
  of the 55–70 ms dictionary load on the emulator). Nothing runs on the main thread.

## Files changed

| File | Change |
| --- | --- |
| `app/src/main/java/.../dictionary/PhraseDictionary.kt` | New: reads phrase files, next-word predictions only |
| `app/src/main/java/.../dictionary/DictionaryFactory.kt` | Adds the phrase dictionary to English main collections (2 lines) |
| `app/src/main/assets/phrases/en-GB.phrases`, `en-US.phrases` | New data, 2.6 MB each |
| `app/src/test/.../latin/PhraseDictionaryTest.kt` | New: 6 tests (examples, sentence start, no Sinhala, UK/US spelling, nothing while typing) |
| `app/src/androidTest/.../EnglishPredictionBenchmark.kt` | Independent test set, safety list, personal-history test, symbol typing |
| `app/src/androidTest/assets/benchmark/*/independent_next_word.tsv` | New independent test data (public domain books) |
| `tools/english-dictionaries/build_english_trigrams.py`, `build_independent_benchmark.py`, README | Reproducible build and tuning |
| `licenses/THIRD_PARTY_NOTICES.md`, `docs/prediction/dataset-licensing.md` | Attribution for the phrase files and the test books |

No native (C++) change. No dependency added.

## Datasets and licences

| Data | Use | Licence |
| --- | --- | --- |
| Tatoeba English sentences (same file as M2, SHA-256 `00e8f461…40302`) | Phrase counts (buckets 10–99) | CC BY 2.0 FR, attributed in `THIRD_PARTY_NOTICES.md` (shown in the app) |
| Tatoeba buckets 0–9 | Test sets only, never counted | same |
| Tatoeba buckets 10–14 | Development set for tuning (then included in the final files) | same |
| Project Gutenberg #1342 (Pride and Prejudice), #1661 (Sherlock Holmes) | Independent test set only, not in the app | Public domain (USA) |

## Accuracy versus storage (development set, UK; US within 0.6 points)

| Pruning | Top-3 | File |
| --- | --- | --- |
| bigrams only (M2) | 29.6 % | – |
| phrase seen ≥ 5 times, 8 per context | 39.2 % | 1.4 MB |
| **phrase seen ≥ 3 times, 8 per context (chosen)** | **40.1 %** | **2.6 MB** |
| phrase seen ≥ 2 times, 8 per context | 40.7 % | 4.5 MB |

Bonus 12 gave the best top-1/top-3 balance (16 lowers top-1). 4 vs 8 next words per context: same top-3, better
top-5 with 8 (+7 % size).

## Benchmark: M1, M2, M3 (same harness, same data)

Emulator (Pixel 8 API 35, arm64, Apple Silicon host), `debugNoMinify`, fresh install. Not physical-device numbers.

### English (UK)

| | M1 | M2 | M3 |
| --- | --- | --- | --- |
| Next word top-1 / top-3 / top-5 | 0 / 0 / 0 % | 16.1 / 29.4 / 36.4 % | **21.8 / 37.0 / 44.5 %** |
| Mean reciprocal rank | 0.000 | 0.250 | **0.316** |
| Independent books top-1 / top-3 / top-5 | 0 / 0 / 0 % | 11.5 / 20.5 / 26.5 % | **13.2 / 25.2 / 30.8 %** |
| Keystroke savings | 28.4 % | 39.2 % | **42.3 %** |
| Typos autocorrected | 75.2 % | 78.2 % | 78.2 % |
| Correct words wrongly autocorrected | 0 / 1,537 | 0 / 1,537 | **0 / 1,537** |
| Safety list changed (34 words) | 1 (json → Jason) | 1 (same) | **1 (same)** |
| Next-word lookup P50 / P95 / P99 | 0.09 / 0.37 / 1.11 ms | 0.26 / 0.41 / 0.50 ms | 0.26 / **0.43** / 0.54 ms |
| Typing lookup P50 / P95 / P99 | 17.6 / 26.4 / 37.8 ms | 17.2 / 25.3 / 36.2 ms | 17.2 / 25.3 / 36.3 ms |

### English (US)

| | M1 | M2 | M3 |
| --- | --- | --- | --- |
| Next word top-1 / top-3 / top-5 | 5.9 / 16.9 / 16.9 % | 17.1 / 29.0 / 35.8 % | **22.7 / 37.5 / 43.8 %** |
| Mean reciprocal rank | 0.105 | 0.251 | **0.318** |
| Independent books top-1 / top-3 / top-5 | 5.6 / 14.4 / 14.4 % | 12.2 / 21.2 / 27.0 % | **14.0 / 23.9 / 29.5 %** |
| Keystroke savings | 32.4 % | 37.5 % | **41.1 %** |
| Typos autocorrected | 78.1 % | 79.5 % | 79.5 % |
| Correct words wrongly autocorrected | 0 / 1,512 | 0 / 1,512 | **0 / 1,512** |
| Safety list changed (34 words) | 1 (json → Jason) | 1 (same) | **1 (same)** |
| Next-word lookup P50 / P95 / P99 | 0.14 / 0.22 / 0.33 ms | 0.25 / 0.40 / 0.48 ms | 0.26 / **0.43** / 0.54 ms |
| Typing lookup P50 / P95 / P99 | 17.4 / 26.6 / 36.8 ms | 17.8 / 27.5 / 37.8 ms | 17.2 / 26.1 / 36.3 ms |

The safety list (rare words, names, technical terms, UK/US spellings, web and e-mail addresses typed exactly)
changed only "json" → "Jason", in every milestone: an existing behaviour, not caused by M2 or M3.

### Examples (fresh install, UK, first five predictions)

| Context | M3 | Expected word |
| --- | --- | --- |
| have a nice | guy, **day**, person, to, place | day (2nd) |
| nice to | me, see, **meet**, be, have | meet (3rd) |
| how are | **you**, we, things, your, they | you |
| I would like | **to**, you, it, a, the | to |
| thank you very | **much**, well, good, busy, happy | much |
| please let me | **know**, do, tell, go, see | know |
| I will be | able, here, a, the, in | there (not in top 5) |
| happy birthday | **to**, party, present, is, cake | to |
| see you | again, in, at, **tomorrow**, here | tomorrow (4th) |
| as soon as | **possible**, I, he, you, she | possible |
| looking forward to | **seeing**, the, it, your, doing | seeing |
| let me | **know**, do, tell, go, see | know |
| I don't | think, **know**, want, have, like | know (2nd) |

No phrase is hard-coded; these come from counted sentences. US differs only slightly ("happy birthday → to" is
below the top 5 in the US data).

### Personal history priority

"see you there" taught through the normal learning path (25 s delay each time), rank of "there" after "see you":

| Uses | UK | US |
| --- | --- | --- |
| 1 | not in top 5 | 4th |
| 2 | 2nd | 2nd |
| 3 | **1st** | **1st** |
| 4 | 1st | 1st |

### Memory and size

| | M2 | M3 |
| --- | --- | --- |
| Minified release APK | 24.4 MB | **27.0 MB** (+2.6 MB) |
| Phrase files | – | 2.6 MB per English locale (1.3 MB compressed) |
| Process memory (PSS, emulator) | 110.8 / 111.2 MB | 112.8–118.7 / 117.3 MB (two runs; the file itself is 2.6 MB in memory, the rest is run-to-run variation) |
| Dictionary load | 41–42 ms | 55–70 ms |

## Validation

| Check | Result |
| --- | --- |
| Unit tests | **452 passed**, 0 failed, 1 skipped (dictionary build tool) |
| Script tests | 115 passed (M2; M3 changes no script) |
| Phrase dictionary tests | 6 passed |
| Upgrade M2 → M3 (minified, same key, version change so cached dictionaries are cleared) | installed, no crash; Fret style and settings kept; learned history kept (M2 test) |
| Fret In-Letter | "have a nice" shows day above D; flick up inserts "day" |
| Fret In-Column / Off | "as soon as" shows possible, I, he in columns / in the normal strip |
| Standard strip | phrase predictions shown (to, guy, day after "have a nice") |
| Sinhala | unchanged: no phrase file is created for Sinhala (unit test), `main_si.dict` not modified, M2 emulator check identical |
| Release smoke test (minified) | passed |
| R8 | 690/720 classes renamed, JNI classes kept |
| 16 KB alignment | verified |
| INTERNET permission | none |
| Glide typing | not affected: phrases are skipped for gesture input; flag unchanged (off) |
| Incognito / password fields | unchanged: the phrase dictionary does not learn and has no access to typed text beyond the two previous words |

No physical phone was used.

## Fix made during M3

The first build predicted "Tom" after "how are": Tatoeba's frequent name "Tom" matched the dictionary word "tom".
The phrase build now ignores a word written with a capital in the middle of a sentence when its dictionary form is
lower case. The results above are after this fix.

## Remaining limitations

- Two words of context at most; "I will be → there" is not reached.
- Some top predictions reflect Tatoeba's style ("have a nice guy" before "day").
- Phrases never help while a word is being typed (only before its first letter); by design, so autocorrection
  cannot change.
- The phrase file is held in memory (2.6 MB per English locale); the spell checker, when used, loads its own copy.
- Latency and memory are emulator numbers.
- Personal phrases need 2–3 uses to rise (learning is M5).

## 4-gram recommendation

Offline estimate (M2): 4-grams add +2.8 points top-3 over trigrams, for about the same data again (+2.5 MB APK).
**Not justified now.** A better use of the next milestone is personal learning (M5) and the "json" type of
correction issue.

## Recommendation

**M3: complete. GO** to commit after approval. Next: personal learning (M5) before any 4-gram work.

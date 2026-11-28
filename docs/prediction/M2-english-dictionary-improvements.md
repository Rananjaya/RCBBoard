# M2 – English dictionary and next-word prediction improvements

Branch `feature/english-prediction-engine`, based on `develop` at `529932af`. Not committed: waiting for the
owner's approval. English only; Sinhala, Wijesekara and Singlish are unchanged.

## Summary

- English (UK) had **no next-word predictions** on a fresh install. It now predicts the next word, and the
  correct word is in the top 3 for **29.4 %** of held-out contexts (was 0 %). English (US) went from 16.9 % to
  **29.0 %**.
- Keystroke savings rose from 28.3 % to **39.1 %** (UK) and from 32.3 % to **37.4 %** (US).
- Autocorrection of typos improved slightly; **correct words were never wrongly autocorrected**, before or after.
- Sinhala results are identical. Fret In-Letter, In-Column and Off all show the new predictions; flick up works.
- No native engine change was needed. Two bugs in the app's Java dictionary encoder were fixed.
- The 16 other-language dictionaries were **not removed**: they are still used (see "Dictionary cleanup").
- APK +1.9 MB; memory about +1 MB; next-word lookup stays below 1 ms.

## Files changed

| File | Change |
| --- | --- |
| `app/src/main/assets/dicts/main_en-GB.dict` | Rebuilt: same words, frequencies, shortcuts and flags, plus 523,431 bigrams (was 0) |
| `app/src/main/assets/dicts/main_en-US.dict` | Rebuilt: same words, plus 538,171 bigrams (was 481,875, all kept) |
| `app/src/main/java/.../makedict/Ver2DictEncoder.java` | Bug fix: writes shortcuts before bigrams, the order the native reader expects |
| `app/src/main/java/.../makedict/BinaryDictEncoderUtils.java` | Bug fix: the maximum node size counts shortcut lists ("Increased size ?!" crash) |
| `app/src/test/.../latin/DictionaryTest.kt` | Regression test for both fixes (fails without them) |
| `app/src/test/.../tools/CompileDictionaryTool.kt` | Compiles `.combined` to `.dict` with the app's encoder; skipped unless asked |
| `app/src/androidTest/.../benchmark/EnglishPredictionBenchmark.kt` | On-device benchmark of the real engine |
| `app/src/androidTest/assets/benchmark/en-GB, en-US` | Held-out test sentences (Tatoeba, CC BY 2.0 FR) |
| `app/build.gradle.kts` | Instrumentation runner; test-only `androidx.test:runner` and JUnit (already used by unit tests); `rcbboardTestBuildType` property (default unchanged) |
| `tools/english-dictionaries/` | `v2_to_combined.py`, `build_english_bigrams.py`, `estimate_trigram_gain.py`, README |
| `licenses/THIRD_PARTY_NOTICES.md` | English dictionaries and Tatoeba attribution (shown in the app) |
| `docs/prediction/*.md` | This report; M1 documents corrected |

Separately requested by the owner during M2 (not part of the prediction work, to be committed on its own):
`Defaults.kt` – long-press key hints are now **off by default** (`PREF_SHOW_HINTS = false`). Users who changed the
setting keep their choice.

## Dictionary sources and licences

| Data | Source | Licence |
| --- | --- | --- |
| Words, frequencies, shortcuts, flags; the existing US bigrams | AOSP keyboard dictionaries via HeliBoard | Apache-2.0 |
| New bigrams | Counted from Tatoeba English sentences (2,042,101 sentences, file SHA-256 `00e8f461…40302`, downloaded 28 Nov 2026) | CC BY 2.0 FR, attribution in `licenses/THIRD_PARTY_NOTICES.md` |

The CC0 part of Tatoeba was checked first (no attribution needed) but has only 41,515 English sentences.
Sentences are not shipped, only word pair statistics. Build and evaluation data are separate: sentences are split
by a hash of their normalised text, 10 % (204,391) held out and never counted, identical sentences always on the
same side. Rebuilding is reproducible: `tools/english-dictionaries/README.md`.

Quality rules: up to 12 next words per word, each seen 3+ times with probability ≥ 1 %; no names or other
capitalised words (Tatoeba overuses "Tom" and "Mary"), no single letters except "a" and "I", nothing flagged
possibly offensive. British spelling: 1,756 American forms are mapped to British ones for en-GB (organise,
colour, centre…) when only the British form is an en-GB dictionary word.

The pipeline was verified end to end: decoding the original English dictionaries and compiling them again gives
**byte-identical files**, and the new dictionaries decode to exactly their source words and pairs.

## Benchmark

Real engine (`Suggest`, dictionaries, native library) on the **Pixel 8 API 35 emulator (arm64, Apple Silicon
host)**, `debugNoMinify` build, fresh state (no history, contacts or app names). Not physical-device numbers.
3,000 held-out next-word contexts, 2,000 typed words with seeded tap noise (σ = 0.25 key), 200 sentences for
keystroke savings.

### Next-word prediction (fresh install)

| | UK before | UK after | US before | US after |
| --- | --- | --- | --- | --- |
| Top-1 | 0.0 % | **16.1 %** | 5.9 % | **17.1 %** |
| Top-3 | 0.0 % | **29.4 %** | 16.9 % | **29.0 %** |
| Top-5 | 0.0 % | **36.4 %** | 16.9 % | **35.8 %** |
| Mean reciprocal rank | 0.000 | **0.250** | 0.105 | **0.251** |
| Contexts with any prediction | 0 % | 100 % | 100 % | 100 % |

### Typing and autocorrection

| | UK before | UK after | US before | US after |
| --- | --- | --- | --- | --- |
| Typos autocorrected to the intended word | 75.2 % | **78.2 %** | 78.1 % | **79.5 %** |
| Intended word among the suggestions | 98.5 % | 99.4 % | 99.6 % | 99.8 % |
| Correct words wrongly autocorrected | **0 / 1,537** | **0 / 1,537** | **0 / 1,512** | **0 / 1,512** |
| Keystroke savings | 28.3 % | **39.1 %** | 32.3 % | **37.4 %** |

Brief's typo examples, before and after: teh, thsi, goign, becuase, recieve and helloworld are autocorrected;
tommorow is offered as "tomorrow" but not autocorrected (unchanged; on the device the strip showed it in the centre).

### Representative examples (fresh install, first five predictions)

| Context | UK before | UK after |
| --- | --- | --- |
| how are | – | **you**, the, going, not, a |
| I would like | – | **to**, a, that, the, this |
| thank you very | – | good, **much**, well, busy, happy |
| happy birthday | – | party, present, is, cake, today |
| good | – | at, for, idea, time, to |
| see you | – | have, want, know, don't, to |
| nice to | – | do, the, be, go, help |
| please let me | – | to, that, a, he, the |
| have a nice | – | to, person, guy, if, of |
| I will be | – | a, able, the, in, here |

Bigrams only use the **last** word, so "have a nice → day", "nice to → meet", "please let me → know" and "see you →
tomorrow" need two or three words of context. That is the trigram question below.

### Latency, memory, size (emulator)

| | UK before | UK after | US before | US after |
| --- | --- | --- | --- | --- |
| Next-word lookup P50 / P95 / P99 | 0.09 / 0.14 / 0.21 ms | 0.26 / 0.41 / 0.51 ms | 0.14 / 0.25 / 0.41 ms | 0.25 / 0.40 / 0.48 ms |
| Typing lookup P50 / P95 / P99 | 17.9 / 27.1 / 38.0 ms | 17.9 / 27.3 / 38.1 ms | 18.3 / 28.4 / 38.9 ms | 18.1 / 29.0 / 38.0 ms |
| Dictionary load | 27 ms | 46 ms | 44 ms | 48 ms |
| Process memory (PSS) | 107.5 MB | 108.5 MB | 109.2 MB | 109.1 MB |

The typing lookup is unchanged by M2 (same native search); its P95 of about 27 ms on this emulator is above the
20 ms target and is an existing cost to measure on real phones in a later milestone. The minified release APK is
**24.4 MB (was 22.5 MB, +1.9 MB)**; `main_en-GB.dict` grew from 1.0 MB to 3.1 MB.

## Dictionary cleanup: not done

The 16 other-language dictionaries (11.7 MB of the APK) are **still used** by existing features, so they were kept,
as the brief requires:

- **Secondary languages**: the language settings let an English keyboard add other Latin-script languages for
  multilingual typing; the choice comes from the bundled dictionaries (`getAvailableSecondaryLocales`).
- **Spell checker**: RCBBoard's system spell checker declares about 40 languages (`res/xml/spellchecker.xml`)
  and uses these files.

The M1 audit's statement that they "can never be used" was wrong and has been corrected. Removing them would save
11.7 MB but remove those features: an owner decision.

## Regression and release checks

| Check | Result |
| --- | --- |
| Unit tests | 446 run, 0 failed, 1 skipped (the dictionary build tool) |
| New encoder test without the fixes | fails, as intended |
| Sinhala (Singlish) on the emulator, "mama gedara yanawa" | identical suggestions before and after (යානාව, යනවා, යනවද); `main_si.dict` unchanged |
| Upgrade 5.1.3 → M2 build (minified release, same key) | installed, no crash; previously learned "see you → at" and "happy birthday → very" kept and still ranked first |
| Standard strip | new predictions shown (screenshots in the session) |
| Fret In-Letter | "how are" shows **you** above Y, the above T, going above G; flick up on Y inserts "you" |
| Fret In-Column | "thank you very" shows much / good / well in three columns |
| Fret Off | the normal strip returns with the same words |
| Release smoke test (minified APK) | passed |
| R8 mapping | 654 classes renamed; JNI classes kept |
| 16 KB alignment | verified |
| INTERNET permission | none |
| Glide typing | not tested: it is off in RCBBoard builds (`rcbboardGlideTyping=false`); dictionary format unchanged |

No physical phone was used.

## Why learning seems inaccurate (owner's report during M2)

Not changed in M2 (the brief keeps learning as it is). Causes found in the code, to measure in M5:

1. A word is learned only **25 seconds** after it is typed (so a quickly corrected typo is not learned).
2. Words that are not in the dictionary start "weak" and need several uses before they are suggested.
3. Capitalised words at the start of a sentence are skipped when the engine cannot tell whether the capital was
   intended.
4. History is written to storage lazily; if the keyboard process is killed, the latest learning is lost.

## Remaining limitations

- Bigrams only: no context beyond the previous word.
- Tatoeba is short, simple sentences (good for chat, weaker for professional writing).
- Predictions are capitalised at sentence start by the existing rules.
- Benchmark numbers are from an emulator; real-phone latency is not measured yet.

## Trigrams and 4-grams

Offline model on the same split, ranking by conditional probability with backoff
(`tools/english-dictionaries/estimate_trigram_gain.py`, 20,000 held-out contexts). Its bigram figure matches the
device (29.2 % vs 29.4 % top-3), so it is a fair predictor:

| | Top-1 | Top-3 | Top-5 | Entries after pruning |
| --- | --- | --- | --- | --- |
| Bigram (M2) | 16.7 % | 29.2 % | 35.7 % | 82,250 |
| + trigram | 22.9 % | **37.6 %** | 44.4 % | +308,239 |
| + 4-gram | 25.8 % | 40.4 % | 47.0 % | +325,095 |

Trigrams add **+8.4 points top-3** (about 29 % more correct predictions) and fix the "have a nice → day" kind of
case; 4-grams add another +2.8 points for about the same data again. **Recommendation: trigram support is
justified; 4-grams are optional**, to decide after trigrams are measured on the device. It needs the n-gram model
of `architecture.md` (option B): the v2 dictionary format cannot store trigrams.

## Recommendation

**M2: complete.** Ready for review and commit after approval.

**M3: GO**, with this order:

1. Commit M2 (prediction data, encoder fixes, benchmark) and, separately, the key-hints default.
2. M3 as planned is correction tuning; the benchmark shows correction is already good (0 wrong autocorrects).
   The larger gain is trigrams. Proposal: do the trigram model (roadmap M4) next, and keep correction tuning small.
3. Decide on the 16 other-language dictionaries (keep, or remove the secondary-language and spell checker
   support for them to save 11.7 MB).

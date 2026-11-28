# M1 – Audit of the existing prediction engine

Audited on 28 November 2026, on `develop` at `529932af` (RCBBoard 5.1.3 candidate). Scope agreed with the
owner: **only English is upgraded; Sinhala (Sinhala, Wijesekara and Singlish) predictions stay exactly as they are.**

No production code was changed for this audit.

## 1. Architecture in one picture

```
 key events / glide ──► InputLogic (UI thread) ──► InputLogicHandler (HandlerThread "InputLogicHandler")
                                                          │  sequence numbers, outdated requests are dropped
                                                          ▼
                                                    Suggest.kt  ── autocorrect decision, capitalisation,
                                                          │        emoji, Singlish spellings, next-word cache
                                                          ▼
                                           DictionaryFacilitatorImpl.kt
                    one DictionaryGroup per enabled locale, weighted by a per-locale confidence
                    ┌───────────────┬──────────────────┬─────────────────┬──────────────┬─────────────┐
                    main (assets,    user history       personal dict    contacts       apps
                    v2, read-only)   (ver4, learned)    (Android)        (ver4, opt-in) (ver4, opt-in)
                    └───────────────┴──────────┬───────┴─────────────────┴──────────────┴─────────────┘
                                               ▼ JNI (BinaryDictionary)
                       libjni_latinime.so: Suggest / DicTraverseSession / TypingTraversal,
                       TypingScoring, TypingWeighting, ProximityInfo, forgetting curve
                                               │
                                               ▼
                          SuggestedWords ──► suggestion strip  and  FretPredictions (In-Letter / In-Column)
```

The Fret presentation layer (`FretPredictions.kt`) only consumes `SuggestedWords`. It does not rank or
look anything up, so a better engine reaches the strip and both Fret modes without Fret changes.

## 2. Component inventory

| Area | Where | What exists today |
| --- | --- | --- |
| Main dictionaries | `assets/dicts/main_*.dict` | Format **v2 (version 202)**, read-only Patricia trie: unigrams with a 0–255 frequency class, optional **bigram** lists, shortcuts, offensive and not-a-word flags. v2 cannot hold trigrams or 4-grams. |
| Personal history | `UserHistoryDictionary` (ver4) | Dynamic, **up to 4-grams** (`MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3`), timestamps and a **forgetting curve** (decay every 2 h, 31 time steps). Bounded: **10,000 words and 30,000 entries for each of 2-, 3- and 4-grams** (`HeaderPolicy::DEFAULT_MAX_NGRAM_COUNTS`). |
| Learning delay | `DictionaryFacilitatorImpl.unlearnDuration` | A word is added **25 s** after typing; correcting it in that time cancels it, so instant typos are not learned. |
| Blocked words | `DictionaryGroup` blacklist file | Words removed from suggestions are never suggested again (a hard rejection penalty). |
| Personal dictionary, contacts, app names | `UserBinaryDictionary`, `ContactsBinaryDictionary`, `AppsBinaryDictionary` | Optional, local. |
| Typo model | `jni/.../typing/scoring_params.cpp` | Weighted edit costs, tuned by AOSP: proximity 0.069, substitution 0.381, omission 0.467, insertion 0.725, **transposition 0.561**, repeated-letter omission/insertion 0.345/0.551, **space omission 0.1 (missing space, e.g. helloworld)**, **space substitution 0.33 (space typed as a letter)**, case and accent penalties for exact matches, completion costs. |
| Key proximity | `ProximityInfo` (native) | Real key geometry and touch distances (spatial model), combined with the language model by `DISTANCE_WEIGHT_LANGUAGE`. |
| Context | `NgramContext`, `NgramContextUtils` | Up to 3 previous words; beginning-of-sentence marker; punctuation ends the context. |
| Autocorrect decision | `Suggest.shouldBeAutoCorrected` | Score threshold from the user's confidence setting; never autocorrects text that looks like e-mail or URL; never when the field disables it; typed word always shown so it can be kept. |
| Capitalisation | `Suggest`, `CapsMode` | Auto-capitalisation, ALL CAPS and capitalised-suggestion handling. |
| Next-word prediction | `Suggest.getNextWordSuggestions` | Empty-word lookup in every dictionary with the n-gram context, cached per context. |
| Threading | `InputLogicHandler` | One background `HandlerThread`; batch (glide) updates carry sequence numbers, so stale results are dropped. |
| Privacy | `InputAttributes`, `InputLogic.performAdditionToUserHistoryDictionary` | No learning in **password fields** (no suggestions there), in **incognito** (forced or requested by the app), with `IME_FLAG_NO_PERSONALIZED_LEARNING`, or on slow input connections. No network permission. |
| Fret | `FretPredictions.kt` | Up to 3 words; In-Letter places each above the key of its next letter, In-Column in three columns; emoji above comma; flick up accepts. |

## 3. Measured baseline

### Dictionaries (measured with a v2 reader written for this audit)

| File | Size | Words | Words with bigrams | Bigram entries | Trigram+ |
| --- | --- | --- | --- | --- | --- |
| `main_en-US.dict` | 2,887 KB | 160,715 | 160,625 | **481,875** (≈3 per word) | none (v2) |
| `main_en-GB.dict` | 1,003 KB | 157,469 | **0** | **0** | none |
| `main_si.dict` (unchanged by this work) | 872 KB | 83,029 | 0 | 0 | none |
| `emoji_en.dict` | 274 KB | 9,065 (shortcut entries) | 0 | 0 | – |

### APK size

- Release APK: **22.5 MB**. Dictionaries: 15.4 MB compressed.
- **11.7 MB compressed (28.1 MB uncompressed) are dictionaries of 16 other languages.** Keyboards are only offered
  for en-US, en-GB and si-LK (`rcbboard_enabled_locales`), but M2 found that these files are still used: as
  secondary languages for multilingual typing on an English keyboard, and by RCBBoard's system spell checker.
  (This audit first said they could never be used; that was wrong, see `M2-english-dictionary-improvements.md`.)
- Native library: about 1.3 MB per ABI (4 ABIs).

### Behaviour on the 5.1.3 release candidate, English (UK), Pixel 9a emulator

Typed with the Material layout. A ✓ means the expected word was the main (centre) suggestion.

| Input | Expected | Result |
| --- | --- | --- |
| teh | the | ✓ the |
| thsi | this | ✓ this |
| goign | going | ✓ going |
| becuase | because | ✓ because |
| recieve | receive | ✓ receive |
| tommorow | tomorrow | ✓ tomorrow |
| helloworld | hello world | ✓ hello world |
| good ␣ | morning, night, luck | **nothing** |
| how are ␣ | you | **nothing** |
| i would like ␣ | to, a, some | **nothing** |
| thank you very ␣ | much | **nothing** |
| see you ␣ | tomorrow, soon | only "at", learned from earlier typing on this device |
| happy birthday ␣ | to | only "very", learned from earlier typing |
| react ␣ | native | **nothing** |

Conclusion: **typo correction is already strong; next-word prediction on a fresh install is missing for
English (UK) and limited to bigrams for English (US).** This matches the earlier Fret finding that
next-word words only come from learned history.

### Runtime

- Memory of the keyboard process on the emulator: **67 MB PSS** (30 MB native heap, 8 MB Java heap).
- Latency, accuracy percentages (top-1/3/5, MRR, keystroke savings) and battery were **not measured**:
  the project has no benchmark harness yet. Building one is the first M2 task (see `benchmark-plan.md`).
  No improvement will be claimed before it exists.

## 4. What exists, what to improve, what is missing

| Requested capability | Status | Action |
| --- | --- | --- |
| Weighted Damerau–Levenshtein, proximity, missing/extra/swapped/repeated letters | **Exists** (native, tuned) | Keep. Only retune with benchmark evidence. |
| Missing space (helloworld), accidental space | **Exists** | Keep; add regression tests. |
| No autocorrect of URLs / e-mail | **Exists** | Keep; add tests for names and technical words. |
| Capitalisation awareness | **Exists** | Keep. |
| Frequency-ranked unigrams, prefix completion | **Exists** | Keep; review vocabulary quality (dated AOSP lists). |
| Bigram prediction | **Partial**: en-US only, ≈3 successors per word | Add good bigrams for en-US **and en-GB**. |
| Trigram / 4-gram from bundled data | **Missing** (v2 format cannot store them) | New compact n-gram model (see architecture). |
| Backoff and smoothing | **Missing** for bundled data | Stupid-backoff style scoring in the new model. |
| Context-aware correction | **Partial**: uses bigrams where present (en-US), none for en-GB | Feed the n-gram model into typing scores. |
| Personal n-grams up to 4, bounded, decaying | **Exists** | Keep; tune interaction with the bundled model. |
| Delay before learning (typos not learned instantly) | **Exists** (25 s) | Keep. |
| Rejection penalty | **Exists** (block word) | Possibly add a soft penalty for reverted autocorrections. |
| Acceptance / recency signals | **Partial** (history counts + forgetting curve) | Evaluate only with benchmark data. |
| Unified configurable ranking | **Partial** (native costs + Java filters) | Add the n-gram score as one documented, configurable term. |
| Fret integration | **Exists**, independent of ranking | No change expected. |
| Privacy (password, incognito, no-learning flag, offline) | **Exists** | Keep; add tests. |
| Benchmark harness | **Missing** | First task of M2. |

## 5. Weaknesses found

1. **No next-word predictions for English (UK) on a fresh install**, and the default English for Sri Lanka is en-GB.
2. English (US) has only bigrams, about 3 successors per word, from old AOSP data.
3. 11.7 MB of dictionaries for other languages make the APK larger; they serve secondary languages and the spell
   checker only (owner decision, see M2).
4. No benchmark, so tuning so far was by feel.
5. Dictionary vocabulary is old (2014 AOSP lists): newer words such as app and technology names are weak.

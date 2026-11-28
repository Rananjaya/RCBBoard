# Proposed architecture: English n-gram model

Status: proposal for M2, not implemented. Scope: English (US and UK) only. Sinhala, Wijesekara and Singlish
are not touched.

## Principles

- Statistical only: counted n-grams, backoff, edit costs. No neural or AI models.
- Offline: data ships in the APK. No downloads, no INTERNET permission.
- Reuse the existing native engine for typing, correction and personal learning. Add only what is missing:
  context data for fresh installs.
- The engine stays independent of the strip and Fret; both keep consuming `SuggestedWords`.

## Options considered

| Option | What | Pros | Cons |
| --- | --- | --- | --- |
| A. Add bigrams to the v2 main dictionaries | Rebuild `main_en-GB.dict` (and better `main_en-US.dict`) with bigram lists | No engine code; works with today's native scoring, including context-aware correction | Bigrams only; v2 stores a few successors per word with 4-bit probabilities |
| B. Separate n-gram model file | New read-only file per English locale with pruned, quantised 2- to 4-grams, read by a small native reader, exposed as a new dictionary type | Trigrams and 4-grams, backoff, full control of size | New format, JNI, tests |
| C. Ship the main dictionary in ver4 | Use the dynamic format (which supports 4-grams) for the bundled dictionary | Uses existing n-gram code | ver4 is a multi-file writable format built for learning, not for assets; larger; not designed to be loaded read-only from the APK |

**Recommendation: A first, then B if the benchmark shows that trigrams add enough.**
Option A alone fixes the largest gap (no next-word predictions for en-GB) at almost no risk.

## Option B design (for M2 stage 2)

- **File**: `assets/ngrams/en.ngram`, shared by en-US and en-GB (spelling handled by mapping to the locale's
  unigram ids at load time), or one file per locale if the benchmark prefers it.
- **Contents**: vocabulary limited to words in the locale's main dictionary; for each context of 1–3 words,
  the top *k* successors (k ≈ 8) with quantised log-probabilities (1 byte). Pruned by count.
- **Layout**: sorted arrays of 32-bit word-id tuples with binary search, memory-mapped read-only
  (the same way main dictionaries are mapped), so it costs page cache rather than heap.
- **Scoring**: stupid backoff (4-gram → trigram × 0.4 → bigram × 0.4² → unigram), merged into the existing
  next-word results and used as the language-model term while typing a word. The weights are constants in one
  place, configurable, and tuned only with the benchmark.
- **Personal history wins**: the user's own n-grams keep their current priority. The bundled model fills gaps
  and never outranks a phrase the user actually typed.
- **Threading**: lookups run on the existing `InputLogicHandler` thread; the file is opened in the background
  with the main dictionaries.
- **Fallback**: a developer setting (debug builds only) switches the bundled model off, to compare old and new.

## Interfaces touched

| Layer | Change |
| --- | --- |
| Assets | Option A: rebuilt `main_en-GB.dict` / `main_en-US.dict`. Option B: new `assets/ngrams/` |
| Native | Option B: a read-only n-gram reader and a JNI entry point next to `BinaryDictionary` |
| `DictionaryFacilitatorImpl` | Option B: one more sub-dictionary in English groups |
| `Suggest` | none expected |
| Fret, strip, settings UI | none |
| R8 / 16 KB / JNI keep rules | New JNI class must be added to `proguard-rules.pro` and `check_r8_mapping.py` |

## Size budget

M2's bigrams add about 1.9 MB to the APK. Removing the 16 other-language dictionaries would save 11.7 MB, but they
serve secondary languages and the spell checker, so that is an owner decision.

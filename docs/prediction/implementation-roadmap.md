# Implementation roadmap (English only)

Every stage ends with benchmark results compared to the previous stage. Nothing is merged without the owner's
approval. Sinhala, Wijesekara and Singlish behaviour must stay identical (checked by tests).

| Stage | Work | Exit criteria |
| --- | --- | --- |
| **M2.0 Benchmark harness** | Instrumented test (androidTest) that drives `Suggest` with recorded key coordinates for a fixed test set; reports top-1/3/5, MRR, keystroke savings, wrong-autocorrect rate, P50/P95/P99 latency, heap. Same harness for old and new engine. | Reproducible baseline numbers for en-US and en-GB, committed with the test set |
| **M2.1 Other-language dictionaries** | Not removed: M2 found they serve secondary languages and the spell checker. Removing them is an owner decision | Decision recorded |
| **M2.2 Bigrams for English (option A)** | Build bigram lists from a licensed corpus (see `dataset-licensing.md`), rebuild `main_en-GB.dict` and `main_en-US.dict` with the app's own encoder | Fresh install shows next words for the brief's examples; benchmark top-3 next-word accuracy improves; no autocorrect regression |
| **M2.3 Decide on trigrams** | Measure the gain trigrams/4-grams would give on the test set (offline simulation) | GO/NO-GO for option B with numbers |
| **M3 Correction tuning** | Only if the benchmark shows weak spots: names, technical words, adaptive confidence | Wrong-autocorrect rate not worse; specific cases fixed |
| **M4 n-gram model (option B)** | Native reader, JNI, dictionary type, backoff, tests | P95 warm latency under 20 ms on a low-end device; accuracy gain shown |
| **M5 Personal learning tuning** | Soft penalty for reverted autocorrections; acceptance signals | Benchmark with simulated sessions |
| **M7 Fret check** | Verify In-Letter / In-Column with the new words; no flicker | Screenshot and unit tests |
| **M9 Release checks** | R8 mapping, 16 KB, smoke test, upgrade from 5.1.x, offline | All CI checks green |

## Estimated impact (to be confirmed by measurement)

| | Today | After M2.1 + M2.2 | After M4 |
| --- | --- | --- | --- |
| APK | 22.5 MB | ≈ 11–12 MB | ≈ 13–16 MB |
| English data | 4 MB uncompressed | ≈ 6–10 MB | ≈ 10–20 MB |
| RAM (PSS) | 67 MB | about the same (memory-mapped) | + a few MB of page cache while typing |
| Next-word on fresh install (en-GB) | none | bigrams | bigrams + trigrams |

The estimates assume pruned data; exact sizes depend on the corpus and pruning chosen in M2.2.

## Risks

| Risk | Mitigation |
| --- | --- |
| Corpus licence incompatible with GPL-3.0 or needing attribution we cannot give | Only sources listed as acceptable in `dataset-licensing.md`; attribution in `licenses/THIRD_PARTY_NOTICES.md` |
| Better predictions make autocorrect more aggressive | Wrong-autocorrect rate is a hard benchmark gate |
| Larger data slows dictionary loading | Memory-mapped files; cold-start latency measured |
| Rebuilding v2 dictionaries | Done with the app's own encoder (two encoder bugs fixed in M2), no external tools |
| Native changes break JNI under R8 | Keep rules and `check_r8_mapping.py` updated; release smoke test |

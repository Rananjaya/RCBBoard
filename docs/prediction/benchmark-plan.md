# Benchmark plan

Goal: compare the current and the improved English engine with numbers, on the same inputs, before any
claim is made.

## Running it

```sh
./gradlew :app:connectedDebugNoMinifyAndroidTest -PrcbboardTestBuildType=debugNoMinify \
  -Pandroid.testInstrumentationRunnerArguments.locale=en-GB     # or en-US
adb logcat -d -s RCBBoardBenchmark:I                              # the results as JSON
```

Uninstall `com.rananjaya.rcbboard.debug` first, so the run starts like a fresh install. The minified `debug` build
cannot run instrumented tests, hence `debugNoMinify` (same code and dictionaries, Java not optimised by R8).

## Harness

- An instrumented test (`app/src/androidTest`) that loads the real dictionaries through
  `DictionaryFacilitatorImpl` and calls `Suggest.getSuggestedWords` with key coordinates taken from the real
  keyboard layout, so the native spatial model is exercised.
- Touch noise: each tap is offset by a seeded random Gaussian (σ ≈ 0.25 key width), so typo behaviour is
  reproducible.
- Personal history starts empty for the "fresh install" runs, and is pre-filled from a separate training text
  for the "personalised" runs.
- Runs on: a low-end device or emulator profile (2 GB RAM), a mid-range and a high-end phone. Release
  (minified) build.

## Test data

- Held-out English sentences that were **not** used to build the n-grams (split by document, not by sentence).
- Conversational sentences (chat style) and general text, en-US and en-GB spelling.
- A fixed typo list including the brief's examples: teh, thsi, goign, becuase, recieve, tommorow, helloworld.
- A "must not correct" list: names, technical words (React, Kotlin, GitHub), URLs, e-mail addresses,
  intentionally typed rare words.

## Metrics

| Metric | Definition |
| --- | --- |
| Next-word top-1 / top-3 / top-5 | Correct next word among the first 1/3/5 predictions before typing a letter |
| MRR | Mean reciprocal rank of the correct word |
| Keystroke savings | Share of letters saved by accepting the correct suggestion as soon as it appears in the top 3 |
| Autocorrect accuracy | Share of typo words corrected to the intended word |
| Wrong-autocorrect rate | Share of correctly typed words changed by autocorrect (**gate: must not increase**) |
| Latency P50 / P95 / P99 | Time of `getSuggestedWords`, warm (after 50 calls) and cold (first call after loading) |
| Memory | PSS and native heap after typing 500 words |
| Storage | APK size and installed dictionary size |
| Battery | Energy over 10 minutes of scripted typing (Battery Historian), compared with the baseline |

## Targets (engineering targets, not promises)

- Warm P95 latency below 20 ms; cold below 100 ms.
- Wrong-autocorrect rate not higher than the baseline.
- Sinhala results identical to the baseline.

## Baseline already recorded (M1)

See `M1-existing-engine-audit.md`: 7/7 example typos corrected; no next-word predictions for en-GB on a
fresh install; 67 MB PSS. The full numeric baseline is the first output of the harness.

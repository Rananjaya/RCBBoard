# English dictionaries

Rebuilds `app/src/main/assets/dicts/main_en-GB.dict` and `main_en-US.dict` with next-word (bigram) data, the
phrase files `app/src/main/assets/phrases/en-GB.phrases` and `en-US.phrases` (trigrams: the next word after two
words), and the held-out sets of the prediction benchmark. Only English is built here; the Sinhala dictionary is never touched.
Licences and attribution: `licenses/THIRD_PARTY_NOTICES.md` ("English dictionaries and next-word data").

## Inputs

| Input | Where | Licence |
| --- | --- | --- |
| English dictionaries before next-word data | `git show 529932af:app/src/main/assets/dicts/main_en-GB.dict` (and `main_en-US.dict`) | Apache-2.0 (AOSP) |
| Tatoeba English sentences | https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2 (SHA-256 of the file used: `00e8f4618f4eadc799a55956af237ed70fdcbd7163151c81711971d71ef40302`) | CC BY 2.0 FR |

Tatoeba changes daily, so a newer download gives slightly different numbers. Keep the downloaded file outside the
repository.

## Steps

```sh
# 1. the original dictionaries (the build always starts from them, never from its own output)
mkdir -p /tmp/en-dicts && for l in en-GB en-US; do
  git show 529932af:app/src/main/assets/dicts/main_$l.dict > /tmp/en-dicts/main_$l.dict; done
bunzip2 -k eng_sentences.tsv.bz2

# 2. word pairs and benchmark sets (deterministic, about 1.5 minutes)
python3 tools/english-dictionaries/build_english_bigrams.py --corpus eng_sentences.tsv \
  --dicts /tmp/en-dicts --out /tmp/en-new --bench app/src/androidTest/assets/benchmark

# 3. compile with the app's own encoder (Ver2DictEncoder), no external tools
RCBBOARD_COMPILE_DICTS="/tmp/en-new/main_en-GB.combined=app/src/main/assets/dicts/main_en-GB.dict;/tmp/en-new/main_en-US.combined=app/src/main/assets/dicts/main_en-US.dict" \
  ./gradlew --init-script .github/ci-unit-tests.init.gradle :app:testRunTestsUnitTest \
  -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false --tests 'helium314.keyboard.tools.CompileDictionaryTool'
# (paths must be absolute: the test runs in app/; add :app:cleanTestRunTestsUnitTest before the test task, or
#  Gradle skips it as up to date when only the files changed)
```

```sh
# 4. phrase files (trigrams), from the M2 dictionaries: tune once on development data, then build
python3 tools/english-dictionaries/build_english_trigrams.py --corpus eng_sentences.tsv \
  --dicts app/src/main/assets/dicts --tune            # prints accuracy and size per pruning setting
python3 tools/english-dictionaries/build_english_trigrams.py --corpus eng_sentences.tsv \
  --dicts app/src/main/assets/dicts --out app/src/main/assets/phrases --min-count 3 --per-context 8 --bonus 12

# 5. independent test set from two public domain books (Project Gutenberg #1342 and #1661, plain text)
python3 tools/english-dictionaries/build_independent_benchmark.py --books pg1342.txt pg1661.txt \
  --dicts app/src/main/assets/dicts --bench app/src/androidTest/assets/benchmark
```

```sh
# 6. modern words from SCOWL (http://wordlist.aspell.net, 2020.12.07) into the current dictionaries, then step 3
python3 tools/english-dictionaries/add_vocabulary.py --scowl scowl-2020.12.07/final \
  --dicts app/src/main/assets/dicts --offensive ldnoobw-en.txt --out /tmp/en-vocab
```

Unit tests need JDK 21 or newer (for example Android Studio's JBR as `JAVA_HOME`).

## What the build does

- Splits the sentences by a hash of their normalised text: 10 % are held out for the benchmark and never counted.
- Counts word pairs within a sentence; punctuation ends the context, as it does while typing.
- Maps American spellings to British ones for en-GB (1,756 words, e.g. organize → organise, color → colour) when
  only the British form is an en-GB dictionary word.
- Keeps up to 12 next words per word, each seen at least 3 times and with a probability of at least 1 %.
- Never predicts names and other capitalised words (except "I"), single letters other than "a" and "I", or words
  marked possibly offensive.
- Writes the probability on the dictionary's own scale: 255 is certain, and each factor of ten less costs as many
  points as it does for single words in that dictionary (fitted, about 20).

## Phrase files

- Counted from Tatoeba buckets 10-99 (the benchmark's test sentences, buckets 0-9, are never read). With `--tune`,
  buckets 10-14 are held back as development data to choose the pruning; the final files include them.
- A phrase is kept when it was seen at least 3 times, with at most 8 next words per two-word context and a
  probability of at least 2 %. Probabilities use absolute discounting (a count c counts as c - 0.5).
- Scores use the dictionary's scale (see above) plus 12 points, because two words of context are more specific
  than one; the dictionary's bigrams then serve as the back-off for contexts the phrase file does not know.
- Same word rules as the bigrams: no names, offensive words or single letters; British spellings in en-GB.
- Format: see `PhraseDictionary.kt`. Lookups are binary searches in the file loaded into memory (about 2.7 MB per
  English locale), only for next-word predictions.

## Checks

- `v2_to_combined.py` decodes a dictionary into text. Decoding the original English dictionaries and compiling
  them again gives byte-identical files.
- `estimate_trigram_gain.py` estimates what trigram and 4-gram context would add (offline model).
- The on-device benchmark (`app/src/androidTest/.../EnglishPredictionBenchmark.kt`) measures the real engine, see
  `docs/prediction/benchmark-plan.md`.

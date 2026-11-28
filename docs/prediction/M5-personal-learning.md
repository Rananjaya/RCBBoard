# M5 – Personal learning

Branch `feature/english-prediction-engine` on top of M3 (`1e281baf`). **Not committed**, waiting for approval.
Learning applies to every language, but nothing in the Sinhala data, transliteration or ranking was changed.

## Summary

| Area | Before (M3) | M5 |
| --- | --- | --- |
| Delay before a typed word or phrase is learned | 25 s | **10 s** (correcting it within that time still cancels it) |
| Phrase predicted after one use | after 25.9 s | **after 11.3 s** |
| Learned words saved to storage | only when leaving a text field | **also 15 s after the last learned word** |
| New name capitalised by the user at a sentence start | never learned | **learned as typed** |
| Word capitalised only automatically at a sentence start (unknown word) | not learned | not learned (unchanged, avoids wrong capitals) |
| New word (not in the dictionary) | suggested after 2 uses | unchanged: 2 uses (typo protection) |
| Typo corrected right away / typed once | not learned | not learned |
| Learned words in settings | – | **"N words learned"** under Personalized suggestions |

## Changes

| File | Change |
| --- | --- |
| `DictionaryFacilitatorImpl.kt` | Learning delay 25 s → 10 s; learned words are saved 15 s after the last one (debounced, background); a new word the user capitalised at a sentence start is learned as typed |
| `dictionary/history/LearnedWords.kt` (new) | Reads the learned word count from the saved history headers (`UNIGRAM_COUNT`, written by the native library) |
| `settings/screens/TextCorrectionScreen.kt`, `values/strings.xml` | Counter under "Personalized suggestions", shown only when learning is on and something was learned |
| `test/.../LearnedWordsTest.kt` (new) | Header reading |
| `androidTest/.../EnglishPredictionBenchmark.kt` | New `learning` benchmark (below) |

No native, format or dependency change. History files keep their names and format, so existing learned words are
kept (verified by upgrade).

## Findings

- **New words need two uses**, by design of the native forgetting curve (a word not in the dictionary starts
  invisible). This is the main typo protection and was kept.
- **Phrases of known words are learned on the first use**; they were only slow (25 s) and could be lost: they
  were saved only when leaving a text field, so learning in a long conversation was lost if the process ended.
- **Recency** is already handled by the native forgetting curve (entries lose a level about every 15 days if
  unused, and are dropped when unused for long). Not changed: no measured problem.
- **Sentence-start words** that only the keyboard capitalised are still not learned when neither form is known:
  learning them would put wrong capitals in mid-sentence suggestions.

## Learning benchmark (emulator, English UK, same test on M3 and M5)

Teaches words through the real learning path, one after the other like typing:

| Check | M3 | M5 |
| --- | --- | --- |
| Seconds until "purple elephant → dances" is predicted after one use | 25.9 | **11.3** |
| New word "kasunika" offered after 1 / 2 uses | no / yes | no / yes |
| "Nimesha" capitalised by the user at a sentence start | not learned | **learned** |
| "Blorptastic" auto-capitalised at a sentence start | not learned | not learned |
| Typo corrected right away (twice) | not learned | not learned |
| Typo typed once | not learned | not learned |
| Saved to storage without leaving the field | **no** | **yes** |

Personal-history priority (M3 test): a phrase typed 3 times comes first; the learning delay only makes this
happen sooner. The fresh-install prediction benchmark does not use learning, so its M3 results apply unchanged.

## json → Jason (investigated separately, not changed)

"json" and "JSON" are not in the 2014 English dictionaries; "Jason" (frequency 112) is one letter away, so a typed
"json" is autocorrected to it. Not a scoring bug: a vocabulary gap, and "Jason" is a valid name. Options:

1. Add common technical words (JSON, API, GitHub, Kotlin, npm…) to the English dictionaries (data only,
   reproducible with the existing tools). **Recommended.**
2. Do not autocorrect an all-lowercase word to a capitalised word that needs letters added or changed (would also
   stop "londn" → "London").

Users can already stop it: undoing the autocorrection with backspace and using the word twice teaches it.

## Validation

| Check | Result |
| --- | --- |
| Unit tests | **454 passed**, 0 failed, 1 skipped (dictionary build tool) |
| Script tests | passed |
| Upgrade M3 → M5 (minified, same key) | installed, no crash; Fret style and settings kept |
| Sinhala (Singlish) on the emulator | identical suggestions to M2/M3 (යානාව, යනවා, යනවද) |
| Learned-words counter on the device | "43 words learned" after typing, no crash |
| Fret | presentation code unchanged; style kept after upgrade |
| Privacy | unchanged paths: no learning in password fields, incognito or fields that ask for it (checked before the facilitator is called); history stays in the app's private storage; the counter shows only a number |
| Minified release | 27.0 MB (same as M3); R8 689/719 classes renamed, JNI kept; 16 KB aligned; no INTERNET permission |

No physical phone was used. Emulator only.

## Remaining limitations

- New words still need two uses; one-use learning for new words would also learn typos.
- Learning is time-based only by the native forgetting curve; no per-user tuning.
- The counter counts saved words (including a few context entries); new words appear in it after the 15 s save.

## Recommendation

**GO** to commit M5. Next: add common technical words to the English dictionaries (fixes json → Jason), then
decide on 4-grams only if more accuracy is wanted.

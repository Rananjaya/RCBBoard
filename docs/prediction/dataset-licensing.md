# Datasets and licences for English n-grams

RCBBoard is GPL-3.0. Any data built into the APK must allow redistribution under, or alongside, GPL-3.0, and
its attribution must be given in `licenses/THIRD_PARTY_NOTICES.md` and the About screen.

**Used in M2:** the AOSP English dictionaries (already bundled) and Tatoeba English sentences, CC BY 2.0 FR, checked
at https://tatoeba.org/en/terms_of_use and https://tatoeba.org/en/downloads on 28 November 2026. The CC0 subset of
Tatoeba was considered to avoid attribution, but it has only 41,515 English sentences, too few. Attribution is in
`licenses/THIRD_PARTY_NOTICES.md`, which the app shows on its third-party licences screen. Every other source
below must be checked again at the source before it is used.

## Candidates

| Source | Kind of text | Licence (to verify at source) | Use |
| --- | --- | --- | --- |
| AOSP English dictionary (already bundled, `main_en-US.dict`) | Unigrams, bigrams | Apache-2.0 (from AOSP LatinIME, via HeliBoard) | Baseline; reuse its bigrams for en-GB with spelling mapping |
| Tatoeba sentences | Short everyday sentences, conversational | CC BY 2.0 FR (verified) | **Used in M2 and M3** for English bigrams and phrases (trigrams); attribution given |
| Wikipedia (English) dump | Encyclopaedic text | CC BY-SA 4.0 (one-way compatible with GPL-3.0) | Vocabulary and general n-grams; share-alike applies to derived data |
| Project Gutenberg texts (public domain in the US) | Older literature | Public domain (US); check each text | **Used in M3** only as an independent test set (#1342, #1661), not in the app |
| Google Books Ngram dataset | Book n-gram counts | CC BY 3.0 | Possible source of counts; book language, large |
| SCOWL / spelling word lists | US/UK spelling variants | Permissive (MIT-like) | Mapping en-US ↔ en-GB spellings |

## Not allowed

| Source | Why |
| --- | --- |
| Google Web 1T and lists derived from it (e.g. popular `count_1w`/`count_2w` files) | LDC licence restricts redistribution |
| COCA / iWeb n-grams | Commercial licence |
| OpenSubtitles corpora | Copyright of the subtitles is unclear |
| Reddit, Twitter/X, message or chat logs | Terms of service, privacy; private conversations are excluded by the brief |
| FUTO keyboard data or models | Excluded by the brief |
| Any text from RCBBoard users | Never collected; the app has no network access |

## Rules for M2.2

1. Record for each source: URL, version or dump date, licence text, SHA-256 of the downloaded files.
2. Keep the build script and the list of sources in `third_party/english-ngrams/` (sources not packaged in
   the APK, like the existing emoji and Sinhala dictionaries).
3. Remove personal data patterns (e-mail addresses, phone numbers, URLs) from the n-grams.
4. Drop offensive words using the existing possibly-offensive flags.
5. Add the attribution to `licenses/THIRD_PARTY_NOTICES.md`, ship the licence texts in
   `assets/third_party_licenses/`, and list them on the About screen.

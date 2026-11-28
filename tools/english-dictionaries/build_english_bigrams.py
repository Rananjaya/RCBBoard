#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Adds next-word (bigram) data to the bundled English (UK) and English (US) dictionaries, and writes the
held-out benchmark sets. Deterministic: the same inputs always give the same outputs. See README.md.

Inputs
  --corpus   Tatoeba English sentences (eng_sentences.tsv: id, language, text), CC BY 2.0 FR
  --dicts    directory with the current main_en-GB.dict and main_en-US.dict (decoded by v2_to_combined.py)
Outputs
  <out>/main_en-GB.combined, <out>/main_en-US.combined   compile with the app's encoder (README.md)
  <bench>/<locale>/next_word.tsv, typing.tsv, sentences.txt   held-out sentences only

Sentences are split by a hash of their normalised text: 10 % are held out for evaluation and never counted,
and identical sentences always fall on the same side.
"""
from __future__ import annotations

import argparse
import hashlib
import math
import os
import random
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from v2_to_combined import decode, write_combined  # noqa: E402

TOKEN = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)*")
# punctuation ends the context: the keyboard does not predict across it either
SEGMENT_BREAK = re.compile(r"[.,;:!?()\[\]{}\"“”—–…/\\]|\s-\s|^-|-$")

MIN_COUNT = 3          # a word pair must occur at least this often in the build sentences
MAX_PER_WORD = 12      # successors kept per previous word
MIN_PROBABILITY = 0.01  # P(next | previous) below this is not stored
EVAL_SHARE = 10        # percent of sentences held out

# British spellings: suffix rules from American to British. A mapping is used only when the British form is a
# word of the en-GB dictionary that the en-US dictionary does not have (so "color" -> "colour", but never a
# word that both spellings share).
US_TO_GB_RULES = [
    ("ization", "isation"), ("izations", "isations"), ("ize", "ise"), ("izes", "ises"), ("ized", "ised"),
    ("izing", "ising"), ("izer", "iser"), ("izers", "isers"), ("yze", "yse"), ("yzed", "ysed"), ("yzes", "yses"),
    ("yzing", "ysing"), ("or", "our"), ("ors", "ours"), ("ored", "oured"), ("oring", "ouring"),
    ("orful", "ourful"), ("orite", "ourite"), ("orites", "ourites"), ("er", "re"), ("ers", "res"),
    ("og", "ogue"), ("ogs", "ogues"), ("eled", "elled"), ("eling", "elling"), ("eler", "eller"),
    ("elers", "ellers"), ("ense", "ence"), ("enses", "ences"), ("ment", "ement"),
]


def normalise(text: str) -> str:
    return text.replace("’", "'").replace("‘", "'")


def split_key(text: str) -> int:
    key = re.sub(r"[^a-z' ]", "", normalise(text).lower()).strip()
    return int(hashlib.sha256(key.encode()).hexdigest(), 16) % 100


def us_to_gb_map(us_words: set[str], gb_words: set[str]) -> dict[str, str]:
    mapping = {}
    for word in us_words:
        lower = word.lower()
        if lower != word:
            continue
        for us, gb in US_TO_GB_RULES:
            if lower.endswith(us):
                candidate = lower[: -len(us)] + gb
                if candidate in gb_words and candidate not in us_words:
                    mapping[lower] = candidate
                    break
    return mapping


class Locale:
    def __init__(self, name: str, path: str, spelling: dict[str, str] | None):
        self.name = name
        self.attributes, self.words = decode(path)
        self.by_word = {w["word"]: w for w in self.words}
        self.spelling = spelling or {}

    def canonical(self, token: str) -> str | None:
        """the dictionary word a corpus token stands for, None if the dictionary does not know it"""
        token = self.spelling.get(token.lower(), token) if token.lower() in self.spelling else token
        for candidate in (token, token.lower()):
            entry = self.by_word.get(candidate)
            if entry is not None and not entry["not_a_word"]:
                return candidate
        return None

    def segments(self, sentence: str) -> list[list[str | None]]:
        result = []
        for part in SEGMENT_BREAK.split(normalise(sentence)):
            tokens = [self.canonical(t) for t in TOKEN.findall(part)]
            if tokens:
                result.append(tokens)
        return result


def counted_segments(locale: Locale, sentence: str) -> list[list[str | None]]:
    """like Locale.segments, for counting: a word written with a capital in the middle of a sentence whose dictionary
    form is lower case is not used, because it is a name ("Tom" would otherwise count as the word "tom")"""
    result = []
    first = True
    for part in SEGMENT_BREAK.split(normalise(sentence)):
        tokens = []
        for raw in TOKEN.findall(part):
            word = locale.canonical(raw)
            if word is not None and not first and raw[0].isupper() and word[0].islower():
                word = None
            tokens.append(word)
            first = False
        if tokens:
            result.append(tokens)
    return result


def fit_frequency_scale(locale: Locale, unigrams: Counter) -> float:
    """slope of a least squares fit of the dictionary's 0-255 frequency against log10 of the corpus probability:
    how many frequency points one factor of ten in probability is worth in this dictionary"""
    total = sum(unigrams.values())
    points = [(math.log10(count / total), locale.by_word[w]["f"]) for w, count in unigrams.most_common(5000)
              if count >= 20 and locale.by_word[w]["f"] > 0]
    n = len(points)
    mx = sum(x for x, _ in points) / n
    my = sum(y for _, y in points) / n
    return sum((x - mx) * (y - my) for x, y in points) / sum((x - mx) ** 2 for x, _ in points)


def allowed_successor(locale: Locale, word: str) -> bool:
    entry = locale.by_word[word]
    if entry["possibly_offensive"] or entry["not_a_word"]:
        return False
    if word[0].isupper() and not (word == "I" or word.startswith("I'")):
        return False  # names (the corpus overuses "Tom" and "Mary") and other proper nouns are not predicted
    if len(word) == 1 and word not in ("a", "I"):
        return False
    return True


def build(locale: Locale, sentences: list[str], existing_bigrams: dict[str, dict[str, int]]) -> dict:
    unigrams: Counter = Counter()
    contexts: Counter = Counter()
    pairs: dict[str, Counter] = defaultdict(Counter)
    for sentence in sentences:
        for tokens in counted_segments(locale, sentence):
            for i, word in enumerate(tokens):
                if word is None:
                    continue
                unigrams[word] += 1
                if i + 1 < len(tokens) and tokens[i + 1] is not None:
                    contexts[word] += 1
                    pairs[word][tokens[i + 1]] += 1
    # 255 is probability 1 in the dictionary format; each factor of ten less costs `slope` points
    slope = fit_frequency_scale(locale, unigrams)

    bigrams: dict[str, dict[str, int]] = defaultdict(dict)
    for previous, successors in pairs.items():
        if locale.by_word[previous]["possibly_offensive"]:
            continue
        kept = 0
        for word, count in successors.most_common():
            if kept >= MAX_PER_WORD or count < MIN_COUNT:
                break
            probability = count / contexts[previous]
            if probability < MIN_PROBABILITY or not allowed_successor(locale, word):
                continue
            frequency = round(255 + slope * math.log10(probability))
            # the engine only predicts words that have a pair with the previous word, and never ranks a pair
            # below the word's own frequency, so a pair at the word's frequency still makes it a prediction
            bigrams[previous][word] = max(frequency, locale.by_word[word]["f"] + 1)
            kept += 1
    # the bigrams already in the AOSP dictionary stay (as words of this locale), the higher value wins
    for previous, successors in existing_bigrams.items():
        previous_word = locale.canonical(previous)
        if previous_word is None or locale.by_word[previous_word]["possibly_offensive"]:
            continue
        for word, frequency in successors.items():
            target = locale.canonical(word)
            if target is None or not allowed_successor(locale, target):
                continue
            if bigrams[previous_word].get(target, 0) < frequency:
                bigrams[previous_word][target] = frequency
    stats = dict(scale=f"f = 255 + {slope:.1f} * log10(p)", corpus_tokens=sum(unigrams.values()),
                 words_with_bigrams=sum(1 for v in bigrams.values() if v), bigrams=sum(len(v) for v in bigrams.values()))
    return dict(bigrams=bigrams, stats=stats)


def write_dictionary(locale: Locale, bigrams: dict[str, dict[str, int]], path: str) -> None:
    attributes = dict(locale.attributes)
    attributes["version"] = str(int(attributes.get("version", "0")) + 1)
    attributes["description"] = attributes.get("description", "") + " with next-word data from Tatoeba"
    for entry in locale.words:
        entry["bigrams"] = sorted(bigrams.get(entry["word"], {}).items(), key=lambda kv: -kv[1])
    with open(path, "w", encoding="utf-8") as out:
        write_combined(attributes, locale.words, out)


def write_benchmark(locale: Locale, sentences: list[str], directory: str, seed: int) -> None:
    rng = random.Random(seed)
    os.makedirs(directory, exist_ok=True)
    next_word, typing, full = [], [], []
    for sentence in sentences:
        segments = locale.segments(sentence)
        for tokens in segments:
            for i in range(1, len(tokens)):
                context = tokens[max(0, i - 3):i]
                if tokens[i] is None or None in context:
                    continue
                next_word.append("\t".join([" ".join(context), tokens[i]]))
                if tokens[i].isalpha() and tokens[i].islower() and len(tokens[i]) >= 2:
                    typing.append("\t".join([" ".join(context), tokens[i]]))
        if len(segments) == 1 and None not in segments[0] and 4 <= len(segments[0]) <= 12:
            full.append(" ".join(segments[0]))
    for name, rows, count in (("next_word.tsv", next_word, 3000), ("typing.tsv", typing, 2000),
                              ("sentences.txt", full, 200)):
        sample = rng.sample(rows, min(count, len(rows)))
        with open(os.path.join(directory, name), "w", encoding="utf-8") as out:
            out.write("\n".join(sample) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", required=True)
    parser.add_argument("--dicts", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--bench", required=True)
    args = parser.parse_args()

    build_sentences, eval_sentences = [], []
    with open(args.corpus, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or parts[1] != "eng":
                continue
            (eval_sentences if split_key(parts[2]) < EVAL_SHARE else build_sentences).append(parts[2])
    print(f"sentences: {len(build_sentences)} for building, {len(eval_sentences)} held out")

    us = Locale("en-US", os.path.join(args.dicts, "main_en-US.dict"), None)
    gb_words = {w["word"] for w in decode(os.path.join(args.dicts, "main_en-GB.dict"))[1]}
    spelling = us_to_gb_map(set(us.by_word), gb_words)
    gb = Locale("en-GB", os.path.join(args.dicts, "main_en-GB.dict"), spelling)
    print(f"US to UK spellings: {len(spelling)}, e.g. " +
          ", ".join(f"{k}->{v}" for k, v in sorted(spelling.items())[:8]))

    aosp_bigrams = {w["word"]: dict(w["bigrams"]) for w in us.words if w["bigrams"]}
    os.makedirs(args.out, exist_ok=True)
    for locale, seed in ((us, 1), (gb, 2)):
        result = build(locale, build_sentences, aosp_bigrams)
        write_dictionary(locale, result["bigrams"], os.path.join(args.out, f"main_{locale.name}.combined"))
        write_benchmark(locale, eval_sentences, os.path.join(args.bench, locale.name), seed)
        print(locale.name, result["stats"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

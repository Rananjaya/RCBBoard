#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Builds the English phrase files (trigram next-word data) and tunes their pruning. See README.md.

A phrase file answers one question: after these two words, which words come next, and how likely are they?
The keyboard asks it only for next-word predictions (nothing typed yet), and merges the answers with the
dictionary's bigram predictions and the user's own history (PhraseDictionary.kt).

Data split (same hash as build_english_bigrams.py, so the M2 test sentences are never used here):
  buckets 0-9    held out: the benchmark's test sets (never read by this script)
  buckets 10-14  development set for tuning, when --tune is given (then not counted)
  buckets 15-99  counted

Usage
  python3 build_english_trigrams.py --corpus eng_sentences.tsv --dicts app/src/main/assets/dicts --tune
  python3 build_english_trigrams.py --corpus eng_sentences.tsv --dicts app/src/main/assets/dicts \
      --out app/src/main/assets/phrases [--min-count 3 --per-context 8 --bonus 8]
The final files are built from buckets 10-99 (development data included, test data never).
"""
from __future__ import annotations

import argparse
import math
import os
import random
import struct
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_english_bigrams import (EVAL_SHARE, Locale, allowed_successor, counted_segments,  # noqa: E402
                                   decode, fit_frequency_scale, split_key, us_to_gb_map)

DEV_END = 15  # buckets EVAL_SHARE..DEV_END-1 are the development set
MAGIC = b"RCBP"
VERSION = 2  # 2: also next words after three words (4-grams)
DISCOUNT = 0.5  # absolute discounting: a phrase seen c times counts as c - 0.5, so rare phrases are trusted less


def read_sentences(path: str, tune: bool) -> tuple[list[str], list[str]]:
    counted, dev = [], []
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or parts[1] != "eng":
                continue
            bucket = split_key(parts[2])
            if bucket < EVAL_SHARE:
                continue  # test data
            (dev if tune and bucket < DEV_END else counted).append(parts[2])
    return counted, dev


def segments(locale: Locale, sentence: str) -> list[list[str | None]]:
    return counted_segments(locale, sentence)  # names are not counted (shared with the bigram build)


def count(locale: Locale, sentences: list[str]):
    unigrams: Counter = Counter()
    trigrams: dict[tuple[str, str], Counter] = defaultdict(Counter)
    quadgrams: dict[tuple[str, str, str], Counter] = defaultdict(Counter)
    for sentence in sentences:
        for tokens in segments(locale, sentence):
            for i, word in enumerate(tokens):
                if word is not None:
                    unigrams[word] += 1
                if i >= 2 and None not in tokens[i - 2:i + 1]:
                    trigrams[(tokens[i - 2], tokens[i - 1])][word] += 1
                if i >= 3 and None not in tokens[i - 3:i + 1]:
                    quadgrams[(tokens[i - 3], tokens[i - 2], tokens[i - 1])][word] += 1
    return unigrams, trigrams, quadgrams


def select(locale: Locale, trigrams, slope: float, min_count: int, per_context: int, bonus: int,
           min_probability: float = 0.02) -> dict[tuple[str, str], list[tuple[str, int]]]:
    """the stored phrases: for each two-word context, its most likely next words with a 0-255 score"""
    result = {}
    for context, successors in trigrams.items():
        total = sum(successors.values())
        if total < min_count:
            continue
        kept = []
        for word, c in successors.most_common():
            if len(kept) >= per_context or c < min_count:
                break
            probability = (c - DISCOUNT) / total
            if probability < min_probability or not allowed_successor(locale, word):
                continue
            # same scale as the dictionary's bigram frequencies (255 = certain), plus a bonus because two words of
            # context are more specific than the one word a bigram uses
            score = round(255 + slope * math.log10(probability)) + bonus
            kept.append((word, max(1, min(255, score))))
        if kept:
            result[context] = kept
    return result


def key(ids: dict[str, int], context: tuple) -> int:
    """two words: id1 << 32 | id2; three words: id1 << 42 | id2 << 21 | id3 (ids < 2^21)"""
    if len(context) == 2:
        return (ids[context[0]] << 32) | ids[context[1]]
    return (ids[context[0]] << 42) | (ids[context[1]] << 21) | ids[context[2]]


def write(phrases: dict, path: str, quad: dict | None = None) -> int:
    quad = quad or {}
    vocab = sorted({w for table in (phrases, quad) for ctx, entries in table.items()
                    for w in (*ctx, *(e[0] for e in entries))}, key=lambda w: w.encode("utf-8"))
    assert len(vocab) < 1 << 21
    ids = {w: i for i, w in enumerate(vocab)}
    encoded = [w.encode("utf-8") for w in vocab]
    with open(path, "wb") as out:
        out.write(MAGIC + struct.pack(">HH", VERSION, 0))
        out.write(struct.pack(">I", len(vocab)))
        offset = 0
        for word in encoded:
            out.write(struct.pack(">I", offset))
            offset += len(word)
        out.write(struct.pack(">I", offset))
        out.write(b"".join(encoded))
        for table in (phrases, quad):  # section: u32 contexts, u32 entries, keys, starts, entries
            contexts = sorted(table, key=lambda c: key(ids, c))
            entries, starts = [], []
            for context in contexts:
                starts.append(len(entries))
                entries.extend((ids[w] << 8) | score for w, score in table[context])
            starts.append(len(entries))
            out.write(struct.pack(">II", len(contexts), len(entries)))
            out.write(struct.pack(f">{len(contexts)}Q", *(key(ids, c) for c in contexts)))
            out.write(struct.pack(f">{len(starts)}I", *starts))
            out.write(struct.pack(f">{len(entries)}I", *entries))
    return os.path.getsize(path)


def bigram_predictions(path: str) -> dict[str, list[tuple[str, int]]]:
    """what the dictionary predicts after one word, with its score (the M2 bigrams)"""
    _, words = decode(path)
    return {w["word"]: w["bigrams"] for w in words if w["bigrams"]}


def evaluate(locale: Locale, dev: list[str], bigrams, phrases, limit: int = 20000, seed: int = 5, quad=None) -> dict:
    """cases with three words of context (so 4-grams can help); the trigram-only result is the comparison"""
    quad = quad or {}
    cases = []
    for sentence in dev:
        for tokens in segments(locale, sentence):
            for i in range(3, len(tokens)):
                if None not in tokens[i - 3:i + 1]:
                    cases.append((tokens[i - 3], tokens[i - 2], tokens[i - 1], tokens[i]))
    cases = random.Random(seed).sample(cases, min(limit, len(cases)))
    hits = Counter()
    for w0, w1, w2, expected in cases:
        scores: dict[str, int] = {}
        for word, score in bigrams.get(w2, []):
            scores[word] = max(scores.get(word, 0), score)
        for word, score in phrases.get((w1, w2), []):
            scores[word] = max(scores.get(word, 0), score)
        for word, score in quad.get((w0, w1, w2), []):
            scores[word] = max(scores.get(word, 0), score)
        ranked = sorted(scores, key=lambda w: -scores[w])
        for k in (1, 3, 5):
            if expected in ranked[:k]:
                hits[k] += 1
    return {k: hits[k] / len(cases) for k in (1, 3, 5)} | {"cases": len(cases)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", required=True)
    parser.add_argument("--dicts", required=True, help="directory with the M2 main_en-GB.dict and main_en-US.dict")
    parser.add_argument("--out")
    parser.add_argument("--tune", action="store_true")
    parser.add_argument("--min-count", type=int, default=3)
    parser.add_argument("--per-context", type=int, default=8)
    parser.add_argument("--bonus", type=int, default=8)
    parser.add_argument("--quad-min-count", type=int, default=3)
    parser.add_argument("--quad-per-context", type=int, default=8)
    parser.add_argument("--quad-bonus", type=int, default=20)
    args = parser.parse_args()

    counted, dev = read_sentences(args.corpus, args.tune)
    print(f"sentences counted: {len(counted)}, development: {len(dev)}")
    us = Locale("en-US", os.path.join(args.dicts, "main_en-US.dict"), None)
    gb_words = {w["word"] for w in decode(os.path.join(args.dicts, "main_en-GB.dict"))[1]}
    gb = Locale("en-GB", os.path.join(args.dicts, "main_en-GB.dict"), us_to_gb_map(set(us.by_word), gb_words))

    for locale in (gb, us):
        unigrams, trigrams, quadgrams = count(locale, counted)
        slope = fit_frequency_scale(locale, unigrams)
        phrases = select(locale, trigrams, slope, args.min_count, args.per_context, args.bonus)
        if args.tune:
            bigrams = bigram_predictions(os.path.join(args.dicts, f"main_{locale.name}.dict"))
            base = evaluate(locale, dev, bigrams, phrases)
            size = write(phrases, "/tmp/rcbboard-phrases-tune.bin")
            print(f"{locale.name} trigrams (M3): top1 {base[1]:.3f} top3 {base[3]:.3f} top5 {base[5]:.3f} "
                  f"size {size / 1024:.0f} KB ({base['cases']} cases with 3 words of context)")
            for min_count in (3, 5):
                for per_context in (4, 8):
                    for bonus in (0, 10, 20, 30):
                        quad = select(locale, quadgrams, slope, min_count, per_context, bonus)
                        size = write(phrases, "/tmp/rcbboard-phrases-tune.bin", quad)
                        r = evaluate(locale, dev, bigrams, phrases, quad=quad)
                        print(f"{locale.name} 4-gram min {min_count} per {per_context} bonus {bonus:2}: top1 {r[1]:.3f} "
                              f"top3 {r[3]:.3f} top5 {r[5]:.3f} size {size / 1024:6.0f} KB contexts {len(quad)}")
        if args.out:
            os.makedirs(args.out, exist_ok=True)
            quad = select(locale, quadgrams, slope, args.quad_min_count, args.quad_per_context, args.quad_bonus)
            size = write(phrases, os.path.join(args.out, f"{locale.name}.phrases"), quad)
            print(f"{locale.name}: {len(phrases)} two-word and {len(quad)} three-word contexts, "
                  f"{sum(map(len, phrases.values())) + sum(map(len, quad.values()))} phrases, {size / 1024:.0f} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

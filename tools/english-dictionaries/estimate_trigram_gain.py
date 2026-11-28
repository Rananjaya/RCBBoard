#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Offline estimate of what trigram (and 4-gram) context would add to English next-word prediction.

Uses the same Tatoeba build / held-out split and the same pruning as build_english_bigrams.py, ranks candidates
by conditional probability with simple backoff (4-gram -> trigram -> bigram), and reports top-1/3/5 on held-out
contexts. This is a model of the ranking, not the keyboard: the on-device benchmark measures the real engine.

Usage: python3 estimate_trigram_gain.py eng_sentences.tsv ../../app/src/main/assets/dicts/main_en-GB.dict
"""
from __future__ import annotations

import random
import sys
from collections import Counter, defaultdict

from build_english_bigrams import EVAL_SHARE, MAX_PER_WORD, MIN_COUNT, MIN_PROBABILITY, Locale, allowed_successor, split_key


def main() -> int:
    corpus, dictionary = sys.argv[1], sys.argv[2]
    locale = Locale("en", dictionary, None)
    build, held_out = [], []
    for line in open(corpus, encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 3:
            (held_out if split_key(parts[2]) < EVAL_SHARE else build).append(parts[2])

    counts = [defaultdict(Counter) for _ in range(4)]  # counts[n][context of n words][next word]
    for sentence in build:
        for tokens in locale.segments(sentence):
            for i in range(1, len(tokens)):
                if tokens[i] is None:
                    continue
                for n in (1, 2, 3):
                    context = tokens[max(0, i - n):i]
                    if len(context) == n and None not in context:
                        counts[n][tuple(context)][tokens[i]] += 1

    def candidates(n: int, context: tuple) -> list[tuple[str, float]]:
        successors = counts[n].get(context)
        if not successors:
            return []
        total = sum(successors.values())
        kept = [(w, c / total) for w, c in successors.most_common()
                if c >= MIN_COUNT and c / total >= MIN_PROBABILITY and allowed_successor(locale, w)]
        return kept[:MAX_PER_WORD]

    def predict(context: list[str], order: int) -> list[str]:
        result: list[str] = []
        for n in range(min(order, len(context)), 0, -1):  # longest context first, back off to shorter ones
            for word, _ in candidates(n, tuple(context[-n:])):
                if word not in result:
                    result.append(word)
        return result

    rng = random.Random(3)
    cases = []
    for sentence in held_out:
        for tokens in locale.segments(sentence):
            for i in range(1, len(tokens)):
                context = tokens[max(0, i - 3):i]
                if tokens[i] is not None and None not in context:
                    cases.append((context, tokens[i]))
    cases = rng.sample(cases, min(20000, len(cases)))
    for order, name in ((1, "bigram (M2 dictionaries)"), (2, "+ trigram"), (3, "+ 4-gram")):
        hits = Counter()
        for context, expected in cases:
            predicted = predict(context, order)
            for k in (1, 3, 5):
                if expected in predicted[:k]:
                    hits[k] += 1
        n = len(cases)
        print(f"{name:26} top1 {hits[1] / n:.3f}  top3 {hits[3] / n:.3f}  top5 {hits[5] / n:.3f}   ({n} held-out contexts)")
    sizes = {n: sum(min(MAX_PER_WORD, len(candidates(n, c))) for c in counts[n]) for n in (1, 2, 3)}
    print(f"entries after pruning: bigram {sizes[1]}, trigram {sizes[2]}, 4-gram {sizes[3]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

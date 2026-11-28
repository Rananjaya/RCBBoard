#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Writes an independent next-word test set from public domain books, so benchmark results are not only measured on
text like the Tatoeba sentences the dictionaries are built from (see README.md).

Books used: Project Gutenberg #1342 (Pride and Prejudice) and #1661 (The Adventures of Sherlock Holmes), public domain
in the USA; only their text between the START and END markers is used, without the Project Gutenberg licence.

Usage: python3 build_independent_benchmark.py --books pg1342.txt pg1661.txt --dicts <dir with main_en-*.dict>
           --bench app/src/androidTest/assets/benchmark
"""
from __future__ import annotations

import argparse
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_english_bigrams import Locale, decode, us_to_gb_map  # noqa: E402


def sentences(path: str) -> list[str]:
    text = open(path, encoding="utf-8-sig").read().replace("\r", "")
    start = text.index("\n", text.index("*** START OF")) + 1
    body = text[start:text.index("*** END OF")]
    body = re.sub(r"\[Illustration[^\]]*\]", " ", body)
    body = re.sub(r"\s+", " ", body.replace("_", ""))
    return [s.strip() for s in re.split(r"(?<=[.!?])\s+", body) if len(s.split()) >= 3]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--books", nargs="+", required=True)
    parser.add_argument("--dicts", required=True)
    parser.add_argument("--bench", required=True)
    args = parser.parse_args()
    text = [s for book in args.books for s in sentences(book)]
    us = Locale("en-US", os.path.join(args.dicts, "main_en-US.dict"), None)
    gb_words = {w["word"] for w in decode(os.path.join(args.dicts, "main_en-GB.dict"))[1]}
    gb = Locale("en-GB", os.path.join(args.dicts, "main_en-GB.dict"), us_to_gb_map(set(us.by_word), gb_words))
    for locale, seed in ((us, 11), (gb, 12)):
        cases = []
        for sentence in text:
            for tokens in locale.segments(sentence):
                for i in range(1, len(tokens)):
                    context = tokens[max(0, i - 3):i]
                    if tokens[i] is not None and None not in context:
                        cases.append("\t".join([" ".join(context), tokens[i]]))
        sample = random.Random(seed).sample(cases, min(3000, len(cases)))
        with open(os.path.join(args.bench, locale.name, "independent_next_word.tsv"), "w", encoding="utf-8") as out:
            out.write("\n".join(sample) + "\n")
        print(f"{locale.name}: {len(text)} sentences, {len(cases)} contexts, {len(sample)} written")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

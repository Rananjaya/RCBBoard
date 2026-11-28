#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Adds modern everyday and technical words from SCOWL to the bundled English dictionaries (see README.md).

SCOWL (Spell Checker Oriented Word Lists, http://wordlist.aspell.net) sorts words by how common they are (sizes
10 to 95). Words up to --max-size that a dictionary does not know yet are added: the common English lists for both
locales, plus the American lists for en-US and the British (-ise) lists for en-GB, so the spellings stay apart.
SCOWL's capitalisation is kept (GitHub, COVID, iPhone).

A new word gets the median frequency of the words of the same SCOWL list and size that the dictionary already has,
minus --below, so it never outranks the established words it is listed with. Words in the LDNOOBW English list are
marked possibly offensive (hidden while "Block offensive words" is on, like the dictionary's own). Everything else
in the dictionaries (words, frequencies, flags, shortcuts, bigrams) is kept.

Usage: python3 add_vocabulary.py --scowl scowl-2020.12.07/final --dicts app/src/main/assets/dicts \
           --offensive ldnoobw-en.txt --out /tmp/en-vocab
then compile the .combined files with CompileDictionaryTool (README.md).
"""
from __future__ import annotations

import argparse
import os
import re
import statistics
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from v2_to_combined import decode, write_combined  # noqa: E402

KINDS = ("words", "upper", "abbreviations", "proper-names")
LISTS = {"en-US": ("english", "american"), "en-GB": ("english", "british")}
WORD = re.compile(r"[A-Za-z]+")  # no apostrophes, hyphens, digits or accents
SHADOW_MARGIN = 40  # a word one typo away that is this much more frequent makes a new word a typo trap


def edits1(word: str) -> set[str]:
    """all strings one deletion, transposition, replacement or insertion (a-z) away from word (lower case)"""
    letters = "abcdefghijklmnopqrstuvwxyz"
    splits = [(word[:i], word[i:]) for i in range(len(word) + 1)]
    return ({a + b[1:] for a, b in splits if b} | {a + b[1] + b[0] + b[2:] for a, b in splits if len(b) > 1}
            | {a + c + b[1:] for a, b in splits if b for c in letters} | {a + c + b for a, b in splits for c in letters})


def useful(word: str, kind: str) -> bool:
    """short lower case words and abbreviations ("io", "tm", "ot") are mostly typos of common words, not new words"""
    if kind == "abbreviations":
        return word.isupper() and len(word) >= 3  # API, COVID
    return len(word) >= 4 or not word.islower()


def scowl_words(final: str, prefixes: tuple[str, ...], max_size: int) -> dict[str, tuple[str, int]]:
    """word -> (kind, smallest size it is listed in)"""
    result: dict[str, tuple[str, int]] = {}
    for name in sorted(os.listdir(final)):
        match = re.fullmatch(r"([a-z_]+)-([a-z-]+)\.(\d+)", name)
        if not match or match.group(1) not in prefixes or match.group(2) not in KINDS:
            continue
        size = int(match.group(3))
        if size > max_size:
            continue
        with open(os.path.join(final, name), encoding="latin-1") as f:
            for line in f:
                word = line.strip()
                if len(word) >= 2 and WORD.fullmatch(word) and (word not in result or result[word][1] > size):
                    result[word] = (match.group(2), size)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scowl", required=True, help="the final directory of SCOWL")
    parser.add_argument("--dicts", required=True)
    parser.add_argument("--offensive", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--max-size", type=int, default=50)
    parser.add_argument("--below", type=int, default=10)
    args = parser.parse_args()
    offensive = {w.strip().lower() for w in open(args.offensive, encoding="utf-8") if w.strip()}
    os.makedirs(args.out, exist_ok=True)

    for locale, prefixes in LISTS.items():
        attributes, words = decode(os.path.join(args.dicts, f"main_{locale}.dict"))
        known = {w["word"].lower(): w for w in words}
        candidates = scowl_words(args.scowl, prefixes, args.max_size)
        by_group = defaultdict(list)  # (kind, size) -> frequencies of words the dictionary has
        for word, group in candidates.items():
            if word.lower() in known:
                by_group[group].append(known[word.lower()]["f"])
        added = []
        shadowed = 0
        for word, group in sorted(candidates.items()):
            if word.lower() in known or len(by_group[group]) < 20 or not useful(word, group[0]):
                continue
            frequency = max(1, round(statistics.median(by_group[group])) - args.below)
            # a typo of a much more common word must not become a valid word (it would stop being autocorrected)
            if any(known[n]["f"] >= frequency + SHADOW_MARGIN for n in edits1(word.lower()) if n in known):
                shadowed += 1
                continue
            entry = dict(word=word, f=frequency, not_a_word=False, possibly_offensive=word.lower() in offensive,
                         shortcuts=[], bigrams=[])
            words.append(entry)
            known[word.lower()] = entry
            added.append(word)
        attributes["version"] = str(int(attributes.get("version", "0")) + 1)
        attributes["description"] = attributes["description"].replace(" with next-word data from Tatoeba", "") + \
            " with next-word data from Tatoeba and words from SCOWL"
        with open(os.path.join(args.out, f"main_{locale}.combined"), "w", encoding="utf-8") as out:
            write_combined(attributes, words, out)
        flagged = sum(1 for w in added if w.lower() in offensive)
        print(f"{locale}: {len(added)} words added ({flagged} marked possibly offensive, {shadowed} skipped as typo "
              f"traps), e.g. "
              + ", ".join(w for w in added if w in ("GitHub", "Kotlin", "API", "selfie", "hashtag", "emoji",
                                                    "blockchain", "podcast", "smartphone", "COVID", "iPhone")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

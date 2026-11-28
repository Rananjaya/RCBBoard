#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Decodes a read-only (version 2, format 202) AOSP/HeliBoard dictionary into the editable .combined text format.

Words, frequencies, the not-a-word and possibly-offensive flags, shortcuts and bigrams are kept, so the output
can be edited and compiled again with the app's own encoder (see README.md). Only dictionaries without a code
point table are supported (all bundled English dictionaries).

Usage: python3 v2_to_combined.py main_en-GB.dict main_en-GB.combined
"""
from __future__ import annotations

import struct
import sys

MAGIC = bytes.fromhex("9bc13afe")
TERMINATOR = 0x1F


class Reader:
    def __init__(self, data: bytes):
        self.b = data

    def code_point(self, pos: int) -> tuple[int | None, int]:
        first = self.b[pos]
        if first == TERMINATOR:
            return None, pos + 1
        if first < 0x20:
            return (first << 16) | (self.b[pos + 1] << 8) | self.b[pos + 2], pos + 3
        return first, pos + 1

    def string(self, pos: int) -> tuple[str, int]:
        chars = []
        while True:
            cp, pos = self.code_point(pos)
            if cp is None:
                return "".join(chars), pos
            chars.append(chr(cp))

    def uint(self, pos: int, size: int) -> int:
        return int.from_bytes(self.b[pos:pos + size], "big")


def decode(path: str) -> tuple[dict[str, str], list[dict]]:
    data = open(path, "rb").read()
    if data[:4] != MAGIC:
        raise ValueError(f"{path} is not a dictionary")
    version = struct.unpack(">H", data[4:6])[0]
    if version != 202:
        raise ValueError(f"{path}: only format 202 is supported, not {version}")
    header_size = struct.unpack(">I", data[8:12])[0]
    r = Reader(data)
    attributes = {}
    pos = 12
    while pos < header_size:
        key, pos = r.string(pos)
        value, pos = r.string(pos)
        attributes[key] = value
    if "codePointTable" in attributes:
        raise ValueError("dictionaries with a code point table are not supported")

    words: list[dict] = []
    by_position: dict[int, dict] = {}

    def read_array(pos: int, prefix: str) -> None:
        first = data[pos]
        pos += 1
        count = first if first < 0x80 else ((first & 0x7F) << 8) | data[pos]
        if first >= 0x80:
            pos += 1
        for _ in range(count):
            node_pos = pos
            flags = data[pos]
            pos += 1
            if flags & 0x20:
                chars, pos = r.string(pos)
            else:
                cp, pos = r.code_point(pos)
                chars = chr(cp)
            word = prefix + chars
            probability = None
            if flags & 0x10:
                probability = data[pos]
                pos += 1
            children = None
            size = {0x40: 1, 0x80: 2, 0xC0: 3}.get(flags & 0xC0, 0)
            if size:
                children = pos + r.uint(pos, size)
                pos += size
            shortcuts = []
            if flags & 0x08:
                end = pos + r.uint(pos, 2)
                pos += 2
                while pos < end:
                    shortcut_flags = data[pos]
                    target, pos = r.string(pos + 1)
                    shortcuts.append((target, shortcut_flags & 0x0F))
            bigram_targets = []
            if flags & 0x04:
                while True:
                    bigram_flags = data[pos]
                    pos += 1
                    size = {0x10: 1, 0x20: 2, 0x30: 3}[bigram_flags & 0x30]
                    origin = pos
                    offset = r.uint(pos, size)
                    pos += size
                    target = origin - offset if bigram_flags & 0x40 else origin + offset
                    bigram_targets.append((target, bigram_flags & 0x0F))
                    if not bigram_flags & 0x80:
                        break
            if flags & 0x10:
                entry = dict(word=word, f=probability, not_a_word=bool(flags & 0x02),
                             possibly_offensive=bool(flags & 0x01), shortcuts=shortcuts, bigram_targets=bigram_targets)
                words.append(entry)
                by_position[node_pos] = entry
            if children is not None:
                read_array(children, word)

    sys.setrecursionlimit(10000)
    read_array(header_size, "")

    # bigram probabilities are stored as 4-bit steps between the target's unigram frequency and 255
    # (BinaryDictEncoderUtils.getBigramFrequencyDiff); this restores the frequency the encoder started from
    for entry in words:
        bigrams = []
        for target_pos, step in entry.pop("bigram_targets"):
            target = by_position[target_pos]
            unigram = target["f"]
            step_size = (255 - unigram) / (1.5 + 15)
            # + 1 so that the encoder's rounding (firstStepStart = 1 + unigram + step / 2) gives the same step back
            bigrams.append((target["word"], min(255, int(unigram + (step + 1) * step_size + 1))))
        entry["bigrams"] = bigrams
    return attributes, words


def write_combined(attributes: dict[str, str], words: list[dict], out) -> None:
    out.write(",".join(f"{k}={v}" for k, v in attributes.items()) + "\n")
    for entry in words:
        line = f" word={entry['word']},f={entry['f']}"
        if entry["not_a_word"]:
            line += ",not_a_word=true"
        if entry["possibly_offensive"]:
            line += ",possibly_offensive=true"
        out.write(line + "\n")
        for target, f in entry["shortcuts"]:
            out.write(f"  shortcut={target},f={'whitelist' if f == 15 else f}\n")
        for target, f in entry["bigrams"]:
            out.write(f"  bigram={target},f={f}\n")


if __name__ == "__main__":
    attributes, words = decode(sys.argv[1])
    with open(sys.argv[2], "w", encoding="utf-8") as out:
        write_combined(attributes, words, out)
    print(f"{sys.argv[1]}: {len(words)} words, {sum(len(w['bigrams']) for w in words)} bigrams, "
          f"header {attributes}")

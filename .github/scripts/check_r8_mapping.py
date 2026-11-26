#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check the release R8 mapping against classes actually present in the APK DEX."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

APP_PACKAGES = ("helium314.keyboard.", "com.android.inputmethod.")
JNI_CLASSES = (
    "com.android.inputmethod.latin.BinaryDictionary",
    "com.android.inputmethod.latin.utils.BinaryDictionaryUtils",
    "com.android.inputmethod.latin.DicTraverseSession",
    "com.android.inputmethod.keyboard.ProximityInfo",
    "com.android.inputmethod.latin.utils.WordInputEventForPersonalization",
)


class MappingError(Exception):
    pass


def read_mapping(path: Path) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^(\S+) -> (\S+):$", line)
        if match:
            mapping[match.group(1)] = match.group(2)
    if not mapping:
        raise MappingError(f"No R8 class mappings found in {path}")
    return mapping


def read_dex_classes(dexdump: Path, apk: Path) -> set[str]:
    result = subprocess.run(
        [str(dexdump), "-f", str(apk)], capture_output=True, text=True, check=False
    )
    if result.returncode:
        raise MappingError(f"dexdump failed: {result.stderr.strip() or result.stdout.strip()}")
    return set(re.findall(r"Class descriptor\s*:\s*'([^']+)'", result.stdout))


def descriptor(class_name: str) -> str:
    return "L" + class_name.replace(".", "/") + ";"


def inspect(mapping: dict[str, str], dex_classes: set[str]) -> tuple[int, int, int]:
    app = {source: target for source, target in mapping.items()
           if source.startswith(APP_PACKAGES)}
    if not app:
        raise MappingError("No RCBBoard application classes found in the R8 mapping")

    missing_jni = [name for name in JNI_CLASSES if mapping.get(name) != name]
    if missing_jni:
        raise MappingError("JNI registration/field classes were renamed or removed: "
                           + ", ".join(missing_jni))

    missing_dex = [name for name in JNI_CLASSES if descriptor(name) not in dex_classes]
    if missing_dex:
        raise MappingError("Required JNI classes are absent from APK DEX: "
                           + ", ".join(missing_dex))

    obfuscated = {source: target for source, target in app.items() if source != target}
    present_renamed = [target for target in obfuscated.values()
                       if descriptor(target) in dex_classes]
    if not present_renamed:
        raise MappingError("No renamed RCBBoard class from the mapping was found in APK DEX")
    return len(app), len(obfuscated), len(present_renamed)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--dexdump", required=True, type=Path)
    parser.add_argument("--github-summary", type=Path)
    args = parser.parse_args()
    try:
        mapping = read_mapping(args.mapping)
        dex_classes = read_dex_classes(args.dexdump, args.apk)
        total, obfuscated, present = inspect(mapping, dex_classes)
    except (OSError, MappingError) as error:
        print(f"R8 mapping check failed: {error}", file=sys.stderr)
        return 1

    percent = obfuscated * 100 / total
    lines = [
        "### R8 mapping and DEX inspection",
        f"- Mapped RCBBoard classes: **{total}**",
        f"- Renamed application class names: **{obfuscated}/{total} ({percent:.1f}%)**",
        f"- Renamed application classes confirmed in APK DEX: **{present}**",
        "- JNI registration and native-field classes retained under their expected names.",
        "- This class-name estimate is not Google Play's obfuscation score.",
    ]
    print("\n".join(lines))
    if args.github_summary:
        with args.github_summary.open("a", encoding="utf-8") as summary:
            summary.write("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

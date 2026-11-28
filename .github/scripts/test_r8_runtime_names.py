#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Static checks against R8 obfuscation and resource shrinking problems.

Release builds rename classes and remove resources that look unused. Code that depends on a class or
package name, or that looks resources up by name at runtime, then breaks only in the release APK:
5.1.2 crashed when the keyboard started. These checks run in seconds on every pull request.
Run: python3 .github/scripts/test_r8_runtime_names.py
"""
from __future__ import annotations

import os
import re
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(ROOT, "app", "src", "main", "java")
KEEP_XML = os.path.join(ROOT, "app", "src", "main", "res", "raw", "keep.xml")
STRINGS_XML = os.path.join(ROOT, "app", "src", "main", "res", "values", "strings.xml")
PROGUARD = os.path.join(ROOT, "app", "proguard-rules.pro")


def sources() -> dict[str, str]:
    result = {}
    for directory, _, files in os.walk(SRC):
        for name in files:
            if name.endswith((".kt", ".java")):
                path = os.path.join(directory, name)
                with open(path, encoding="utf-8") as f:
                    result[os.path.relpath(path, ROOT)] = f.read()
    return result


def kept_string_patterns() -> list[str]:
    with open(KEEP_XML, encoding="utf-8") as f:
        keep = re.search(r'tools:keep="([^"]*)"', f.read())
    assert keep, "keep.xml has no tools:keep attribute"
    return [p.strip()[len("@string/"):] for p in keep.group(1).split(",") if p.strip().startswith("@string/")]


def is_kept(name: str, patterns: list[str]) -> bool:
    return any(re.fullmatch(re.escape(p).replace(r"\*", ".*"), name) for p in patterns)


class R8RuntimeNamesTest(unittest.TestCase):
    def test_no_java_package_lookups(self):
        # classes are moved out of their packages by obfuscation: getPackage() returns null
        found = [f"{path}:{text[:text.index('getPackage()')].count(chr(10)) + 1}"
                 for path, text in sources().items() if "getPackage()" in text]
        self.assertFalse(found, f"Java package lookups, which return null in release builds: {found}")

    def test_storage_names_do_not_come_from_class_names(self):
        # names of files, databases and preferences must survive renaming between releases
        # (Java and Kotlin built-in classes such as Integer or String are never renamed)
        builtin = r"(?!(?:Integer|String|Long|Boolean|Float|Double|Object|Int)\b)"
        pattern = re.compile(r"(?:NAME|FILE|KEY|PREFIX)\w*\s*=\s*" + builtin + r"[\w.]*(?:class\.getSimpleName\(\)"
                             r"|class\.getName\(\)|::class\.simpleName|::class\.java\.simpleName|::class\.java\.name"
                             r"|::class\.qualifiedName)")
        found = [f"{path}: {m.group(0)}" for path, text in sources().items() for m in pattern.finditer(text)]
        self.assertFalse(found, f"stored names derived from class names, renamed in release builds: {found}")
        history = sources()[os.path.join("app", "src", "main", "java", "helium314", "keyboard", "latin",
                                         "dictionary", "history", "UserHistoryDictionary.java")]
        self.assertIn('NAME = "UserHistoryDictionary"', history, "history file name changed: learned words would be lost")

    def test_strings_looked_up_by_name_are_kept(self):
        patterns = kept_string_patterns()
        all_sources = "\n".join(sources().values())
        prefixes = set(re.findall(r'getStringResourceOrName\("([a-z_]+)"', all_sources))
        prefixes |= set(re.findall(r'getStringResIdByName\("([a-z_]+)\$', all_sources))
        self.assertTrue(prefixes, "no runtime string lookups found, check the regular expressions")
        with open(STRINGS_XML, encoding="utf-8") as f:
            names = re.findall(r'<string name="([^"]+)"', f.read())
        for prefix in sorted(prefixes):
            matching = [n for n in names if n.startswith(prefix)]
            self.assertTrue(matching, f"no strings start with {prefix}")
            missing = [n for n in matching if not is_kept(n, patterns)]
            self.assertFalse(missing, f"strings looked up by name are not in keep.xml: {missing}")

    def test_strings_named_in_layout_files_are_kept(self):
        patterns = kept_string_patterns()
        names = set()
        for directory, _, files in os.walk(os.path.join(ROOT, "app", "src", "main", "assets")):
            for name in files:
                if name.endswith((".json", ".txt")):
                    with open(os.path.join(directory, name), encoding="utf-8", errors="ignore") as f:
                        names |= set(re.findall(r"!string/([A-Za-z0-9_]+)", f.read()))
        missing = sorted(n for n in names if not is_kept(n, patterns))
        self.assertFalse(missing, f"strings named in layout files are not in keep.xml: {missing}")

    def test_enum_names_are_kept(self):
        # toolbar keys, modes, swipe actions and colour types are stored in preferences by name
        with open(PROGUARD, encoding="utf-8") as f:
            self.assertRegex(f.read(), r"-keepclassmembernames[^\n]*enum helium314\.keyboard\.\*\*")


if __name__ == "__main__":
    unittest.main()

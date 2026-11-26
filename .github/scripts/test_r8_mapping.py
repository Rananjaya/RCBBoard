#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
import unittest

from check_r8_mapping import MappingError, descriptor, inspect


class MappingInspectionTest(unittest.TestCase):
    def test_descriptor_converts_java_name(self):
        self.assertEqual(descriptor("helium314.keyboard.Foo$Nested"),
                         "Lhelium314/keyboard/Foo$Nested;")

    def test_counts_obfuscation_and_keeps_jni_classes(self):
        jni = (
            "com.android.inputmethod.latin.BinaryDictionary",
            "com.android.inputmethod.latin.utils.BinaryDictionaryUtils",
            "com.android.inputmethod.latin.DicTraverseSession",
            "com.android.inputmethod.keyboard.ProximityInfo",
            "com.android.inputmethod.latin.utils.WordInputEventForPersonalization",
        )
        mapping = {
            "helium314.keyboard.Foo": "a",
            "helium314.keyboard.Bar": "helium314.keyboard.Bar",
            **{name: name for name in jni},
        }
        dex = {descriptor(target) for target in mapping.values()}
        self.assertEqual(inspect(mapping, dex), (7, 1, 1))

    def test_fails_if_a_jni_class_is_renamed(self):
        mapping = {"com.android.inputmethod.latin.BinaryDictionary": "a"}
        with self.assertRaisesRegex(MappingError, "JNI registration"):
            inspect(mapping, {descriptor("a")})

    def test_fails_if_renamed_names_are_not_in_dex(self):
        jni = (
            "com.android.inputmethod.latin.BinaryDictionary",
            "com.android.inputmethod.latin.utils.BinaryDictionaryUtils",
            "com.android.inputmethod.latin.DicTraverseSession",
            "com.android.inputmethod.keyboard.ProximityInfo",
            "com.android.inputmethod.latin.utils.WordInputEventForPersonalization",
        )
        mapping = {"helium314.keyboard.Foo": "a", **{name: name for name in jni}}
        with self.assertRaisesRegex(MappingError, "No renamed RCBBoard"):
            inspect(mapping, {descriptor(name) for name in jni})


if __name__ == "__main__":
    unittest.main(verbosity=2)

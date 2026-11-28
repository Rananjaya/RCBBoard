# JNI_OnLoad uses RegisterNatives with hard-coded class names and method names. Keep
# only those classes and methods, while allowing R8 to optimize their implementations.
-keep,allowoptimization class com.android.inputmethod.latin.BinaryDictionary { native <methods>; }
-keep,allowoptimization class com.android.inputmethod.latin.utils.BinaryDictionaryUtils { native <methods>; }
-keep,allowoptimization class com.android.inputmethod.latin.DicTraverseSession { native <methods>; }
-keep,allowoptimization class com.android.inputmethod.keyboard.ProximityInfo { native <methods>; }

# Native dictionary code looks up these fields by name on WordInputEventForPersonalization.
-keepclassmembers,allowoptimization class com.android.inputmethod.latin.utils.WordInputEventForPersonalization {
    int[] mTargetWord;
    int mPrevWordsCount;
    int[][] mPrevWordArray;
    boolean[] mIsPrevWordBeginningOfSentenceArray;
    boolean mIsValid;
    int mTimestamp;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable

# Enum constants are stored in preferences by name (toolbar keys and mode, swipe actions, colour types,
# layout types) and read back with valueOf. Keep the names of the constants stable, so settings saved by
# one release are still understood by the next (the enums themselves can still be optimized).
-keepclassmembernames,allowoptimization enum helium314.keyboard.** { <fields>; }

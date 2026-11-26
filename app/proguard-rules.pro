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

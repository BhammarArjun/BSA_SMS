# ── LiteRT-LM SDK (com.google.ai.edge.litertlm) ──────────────────────────────
# The SDK uses JNI-loaded native libs; keep the entire public + internal surface.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }
# Native method stubs may be referenced by the .so at runtime
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Room (generated DAO impls, database impl) ─────────────────────────────────
# Room generates *_Impl classes at compile time; R8 may not see them referenced
# directly in the source. Keep all generated impls and entity classes.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
    public static ** Companion;
}

# ── Hilt / Dagger (generated components) ─────────────────────────────────────
# Hilt's generated code (Hilt_*, *_Factory, *_MembersInjector etc.) references
# application classes by name; the compiler usually emits keep rules via the
# @Keep annotation, but an explicit rule avoids any gap.
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class **_HiltComponents* { *; }
-keep class **Hilt_* { *; }

# ── kotlinx.serialization ─────────────────────────────────────────────────────
# Standard keep rules from the kotlinx-serialization documentation.
# Keeps @Serializable-annotated classes and their generated companion serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

-keep,includedescriptorclasses class com.local.smsllm.**$$serializer { *; }
-keepclassmembers class com.local.smsllm.** {
    *** Companion;
}
-keepclasseswithmembers class com.local.smsllm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Also keep the internal ExtractionParser.ExtractionDto serializer (private class)
-keep class com.local.smsllm.llm.ExtractionParser$ExtractionDto { *; }
-keep class com.local.smsllm.llm.ExtractionParser$ExtractionDto$$serializer { *; }

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── WorkManager (Hilt worker factories) ──────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ── Strip verbose / debug logging in release ──────────────────────────────────
# Removes Log.v and Log.d call sites entirely; Log.i / Log.w / Log.e remain for
# crash diagnostics (they never contain SMS content per §13).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static boolean isLoggable(java.lang.String, int);
}

# ── General: keep line numbers for crash reporting ───────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# VASU ProGuard Rules

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep Compose
-dontwarn androidx.compose.**

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Release-readiness additions (A1) ---
# Verified: com.vasu.assistant.core.ai uses org.json + kotlinx-serialization-json
# (Gson dep still present, so Gson rules stay as a safety net).
# Verified: Room entities/DAOs live in com.vasu.assistant.database (VasuDatabase,
# ConversationMessageEntity/ConversationDao, UserMemoryEntity/MemoryDao).

# Keep attributes needed by Gson / kotlinx-serialization / Room / Hilt.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Hilt / Dagger (extends existing dagger.hilt.** + javax.inject.** keeps above).
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *

# Room: keep whole database package (entities + DAOs + VasuDatabase + DatabaseModule).
-keep class com.vasu.assistant.database.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# Gson models + AI JSON models (conservative: whole core.ai package).
-keep class com.vasu.assistant.core.ai.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation class com.vasu.assistant.core.ai.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# kotlinx-serialization (used for JSON: kotlinx-serialization-json).
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembernames class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# OkHttp / okio.
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ML Kit.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# TFLite (incl. GPU delegate + support library).
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# kotlinx-coroutines (extends existing MainDispatcherFactory keeps above).
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Blanket keep for app code so release minify/shrink cannot strip app classes.
-keep class com.vasu.assistant.** { *; }

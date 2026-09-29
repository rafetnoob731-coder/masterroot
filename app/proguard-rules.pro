# MASTER ROOT ProGuard Rules

# Keep data model classes for Gson serialization
-keep class com.masterroot.domain.model.** { *; }
-keep class com.masterroot.infrastructure.storage.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Timber
-dontwarn org.jetbrains.annotations.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Security: Never expose internals
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

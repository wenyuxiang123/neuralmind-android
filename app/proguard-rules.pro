# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep llama.cpp native methods
-keep class com.neuralmind.llama.** { *; }

# Keep Room entities
-keep class com.neuralmind.data.local.db.entity.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.neuralmind.data.model.** { *; }
-keep class com.neuralmind.domain.model.** { *; }

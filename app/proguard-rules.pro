# Room database keep rules
-keep class com.jeffers.notimindlite.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Firebase keep rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Notification Listener Service keep rules
-keep class com.jeffers.notimindlite.service.NotificationLoggerService { *; }

# Jetpack Compose keep rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# General reflection safety
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# R8 optimization passes and access modification
-optimizationpasses 5
-allowaccessmodification

# Strip all Android logging calls from release bytecode. AppLogger already
# disables logging at source level; this is the release-build defense in depth.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve security providers and encrypted-preference metadata used by runtime reflection.
-keep class androidx.security.crypto.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Suppress harmless warnings from coroutines and compose tooling in release builds
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**

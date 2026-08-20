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

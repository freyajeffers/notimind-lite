# Room database keep rules
-keep class com.jeffers.notimindlite.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Notification Listener Service keep rules
-keep class com.jeffers.notimindlite.service.NotificationLoggerService { *; }

# Jetpack Compose keep rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

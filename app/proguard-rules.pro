# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.wearbubbles.api.dto.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Socket.IO
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }

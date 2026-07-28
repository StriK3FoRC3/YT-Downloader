# youtubedl-android reflects over its request/response models and loads native
# components by name. Keep the whole surface rather than chase individual members.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# yt-dlp's --dump-json output is deserialised into these models.
-keepclassmembers class com.yausername.youtubedl_android.mapper.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

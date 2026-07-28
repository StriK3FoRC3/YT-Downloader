# Attributes the reflective libraries below need. Signature carries generic types, which
# Jackson uses to resolve collection element types; without it, deserialisation fails at
# runtime with errors that look nothing like a missing keep rule.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisible*

# youtubedl-android reflects over its request/response models and loads native
# components by name. Keep the whole surface rather than chase individual members.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# yt-dlp's --dump-json output is deserialised into these models.
-keepclassmembers class com.yausername.youtubedl_android.mapper.** { *; }

# Jackson, which youtubedl-android holds as a static ObjectMapper on YoutubeDL.
#
# Without these R8 strips enough of Jackson's internals that the static initialiser
# throws, and the failure surfaces as ExceptionInInitializerError the first time
# YoutubeDL is touched — with no mention of Jackson anywhere in the trace. Verified: the
# release build launched and reported "yt-dlp could not start" until these were added.
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}
-dontwarn com.fasterxml.jackson.**

# Jackson probes for optional modules and JDK classes that do not exist on Android.
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn javax.xml.stream.**

# Apache Commons Compress, which youtubedl-android uses to unpack the Python and FFmpeg
# payloads on first run.
#
# ExtraFieldUtils holds a static registry of ZipExtraField implementations that it
# instantiates reflectively. R8 removed their no-argument constructors, so the static
# initialiser threw "class AsiExtraField is not a concrete class" and the whole unpack
# failed. Deobfuscated from the release mapping after the app reported "yt-dlp could not
# start" on launch.
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Kotlin coroutines' debug agent probes for these; absent at runtime on Android.
-dontwarn kotlinx.coroutines.debug.**

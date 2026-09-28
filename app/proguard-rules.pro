# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**


# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**
-dontwarn org.jspecify.annotations.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**


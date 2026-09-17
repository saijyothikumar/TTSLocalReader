# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Sherpa-ONNX & JNI
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Jsoup
-keep public class org.jsoup.** { public *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

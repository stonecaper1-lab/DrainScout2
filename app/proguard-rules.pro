-keepattributes *Annotation*

# Shizuku.newProcess is reached through reflection.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.**
-dontwarn moe.shizuku.**
-dontwarn android.**
-dontwarn com.android.**

# ViewModels are created reflectively.
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }

# JobService and app classes are tiny, keep them readable in crash reports.
-keep class com.drain.scout.** { *; }

-keepattributes *Annotation*

# Shizuku starts ShellService by class name inside its own process, and the fallback
# reaches Shizuku.newProcess through reflection.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.drain.scout.** { *; }
-dontwarn rikka.**
-dontwarn moe.shizuku.**
-dontwarn android.**

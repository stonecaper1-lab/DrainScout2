-keepattributes *Annotation*

# Shizuku.newProcess is reached through reflection.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.**
-dontwarn moe.shizuku.**

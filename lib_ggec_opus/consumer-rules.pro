# Keep JNI native entry points
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.bes.opus.** { *; }
-keep class com.bes.lame.** { *; }
-keep class com.yovo.lib_ggec_opus.** { *; }

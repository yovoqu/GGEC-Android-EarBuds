# JNI 入口：native 方法名与类名绑定，不能被裁剪/改名。
-keepclasseswithmembernames class * {
    native <methods>;
}
# vendored 第三方 SDK（src/vendored/java，包名 com.bes.opus）：JNI 符号为
# Java_com_bes_opus_OpusDecoder_* / Java_com_bes_opus_OpusEncoder_*，必须整包保留。
-keep class com.bes.opus.** { *; }
-keep class com.bes.lame.** { *; }
# 模块自身实现（internal/** 与 api/**）：反射/JNI 调用面保守保留。
-keep class com.yovo.lib_ggec_opus.** { *; }

# 本工程 release 默认未开启混淆（isMinifyEnabled = false）。
# 若开启混淆，请按需追加 keep 规则。

# BLE / MediaCodec 均通过 Android 框架调用，无需额外 keep 规则。
# sherpa-onnx 通过 JNI 调用 Kotlin 接口，开启混淆时仍需保留。
-keep class com.k2fsa.sherpa.onnx.** { *; }

# 保留 Kotlin 元数据，便于反射类工具（如需要）：
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

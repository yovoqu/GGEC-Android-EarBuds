# ---------------- Consumer ProGuard rules for lib_bluetooth ----------------

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# 唯一对外门面（根包，不在 api.** 下）
-keep class com.yovo.lib_bluetooth.YoBluetoothSDK { public *; }

# Public Bluetooth SDK APIs.
-keep class com.yovo.lib_bluetooth.api.** { public *; }
-keep class com.yovo.lib_bluetooth.model.common.** { public *; }
-keep class com.yovo.lib_bluetooth.model.classic.** { public *; }
-keep class com.yovo.lib_bluetooth.model.ble.** { public *; }

# Public callback contracts.
-keep interface com.yovo.lib_bluetooth.model.classic.** { *; }
-keep interface com.yovo.lib_bluetooth.model.ble.** { *; }

# Consumer callback implementations.
-keep class * implements com.yovo.lib_bluetooth.model.classic.** { *; }
-keep class * implements com.yovo.lib_bluetooth.model.ble.** { *; }

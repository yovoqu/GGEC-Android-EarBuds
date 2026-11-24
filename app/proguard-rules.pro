# App 业务模块混淆规则

# 四大组件
-keep class com.ggec.earbuds.** extends android.app.Activity { *; }
-keep class com.ggec.earbuds.** extends android.app.Service { *; }
-keep class com.ggec.earbuds.** extends android.content.BroadcastReceiver { *; }
-keep class com.ggec.earbuds.** extends android.app.Application { *; }
-keep class com.ggec.earbuds.** extends android.content.ContentProvider { *; }

# Fragment / DialogFragment
-keep class com.ggec.earbuds.** extends androidx.fragment.app.Fragment { *; }
-keep class com.ggec.earbuds.** extends androidx.fragment.app.DialogFragment { *; }

# ViewModel & Factory
-keep class * implements androidx.lifecycle.ViewModelProvider$Factory {
    public androidx.lifecycle.ViewModel create(java.lang.Class);
}
-keepclassmembers class com.ggec.earbuds.** extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keepclassmembers class com.ggec.earbuds.** extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}

# 自定义 View（XML 反射构造）
-keep class com.ggec.earbuds.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
    public *;
}

# JNI native 方法保活
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 保留行号以便排查线上堆栈
-keepattributes SourceFile,LineNumberTable

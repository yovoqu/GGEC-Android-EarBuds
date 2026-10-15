# ============================================================================
# YoTheme SDK ProGuard 配置文件
# 版本: 1.0.0.2
# 说明: 本文件为 YoTheme UI 组件库的混淆规则，用于保护 SDK 代码的同时保证功能正常
# ============================================================================

# ----------------------------------------------------------------------------
# 通用配置
# ----------------------------------------------------------------------------

# 保留注解（用于调试和运行时）
-keepattributes *Annotation*

# 保留行号信息和源文件名（便于调试崩溃日志）
-keepattributes SourceFile,LineNumberTable

# 保留泛型签名（避免泛型擦除导致的问题）
-keepattributes Signature

# 保留异常信息
-keepattributes Exceptions

# ----------------------------------------------------------------------------
# 颜色系统
# ----------------------------------------------------------------------------

# 保留颜色枚举和公共 API
-keep enum com.yovo.yotheme.color.YoThemeColor {
    *;
}

-keep class com.yovo.yotheme.color.YoTheme {
    public *;
}

-keep class com.yovo.yotheme.color.YoStyle {
    public *;
}

-keep class com.yovo.yotheme.color.YoColors {
    public *;
}

# ColorMapper 内部使用，保留公共方法
-keep class com.yovo.yotheme.color.ColorMapper {
    public static int map(com.yovo.yotheme.color.YoThemeColor);
}

# ----------------------------------------------------------------------------
# UI 组件 - 自定义 View
# ----------------------------------------------------------------------------

# 保留所有自定义 View 的构造函数（Android 系统需要通过反射创建 View）
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留自定义 View 的公共方法
-keep class com.yovo.yotheme.components.** extends android.view.View {
    public *;
}

# 保留自定义 ViewGroup 的公共方法
-keep class com.yovo.yotheme.components.** extends android.view.ViewGroup {
    public *;
}

# ----------------------------------------------------------------------------
# Builder 模式相关类
# ----------------------------------------------------------------------------

# 保留所有 Builder 类及其公共方法
-keep class **.*Builder {
    public *;
}

# 特别保留导航栏、列表、菜单的 Builder
-keep class com.yovo.yotheme.components.yo_navigation.YoNavigationBuilder {
    public *;
}

-keep class com.yovo.yotheme.components.yo_list.YoListBuilder {
    public *;
}

-keep class com.yovo.yotheme.components.yo_menu.YoMenuBuilder {
    public *;
}

# ----------------------------------------------------------------------------
# 接口和回调
# ----------------------------------------------------------------------------

# 保留所有公共接口（用于外部实现回调）
-keep interface com.yovo.yotheme.** {
    *;
}

# ----------------------------------------------------------------------------
# 对话框
# ----------------------------------------------------------------------------

# 保留对话框类（继承自 Android Dialog）
-keep class * extends android.app.Dialog {
    public <init>(android.content.Context);
    public <init>(android.content.Context, int);
    public <init>(android.content.Context, java.lang.String, java.lang.String);
    public *;
}

# 保留所有对话框类的公共方法
-keep class com.yovo.yotheme.dialog.** {
    public *;
}

# ----------------------------------------------------------------------------
# 常量和枚举
# ----------------------------------------------------------------------------

# 保留所有枚举类（用于配置和常量）
-keepclassmembers enum com.yovo.yotheme.constants.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public ** getIntValue();
    public ** getFloatValue();
    public ** getValue();
    public ** getString();
}

# ----------------------------------------------------------------------------
# 配置类和数据类
# ----------------------------------------------------------------------------

# 保留配置类的所有字段和公共方法
-keepclassmembers class **.*Config {
    public *;
}

-keepclassmembers class **.*Configuration {
    public *;
}

# 保留列表项配置
-keep class com.yovo.yotheme.components.yo_list.YoListInterface$ListItemConfig {
    public *;
}

# 保留菜单项配置
-keep class com.yovo.yotheme.components.yo_menu.YoMenuInterface$MenuItemConfig {
    public *;
}

# ----------------------------------------------------------------------------
# 小部件和动画
# ----------------------------------------------------------------------------

# 保留小部件的公共方法
-keep class com.yovo.yotheme.small_widget.** extends android.view.View {
    public *;
}

# ----------------------------------------------------------------------------
# 内部实现优化和混淆策略
# ----------------------------------------------------------------------------

# 允许混淆内部实现类的私有/保护方法和字段（保持代码最小化）
# 注意：公共 API 已在上面保留，这里只是明确可以混淆内部实现

# 混淆所有非 keep 指定的类（内部实现类）
# 以下包下的类如果没有被上面的规则保留，将会被完全混淆：
# - com.yovo.yotheme.background.shapeblurbiew.impl.**
# - com.yovo.yotheme.background.shapeblurbiew.internal.**
# - com.yovo.yotheme.components.**.impl.**
# - com.yovo.yotheme.components.**.internal.**
# - com.yovo.yotheme.small_widget.animation.**

# 特别说明：内部工具类和辅助类全部混淆
# 只有通过 YoUI 对外暴露的 API 才会被保留

# ----------------------------------------------------------------------------
# 移除未使用的代码
# ----------------------------------------------------------------------------

# 移除未被引用的类和方法（进一步减小体积）
-assumenosideeffects class * {
    # 移除空方法
    void <init>();
}

# ----------------------------------------------------------------------------
# 警告处理
# ----------------------------------------------------------------------------

# 忽略 Android 支持库的警告
-dontwarn androidx.**
-dontwarn android.support.**

# 忽略反射警告（某些混淆后的类可能产生）
-dontwarn java.lang.invoke.**

# ----------------------------------------------------------------------------
# 额外的内部类保护
# ----------------------------------------------------------------------------

# 对于没有在 keep 规则中指定的类，应用最强混淆
# 包括但不限于：
# 1. 类名混淆为无意义字符
# 2. 方法名混淆为单字符或无意义序列
# 3. 字段名混淆
# 4. 内联小方法
# 5. 移除未使用的参数
# 6. 优化控制流

# ----------------------------------------------------------------------------
# 结束
# ----------------------------------------------------------------------------

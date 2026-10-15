# ============================================================================
# YoTheme SDK Consumer ProGuard Rules
# 版本: 1.0.0.2
# 说明: 当 YoTheme 库被其他模块依赖时，这些规则会自动应用到消费者的混淆配置中
# 目标: 确保外部 APP 在混淆时正确处理 YoTheme 库，避免运行时错误
# ============================================================================

# ----------------------------------------------------------------------------
# 核心说明
# ----------------------------------------------------------------------------
# 本文件定义了最小必要的保留规则，用于确保 YoTheme SDK 在被集成到其他应用后
# 仍能正常工作。这些规则只保留公共 API 和必要的运行时依赖。
# 
# 重要：YoTheme 库内部已经过混淆，此文件只是为了防止外部应用的混淆
# 再次破坏已混淆的代码。
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# 颜色系统公共 API
# ----------------------------------------------------------------------------

# 保留颜色枚举（外部可能直接引用）
-keep enum com.yovo.yotheme.color.YoThemeColor {
    *;
}

# 保留颜色样式类的公共方法
-keep class com.yovo.yotheme.color.YoStyle {
    public *;
}

-keep class com.yovo.yotheme.color.YoTheme {
    public *;
}

-keep class com.yovo.yotheme.color.YoColors {
    public *;
}

# ----------------------------------------------------------------------------
# 自定义 View 和 ViewGroup
# ----------------------------------------------------------------------------

# 保留所有自定义 View 的构造函数
# Android 系统需要通过反射创建 View（XML 布局或代码创建）
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留自定义 View 的公共方法（外部可能调用）
-keep class com.yovo.yotheme.components.** extends android.view.View {
    public *;
}

# 保留自定义 ViewGroup 的公共方法
-keep class com.yovo.yotheme.components.** extends android.view.ViewGroup {
    public *;
}

# ----------------------------------------------------------------------------
# Builder 模式类
# ----------------------------------------------------------------------------

# 保留所有 Builder 类的公共方法（外部使用链式调用）
-keep class **.*Builder {
    public *;
}

# ----------------------------------------------------------------------------
# YoDialog 2.0 公共 API
# ----------------------------------------------------------------------------

-keep class com.yovo.yotheme.dialog.YoDialog {
    public *;
}

-keep class com.yovo.yotheme.dialog.YoDialog$Scope {
    public *;
}

-keep class com.yovo.yotheme.dialog.preset.** {
    public *;
}

-keep class com.yovo.yotheme.dialog.api.** {
    public *;
}

-keep interface com.yovo.yotheme.dialog.api.** {
    *;
}

# 自定义对话框扩展组件（宿主自定义内容使用）
-keep class com.yovo.yotheme.dialog.widget.** {
    public *;
}

-keep class com.yovo.yotheme.dialog.option.** {
    public *;
}

# ColorMapper 公共入口
-keep class com.yovo.yotheme.color.ColorMapper {
    public static int map(com.yovo.yotheme.color.YoThemeColor);
}

# ----------------------------------------------------------------------------
# 系统工具类
# ----------------------------------------------------------------------------

# 保留 SystemBarUtils 类（状态栏/导航栏工具类）
-keep class com.yovo.yotheme.system_set.** {
    public *;
}

# ----------------------------------------------------------------------------
# 小部件和动画类
# ----------------------------------------------------------------------------

# 保留小部件和动画类的公共方法
-keep class com.yovo.yotheme.small_widget.** {
    public *;
}

# 保留所有组件包的公共类（Interface、Builder、Factory、Popup 等非 View 类）
-keep class com.yovo.yotheme.components.yo_list.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_menu.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_model_panel.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_navigation.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_button.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_card.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_slider.** {
    public *;
}

-keep class com.yovo.yotheme.components.yo_switch.** {
    public *;
}

# ----------------------------------------------------------------------------
# 背景和模糊视图
# ----------------------------------------------------------------------------

# 保留背景相关类（包括模糊视图）
-keep class com.yovo.yotheme.background.** {
    public *;
}

# ----------------------------------------------------------------------------
# 常量类
# ----------------------------------------------------------------------------

# 保留所有常量类
-keep class com.yovo.yotheme.constants.** {
    public *;
}

# ----------------------------------------------------------------------------
# 接口和回调
# ----------------------------------------------------------------------------

# 保留所有接口（外部需要实现这些接口）
-keep interface com.yovo.yotheme.** {
    *;
}

# ----------------------------------------------------------------------------
# 枚举类
# ----------------------------------------------------------------------------

# 保留所有枚举的标准方法
-keepclassmembers enum com.yovo.yotheme.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# ----------------------------------------------------------------------------
# 配置类和数据类
# ----------------------------------------------------------------------------

# 保留配置类的公共成员
-keepclassmembers class **.*Config {
    public *;
}

-keepclassmembers class **.*Configuration {
    public *;
}

# ----------------------------------------------------------------------------
# 泛型和反射
# ----------------------------------------------------------------------------

# 保留泛型签名（避免类型擦除问题）
-keepattributes Signature

# 保留注解
-keepattributes *Annotation*

# 保留异常信息
-keepattributes Exceptions

# 保留行号信息（便于调试崩溃）
-keepattributes SourceFile,LineNumberTable

# ----------------------------------------------------------------------------
# 序列化相关（如果使用）
# ----------------------------------------------------------------------------

# 如果 YoTheme 的配置类需要序列化，保留序列化字段
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ----------------------------------------------------------------------------
# 警告抑制
# ----------------------------------------------------------------------------

# 忽略 YoTheme 内部已混淆代码的警告
-dontwarn com.yovo.yotheme.**

# 忽略 Android 支持库警告
-dontwarn androidx.**
-dontwarn android.support.**

# ----------------------------------------------------------------------------
# 重要说明
# ----------------------------------------------------------------------------
# 1. YoTheme 库内部已经过完整混淆，包括类名、方法名、字段名
# 2. 本文件只保留必要的公共 API，确保外部应用可以正常调用
# 3. 所有内部实现类都已被混淆，反编译后将无法看到原始代码结构
# 4. 如果在集成后遇到运行时错误，请检查是否缺少必要的 keep 规则
# 
# 关于混淆保护：
# - 内部实现类：完全混淆（类名、方法名、字段名全部变为无意义字符）
# - 公共 API：保留类名和方法名（便于外部调用）
# - 包结构：重新打包到 com.ggec.yotheme.core（增加逆向难度）
# - 代码优化：移除未使用代码、内联方法、优化控制流
# ============================================================================


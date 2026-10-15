# ----------------------------------------------------------------------------
# 代码优化配置
# ----------------------------------------------------------------------------

# 启用代码优化，增加优化次数以获得更好的混淆效果
-optimizationpasses 7

# 不跳过非公共的库类（确保内部类也被优化）
-dontskipnonpubliclibraryclasses

# 不跳过非公共的库类成员（确保私有成员也被优化）
-dontskipnonpubliclibraryclassmembers

# 允许修改访问修饰符（提高优化效果，private 可能变为 public）
-allowaccessmodification

# 合并接口（减少类的数量）
-mergeinterfacesaggressively

# 重载方法混淆（增加反编译难度）
-overloadaggressively

# ----------------------------------------------------------------------------
# 包名重写（混淆后所有类统一打包）
# ----------------------------------------------------------------------------

-repackageclasses 'com.ggec.yotheme.core'

# ----------------------------------------------------------------------------
# 混淆选项（增强版）
# ----------------------------------------------------------------------------

# 混淆时使用大小写混合类名（增加混淆强度）
# 注释掉以使用默认混淆（小写字母）
# -dontusemixedcaseclassnames

# 不预校验（加快混淆速度，Android 不需要预校验）
-dontpreverify

# 输出详细的混淆信息（用于调试）
-verbose

# ----------------------------------------------------------------------------
# 混淆字典（自定义混淆名称）
# ----------------------------------------------------------------------------

# 使用自定义字典混淆类名、字段名、方法名
# 这里使用无意义的字符序列，极大增加反编译难度
# 字典文件包含简短的无意义标识符（a, b, c, aa, ab 等）
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt
-obfuscationdictionary dictionary.txt

# ----------------------------------------------------------------------------
# 优化策略
# ----------------------------------------------------------------------------

# 指定优化算法（移除未使用的代码，内联方法，优化算术运算等）
# 移除一些不安全的优化选项，保留更多优化
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# 启用方法内联（减少方法调用，增加反编译难度）
-optimizations code/removal/simple,code/removal/advanced

# ----------------------------------------------------------------------------
# 额外的代码保护
# ----------------------------------------------------------------------------

# 移除日志代码（生产环境不需要）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 移除调试信息中的源文件名（保留行号便于调试崩溃）
# -renamesourcefileattribute SourceFile

# ----------------------------------------------------------------------------
# 属性保留配置
# ----------------------------------------------------------------------------

# 保留注解（用于调试和运行时）
-keepattributes *Annotation*

# 保留行号信息和源文件名（便于调试崩溃日志）
-keepattributes SourceFile,LineNumberTable

# 保留泛型签名（避免泛型擦除导致的问题）
-keepattributes Signature

# 保留异常信息
-keepattributes Exceptions

# 移除调试相关的属性（增加逆向难度）
# 注意：这会让崩溃日志难以定位，谨慎使用
# -keepattributes !LocalVariableTable,!LocalVariableTypeTable

# ============================================================================
# Keep 规则 - 保留必要的公共 API
# ============================================================================

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

# 保留 YoDialog 的 protected 方法（供外部子类调用）
# 这些方法可能被其他模块的子类重写或调用
-keepclassmembers class com.yovo.yotheme.dialog.YoDialog {
    protected *;
}

# 保留 YoSelectConfirmDialog 的 protected 方法
-keepclassmembers class com.yovo.yotheme.dialog.YoSelectConfirmDialog {
    protected *;
}

# 阻止 R8 将 YoDialog 及其子类的方法标记为 final
# 这是为了允许外部模块的类继承并重写这些方法
-keep,allowobfuscation class com.yovo.yotheme.dialog.YoDialog {
    !private !static <methods>;
}

-keep,allowobfuscation class com.yovo.yotheme.dialog.YoSelectConfirmDialog {
    !private !static <methods>;
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
# 小部件和动画
# ----------------------------------------------------------------------------

# 保留小部件和动画类的公共方法
-keep class com.yovo.yotheme.small_widget.** {
    public *;
}

# ----------------------------------------------------------------------------
# 系统工具类
# ----------------------------------------------------------------------------

# 保留 SystemBarUtils 类（状态栏/导航栏工具类）
-keep class com.yovo.yotheme.system_set.** {
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


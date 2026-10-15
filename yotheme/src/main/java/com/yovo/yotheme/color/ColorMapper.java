package com.yovo.yotheme.color;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description 内部颜色映射器
 * @note 核心职责：将公共的 {@link YoThemeColor} 枚举安全地映射到内部的颜色整型值。
 * @note 此类为 yotheme 模块的内部API，模块外请使用公开门面 {@link YoTheme}。
 * 
 */
@Deprecated
public final class ColorMapper {

    private static volatile int brandOverride = -1;

    private ColorMapper() {
        // 工具类不应被实例化
    }

    /** 覆盖品牌色（主题定制入口）；传 -1 恢复默认。 */
    public static void setBrandColor(int color) {
        brandOverride = color;
    }

    public static void resetBrandColor() {
        brandOverride = -1;
    }

    /**
     * 将公共的颜色枚举映射到内部的颜色值
     *
     * @param color 公共颜色枚举
     * @return 对应的颜色int值
     */
    public static int map(YoThemeColor color) {
        if (color == null) {
            return YoColors.BLACK; // 如果传入null，返回默认颜色
        }
        switch (color) {
            // 基础颜色
            case BLACK: return YoColors.BLACK;
            case WHITE: return YoColors.WHITE;
            case TRANSPARENT: return android.graphics.Color.TRANSPARENT;

            // 品牌色
            case BRAND: return brandOverride >= 0 ? brandOverride : YoColors.BRAND;
            case COLOR_PRIMARY_DARK: return YoColors.COLOR_PRIMARY_DARK;
            case COLOR_ACCENT: return YoColors.COLOR_ACCENT;

            // 警示色
            case WARNING: return YoColors.WARNING;
            case ALERT: return YoColors.ALERT;
            case CONFIRM: return YoColors.CONFIRM;
            case SUCCESS_GREEN: return YoColors.SUCCESS_GREEN;
            case ERROR_RED: return YoColors.ERROR_RED;

            // 文本颜色
            case FONT_PRIMARY: return YoColors.FONT_PRIMARY;
            case FONT_SECONDARY: return YoColors.FONT_SECONDARY;
            case FONT_TERTIARY: return YoColors.FONT_TERTIARY;
            case FONT_FOURTH: return YoColors.FONT_FOURTH;
            case FONT_EMPHASIZE: return YoColors.FONT_EMPHASIZE;
            case FONT_ON_PRIMARY: return YoColors.FONT_ON_PRIMARY;
            case FONT_ON_SECONDARY: return YoColors.FONT_ON_SECONDARY;
            case FONT_ON_TERTIARY: return YoColors.FONT_ON_TERTIARY;
            case FONT_ON_FOURTH: return YoColors.FONT_ON_FOURTH;

            // 图标颜色
            case ICON_PRIMARY: return YoColors.ICON_PRIMARY;
            case ICON_SECONDARY: return YoColors.ICON_SECONDARY;
            case ICON_TERTIARY: return YoColors.ICON_TERTIARY;
            case ICON_FOURTH: return YoColors.ICON_FOURTH;
            case ICON_EMPHASIZE: return YoColors.ICON_EMPHASIZE;
            case ICON_SUB_EMPHASIZE: return YoColors.ICON_SUB_EMPHASIZE;
            case ICON_ON_PRIMARY: return YoColors.ICON_ON_PRIMARY;
            case ICON_ON_SECONDARY: return YoColors.ICON_ON_SECONDARY;
            case ICON_ON_TERTIARY: return YoColors.ICON_ON_TERTIARY;
            case ICON_ON_FOURTH: return YoColors.ICON_ON_FOURTH;

            // 背景颜色
            case BACKGROUND_PRIMARY: return YoColors.BACKGROUND_PRIMARY;
            case BACKGROUND_SECONDARY: return YoColors.BACKGROUND_SECONDARY;
            case BACKGROUND_TERTIARY: return YoColors.BACKGROUND_TERTIARY;
            case BACKGROUND_FOURTH: return YoColors.BACKGROUND_FOURTH;
            case BACKGROUND_EMPHASIZE: return YoColors.BACKGROUND_EMPHASIZE;

            // 组件颜色
            case COMP_FOREGROUND_PRIMARY: return YoColors.COMP_FOREGROUND_PRIMARY;
            case COMP_BACKGROUND_PRIMARY: return YoColors.COMP_BACKGROUND_PRIMARY;
            case COMP_BACKGROUND_PRIMARY_TRANS: return YoColors.COMP_BACKGROUND_PRIMARY_TRANS;
            case COMP_BACKGROUND_PRIMARY_CONTRARY: return YoColors.COMP_BACKGROUND_PRIMARY_CONTRARY;
            case COMP_BACKGROUND_GRAY: return YoColors.COMP_BACKGROUND_GRAY;
            case COMP_BACKGROUND_LIGHT_GRAY: return YoColors.COMP_BACKGROUND_LIGHT_GRAY;
            case COMP_BACKGROUND_SECONDARY: return YoColors.COMP_BACKGROUND_SECONDARY;
            case COMP_BACKGROUND_TERTIARY: return YoColors.COMP_BACKGROUND_TERTIARY;
            case COMP_BACKGROUND_EMPHASIZE: return YoColors.COMP_BACKGROUND_EMPHASIZE;
            case COMP_BACKGROUND_NEUTRAL: return YoColors.COMP_BACKGROUND_NEUTRAL;
            case COMP_EMPHASIZE_SECONDARY: return YoColors.COMP_EMPHASIZE_SECONDARY;
            case COMP_EMPHASIZE_TERTIARY: return YoColors.COMP_EMPHASIZE_TERTIARY;
            case COMP_DIVIDER: return YoColors.COMP_DIVIDER;
            case COMP_COMMON_CONTRARY: return YoColors.COMP_COMMON_CONTRARY;
            case COMP_BACKGROUND_FOCUS: return YoColors.COMP_BACKGROUND_FOCUS;
            case COMP_FOCUSED_PRIMARY: return YoColors.COMP_FOCUSED_PRIMARY;
            case COMP_FOCUSED_SECONDARY: return YoColors.COMP_FOCUSED_SECONDARY;
            case COMP_FOCUSED_TERTIARY: return YoColors.COMP_FOCUSED_TERTIARY;

            // 交互颜色
            case INTERACTIVE_HOVER: return YoColors.INTERACTIVE_HOVER;
            case INTERACTIVE_PRESSED: return YoColors.INTERACTIVE_PRESSED;
            case INTERACTIVE_FOCUS: return YoColors.INTERACTIVE_FOCUS;
            case INTERACTIVE_ACTIVE: return YoColors.INTERACTIVE_ACTIVE;
            case INTERACTIVE_SELECT: return YoColors.INTERACTIVE_SELECT;
            case INTERACTIVE_CLICK: return YoColors.INTERACTIVE_CLICK;

            default:
                // 提供一个默认或备用颜色，避免崩溃
                return YoColors.BLACK;
        }
    }
} 

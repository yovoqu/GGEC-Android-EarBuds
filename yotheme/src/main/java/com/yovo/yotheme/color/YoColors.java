package com.yovo.yotheme.color;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description YoTheme 颜色常量类
 * @note 提供统一的颜色管理，对应 colors.xml 中的颜色定义
 * @note 此类为模块内部类，不应被外部直接调用
 * 
 */
public final class YoColors {

    /**
     * 私有构造函数，防止实例化
     */
    private YoColors() {
        // 工具类不应被实例化
    }

    // 基础颜色
    static final int BLACK = 0xFF000000;
    static final int WHITE = 0xFFFFFFFF;

    // 品牌色
    static final int BRAND = 0xFF0A59F7; // 宇宙蓝
    static final int COLOR_PRIMARY_DARK = 0xFF0847C5; // 深蓝色
    static final int COLOR_ACCENT = BRAND;

    // 警示色
    static final int WARNING = 0xFFE84026;
    static final int ALERT = 0xFFED6F21;
    static final int CONFIRM = 0xFF64BB5C;
    static final int SUCCESS_GREEN = 0xFF64BB5C; // 成功绿色
    static final int ERROR_RED = 0xFFE84026; // 错误红色

    // 文本颜色
    static final int FONT_PRIMARY = 0xE5000000;
    static final int FONT_SECONDARY = 0x99000000;
    static final int FONT_TERTIARY = 0x66000000;
    static final int FONT_FOURTH = 0x33000000;
    static final int FONT_EMPHASIZE = 0xFF0A59F7;
    static final int FONT_ON_PRIMARY = 0xFFFFFFFF;
    static final int FONT_ON_SECONDARY = 0x99FFFFFF;
    static final int FONT_ON_TERTIARY = 0x66FFFFFF;
    static final int FONT_ON_FOURTH = 0x33FFFFFF;

    // 图标颜色
    static final int ICON_PRIMARY = 0xE5000000;
    static final int ICON_SECONDARY = 0x99000000;
    static final int ICON_TERTIARY = 0x66000000;
    static final int ICON_FOURTH = 0x33000000;
    static final int ICON_EMPHASIZE = 0xFF0A59F7;
    static final int ICON_SUB_EMPHASIZE = 0x660A59F7;
    static final int ICON_ON_PRIMARY = 0xFFFFFFFF;
    static final int ICON_ON_SECONDARY = 0x99FFFFFF;
    static final int ICON_ON_TERTIARY = 0x66FFFFFF;
    static final int ICON_ON_FOURTH = 0x33FFFFFF;

    // 背景颜色
    static final int BACKGROUND_PRIMARY = 0xFFFFFFFF;
    static final int BACKGROUND_SECONDARY = 0xFFF1F3F5;
    static final int BACKGROUND_TERTIARY = 0xFFE5E5EA;
    static final int BACKGROUND_FOURTH = 0xFFD1D1D6;
    static final int BACKGROUND_EMPHASIZE = 0xFF0A59F7;

    // 组件颜色
    static final int COMP_FOREGROUND_PRIMARY = 0xFF000000;
    static final int COMP_BACKGROUND_PRIMARY = 0xFFFFFFFF;
    static final int COMP_BACKGROUND_PRIMARY_TRANS = 0xFFFFFFFF;
    static final int COMP_BACKGROUND_PRIMARY_CONTRARY = 0xFFFFFFFF;
    static final int COMP_BACKGROUND_GRAY = 0xFFF1F3F5; // 雪域灰
    static final int COMP_BACKGROUND_LIGHT_GRAY = 0xFFE4E6E8; // 浅灰色
    static final int COMP_BACKGROUND_SECONDARY = 0x19000000;
    static final int COMP_BACKGROUND_TERTIARY = 0x0C000000;
    static final int COMP_BACKGROUND_EMPHASIZE = 0xFF0A59F7;
    static final int COMP_BACKGROUND_NEUTRAL = 0xFF000000;
    static final int COMP_EMPHASIZE_SECONDARY = 0x330A59F7;
    static final int COMP_EMPHASIZE_TERTIARY = 0x190A59F7;
    static final int COMP_DIVIDER = 0x33000000;
    static final int COMP_COMMON_CONTRARY = 0xFFFFFFFF;
    static final int COMP_BACKGROUND_FOCUS = 0xFFF1F3F5;
    static final int COMP_FOCUSED_PRIMARY = 0xE5000000;
    static final int COMP_FOCUSED_SECONDARY = 0x99000000;
    static final int COMP_FOCUSED_TERTIARY = 0x66000000;

    // 交互颜色
    static final int INTERACTIVE_HOVER = 0x0C000000;
    static final int INTERACTIVE_PRESSED = 0x19000000;
    static final int INTERACTIVE_FOCUS = 0xFF0A59F7;
    static final int INTERACTIVE_ACTIVE = 0xFF0A59F7;
    static final int INTERACTIVE_SELECT = 0x33000000;
    static final int INTERACTIVE_CLICK = 0x19000000;
} 
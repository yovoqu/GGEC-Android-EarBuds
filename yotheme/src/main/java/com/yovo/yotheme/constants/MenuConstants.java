package com.yovo.yotheme.constants;

/**
 * 菜单组件常量枚举
 * 参考华为设计-菜单规范并结合本项目主题体系
 */
public enum MenuConstants {
    // 菜单容器（用于容纳水波纹margin）
    MENU_PADDING_HORIZONTAL_DP(4),
    MENU_PADDING_VERTICAL_DP(4),
    
    // 菜单卡片圆角
    MENU_CARD_CORNER_RADIUS_DP(16),
    
    // 菜单阴影效果
    MENU_ELEVATION_DP(4),
    MENU_SHADOW_PADDING_DP(10),
    
    // 菜单项水波纹效果
    MENU_ITEM_RIPPLE_CORNER_RADIUS_DP(12),
    MENU_ITEM_RIPPLE_MARGIN_DP(0),
    
    // 菜单项内边距（文本区域）
    MENU_ITEM_PADDING_HORIZONTAL_DP(12),
    MENU_ITEM_PADDING_VERTICAL_DP(8),
    
    // 菜单项最小高度
    MENU_ITEM_MIN_HEIGHT_DP(48),
    
    // 文本样式
    MENU_ITEM_TEXT_SIZE_SP(16),
    
    // 分隔线
    DIVIDER_HEIGHT_PX(1),
    
    // 动画
    ANIMATION_DURATION_MS(150),
    
    // Popup定位配置
    POPUP_DEFAULT_WIDTH_DP(160),
    POPUP_Y_OFFSET_DP(4),           // 菜单与锚点之间的垂直间距
    POPUP_X_OFFSET_LEFT_DP(4),      // 菜单向左的水平偏移（考虑阴影边界）
    POPUP_ANIMATION_DURATION_MS(180),
    POPUP_ANIMATION_TRANSLATION_DP(8),
    
    // 通用浮点常量
    ROUNDING_FLOAT(NavigationConstants.ROUNDING_FLOAT.getFloatValue());

    private final int intValue;
    private final float floatValue;

    MenuConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }

    MenuConstants(float value) {
        this.intValue = (int) value;
        this.floatValue = value;
    }

    public int getIntValue() {
        return intValue;
    }

    public float getFloatValue() {
        return floatValue;
    }
}


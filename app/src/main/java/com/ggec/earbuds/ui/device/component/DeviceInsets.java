package com.ggec.earbuds.ui.device.component;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceTheme;

/**
 * 设备 UI 内边距的唯一控制点（公共组件层）。
 *
 * <p>全屏只认两条竖线：卡片外沿 = 屏幕内边距 {@link #page}；卡片标题 = 外沿 + 卡片内边距
 * {@link #cardInner}。版块标题用 {@link #headerText} 与卡片标题同线。任何组件都从这里取，
 * 不再各自写死 dp，避免边距口径漂移。</p>
 */
public final class DeviceInsets {

    private DeviceInsets() {
    }

    /** 卡片外沿到屏幕的水平内边距。 */
    public static int page(@NonNull DeviceTheme theme) {
        return theme.dp(DeviceTheme.LIST_EDGE);
    }

    /** 卡片内边距（标题到卡片外沿）。 */
    public static int cardInner(@NonNull DeviceTheme theme) {
        return theme.dp(DeviceTheme.CARD_PADDING_H);
    }

    /** 卡片标题 / 版块标题到屏幕的水平位置（外沿 + 卡片内边距）。 */
    public static int headerText(@NonNull DeviceTheme theme) {
        return page(theme) + cardInner(theme);
    }

    /** 卡片之间（同列竖距、两列横距）的间距。 */
    public static int gap(@NonNull DeviceTheme theme) {
        return theme.dp(DeviceTheme.CARD_GAP);
    }
}

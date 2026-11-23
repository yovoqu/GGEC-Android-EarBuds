package com.ggec.earbuds.ui.device.component;

import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceTheme;

/** 设备模块的布局参数工厂（公共组件层）。 */
public final class DeviceLayouts {

    private DeviceLayouts() {
    }

    @NonNull
    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 内容块统一页边距：左右 {@link DeviceInsets#page}，上下由调用方给出（dp）。 */
    @NonNull
    public static LinearLayout.LayoutParams pageInset(@NonNull DeviceTheme theme,
                                                      float topDp, float bottomDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.leftMargin = DeviceInsets.page(theme);
        params.rightMargin = DeviceInsets.page(theme);
        params.topMargin = theme.dp(topDp);
        params.bottomMargin = theme.dp(bottomDp);
        return params;
    }

    /** 只设上下间距、左右交给子组件的块。 */
    @NonNull
    public static LinearLayout.LayoutParams verticalOnly(@NonNull DeviceTheme theme,
                                                         float topDp, float bottomDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = theme.dp(topDp);
        params.bottomMargin = theme.dp(bottomDp);
        return params;
    }
}

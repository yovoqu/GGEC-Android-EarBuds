package com.ggec.earbuds.ui.device.component;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceTheme;

/**
 * 版块标题（公共组件层）：灰色小标题，水平内边距取 {@link DeviceInsets#page}，
 * 让标题左沿与卡片外沿落在同一条竖线（标题贴卡片左侧，卡片内容再内缩）。
 */
public final class DeviceSectionHeader {

    private DeviceSectionHeader() {
    }

    @NonNull
    public static TextView create(@NonNull Context context, @NonNull DeviceTheme theme,
                                  @NonNull CharSequence title) {
        TextView view = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text3);
        view.setText(title);
        int inset = DeviceInsets.page(theme);
        view.setPadding(inset, 0, inset, 0);
        return view;
    }
}

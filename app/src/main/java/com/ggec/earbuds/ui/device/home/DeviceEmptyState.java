package com.ggec.earbuds.ui.device.home;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;

/**
 * 未连接设备时的空态引导：左对齐主标题 + 说明文字，对齐 AI 页标准空态基线。
 */
public final class DeviceEmptyState {

    private final DeviceTheme theme;
    private final ScrollView view;
    private final LinearLayout column;

    public DeviceEmptyState(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme) {
        this.theme = theme;

        view = new ScrollView(activity);
        view.setFillViewport(true);
        view.setVerticalScrollBarEnabled(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setClipToPadding(false);

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, theme.dp(DeviceTheme.EMPTY_TOP), 0, theme.dp(DeviceTheme.SPACE_XL));
        view.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(theme.dp(DeviceTheme.PAGE_INSET), 0, theme.dp(DeviceTheme.PAGE_INSET), 0);
        column.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        heading.setText(R.string.device_empty_title);
        heading.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        block.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView message = theme.text(DeviceTheme.TEXT_CALLOUT, theme.text2);
        message.setText(R.string.device_empty_body);
        message.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        block.addView(message, messageParams);
    }

    @NonNull
    public View view() {
        return view;
    }

    public void setBottomPadding(int bottomPx) {
        column.setPadding(0, theme.dp(DeviceTheme.EMPTY_TOP), 0, bottomPx);
    }
}

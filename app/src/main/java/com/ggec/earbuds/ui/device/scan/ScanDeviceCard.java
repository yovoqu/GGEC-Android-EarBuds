package com.ggec.earbuds.ui.device.scan;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;

/** 单个扫描结果的设备信息卡：图标 + 机型 + 地址/信号 + 配对徽标 + 箭头。 */
public final class ScanDeviceCard {

    private final View view;

    public ScanDeviceCard(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                          @NonNull ScanItem result, @NonNull Runnable onClick) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(theme.dp(76));
        card.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M));
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        card.setOnClickListener(v -> onClick.run());

        FrameLayout iconBox = new FrameLayout(activity);
        iconBox.setBackground(theme.circle(theme.brandSoft));
        View icon = theme.icon(R.drawable.ic_ai_bolt, theme.brand);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                theme.dp(20), theme.dp(20), Gravity.CENTER);
        iconBox.addView(icon, iconParams);
        card.addView(iconBox, new LinearLayout.LayoutParams(theme.dp(40), theme.dp(40)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = theme.text(DeviceTheme.TEXT_TITLE, theme.text1, DeviceTheme.Weight.MEDIUM);
        name.setText(result.modelId());
        TextView detail = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        detail.setText(result.mac() + "  ·  " + result.rssi() + " dBm");
        texts.addView(name, wrap());
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = theme.dp(2);
        texts.addView(detail, detailParams);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        card.addView(texts, textParams);

        if (result.paired()) {
            TextView badge = theme.text(DeviceTheme.TEXT_CAPTION, theme.brand);
            badge.setText(R.string.device_scan_paired);
            badge.setPadding(theme.dp(DeviceTheme.SPACE_S), theme.dp(2),
                    theme.dp(DeviceTheme.SPACE_S), theme.dp(2));
            badge.setBackground(theme.rounded(theme.brandSoft, 999f));
            card.addView(badge, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        View chevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        chevron.setRotation(-90f);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                theme.dp(16), theme.dp(16));
        chevronParams.setMarginStart(theme.dp(DeviceTheme.SPACE_S));
        card.addView(chevron, chevronParams);
        view = card;
    }

    @NonNull
    public View view() {
        return view;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

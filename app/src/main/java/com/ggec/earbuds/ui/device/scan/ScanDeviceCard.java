package com.ggec.earbuds.ui.device.scan;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单个扫描结果的设备信息卡：图标 + 名称/机型 + 地址/信号 + 配对徽标 + 箭头。
 * 支持 RecyclerView ViewHolder 复用原地刷新，避免滚动与高频广播刷新时重复创建视图树与 Drawable。
 */
public final class ScanDeviceCard {

    private final LinearLayout view;
    private final TextView name;
    private final TextView detail;
    private final TextView badge;
    @Nullable
    private ScanItem boundItem;

    public ScanDeviceCard(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                          @NonNull ScanItem result, @NonNull Runnable onClick) {
        this(activity, theme, ignored -> onClick.run());
        bind(result);
    }

    public ScanDeviceCard(@NonNull Context context, @NonNull DeviceTheme theme,
                          @NonNull Consumer<ScanItem> onItemClick) {
        Objects.requireNonNull(onItemClick, "onItemClick");
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(theme.dp(76));
        card.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M));
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        card.setOnClickListener(v -> {
            ScanItem current = boundItem;
            if (current != null) {
                onItemClick.accept(current);
            }
        });

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(theme.circle(theme.brandSoft));
        View icon = theme.icon(R.drawable.ic_ai_bolt, theme.brand);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                theme.dp(20), theme.dp(20), Gravity.CENTER);
        iconBox.addView(icon, iconParams);
        card.addView(iconBox, new LinearLayout.LayoutParams(theme.dp(40), theme.dp(40)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        name = theme.text(DeviceTheme.TEXT_TITLE, theme.text1, DeviceTheme.Weight.MEDIUM);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);

        detail = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);

        texts.addView(name, wrap());
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = theme.dp(2);
        texts.addView(detail, detailParams);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        textParams.setMarginEnd(theme.dp(DeviceTheme.SPACE_S));
        card.addView(texts, textParams);

        badge = theme.text(DeviceTheme.TEXT_CAPTION, theme.brand);
        badge.setText(R.string.device_scan_paired);
        badge.setPadding(theme.dp(DeviceTheme.SPACE_S), theme.dp(2),
                theme.dp(DeviceTheme.SPACE_S), theme.dp(2));
        badge.setBackground(theme.rounded(theme.brandSoft, 999f));
        badge.setVisibility(View.GONE);
        card.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View chevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        chevron.setRotation(-90f);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                theme.dp(16), theme.dp(16));
        chevronParams.setMarginStart(theme.dp(DeviceTheme.SPACE_S));
        card.addView(chevron, chevronParams);
        view = card;
    }

    public void bind(@NonNull ScanItem result) {
        boundItem = result;
        String displayName = result.name().isEmpty() ? result.modelId() : result.name();
        if (!displayName.contentEquals(name.getText())) {
            name.setText(displayName);
        }
        String subtitle = result.name().isEmpty() || result.name().equalsIgnoreCase(result.modelId())
                ? result.mac() + "  ·  " + result.rssi() + " dBm"
                : result.modelId() + "  ·  " + result.mac() + "  ·  " + result.rssi() + " dBm";
        if (!subtitle.contentEquals(detail.getText())) {
            detail.setText(subtitle);
        }
        int badgeVisibility = result.paired() ? View.VISIBLE : View.GONE;
        if (badge.getVisibility() != badgeVisibility) {
            badge.setVisibility(badgeVisibility);
        }
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

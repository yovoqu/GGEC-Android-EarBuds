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

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单个扫描结果的设备信息卡：图标 + 名称/机型 + 地址/信号 + 箭头。
 * 支持 RecyclerView ViewHolder 复用原地刷新，避免滚动与高频广播刷新时重复创建视图树与 Drawable。
 */
public final class ScanDeviceCard {

    private final LinearLayout view;
    private final TextView name;
    private final TextView detail;
    @Nullable
    private ScanItem boundItem;

    public ScanDeviceCard(@NonNull Context context, @NonNull DeviceTheme theme,
                          @NonNull Consumer<ScanItem> onItemClick) {
        Objects.requireNonNull(onItemClick, "onItemClick");
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(theme.dp(DeviceTheme.SCAN_CARD_MIN_HEIGHT));
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
                theme.dp(DeviceTheme.SCAN_ICON), theme.dp(DeviceTheme.SCAN_ICON), Gravity.CENTER);
        iconBox.addView(icon, iconParams);
        card.addView(iconBox, new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.SCAN_ICON_BOX), theme.dp(DeviceTheme.SCAN_ICON_BOX)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        name = theme.text(DeviceTheme.TEXT_TITLE, theme.text1, DeviceTheme.Weight.MEDIUM);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);

        detail = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);

        texts.addView(name, fillWidth());
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = theme.dp(DeviceTheme.SCAN_SUBTITLE_GAP);
        texts.addView(detail, detailParams);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        textParams.setMarginEnd(theme.dp(DeviceTheme.SPACE_S));
        card.addView(texts, textParams);

        View chevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        chevron.setRotation(-90f);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.SCAN_CHEVRON), theme.dp(DeviceTheme.SCAN_CHEVRON));
        chevronParams.setMarginStart(theme.dp(DeviceTheme.SPACE_S));
        card.addView(chevron, chevronParams);
        view = card;
    }

    public void bind(@NonNull ScanItem result) {
        boundItem = result;
        String displayName = result.bleName().isEmpty() ? result.modelId() : result.bleName();
        if (!displayName.contentEquals(name.getText())) {
            name.setText(displayName);
        }
        if (!subtitleOf(result).contentEquals(detail.getText())) {
            detail.setText(subtitleOf(result));
        }
    }

    /**
     * 轻量局部刷新：仅 RSSI 变化时只更新副标题，避免完整重绑名称/徽标（配合 DiffUtil payload 使用）。
     * 由于名称与副标题宽度已固定为 MATCH_PARENT，更新 RSSI 文本不再触发 RecyclerView 逐项 requestLayout。
     */
    public void bindRssi(@NonNull ScanItem result) {
        boundItem = result;
        String subtitle = subtitleOf(result);
        if (!subtitle.contentEquals(detail.getText())) {
            detail.setText(subtitle);
        }
    }

    @NonNull
    private String subtitleOf(@NonNull ScanItem result) {
        String signal = DeviceFormats.rssi(result.rssi());
        return result.bleName().isEmpty() || result.bleName().equalsIgnoreCase(result.modelId())
                ? result.bleMac() + DeviceFormats.META_SEPARATOR + signal
                : result.modelId() + DeviceFormats.META_SEPARATOR + result.bleMac()
                        + DeviceFormats.META_SEPARATOR + signal;
    }

    @NonNull
    public View view() {
        return view;
    }

    private LinearLayout.LayoutParams fillWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

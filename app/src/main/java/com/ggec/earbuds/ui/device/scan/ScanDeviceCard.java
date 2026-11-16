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
import com.yohu.youi.api.feedback.YoLoadingProgress;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 扫描结果卡：展示 BLE 名称、BLE MAC、信号强度及连接中 Loading 状态。
 */
public final class ScanDeviceCard {

    private final DeviceTheme theme;
    private final LinearLayout view;
    private final TextView name;
    private final TextView detail;
    private final FrameLayout endSlot;
    private final View chevron;
    private final YoLoadingProgress loadingProgress;
    @Nullable
    private ScanItem boundItem;

    public ScanDeviceCard(@NonNull Context context, @NonNull DeviceTheme theme,
                          @NonNull Consumer<ScanItem> onItemClick) {
        this.theme = theme;
        Objects.requireNonNull(onItemClick, "onItemClick");
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setMinimumHeight(theme.dp(DeviceTheme.SCAN_CARD_MIN_HEIGHT));
        card.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M));
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        card.setOnClickListener(v -> {
            ScanItem current = boundItem;
            if (current != null && !current.connecting()) {
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

        endSlot = new FrameLayout(context);
        endSlot.setClipChildren(false);
        endSlot.setClipToPadding(false);
        LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.SCAN_INDICATOR_SIZE), theme.dp(DeviceTheme.SCAN_INDICATOR_SIZE));
        slotParams.setMarginStart(theme.dp(DeviceTheme.SPACE_S));
        slotParams.gravity = Gravity.CENTER_VERTICAL;

        chevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        chevron.setRotation(-90f);
        FrameLayout.LayoutParams chevronParams = new FrameLayout.LayoutParams(
                theme.dp(DeviceTheme.SCAN_CHEVRON), theme.dp(DeviceTheme.SCAN_CHEVRON), Gravity.CENTER);
        endSlot.addView(chevron, chevronParams);

        loadingProgress = YoLoadingProgress.create(context)
                .setStyle(YoLoadingProgress.Style.SPINNER)
                .setSize(YoLoadingProgress.Size.SMALL)
                .setColor(theme.brand)
                .setAnimating(false)
                .build();
        View loadingView = loadingProgress.getView();
        loadingView.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        endSlot.addView(loadingView, loadingParams);

        card.addView(endSlot, slotParams);
        view = card;
    }

    public void bind(@NonNull ScanItem result, boolean anyConnecting) {
        boundItem = result;
        String title = DeviceFormats.textOrUnknown(result.bleName());
        if (!title.contentEquals(name.getText())) {
            name.setText(title);
        }
        applyConnectingState(result, anyConnecting);
    }

    public void bindConnecting(@NonNull ScanItem result, boolean anyConnecting) {
        boundItem = result;
        applyConnectingState(result, anyConnecting);
    }

    public void bindRssi(@NonNull ScanItem result) {
        boundItem = result;
        if (!result.connecting()) {
            String subtitle = subtitleOf(result);
            if (!subtitle.contentEquals(detail.getText())) {
                detail.setText(subtitle);
            }
        }
    }

    private void applyConnectingState(@NonNull ScanItem result, boolean anyConnecting) {
        boolean connecting = result.connecting();
        if (connecting) {
            chevron.setVisibility(View.GONE);
            loadingProgress.getView().setVisibility(View.VISIBLE);
            if (!loadingProgress.isAnimating()) {
                loadingProgress.setAnimating(true);
            }
            detail.setText(R.string.device_connect_in_progress);
            detail.setTextColor(theme.brand);
            view.setAlpha(1.0f);
        } else {
            if (loadingProgress.isAnimating()) {
                loadingProgress.setAnimating(false);
            }
            loadingProgress.getView().setVisibility(View.GONE);
            chevron.setVisibility(View.VISIBLE);
            detail.setText(subtitleOf(result));
            detail.setTextColor(theme.text3);
            view.setAlpha(anyConnecting ? 0.6f : 1.0f);
        }
    }

    public void destroy() {
        loadingProgress.destroy();
    }

    @NonNull
    private String subtitleOf(@NonNull ScanItem result) {
        return result.bleAddress() + DeviceFormats.META_SEPARATOR + DeviceFormats.rssi(result.rssi());
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

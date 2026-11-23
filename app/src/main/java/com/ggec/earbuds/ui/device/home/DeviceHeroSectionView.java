package com.ggec.earbuds.ui.device.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;

/**
 * 定制组件：设备首页概览区（状态胶囊 + 产品主图 + 三段电量）。
 * 产品图直贴柔光渐变；替换真实渲染图只改 {@link #product} 一处。
 */
final class DeviceHeroSectionView {

    private DeviceHeroSectionView() {
    }

    @NonNull
    static View create(@NonNull Context context, @NonNull DeviceTheme theme,
                       @NonNull DeviceHomeUiState state) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(theme.dp(20f), theme.dp(16f), theme.dp(20f), theme.dp(20f));
        card.setBackground(theme.roundedGradient(theme.heroTop, theme.card,
                DeviceTheme.HERO_RADIUS));

        card.addView(statusPill(context, theme, state), wrapWrap());
        card.addView(product(context, theme), productParams(theme));
        View battery = DeviceBatteryStrip.create(context, theme, state.battery());
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        batteryParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        card.addView(battery, batteryParams);
        return card;
    }

    private static View statusPill(@NonNull Context context, @NonNull DeviceTheme theme,
                                   @NonNull DeviceHomeUiState state) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(theme.rounded(ColorUtils.setAlphaComponent(theme.text1, 0x0D), 20f));
        pill.setPadding(theme.dp(10f), theme.dp(5f), theme.dp(12f), theme.dp(5f));

        View dot = new View(context);
        dot.setBackground(theme.circle(state.statusOnline() ? theme.confirm : theme.hint));
        pill.addView(dot, new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.STATUS_DOT), theme.dp(DeviceTheme.STATUS_DOT)));

        TextView label = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text2, DeviceTheme.Weight.MEDIUM);
        label.setText(state.statusTextRes());
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = theme.dp(6f);
        pill.addView(label, labelParams);
        return pill;
    }

    private static View product(@NonNull Context context, @NonNull DeviceTheme theme) {
        FrameLayout area = new FrameLayout(context);
        View glow = new View(context);
        glow.setBackground(theme.radialGlow(
                ColorUtils.setAlphaComponent(theme.brand, 0x33), 200f));
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                theme.dp(200f), theme.dp(200f));
        glowParams.gravity = Gravity.CENTER;
        area.addView(glow, glowParams);

        ImageView art = theme.icon(R.drawable.ic_device_earbuds_placeholder, theme.text3);
        art.setContentDescription(context.getString(R.string.device_hero_image_desc));
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(
                theme.dp(156f), theme.dp(130f));
        artParams.gravity = Gravity.CENTER;
        area.addView(art, artParams);
        return area;
    }

    private static LinearLayout.LayoutParams productParams(@NonNull DeviceTheme theme) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(DeviceTheme.HERO_IMAGE_HEIGHT));
        params.topMargin = theme.dp(6f);
        return params;
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

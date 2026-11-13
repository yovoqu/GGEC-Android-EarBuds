package com.ggec.earbuds.ui.device.home;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.device.DeviceTheme;

/**
 * 标题栏里的设备信息组件：左侧「名称 + MAC·型号」，右侧圆形下拉钮，整体是一个可点击单元。
 *
 * <p>下拉锚点就是这个组件本身（{@link #anchor()}），而不是里面那颗圆形按钮：用户点名称、
 * 点副标题、点整个区域都能展开设备列表，视觉上也只有这个组件带按下反馈。</p>
 */
public final class DeviceTitleView {

    public interface Listener {
        void onOpenMenu();
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;

    private final LinearLayout root;
    private final TextView titleView;
    private final TextView subtitleView;
    private final AiHeaderActionButton switchButton;

    private boolean menuAvailable;

    public DeviceTitleView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                           @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(0, theme.dp(DeviceTheme.SPACE_XS), 0, theme.dp(DeviceTheme.SPACE_XS));
        root.setOnClickListener(v -> {
            if (menuAvailable) {
                listener.onOpenMenu();
            }
        });

        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        titleView = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        titleView.setText(R.string.tab_device);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleView, matchWrap());
        subtitleView = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setVisibility(View.GONE);
        info.addView(subtitleView, matchWrap());
        root.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        switchButton = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_chevron_down)
                .contentDesc(R.string.device_switch)
                .onClick(v -> {
                    if (menuAvailable) {
                        listener.onOpenMenu();
                    }
                });
        switchButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams switchParams = switchButton.createLinearLayoutParams();
        switchParams.setMarginStart(theme.dp(DeviceTheme.SPACE_S));
        root.addView(switchButton, switchParams);

        View spacer = new View(activity);
        root.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
    }

    @NonNull
    public View view() {
        return root;
    }

    /** 菜单锚点：整个设备信息组件。 */
    @NonNull
    public View anchor() {
        return root;
    }

    public void render(@NonNull DeviceHomeUiState state) {
        int maxWidth = availableTextWidth();
        titleView.setMaxWidth(maxWidth);
        subtitleView.setMaxWidth(maxWidth);
        boolean connected = !state.title().isEmpty();
        titleView.setText(connected ? state.title() : activity.getString(R.string.tab_device));
        subtitleView.setText(state.subtitle());
        subtitleView.setVisibility(connected && !state.subtitle().isEmpty()
                ? View.VISIBLE : View.GONE);
        menuAvailable = !state.devices().isEmpty();
        switchButton.setVisibility(menuAvailable ? View.VISIBLE : View.GONE);
        root.setClickable(menuAvailable);
    }

    /**
     * 标题/副标题可用的最大宽度：屏幕宽度减去页边距、右侧圆形下拉钮与「添加设备」。
     *
     * <p>不设这个上限时，两行文本会被「较窄的那一行」反向约束——副标题（MAC·型号）短，
     * 主标题就会被提前省略。上限只用于限制极长名称，不影响短名称自然贴合。</p>
     */
    private int availableTextWidth() {
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int reserved = theme.dp(DeviceTheme.PAGE_INSET) * 2
                + theme.dp(DeviceTheme.HEADER_HEIGHT)
                + theme.dp(DeviceTheme.SPACE_S) * 2
                + theme.dp(DeviceTheme.SPACE_XS) * 2;
        return Math.max(theme.dp(120f), screen - reserved);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

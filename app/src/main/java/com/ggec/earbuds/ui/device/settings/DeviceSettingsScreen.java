package com.ggec.earbuds.ui.device.settings;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.section.ControlSectionView;
import com.ggec.earbuds.ui.device.settings.section.FunctionSectionView;
import com.ggec.earbuds.ui.device.settings.section.ListSectionView;
import com.ggec.earbuds.ui.device.settings.section.MusicSectionView;
import com.ggec.earbuds.ui.device.settings.section.SettingsSectionView;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 二级设置页视图：只做页头、滚动容器、未连接空态与区块切换。
 * 每个功能的布局与交互在自己的 section view 内，本类不含任何具体控件排布。
 */
public final class DeviceSettingsScreen {

    public interface Listener {
        void onBack();
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;

    private final LinearLayout root;
    private final TextView title;
    private final NestedScrollView scroll;
    private final ScrollView empty;
    private final LinearLayout content;
    private final SettingsSectionView section;

    public DeviceSettingsScreen(@NonNull AppCompatActivity activity,
                                @NonNull DeviceSettingsViewModel viewModel,
                                @NonNull Listener listener) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.theme = DeviceTheme.of(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        title.setText(viewModel.page().titleRes);
        root.addView(buildHeader(listener), matchWrap());

        FrameLayout host = new FrameLayout(activity);
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVisibility(View.GONE);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, theme.dp(DeviceTheme.SPACE_S), 0, theme.dp(DeviceTheme.SPACE_XL));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        empty = buildEmptyState();
        empty.setVisibility(View.GONE);
        host.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        section = createSection();
        content.addView(section.view(), matchWrap());
    }

    @NonNull
    public View view() {
        return root;
    }

    public void render(@NonNull DeviceSettingsUiState state) {
        title.setText(state.titleRes());
        if (!state.loaded()) {
            return;
        }
        boolean connected = state.connected();
        scroll.setVisibility(connected ? View.VISIBLE : View.GONE);
        empty.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (connected) {
            section.render(state);
        }
    }

    public void destroy() {
        section.destroy();
    }

    private SettingsSectionView createSection() {
        return switch (viewModel.page()) {
            case MUSIC -> new MusicSectionView(activity, theme, viewModel);
            case CONTROL -> new ControlSectionView(activity, theme, viewModel);
            case FUNCTION -> new FunctionSectionView(activity, theme, viewModel);
            case DUAL, RECORD, ABOUT -> new ListSectionView(activity, theme, viewModel);
        };
    }

    private ScrollView buildEmptyState() {
        ScrollView scrollEmpty = new ScrollView(activity);
        scrollEmpty.setFillViewport(true);
        scrollEmpty.setVerticalScrollBarEnabled(false);
        scrollEmpty.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollEmpty.setClipToPadding(false);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XL));
        scrollEmpty.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        heading.setText(R.string.device_empty_title);
        heading.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        column.addView(heading, matchWrap());

        TextView body = theme.text(DeviceTheme.TEXT_CALLOUT, theme.text2);
        body.setText(R.string.device_settings_empty_body);
        body.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        column.addView(body, bodyParams);
        return scrollEmpty;
    }

    private View buildHeader(Listener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));
        row.setPadding(theme.dp(DeviceTheme.PAGE_INSET), 0, theme.dp(DeviceTheme.PAGE_INSET), 0);

        AiHeaderActionButton back = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.device_back)
                .onClick(v -> listener.onBack());
        row.addView(back, back.createLinearLayoutParams());

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        row.addView(title, titleParams);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

package com.ggec.earbuds.ui.device.home;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DevicePage;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 设备页签视图：只负责组合卡片与转交事件。
 * 摘要卡、快捷开关、功能入口、空态各自独立成组件。
 */
public final class DeviceHomePage {

    public interface Listener {
        void onOpenScan();

        void onOpenEntry(@NonNull DevicePage page);

        void onSelectModel();

        void onToggle(@NonNull HomeToggle key, boolean checked);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final YoComponents components = new YoComponents();
    private final Listener listener;

    private final LinearLayout root;
    private final NestedScrollView scroll;
    private final View content;
    private final View empty;
    private final DeviceSummaryCard summary;
    private final QuickToggleCard quick;
    private final DeviceEntryListView entries;
    private final DeviceEmptyState emptyState;
    private final TextView footer;

    private int bottomInset;
    private int lastSystemBottom;

    public DeviceHomePage(@NonNull AppCompatActivity activity,
                          @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);
        root.addView(buildHeader(), matchWrap());

        FrameLayout contentHost = new FrameLayout(activity);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        summary = new DeviceSummaryCard(activity, theme);
        column.addView(summary.view(), cardParams());

        quick = new QuickToggleCard(activity, components, listener::onToggle);
        column.addView(quick.view(), topOnlyParams());

        entries = new DeviceEntryListView(activity, theme, components, listener::onOpenEntry);
        column.addView(entries.view(), topOnlyParams());

        footer = theme.text(DeviceTheme.TEXT_CAPTION, theme.hint);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = theme.dp(DeviceTheme.SPACE_XL);
        column.addView(footer, footerParams);

        content = scroll;
        contentHost.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        emptyState = new DeviceEmptyState(activity, theme, listener::onOpenScan);
        empty = emptyState.view();
        empty.setVisibility(View.GONE);
        contentHost.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            applyScrollBottom(bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @NonNull
    public View view() {
        return root;
    }

    public void setBottomInset(int px) {
        bottomInset = px;
        applyScrollBottom(lastSystemBottom);
    }

    public void render(@NonNull DeviceHomeUiState state) {
        if (!state.loaded()) {
            return;
        }
        boolean hasDevice = state.connected();
        content.setVisibility(hasDevice ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasDevice ? View.GONE : View.VISIBLE);
        if (!hasDevice) {
            return;
        }
        summary.render(state);
        quick.render(state);
        entries.render(state.entries());
        footer.setText(state.firmware().isEmpty() ? "" : activity.getString(
                R.string.device_firmware_footer, state.firmware()));
    }

    public void destroy() {
        emptyState.destroy();
        components.close();
    }

    private void applyScrollBottom(int systemBottom) {
        lastSystemBottom = systemBottom;
        if (scroll != null) {
            scroll.setPadding(0, 0, 0,
                    Math.max(systemBottom, bottomInset) + theme.dp(DeviceTheme.SPACE_XL));
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = theme.text(28f, theme.text1, DeviceTheme.Weight.BOLD);
        title.setText(R.string.tab_device);
        TextView sub = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text3);
        sub.setText(R.string.device_header_subtitle);
        sub.setOnClickListener(v -> listener.onSelectModel());
        titles.addView(title, matchWrap());
        titles.addView(sub, matchWrap());
        row.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        AiHeaderActionButton add = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_plus)
                .contentDesc(R.string.device_add)
                .onClick(v -> listener.onOpenScan());
        row.addView(add, add.createLinearLayoutParams());
        return row;
    }

    private LinearLayout.LayoutParams topOnlyParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(DeviceTheme.SPACE_M);
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.topMargin = theme.dp(DeviceTheme.SPACE_M);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

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
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 设备页签视图骨架：页头、滚动容器、摘要卡、能力分区列表、空态。
 * 分区与行由 {@link DeviceCapabilityListView} 渲染，本类只负责组合与转交事件。
 */
public final class DeviceHomePage {

    public interface Listener {
        void onOpenScan();

        void onOpenGroup(@NonNull DeviceCapabilityGroup group);

        void onToggle(@NonNull String capabilityId, boolean checked);

        void onSelectOption(@NonNull String capabilityId, @NonNull String optionId);
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
    private final DeviceCapabilityListView capabilities;
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

        capabilities = new DeviceCapabilityListView(activity, theme, components,
                new DeviceCapabilityListView.Listener() {
                    @Override
                    public void onToggle(@NonNull String capabilityId, boolean checked) {
                        listener.onToggle(capabilityId, checked);
                    }

                    @Override
                    public void onSelectOption(@NonNull String capabilityId, @NonNull String optionId) {
                        listener.onSelectOption(capabilityId, optionId);
                    }

                    @Override
                    public void onOpenGroup(@NonNull DeviceCapabilityGroup group) {
                        listener.onOpenGroup(group);
                    }
                });
        column.addView(capabilities.view(), matchWrap());

        footer = theme.text(DeviceTheme.TEXT_CAPTION, theme.hint);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = theme.dp(DeviceTheme.SPACE_XL);
        column.addView(footer, footerParams);

        content = scroll;
        content.setVisibility(View.GONE);
        contentHost.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        emptyState = new DeviceEmptyState(activity, theme);
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
        boolean hasDevice = state.connected();
        content.setVisibility(hasDevice ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasDevice ? View.GONE : View.VISIBLE);
        if (!hasDevice) {
            return;
        }
        summary.render(state);
        capabilities.render(state.sections(), state.interactive());
        footer.setText(state.firmware().isEmpty() ? "" : activity.getString(
                R.string.device_firmware_footer, state.firmware()));
    }

    /** 写入落地结果：失败时让开关回到「未上报」呈现，不假装成功。 */
    public void onControlOutcome(@NonNull DeviceControlOutcome outcome) {
        capabilities.onControlOutcome(outcome);
    }

    public void destroy() {
        components.close();
    }

    private void applyScrollBottom(int systemBottom) {
        lastSystemBottom = systemBottom;
        int bottom = Math.max(systemBottom, bottomInset) + theme.dp(DeviceTheme.SPACE_XL);
        scroll.setPadding(0, 0, 0, bottom);
        emptyState.setBottomPadding(bottom);
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));
        row.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.PAGE_INSET), 0);

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.tab_device);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        AiHeaderActionButton add = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_plus)
                .contentDesc(R.string.device_add)
                .onClick(v -> listener.onOpenScan());
        row.addView(add, add.createLinearLayoutParams());
        return row;
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

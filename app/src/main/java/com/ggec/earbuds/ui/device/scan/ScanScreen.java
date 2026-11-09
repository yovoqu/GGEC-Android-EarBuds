package com.ggec.earbuds.ui.device.scan;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/** 扫描连接页视图。空态遵循统一左对齐标题+说明排版，避免重复提示文案。 */
public final class ScanScreen {

    public interface Listener {
        void onBack();

        void onScan();

        void onConnect(@NonNull ScanItem item);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;

    private final LinearLayout root;
    private final TextView status;
    private final LinearLayout list;
    private final YoButton action;

    public ScanScreen(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        root.addView(buildHeader(), matchWrap());

        status = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text3);
        status.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S));
        status.setVisibility(View.GONE);
        root.addView(status, matchWrap());

        NestedScrollView scroll = new NestedScrollView(activity);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        action = YoButton.create(activity).build();
        action.setText(activity.getString(R.string.device_scan_start));
        action.setOnClickListener(v -> listener.onScan());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(48));
        actionParams.setMargins(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_M));
        root.addView(action.getView(), actionParams);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @NonNull
    public View view() {
        return root;
    }

    public void destroy() {
        action.destroy();
    }

    public void render(@NonNull ScanUiState state) {
        action.setText(activity.getString(state.scanning()
                ? R.string.device_scan_stop : R.string.device_scan_start));
        List<ScanItem> results = state.results();
        if (results.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(state.scanning()
                    ? activity.getString(R.string.device_scan_searching)
                    : activity.getString(R.string.device_scan_found_count, results.size()));
        }
        rebuild(results, state.scanning(), state.hasScanned());
    }

    private void rebuild(List<ScanItem> results, boolean scanning, boolean hasScanned) {
        list.removeAllViews();
        if (results.isEmpty()) {
            list.addView(buildEmptyState(scanning, hasScanned), matchWrap());
            return;
        }
        for (ScanItem result : results) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
            params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
            params.topMargin = theme.dp(DeviceTheme.SPACE_S);
            list.addView(new ScanDeviceCard(activity, theme, result,
                    () -> listener.onConnect(result)).view(), params);
        }
    }

    private View buildEmptyState(boolean scanning, boolean hasScanned) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XL));

        int titleRes;
        int bodyRes;
        if (scanning) {
            titleRes = R.string.device_scan_searching;
            bodyRes = R.string.device_scan_searching_hint;
        } else if (!hasScanned) {
            titleRes = R.string.device_scan_idle_title;
            bodyRes = R.string.device_scan_idle_body;
        } else {
            titleRes = R.string.device_scan_empty_title;
            bodyRes = R.string.device_scan_empty_body;
        }

        TextView heading = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        heading.setText(titleRes);
        heading.setLineSpacing(theme.dpf(5), 1f);
        block.addView(heading, matchWrap());

        TextView body = theme.text(DeviceTheme.TEXT_CALLOUT, theme.text2);
        body.setText(bodyRes);
        body.setLineSpacing(theme.dpf(5), 1f);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        block.addView(body, bodyParams);
        return block;
    }

    private View buildHeader() {
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

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.device_scan_title);
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

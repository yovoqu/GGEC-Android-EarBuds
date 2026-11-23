package com.ggec.earbuds.ui.device.wear;

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
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceLayouts;
import com.ggec.earbuds.ui.device.component.DeviceSectionHeader;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 佩戴设置独立页：分区标题「佩戴」+ 原生 YoList（佩戴检测开关）。与其它二级页同一视觉口径。
 */
public final class WearSettingsScreen {

    public interface Listener {
        void onBack();

        void onToggle(boolean enabled);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;

    private final LinearLayout root;
    private final LinearLayout listHost;
    private YoComponents components = new YoComponents();
    private WearSettingsUiState rendered;

    public WearSettingsScreen(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        root.addView(buildHeader(), DeviceLayouts.matchWrap());

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, theme.dp(DeviceTheme.SPACE_S), 0, theme.dp(DeviceTheme.SPACE_XL));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView header = DeviceSectionHeader.create(activity, theme,
                activity.getString(R.string.wear_settings_section));
        LinearLayout.LayoutParams headerParams = DeviceLayouts.matchWrap();
        headerParams.bottomMargin = theme.dp(DeviceTheme.SPACE_S);
        content.addView(header, headerParams);

        listHost = new LinearLayout(activity);
        listHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(listHost, DeviceLayouts.matchWrap());

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

    public void render(@NonNull WearSettingsUiState state) {
        if (state.equals(rendered)) {
            return;
        }
        rendered = state;
        components.close();
        components = new YoComponents();
        listHost.removeAllViews();

        YoList list = YoList.create(activity).build();
        components.own(list::destroy);
        ListSuffixSpec suffix = ListSuffixSpec.switch_(state.checked(), null);
        if (state.enabled()) {
            suffix = ListSuffixSpec.switch_(state.checked(), next -> listener.onToggle(next));
        }
        list.addItem(ListPrefixSpec.none(),
                ListTextSpec.of(activity.getString(R.string.wear_detect_title),
                        activity.getString(R.string.wear_detect_subtitle)),
                suffix);
        listHost.addView(list.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void destroy() {
        components.close();
        listHost.removeAllViews();
        rendered = null;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));
        int side = theme.dp(DeviceTheme.PAGE_INSET);
        header.setPadding(side, 0, side, 0);

        AiHeaderActionButton back = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.device_back)
                .onClick(v -> listener.onBack());
        header.addView(back, back.createLinearLayoutParams());

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1,
                DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.wear_settings_title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        header.addView(title, titleParams);
        return header;
    }
}

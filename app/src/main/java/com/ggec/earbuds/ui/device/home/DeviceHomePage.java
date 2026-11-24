package com.ggec.earbuds.ui.device.home;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
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
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceLayouts;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/**
 * 设备首页页面：只做装配。标题栏（{@link DeviceTitleView}）→ 概览（{@link DeviceHeroSectionView}）
 * → 五个版块（{@link DeviceHomeSectionsView}），或空态。分组策略在 {@link DeviceHomeUiMapper}。
 */
public final class DeviceHomePage {

    public interface Listener {
        void onOpenScan();

        void onSelectDevice(@NonNull String key);

        void onOpenEntry(@NonNull DeviceCapabilityGroup group);

        void onOpenPage(@NonNull String pageId);

        void onQuickToggle(@NonNull String capabilityId, boolean enabled);

        void onCheckAppUpdate();
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final YoComponents components = new YoComponents();
    private final Listener listener;

    private final LinearLayout root;
    private final DeviceTitleView titleView;
    private final NestedScrollView scroll;
    private final LinearLayout contentColumn;
    private final DeviceHomeSectionsView sections;
    private final View contentFooter;
    private final LinearLayout emptyBox;
    private final TextView emptyTitle;
    private final TextView emptyBody;

    private List<DeviceHomeUiState.Choice> choices = List.of();
    private int bottomInset;
    private int lastSystemBottom;

    public DeviceHomePage(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        titleView = new DeviceTitleView(activity, theme, this::showDeviceMenu);
        root.addView(buildHeader(), DeviceLayouts.matchWrap());

        FrameLayout host = new FrameLayout(activity);
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        contentColumn = new LinearLayout(activity);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(contentColumn, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        sections = new DeviceHomeSectionsView(activity, theme, listener);
        contentFooter = DeviceHomeFooterView.create(activity, theme, components,
                listener::onCheckAppUpdate);

        emptyBox = new LinearLayout(activity);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), 0);
        emptyTitle = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        emptyTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.addView(emptyTitle, DeviceLayouts.matchWrap());
        emptyBody = theme.text(DeviceTheme.TEXT_CALLOUT, theme.hint);
        emptyBody.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBody.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        emptyBody.setPadding(0, theme.dp(DeviceTheme.SPACE_S), 0, 0);
        emptyBox.addView(emptyBody, DeviceLayouts.matchWrap());
        emptyBox.addView(new Space(activity), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        emptyBox.addView(DeviceHomeFooterView.create(activity, theme, components,
                listener::onCheckAppUpdate), DeviceLayouts.matchWrap());
        emptyBox.setVisibility(View.GONE);
        host.addView(emptyBox, new FrameLayout.LayoutParams(
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
        choices = state.devices();
        titleView.render(state);

        boolean hasContent = !state.title().isEmpty();
        scroll.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        emptyBox.setVisibility(hasContent ? View.GONE : View.VISIBLE);
        if (!hasContent) {
            emptyTitle.setText(state.emptyTitleRes());
            emptyBody.setText(state.emptyBodyRes());
            sections.dispose();
            return;
        }
        rebuild(state);
    }

    public void destroy() {
        sections.dispose();
        components.close();
    }

    private void showDeviceMenu() {
        if (choices.isEmpty()) {
            return;
        }
        YoMenu.Builder builder = YoMenu.create(activity).setAnchorView(titleView.anchor());
        for (int index = 0; index < choices.size(); index++) {
            DeviceHomeUiState.Choice choice = choices.get(index);
            YoMenu.Item item = YoMenu.Item.of(index, choice.name())
                    .subtitle(choice.address() + DeviceFormats.META_SEPARATOR + choice.modelName());
            if (choice.connected()) {
                item.endIcon(R.drawable.ic_device_status_connected).endIconTinted(false);
            }
            builder.addItem(item);
        }
        YoMenu menu = builder
                .setOnItemClickListener(id -> {
                    if (id >= 0 && id < choices.size()) {
                        listener.onSelectDevice(choices.get(id).key());
                    }
                })
                .build();
        components.own(menu::destroy);
        menu.show();
    }

    private void rebuild(@NonNull DeviceHomeUiState state) {
        contentColumn.removeAllViews();
        contentColumn.addView(DeviceHeroSectionView.create(activity, theme, state),
                DeviceLayouts.pageInset(theme, DeviceTheme.SPACE_S, 0f));
        sections.render(state.sections());
        contentColumn.addView(sections.view(),
                DeviceLayouts.verticalOnly(theme, 0f, DeviceTheme.SPACE_L));
        contentColumn.addView(contentFooter, DeviceLayouts.matchWrap());
    }

    private void applyScrollBottom(int systemBottom) {
        lastSystemBottom = systemBottom;
        int bottom = Math.max(systemBottom, bottomInset) + theme.dp(DeviceTheme.SPACE_XL);
        scroll.setPadding(0, 0, 0, bottom);
        emptyBox.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), bottom);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));

        row.addView(titleView.view(), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        AiHeaderActionButton add = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_plus)
                .contentDesc(R.string.device_add)
                .onClick(v -> listener.onOpenScan());
        LinearLayout.LayoutParams addParams = add.createLinearLayoutParams();
        addParams.setMarginStart(theme.dp(DeviceTheme.SPACE_XS));
        row.addView(add, addParams);

        header.addView(row, DeviceLayouts.matchWrap());
        return header;
    }
}

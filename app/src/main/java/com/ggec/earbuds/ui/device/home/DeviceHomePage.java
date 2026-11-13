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
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/**
 * 设备页视图：标题组件（设备信息 + 下拉）、能力入口卡片、空态、添加设备。
 *
 * <p>职责拆分：设备信息与下拉放在 {@link DeviceTitleView}，单张能力卡放在
 * {@link DeviceEntryCardView}；本类只做页面装配、状态分发与菜单编排。下拉菜单用 YoUI 原生能力
 * （{@code subtitle} / {@code endIconTinted}），分隔线与按压反馈由组件自身提供。</p>
 */
public final class DeviceHomePage {

    public interface Listener {
        void onOpenScan();

        void onSelectDevice(@NonNull String key);

        void onOpenEntry(@NonNull DeviceCapabilityGroup group);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final YoComponents components = new YoComponents();
    private final Listener listener;

    private final LinearLayout root;
    private final DeviceTitleView titleView;
    private final NestedScrollView scroll;
    private final LinearLayout cardColumn;
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
        root.addView(buildHeader(), matchWrap());

        FrameLayout host = new FrameLayout(activity);
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        cardColumn = new LinearLayout(activity);
        cardColumn.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cardColumn, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        emptyBox = new LinearLayout(activity);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), 0);
        emptyTitle = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        emptyTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.addView(emptyTitle, matchWrap());
        emptyBody = theme.text(DeviceTheme.TEXT_CALLOUT, theme.hint);
        emptyBody.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBody.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        emptyBody.setPadding(0, theme.dp(DeviceTheme.SPACE_S), 0, 0);
        emptyBox.addView(emptyBody, matchWrap());
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

        boolean hasEntries = !state.entries().isEmpty();
        scroll.setVisibility(hasEntries ? View.VISIBLE : View.GONE);
        emptyBox.setVisibility(hasEntries ? View.GONE : View.VISIBLE);
        if (!hasEntries) {
            emptyTitle.setText(state.emptyTitleRes());
            emptyBody.setText(state.emptyBodyRes());
            return;
        }
        rebuild(state.entries());
    }

    public void destroy() {
        components.close();
    }

    /** 菜单 = 设备列表（原生主副标题 + 右侧状态点）+ 分隔线 + 重命名/忘记。 */
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

    private void rebuild(@NonNull List<DeviceHomeUiState.Entry> entries) {
        cardColumn.removeAllViews();
        for (DeviceHomeUiState.Entry entry : entries) {
            DeviceEntryCardView card = new DeviceEntryCardView(activity, theme, entry.group(),
                    listener::onOpenEntry);
            card.render(entry);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
            params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
            params.topMargin = theme.dp(DeviceTheme.SPACE_S);
            cardColumn.addView(card.view(), params);
        }
    }

    private void applyScrollBottom(int systemBottom) {
        lastSystemBottom = systemBottom;
        int bottom = Math.max(systemBottom, bottomInset) + theme.dp(DeviceTheme.SPACE_XL);
        scroll.setPadding(0, 0, 0, bottom);
    }

    /** 标题栏：左侧设备信息组件占满剩余宽度，右侧只留「添加设备」。 */
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

        header.addView(row, matchWrap());
        return header;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

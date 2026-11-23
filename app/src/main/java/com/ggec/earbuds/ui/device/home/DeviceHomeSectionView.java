package com.ggec.earbuds.ui.device.home;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceCardRow;
import com.ggec.earbuds.ui.device.component.DeviceCardView;
import com.ggec.earbuds.ui.device.component.DeviceInsets;
import com.ggec.earbuds.ui.device.component.DeviceLayouts;
import com.ggec.earbuds.ui.device.component.DeviceSectionHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * 定制组件：一个版块 = 标题 + 卡片排布。
 *
 * <p>卡片用公共 {@link DeviceCardView}，整列/半列交给 {@link DeviceCardRow}。开关卡里的
 * {@code YoSwitch} 需要释放：本版块自带一个 {@link YoComponents}，每次重建前先 close 释放旧开关、
 * 再换新集合登记本次开关；页面销毁时 {@link #dispose()} 释放最后一批。</p>
 */
final class DeviceHomeSectionView {

    private final Context context;
    private final DeviceTheme theme;
    private final DeviceHomePage.Listener listener;
    private final LinearLayout root;
    private final TextView header;
    private final LinearLayout cardArea;
    private YoComponents cardComponents = new YoComponents();

    DeviceHomeSectionView(@NonNull Context context, @NonNull DeviceTheme theme,
                          @NonNull DeviceHomePage.Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        header = DeviceSectionHeader.create(context, theme, "");
        LinearLayout.LayoutParams headerParams = DeviceLayouts.matchWrap();
        headerParams.bottomMargin = theme.dp(DeviceTheme.SPACE_M);
        root.addView(header, headerParams);

        cardArea = new LinearLayout(context);
        cardArea.setOrientation(LinearLayout.VERTICAL);
        // 卡片外沿离屏幕 LIST_EDGE；卡片自身再给标题内边距 → 版块标题与卡片标题同线。
        int side = DeviceInsets.page(theme);
        cardArea.setPadding(side, 0, side, 0);
        root.addView(cardArea, DeviceLayouts.matchWrap());
    }

    @NonNull
    View view() {
        return root;
    }

    void bind(@NonNull DeviceHomeUiState.Section section) {
        // 释放上一批开关，再换新集合，避免重复渲染累积 YoSwitch。
        cardComponents.close();
        cardComponents = new YoComponents();

        boolean show = !section.cards().isEmpty();
        root.setVisibility(show ? View.VISIBLE : View.GONE);
        cardArea.removeAllViews();
        if (!show) {
            return;
        }
        header.setText(section.titleRes());

        List<DeviceCardRow.Item> items = new ArrayList<>(section.cards().size());
        for (DeviceHomeUiState.Card card : section.cards()) {
            if (card instanceof DeviceHomeUiState.EntryCard entry) {
                View view = DeviceCardView.entry(context, theme,
                        context.getString(entry.titleRes()), entry.iconRes(),
                        () -> listener.onOpenEntry(entry.group()));
                items.add(new DeviceCardRow.Item(view, entry.size() == DeviceHomeUiState.CardSize.HALF));
            } else if (card instanceof DeviceHomeUiState.ToggleCard toggle) {
                View view = DeviceCardView.toggle(context, theme, cardComponents, toggle.title(),
                        toggle.iconRes(), toggle.checked(), toggle.enabled() && !toggle.pending(),
                        checked -> listener.onQuickToggle(toggle.capabilityId(), checked));
                items.add(new DeviceCardRow.Item(view, toggle.size() == DeviceHomeUiState.CardSize.HALF));
            } else if (card instanceof DeviceHomeUiState.PageCard page) {
                View view = DeviceCardView.entry(context, theme,
                        context.getString(page.titleRes()), page.iconRes(),
                        () -> listener.onOpenPage(page.pageId()));
                items.add(new DeviceCardRow.Item(view, page.size() == DeviceHomeUiState.CardSize.HALF));
            }
        }
        cardArea.addView(DeviceCardRow.build(context, theme, items), DeviceLayouts.matchWrap());
    }

    void dispose() {
        cardComponents.close();
    }
}

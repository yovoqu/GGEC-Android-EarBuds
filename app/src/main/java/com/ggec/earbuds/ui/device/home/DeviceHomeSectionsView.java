package com.ggec.earbuds.ui.device.home;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceLayouts;

import java.util.ArrayList;
import java.util.List;

/**
 * 定制组件：首页正文五个大标题版块（设备连接中心 / 声音 / 交互 / 佩戴 / 更多）。
 * 固定 5 个 {@link DeviceHomeSectionView} 槽位，空版块整块隐藏；版块间距由槽位上外边距给出。
 */
final class DeviceHomeSectionsView {

    private static final int SLOT_COUNT = 5;

    private final DeviceTheme theme;
    private final LinearLayout view;
    private final List<DeviceHomeSectionView> slots = new ArrayList<>(SLOT_COUNT);

    DeviceHomeSectionsView(@NonNull Context context, @NonNull DeviceTheme theme,
                           @NonNull DeviceHomePage.Listener listener) {
        this.theme = theme;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < SLOT_COUNT; index++) {
            DeviceHomeSectionView slot = new DeviceHomeSectionView(context, theme, listener);
            slots.add(slot);
            view.addView(slot.view(), slotParams(index));
        }
        view.setVisibility(View.GONE);
    }

    @NonNull
    View view() {
        return view;
    }

    void render(@NonNull List<DeviceHomeUiState.Section> sections) {
        view.setVisibility(View.VISIBLE);
        for (int index = 0; index < slots.size(); index++) {
            DeviceHomeUiState.Section section = index < sections.size()
                    ? sections.get(index)
                    : new DeviceHomeUiState.Section(0, List.of());
            slots.get(index).bind(section);
        }
    }

    void dispose() {
        for (DeviceHomeSectionView slot : slots) {
            slot.dispose();
        }
    }

    private LinearLayout.LayoutParams slotParams(int index) {
        LinearLayout.LayoutParams params = DeviceLayouts.matchWrap();
        params.topMargin = theme.dp(index == 0 ? DeviceTheme.SPACE_L : DeviceTheme.SPACE_XL);
        return params;
    }
}

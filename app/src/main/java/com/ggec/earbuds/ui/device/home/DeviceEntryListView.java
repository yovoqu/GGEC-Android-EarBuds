package com.ggec.earbuds.ui.device.home;

import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DevicePage;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.List;

/** 设备首页功能入口列表：能力变化时重建一次 YoList。 */
public final class DeviceEntryListView {

    public interface OnEntryClick {
        void onClick(@NonNull DevicePage page);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final YoComponents components;
    private final OnEntryClick listener;
    private final FrameLayout host;
    private String signature = "";
    private List<DeviceHomeUiState.Entry> current = List.of();

    public DeviceEntryListView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                               @NonNull YoComponents components, @NonNull OnEntryClick listener) {
        this.activity = activity;
        this.theme = theme;
        this.components = components;
        this.listener = listener;
        host = new FrameLayout(activity);
    }

    @NonNull
    public View view() {
        return host;
    }

    public void render(@NonNull List<DeviceHomeUiState.Entry> entries) {
        StringBuilder next = new StringBuilder();
        for (DeviceHomeUiState.Entry entry : entries) {
            next.append(entry.page().name()).append('|');
        }
        if (next.toString().equals(signature)) {
            return;
        }
        signature = next.toString();
        current = entries;
        host.removeAllViews();

        YoList.Builder builder = YoList.create(activity);
        for (DeviceHomeUiState.Entry entry : entries) {
            builder.addItem(
                    ListPrefixSpec.iconTinted(entry.iconRes(), theme.text2),
                    ListTextSpec.of(activity.getString(entry.titleRes())),
                    ListSuffixSpec.arrow());
        }
        YoList list = builder.build();
        components.own(list::destroy);
        list.setOnItemClickListener(index -> {
            if (index >= 0 && index < current.size()) {
                listener.onClick(current.get(index).page());
            }
        });
        host.addView(list.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }
}

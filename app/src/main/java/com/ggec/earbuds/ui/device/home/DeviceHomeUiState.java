package com.ggec.earbuds.ui.device.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;

import java.util.List;

/**
 * 设备页的界面状态（不可变）。
 *
 * <p>标题栏两行：主标题是当前连接设备的名称，副标题是「MAC · 型号」；标题右侧是设备下拉，
 * 每项只给名称、MAC、型号，已连接的那台带绿点。正文只有能力入口卡片——按 biz 目录里这台
 * 机型真正具备的分区生成，没有横排设备卡片。</p>
 */
public record DeviceHomeUiState(@NonNull String title,
                                @NonNull String subtitle,
                                @NonNull List<Choice> devices,
                                @NonNull List<Entry> entries,
                                @StringRes int emptyTitleRes,
                                @StringRes int emptyBodyRes) {

    public DeviceHomeUiState {
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        devices = devices == null ? List.of() : List.copyOf(devices);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** 下拉里的一台设备。 */
    public record Choice(@NonNull String key,
                         @NonNull String name,
                         @NonNull String address,
                         @NonNull String modelName,
                         boolean connected) {

        public Choice {
            key = key == null ? "" : key;
            name = name == null ? "" : name;
            address = address == null ? "" : address;
            modelName = modelName == null ? "" : modelName;
        }
    }

    /** 一个能力入口卡片。 */
    public record Entry(@NonNull DeviceCapabilityGroup group,
                        @StringRes int titleRes,
                        @DrawableRes int iconRes,
                        int count) {
    }
}

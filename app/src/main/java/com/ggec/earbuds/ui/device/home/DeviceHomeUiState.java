package com.ggec.earbuds.ui.device.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;

import java.util.List;
import java.util.Optional;

/**
 * 设备首页界面状态（不可变）。
 *
 * <p>正文按 HarmonyOS 智慧生活的大标题版块组织：设备连接中心 / 声音 / 交互 / 佩戴 / 更多。
 * 每个版块是一组 {@link Card}：入口卡（带右箭头，可整列/半列）或开关卡（右侧原生开关）。
 * 版块顺序与内容由 {@link DeviceHomeUiMapper} 决定，视图只按 {@code sections} 渲染。</p>
 */
public record DeviceHomeUiState(@NonNull String title,
                                @NonNull String subtitle,
                                @NonNull List<Choice> devices,
                                @NonNull List<Section> sections,
                                @NonNull BatterySnapshot battery,
                                @StringRes int statusTextRes,
                                boolean statusOnline,
                                @StringRes int emptyTitleRes,
                                @StringRes int emptyBodyRes) {

    public DeviceHomeUiState {
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        devices = devices == null ? List.of() : List.copyOf(devices);
        sections = sections == null ? List.of() : List.copyOf(sections);
        battery = battery == null ? BatterySnapshot.unknown() : battery;
    }

    public boolean hasContent() {
        for (Section section : sections) {
            if (!section.cards().isEmpty()) {
                return true;
            }
        }
        return false;
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

    /** 一个大标题版块：标题 + 卡片序列。 */
    public record Section(@StringRes int titleRes, @NonNull List<Card> cards) {

        public Section {
            cards = cards == null ? List.of() : List.copyOf(cards);
        }
    }

    /** 卡片宽度：整列（一行一列）/ 半列（一行两列）。 */
    public enum CardSize {
        FULL,
        HALF
    }

    /** 卡片（sealed）：入口卡、开关卡，或跳转独立页的页面卡（查找设备 / 佩戴设置）。 */
    public sealed interface Card permits EntryCard, ToggleCard, PageCard {
        @NonNull
        CardSize size();
    }

    /** 入口卡：整行可点，尾槽为「彩色图标 + 右箭头」。 */
    public record EntryCard(@NonNull DeviceCapabilityGroup group,
                            @StringRes int titleRes,
                            @DrawableRes int iconRes,
                            @NonNull CardSize size) implements Card {
    }

    /** 页面卡：整行可点，打开独立功能页。{@code pageId} 由宿主路由到具体 Activity。 */
    public record PageCard(@NonNull String pageId,
                           @StringRes int titleRes,
                           @DrawableRes int iconRes,
                           @NonNull CardSize size) implements Card {

        public PageCard {
            pageId = pageId == null ? "" : pageId;
        }
    }

    /** 开关卡：尾槽为原生开关。 */
    public record ToggleCard(@NonNull String capabilityId,
                             @NonNull String title,
                             @DrawableRes int iconRes,
                             boolean checked,
                             boolean enabled,
                             boolean pending,
                             @NonNull CardSize size) implements Card {

        public ToggleCard {
            capabilityId = capabilityId == null ? "" : capabilityId;
            title = title == null ? "" : title;
        }
    }

    /** 写入状态（内联开关用）。 */
    public enum WriteState {
        PENDING,
        CONFIRMED,
        FAILED
    }

    /** 一次写入槽：带本次值，失败即丢弃（呈现回退到设备上报值）。 */
    public record WriteSlot(@NonNull WriteState state, @NonNull Optional<Boolean> value) {

        public WriteSlot {
            value = value == null ? Optional.empty() : value;
        }
    }
}

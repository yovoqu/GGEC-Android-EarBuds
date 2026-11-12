package com.ggec.earbuds.ui.device.home;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.device.DeviceFormats;

import java.util.List;
import java.util.Optional;

/**
 * 设备页签的界面状态（不可变）。
 *
 * <p>分区与行全部由能力描述符生成：{@link Section#group()} 是分区（按
 * {@link DeviceCapabilityGroup} 声明顺序），{@link Row} 是行，控件种类由描述符的 kind 决定。
 * 这里没有任何机型知识，也没有写死的功能清单。</p>
 */
public record DeviceHomeUiState(
        String title,
        String subtitle,
        int connectionTextRes,
        boolean connected,
        String leftText,
        String rightText,
        String caseText,
        int wearTextRes,
        int chargingTextRes,
        String firmware,
        boolean interactive,
        List<Section> sections
) {

    public DeviceHomeUiState {
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        leftText = leftText == null ? DeviceFormats.UNKNOWN : leftText;
        rightText = rightText == null ? DeviceFormats.UNKNOWN : rightText;
        caseText = caseText == null ? DeviceFormats.UNKNOWN : caseText;
        firmware = firmware == null ? "" : firmware;
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** 一个能力分区：分组决定分区，行顺序等于描述符声明顺序。 */
    public record Section(DeviceCapabilityGroup group, int titleRes, List<Row> rows) {

        public Section {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** 一行：能力 id 与标题都来自描述符，控件由 kind 决定。 */
    public sealed interface Row {

        String capabilityId();

        String title();

        /** TOGGLE：直接开关；{@code checked} 为空表示设备未上报该状态。 */
        record Toggle(String capabilityId, String title, Optional<Boolean> checked) implements Row {

            public Toggle {
                checked = checked == null ? Optional.empty() : checked;
            }
        }

        /** CHOICE：点开选项；{@code value} 为空表示未上报当前选项。 */
        record Choice(String capabilityId, String title, Optional<String> value,
                      List<CapabilityOption> options) implements Row {

            public Choice {
                value = value == null ? Optional.empty() : value;
                options = options == null ? List.of() : List.copyOf(options);
            }
        }

        /** LEVEL / CURVE / LIST / ACTION：首页只做入口，点击跳到所属分区的设置页。 */
        record Link(String capabilityId, String title) implements Row {
        }

    }

    public static DeviceHomeUiState disconnected() {
        return new DeviceHomeUiState("", "", R.string.device_state_idle, false,
                DeviceFormats.UNKNOWN, DeviceFormats.UNKNOWN, DeviceFormats.UNKNOWN,
                0, 0, "", false, List.of());
    }
}

package com.ggec.earbuds.ui.device.settings;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 设置页的行模型（强类型封闭集合）。
 *
 * <p>行由能力描述符生成：{@code capabilityId} 与 {@code title} 来自描述符，控件种类来自 kind。
 * 顶层分成两族，渲染层据此分派、无需再猜测：</p>
 * <ul>
 *   <li>{@link Entry}：聚成一个 YoList 卡片的轻行；</li>
 *   <li>{@link Component}：需要专门组件的行（滑杆 / 动作按钮组 / 列表组件）。</li>
 * </ul>
 * <p>需要专门组件的行带 {@link ActionKind} / {@link ListKind}，由
 * {@link SettingsSectionFactory} 的单一路由表产出，渲染层不再判断能力 id。</p>
 */
public sealed interface DeviceSettingRow {

    /** 轻行：可以聚在同一个列表卡片里。 */
    sealed interface Entry extends DeviceSettingRow {
    }

    /** 专门组件行：自带布局与交互。 */
    sealed interface Component extends DeviceSettingRow {
    }

    /** 开关行；{@code checked} 为空表示设备未上报该状态。 */
    record Toggle(String capabilityId, String title, Optional<Boolean> checked) implements Entry {

        public Toggle {
            checked = checked == null ? Optional.empty() : checked;
        }
    }

    /** 选择行；{@code value} 为空表示未上报当前选项。 */
    record Choice(String capabilityId, String title, Optional<String> value,
                  List<CapabilityOption> options) implements Entry {

        public Choice {
            value = value == null ? Optional.empty() : value;
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /**
     * 曲线入口行：自定义 EQ。
     *
     * <p>{@code options} 是描述符给出的频段表（段数与标签），{@code range} 是增益值域；
     * 编辑器只渲染这两项，不猜段数、不猜取值域。缺任何一个，工厂不会生成该行
     * （目录不变量被违反 → 能力不可用）。{@code points} 为空表示未上报当前曲线；
     * {@code name} 是这条自定义音效的 APP 侧命名（设备不承载，可空）。</p>
     */
    record Curve(String capabilityId, String title, List<CapabilityOption> options,
                 Optional<ControlRange> range, Optional<List<Integer>> points,
                 Optional<String> name) implements Entry {

        public Curve {
            options = options == null ? List.of() : List.copyOf(options);
            range = range == null ? Optional.empty() : range;
            points = points == null ? Optional.empty() : points;
            name = name == null ? Optional.empty() : name;
        }
    }

    /** 设备事实行（固件、地址等），不来自能力。 */
    record Info(int titleRes, String value) implements Entry {
    }

    /** 页面级说明行：只用来把「本页为什么是空的」说清楚（例如缺这款机型的描述）。 */
    record Notice(int textRes) implements Entry {
    }


    /** 能力 id 无法路由到本机组件：显式可见，不静默丢弃。 */
    record Unsupported(String capabilityId, String title) implements Entry {
    }

    /** 档位滑杆行：范围来自描述符（{@code range}），界面不做值域推导。 */
    record Level(String capabilityId, String title, ControlRange range, OptionalInt value)
            implements Component {

        public Level {
            value = value == null ? OptionalInt.empty() : value;
        }
    }

    /** 动作行：具体按钮组由 {@link ActionKind} 决定。 */
    record Action(String capabilityId, String title, ActionKind kind) implements Component {
    }

    /** 列表行：具体列表组件由 {@link ListKind} 决定。 */
    record CapabilityList(String capabilityId, String title, ListKind kind) implements Component {
    }

    /** 需要专门按钮组的动作类型：新增能力只需在工厂的路由表里加一项。 */
    enum ActionKind {
        FIND_EAR,
        RENAME,
        REBOOT,
        CLEAR_PAIRING,
        RESET_CONTROL,
        RECORD_LOCAL,
        RECORD_STREAM,
        RECORD_TRANSFER,
        DEVICE_MANAGEMENT
    }

    /** 需要专门列表组件的列表类型。 */
    enum ListKind {
        DUAL_CONNECTION,
        RECORD_FILES
    }
}

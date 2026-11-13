package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceGroupLabels;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 通用设置行工厂：把能力描述符按 {@link DeviceCapabilityGroup} 顺序 + {@link DeviceCapabilityKind}
 * 翻成 {@link DeviceSettingRow}。
 *
 * <p>这里是「能力 id → 专门组件」的唯一路由表：TOGGLE/CHOICE 直接由描述符生成；LEVEL 由描述符的
 * range 生成；CURVE/LIST/ACTION 按 {@link DeviceCapabilityIds} 路由到自定义 EQ、录音列表、
 * 双连接设备列表、查找耳机、改名、重启、清除配对等专门组件；其余 id 生成
 * {@link DeviceSettingRow.Unsupported}（可见，不静默丢弃）。</p>
 */
public final class SettingsSectionFactory {

    /** 一个分区的行：分区按 {@link DeviceCapabilityGroup} 声明顺序输出，行保持描述符声明顺序。 */
    public record Section(DeviceCapabilityGroup group, int titleRes, List<DeviceSettingRow> rows) {

        public Section {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /**
     * 「关于设备」末尾的 APP 级设备管理行：重命名别名、忘记设备。
     *
     * <p>这两件事属于 APP 台账，不来自 biz 能力目录，所以由工厂以固定 id 追加，视图按 kind 渲染。</p>
     */
    @NonNull
    public DeviceSettingRow deviceManagementRow() {
        return new DeviceSettingRow.Action(DeviceCapabilityIds.DEVICE_MANAGEMENT, "",
                DeviceSettingRow.ActionKind.DEVICE_MANAGEMENT);
    }

    @NonNull
    public List<Section> build(@NonNull DeviceProfileView profile, @NonNull DeviceState state) {
        List<Section> sections = new ArrayList<>();
        for (DeviceCapabilityGroup group : DeviceCapabilityGroup.values()) {
            List<DeviceSettingRow> rows = rows(profile, state, group);
            if (!rows.isEmpty()) {
                sections.add(new Section(group, DeviceGroupLabels.title(group), rows));
            }
        }
        return sections;
    }

    /** 单个分区的行：按描述符声明顺序，跳过契约违约项（LEVEL 缺 range）与被聚合的其它分区。 */
    @NonNull
    public List<DeviceSettingRow> rows(@NonNull DeviceProfileView profile, @NonNull DeviceState state,
                                       @NonNull DeviceCapabilityGroup group) {
        List<DeviceSettingRow> rows = new ArrayList<>();
        for (DeviceCapabilityView capability : profile.capabilities()) {
            if (capability.group() != group) {
                continue;
            }
            row(capability, state).ifPresent(rows::add);
        }
        return rows;
    }

    /**
     * 七种 kind（{@code TOGGLE/LEVEL/CHOICE/CURVE/ACTION/LIST/GATE}）全部显式处理，没有兜底分支。
     *
     * <p>{@code GATE} 只用于功能门控（{@code DeviceProfileView.supports(id)}），不生成任何通用行；
     * {@code LEVEL} 缺 range 时为空（目录不变量被违反，不渲染）。</p>
     */
    private static Optional<DeviceSettingRow> row(DeviceCapabilityView capability, DeviceState state) {
        return switch (capability.kind()) {
            case TOGGLE -> Optional.of(new DeviceSettingRow.Toggle(
                    capability.id(), capability.title(), toggleOf(state, capability.id())));
            case CHOICE -> Optional.of(new DeviceSettingRow.Choice(
                    capability.id(), capability.title(), choiceLabel(capability, state),
                    capability.options()));
            case LEVEL -> capability.range().map(range -> levelRow(capability, range, state));
            case CURVE -> Optional.of(curveRow(capability, state));
            case ACTION -> Optional.of(actionRow(capability));
            case LIST -> Optional.of(listRow(capability));
            case GATE -> Optional.empty();
        };
    }

    private static DeviceSettingRow levelRow(DeviceCapabilityView capability, ControlRange range,
                                             DeviceState state) {
        return new DeviceSettingRow.Level(capability.id(), capability.title(), range,
                levelOf(state, capability.id()));
    }

    /** CURVE 只对自定义 EQ 有专门编辑器；其它曲线能力没有对应组件，显式标为未支持。 */
    private static DeviceSettingRow curveRow(DeviceCapabilityView capability, DeviceState state) {
        if (!DeviceCapabilityIds.CUSTOM_EQ.equals(capability.id())) {
            return new DeviceSettingRow.Unsupported(capability.id(), capability.title());
        }
        return new DeviceSettingRow.Curve(capability.id(), capability.title(),
                curveOf(state, capability.id()));
    }

    private static DeviceSettingRow actionRow(DeviceCapabilityView capability) {
        DeviceSettingRow.ActionKind kind = actionKindOf(capability.id());
        if (kind == null) {
            return new DeviceSettingRow.Unsupported(capability.id(), capability.title());
        }
        return new DeviceSettingRow.Action(capability.id(), capability.title(), kind);
    }

    private static DeviceSettingRow listRow(DeviceCapabilityView capability) {
        DeviceSettingRow.ListKind kind = listKindOf(capability.id());
        if (kind == null) {
            return new DeviceSettingRow.Unsupported(capability.id(), capability.title());
        }
        return new DeviceSettingRow.CapabilityList(capability.id(), capability.title(), kind);
    }

    /** 能力 id → 动作组件。未知 id 返回 null，由调用方生成未支持行。 */
    private static DeviceSettingRow.ActionKind actionKindOf(String capabilityId) {
        return switch (capabilityId) {
            case DeviceCapabilityIds.FIND_EAR -> DeviceSettingRow.ActionKind.FIND_EAR;
            case DeviceCapabilityIds.RENAME -> DeviceSettingRow.ActionKind.RENAME;
            case DeviceCapabilityIds.REBOOT -> DeviceSettingRow.ActionKind.REBOOT;
            case DeviceCapabilityIds.CLEAR_PAIRING -> DeviceSettingRow.ActionKind.CLEAR_PAIRING;
            case DeviceCapabilityIds.RESET_CONTROL -> DeviceSettingRow.ActionKind.RESET_CONTROL;
            case DeviceCapabilityIds.RECORD_LOCAL -> DeviceSettingRow.ActionKind.RECORD_LOCAL;
            case DeviceCapabilityIds.RECORD_STREAM -> DeviceSettingRow.ActionKind.RECORD_STREAM;
            case DeviceCapabilityIds.RECORD_TRANSFER -> DeviceSettingRow.ActionKind.RECORD_TRANSFER;
            case DeviceCapabilityIds.DEVICE_MANAGEMENT -> DeviceSettingRow.ActionKind.DEVICE_MANAGEMENT;
            default -> null;
        };
    }

    /** 能力 id → 列表组件。未知 id 返回 null，由调用方生成未支持行。 */
    private static DeviceSettingRow.ListKind listKindOf(String capabilityId) {
        return switch (capabilityId) {
            case DeviceCapabilityIds.DUAL_CONNECTION_DEVICES ->
                    DeviceSettingRow.ListKind.DUAL_CONNECTION;
            case DeviceCapabilityIds.RECORD_LIST -> DeviceSettingRow.ListKind.RECORD_FILES;
            default -> null;
        };
    }

    private static Optional<Boolean> toggleOf(DeviceState state, String capabilityId) {
        return state.value(capabilityId).flatMap(value -> switch (value) {
            case ControlValue.Toggle toggle -> Optional.of(toggle.enabled());
            case ControlValue.Level level -> Optional.empty();
            case ControlValue.Choice choice -> Optional.empty();
            case ControlValue.Curve curve -> Optional.empty();
        });
    }

    private static Optional<String> choiceLabel(DeviceCapabilityView capability, DeviceState state) {
        return state.value(capability.id()).flatMap(value -> switch (value) {
            case ControlValue.Choice choice -> optionTitle(capability.options(), choice.optionId());
            case ControlValue.Toggle toggle -> Optional.empty();
            case ControlValue.Level level -> Optional.empty();
            case ControlValue.Curve curve -> Optional.empty();
        });
    }

    private static OptionalInt levelOf(DeviceState state, String capabilityId) {
        return state.value(capabilityId).map(value -> switch (value) {
            case ControlValue.Level level -> OptionalInt.of(level.value());
            case ControlValue.Toggle toggle -> OptionalInt.empty();
            case ControlValue.Choice choice -> OptionalInt.empty();
            case ControlValue.Curve curve -> OptionalInt.empty();
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<List<Integer>> curveOf(DeviceState state, String capabilityId) {
        return state.value(capabilityId).flatMap(value -> switch (value) {
            case ControlValue.Curve curve -> Optional.of(curve.points());
            case ControlValue.Toggle toggle -> Optional.empty();
            case ControlValue.Level level -> Optional.empty();
            case ControlValue.Choice choice -> Optional.empty();
        });
    }

    private static Optional<String> optionTitle(List<CapabilityOption> options, String optionId) {
        for (CapabilityOption option : options) {
            if (option.id().equals(optionId)) {
                return Optional.of(option.title());
            }
        }
        return Optional.empty();
    }
}

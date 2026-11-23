package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.RecordFileView;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 设置页界面状态（不可变）：一页对应一个 {@link DeviceCapabilityGroup}。
 *
 * <p>{@code rows} 由 {@link SettingsSectionFactory} 按描述符生成；{@code writes} 是本页每一次
 * 控件写入的落地状态，按 {@code capabilityId} 索引——**写入状态的唯一来源**：
 * 行上「未确认 / 已确认 / 失败回退」的呈现全部从这里派生，视图层不再自己存一份。</p>
 *
 * <p>{@code dualConnection} / {@code recordFiles} 是列表类组件的设备事实（来自快照投影）。</p>
 */
public record DeviceSettingsUiState(
        @NonNull DeviceCapabilityGroup group,
        int titleRes,
        boolean connected,
        boolean interactive,
        @NonNull List<DeviceSettingRow> rows,
        @NonNull Map<String, WriteSlot> writes,
        @NonNull Optional<DualConnectionView> dualConnection,
        @NonNull List<RecordFileView> recordFiles) {

    public DeviceSettingsUiState {
        Objects.requireNonNull(group, "group");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        writes = Map.copyOf(Objects.requireNonNull(writes, "writes"));
        dualConnection = Objects.requireNonNull(dualConnection, "dualConnection");
        recordFiles = List.copyOf(Objects.requireNonNull(recordFiles, "recordFiles"));
    }

    /**
     * 一次控件写入的生命周期（APP 层对「命令状态」的抽象：发送 / 处理中 / 成功 / 失败）。
     *
     * <pre>
     * IDLE --(用户操作)--> SENDING --(链路写完成/设备受理)--> PROCESSING --(端口 Ok)--> CONFIRMED
     *                         \----------------(端口失败)------------------------------> FAILED
     * </pre>
     *
     * <p>状态 ↔ 命令阶段的映射：</p>
     * <ul>
     *   <li>{@code IDLE}：空闲，未发起命令；</li>
     *   <li>{@code SENDING}：发送中——命令已提交、正在写入链路（用户点击后立即进入，
     *       驱动「点击即先切换 + 禁用重复点击」）；</li>
     *   <li>{@code PROCESSING}：处理中——链路写完成 / 设备已受理，等待最终回执（仍禁用点击）；</li>
     *   <li>{@code CONFIRMED}：成功——设备已确认，解除禁用；</li>
     *   <li>{@code FAILED}：失败——丢弃本次值，解除禁用并回退到设备上报值。</li>
     * </ul>
     *
     * <p>{@code CONFIRMED} 保留本次写入值：设备没有 Query 能力时回读不到，界面仍需显示用户的选择；
     * {@code FAILED} 丢弃写入值，呈现回到设备上报值（回退由状态驱动，不再由视图手工撤销）。</p>
     */
    public enum WriteState {
        IDLE,
        SENDING,
        PROCESSING,
        CONFIRMED,
        FAILED;

        /** 是否处于「未落地」阶段（发送中或处理中）：此时应禁用重复点击并乐观呈现目标值。 */
        public boolean inFlight() {
            return this == SENDING || this == PROCESSING;
        }
    }

    /** 一个能力 id 的写入槽：状态 + 本次写入的值（未确认值）。 */
    public record WriteSlot(@NonNull WriteState state, @NonNull Optional<ControlValue> pendingValue) {

        public WriteSlot {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(pendingValue, "pendingValue");
        }

        @NonNull
        public static WriteSlot idle() {
            return new WriteSlot(WriteState.IDLE, Optional.empty());
        }

        @NonNull
        public Optional<Boolean> toggleValue() {
            return valueOf(ControlValue.Toggle.class).map(ControlValue.Toggle::enabled);
        }

        @NonNull
        public Optional<String> choiceValue() {
            return valueOf(ControlValue.Choice.class).map(ControlValue.Choice::optionId);
        }

        @NonNull
        public OptionalInt levelValue() {
            return valueOf(ControlValue.Level.class)
                    .map(level -> OptionalInt.of(level.value()))
                    .orElseGet(OptionalInt::empty);
        }

        @NonNull
        public Optional<List<Integer>> curveValue() {
            return valueOf(ControlValue.Curve.class).map(ControlValue.Curve::points);
        }

        /** 本次写入携带的自定义音效名称（APP 侧命名，设备不承载）。 */
        @NonNull
        public Optional<String> curveName() {
            return valueOf(ControlValue.Curve.class)
                    .map(ControlValue.Curve::name)
                    .filter(name -> !name.isEmpty());
        }

        @NonNull
        private <T extends ControlValue> Optional<T> valueOf(@NonNull Class<T> type) {
            return pendingValue.filter(type::isInstance).map(type::cast);
        }
    }

    /** 该能力 id 的写入槽；从未写过就是 {@link WriteState#IDLE} 的空槽。 */
    @NonNull
    public WriteSlot writeOf(@NonNull String capabilityId) {
        return writes.getOrDefault(capabilityId, WriteSlot.idle());
    }

    /** 未连接：整页空态，没有行也没有写入。 */
    @NonNull
    public static DeviceSettingsUiState unconnected(@NonNull DeviceCapabilityGroup group,
                                                    int titleRes) {
        return new DeviceSettingsUiState(group, titleRes, false, false, List.of(), Map.of(),
                Optional.empty(), List.of());
    }

    /**
     * 已连接但能力目录里没有这款机型的描述：显式空态（不是「什么都没有」）。
     *
     * <p>domain 不变量是「READY 必有 modelId」，但目录里缺这款机型的描述是配置事实，
     * 页面必须说出来，而不是渲染成一张空页。</p>
     */
    @NonNull
    public static DeviceSettingsUiState descriptorMissing(@NonNull DeviceCapabilityGroup group,
                                                          int titleRes,
                                                          boolean interactive,
                                                          @NonNull Optional<DualConnectionView> dualConnection,
                                                          @NonNull List<RecordFileView> recordFiles) {
        return new DeviceSettingsUiState(group, titleRes, true, interactive,
                List.of(new DeviceSettingRow.Notice(R.string.device_settings_descriptor_missing)),
                Map.of(), dualConnection, recordFiles);
    }
}

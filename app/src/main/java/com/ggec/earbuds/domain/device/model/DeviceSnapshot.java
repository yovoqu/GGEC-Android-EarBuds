package com.ggec.earbuds.domain.device.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 当前设备状态快照。BLE 地址、BLE 名称、经典地址、经典名称分开保存。
 * 空串表示这一侧还没上报。
 *
 * <p>{@code earColor} 保存设备回报的颜色协议原值（正数色号），未回报时取
 * {@link #UNKNOWN_EAR_COLOR}。领域层只持有协议事实，不持有任何展示文案；
 * 色号到界面文字的换算在展示层单点完成。</p>
 *
 * <p>{@code wearState} 的来源是设备主动上报的耳机在仓状态：两耳都在仓时确知未佩戴，取
 * {@link WearState#NOT_WORN}；有耳出仓或未上报时协议不能证明耳机戴在耳朵上，取
 * {@link WearState#UNKNOWN}。规则单点在 {@code provider/biz_ggec_hs/HsDevicePortAdapter}，
 * 本快照只承载结果。</p>
 *
 * <p>{@code state} 是能力 id 索引的控制值集合；{@code dualConnection} 与 {@code recordFiles}
 * 承载 LIST 类能力的数据，未上报时分别是空 Optional 与空列表。三者都来自 biz 快照/事件投影，
 * 不含任何机型知识。</p>
 *
 * <p><b>契约：</b>除 {@code modelId} 外所有字段非 null；缺失语义由调用方写成
 * 显式值（{@link BatterySnapshot#unknown()}、{@link WearState#UNKNOWN}、{@code ""}、
 * 空的 {@link DeviceState}、{@code Optional#empty()}、{@code List#of()}），
 * 本记录不替调用方补默认值：传 null 是上游未赋值的缺陷，在构造点直接抛出。
 * {@code modelId} 只在未连接（{@link #idle()}）时为 null，
 * 已连接状态下由端口实现保证非空。</p>
 */
public record DeviceSnapshot(
        ConnectionState connection,
        String modelId,
        BatterySnapshot battery,
        WearState wearState,
        String firmware,
        String bleAddress,
        String bleName,
        String classicAddress,
        String classicName,
        int earColor,
        DeviceState state,
        Optional<DualConnectionView> dualConnection,
        List<RecordFileView> recordFiles
) {

    /**
     * 设备尚未回报耳机颜色。与 {@code biz_ggec_hs} 的 {@code HsDevice.Info.UNKNOWN_EAR_COLOR}
     * 同值：协议色号为正整数，负值不会与真实值冲突。同值关系由
     * {@code HsDeviceInfoContractTest} 锁死。
     */
    public static final int UNKNOWN_EAR_COLOR = -1;

    public DeviceSnapshot {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(battery, "battery");
        Objects.requireNonNull(wearState, "wearState");
        Objects.requireNonNull(firmware, "firmware");
        Objects.requireNonNull(bleAddress, "bleAddress");
        Objects.requireNonNull(bleName, "bleName");
        Objects.requireNonNull(classicAddress, "classicAddress");
        Objects.requireNonNull(classicName, "classicName");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(dualConnection, "dualConnection");
        recordFiles = List.copyOf(Objects.requireNonNull(recordFiles, "recordFiles"));
    }

    /** 无设备快照：未知值全部在此显式写出，调用方不需要也不允许用 null 表达缺失。 */
    public static DeviceSnapshot idle() {
        return new DeviceSnapshot(ConnectionState.IDLE, null,
                BatterySnapshot.unknown(), WearState.UNKNOWN, "", "", "", "", "", UNKNOWN_EAR_COLOR,
                new DeviceState(Map.of()), Optional.empty(), List.of());
    }

    public boolean isConnected() {
        return connection.isConnected();
    }
}

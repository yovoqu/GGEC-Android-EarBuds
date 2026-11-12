package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 当前设备状态。{@code deviceName} 是 BLE 广播本地名，{@code mac} 是 BLE 扫描地址。
 * 经典蓝牙地址不进入这个快照。
 *
 * <p>{@code earColor} 保存设备回报的颜色协议原值（正数色号），未回报时取
 * {@link #UNKNOWN_EAR_COLOR}。领域层只持有协议事实，不持有任何展示文案；
 * 色号到界面文字的换算在展示层单点完成。</p>
 *
 * <p>{@code wear} 的来源是设备主动上报的耳机在仓状态：两耳都在仓时确知未佩戴，取
 * {@link WearState#NOT_WORN}；有耳出仓或未上报时协议不能证明耳机戴在耳朵上，取
 * {@link WearState#UNKNOWN}。规则单点在 {@code HsDevicePort}，本快照只承载结果。</p>
 *
 * <p><b>契约：</b>{@code connection}、{@code battery}、{@code wear}、{@code firmware}、
 * {@code deviceName}、{@code mac} 均非空。缺失语义由调用方写成显式值
 * （{@link BatterySnapshot#unknown()}、{@link WearState#UNKNOWN}、{@code ""}），
 * 本记录不替调用方补默认值：传 null 是上游未赋值的缺陷，在构造点直接抛出。
 * {@code modelId}/{@code variantId} 只在未连接（{@link #idle()}）时为 null，
 * 已连接状态下由 {@code HsDevicePort} 保证非空。</p>
 */
public record DeviceSnapshot(
        ConnectionState connection,
        String modelId,
        String variantId,
        BatterySnapshot battery,
        WearState wear,
        String firmware,
        String deviceName,
        String mac,
        int earColor
) {

    /**
     * 设备尚未回报耳机颜色。与 {@code biz_ggec_hs} 的 {@code HsDeviceInfo.UNKNOWN_EAR_COLOR}
     * 同值：协议色号为正整数，负值不会与真实值冲突。同值关系由
     * {@code HsDeviceInfoContractTest} 锁死。
     */
    public static final int UNKNOWN_EAR_COLOR = -1;

    public DeviceSnapshot {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(battery, "battery");
        Objects.requireNonNull(wear, "wear");
        Objects.requireNonNull(firmware, "firmware");
        Objects.requireNonNull(deviceName, "deviceName");
        Objects.requireNonNull(mac, "mac");
    }

    /** 无设备快照：未知值全部在此显式写出，调用方不需要也不允许用 null 表达缺失。 */
    public static DeviceSnapshot idle() {
        return new DeviceSnapshot(ConnectionState.IDLE, null, null,
                BatterySnapshot.unknown(), WearState.UNKNOWN, "", "", "", UNKNOWN_EAR_COLOR);
    }

    public boolean isConnected() {
        return connection.isConnected();
    }
}

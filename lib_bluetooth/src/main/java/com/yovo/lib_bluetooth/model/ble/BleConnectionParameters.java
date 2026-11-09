package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * BLE 连接参数快照（API 26+ onConnectionUpdated 协商结果；单位参考 SIG 规范转换后的整数/浮点值）。
 */
public final class BleConnectionParameters {

    private final int intervalUnits;
    private final int latency;
    private final int timeoutUnits;

    public BleConnectionParameters(int intervalUnits, int latency, int timeoutUnits) {
        this.intervalUnits = intervalUnits;
        this.latency = latency;
        this.timeoutUnits = timeoutUnits;
    }

    /** 原始连接间隔单元（每单位 1.25ms）。 */
    public int intervalUnits() {
        return intervalUnits;
    }

    /** 连接间隔（毫秒 = intervalUnits * 1.25ms）。 */
    public double intervalMillis() {
        return intervalUnits * 1.25d;
    }

    /** Peripheral Latency（跳过连接事件数）。 */
    public int latency() {
        return latency;
    }

    /** 原始监控超时单元（每单位 10ms）。 */
    public int timeoutUnits() {
        return timeoutUnits;
    }

    /** 监控超时（毫秒 = timeoutUnits * 10ms）。 */
    public int supervisionTimeoutMillis() {
        return timeoutUnits * 10;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleConnectionParameters)) return false;
        BleConnectionParameters that = (BleConnectionParameters) o;
        return intervalUnits == that.intervalUnits
                && latency == that.latency
                && timeoutUnits == that.timeoutUnits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervalUnits, latency, timeoutUnits);
    }

    @NonNull
    @Override
    public String toString() {
        return "BleConnectionParameters{"
                + "intervalMs=" + intervalMillis()
                + ", latency=" + latency
                + ", timeoutMs=" + supervisionTimeoutMillis() + '}';
    }
}

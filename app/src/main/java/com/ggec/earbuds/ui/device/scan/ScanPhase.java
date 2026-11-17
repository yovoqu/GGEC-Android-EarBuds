package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceFailure;

import java.util.Objects;

/**
 * 扫描页的单一阶段字段：取代过去的 {@code scanning}/{@code hasScanned}/{@code connectPhase} 三源。
 *
 * <p>一次扫描轮次在 {@link Idle} → {@link Scanning} → {@link Stopped} 之间走；
 * 连接尝试是 {@link Connecting}，扫描失败是 {@link Failed}。列表内容不在这里表达，
 * 由 {@code ScanUiState.results()} 承载。</p>
 */
public sealed interface ScanPhase {

    /** 本轮还没开始扫（进页、或连接成功后回到这里）。 */
    record Idle() implements ScanPhase {
    }

    /** 正在扫描。 */
    record Scanning() implements ScanPhase {
    }

    /** 扫过一轮并已停止；有结果还是空态由结果列表决定。 */
    record Stopped() implements ScanPhase {
    }

    /** 正在连接 {@code address} 这台设备，扫描已让位。 */
    record Connecting(String address) implements ScanPhase {

        public Connecting {
            Objects.requireNonNull(address, "address");
        }
    }

    /** 本轮扫描以 {@code failure} 结束。提示走一次性事件，状态只表达「这一轮怎么收的」。 */
    record Failed(DeviceFailure failure) implements ScanPhase {

        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /** 是否正在扫描。 */
    default boolean scanning() {
        return this instanceof Scanning;
    }

    /** 是否正在连接某台设备。 */
    default boolean connecting() {
        return this instanceof Connecting;
    }

    /** 连接中的设备地址；不在连接阶段时为空串。 */
    @NonNull
    default String connectingAddress() {
        return this instanceof Connecting connecting ? connecting.address() : "";
    }
}

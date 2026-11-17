package com.ggec.earbuds.domain.device.session;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceTarget;

import java.util.Objects;

/**
 * 活动设备会话的显式状态。
 *
 * <p>四个状态各自带齐判定所需的数据，不靠「target 是否为 null + 快照连接态」两个变量拼出事实：
 * {@link Idle} 没有目标；{@link Connecting} 与 {@link Ready} 目标是当前会话设备；
 * {@link Failed} 保留这次尝试的目标与失败分类，直到下一次 open/close 覆盖它。</p>
 */
public sealed interface SessionState {

    /** 没有任何会话目标。 */
    record Idle() implements SessionState {
    }

    /** 正在为 {@code target} 建立链路。 */
    record Connecting(DeviceTarget target) implements SessionState {

        public Connecting {
            Objects.requireNonNull(target, "target");
        }
    }

    /** {@code target} 已就绪，可以下发控制与录音指令。 */
    record Ready(DeviceTarget target) implements SessionState {

        public Ready {
            Objects.requireNonNull(target, "target");
        }
    }

    /** 为 {@code target} 的连接尝试以 {@code failure} 结束。 */
    record Failed(DeviceTarget target, DeviceFailure failure) implements SessionState {

        public Failed {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(failure, "failure");
        }
    }
}

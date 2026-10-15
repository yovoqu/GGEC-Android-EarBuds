package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.Collections;
import java.util.EnumMap;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备发现相关事件的基类，各子类承载发现生命周期特定阶段的数据。
 */
public abstract class YoCbtDiscoveryEvent {

    private final YoCbtDiscoveryState state;
    private final long timestampMillis;

    /**
     * @param state           与本事件关联的发现状态
     * @param timestampMillis 事件发生时间（毫秒）
     */
    protected YoCbtDiscoveryEvent(@NonNull YoCbtDiscoveryState state, long timestampMillis) {
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.timestampMillis = timestampMillis;
    }

    /**
     * 返回本事件发生时的发现状态。
     */
    @NonNull
    public final YoCbtDiscoveryState getState() {
        return state;
    }

    /**
     * 返回本事件发生时的墙钟时间。
     */
    public final long getTimestampMillis() {
        return timestampMillis;
    }

    // ── 具体子类 ────────────────────────────────────────────

    /**
     * 发现会话已启动时触发的事件。
     */
    public static final class DiscoveryStartedEvent extends YoCbtDiscoveryEvent {
        private final YoCbtDiscoverySession session;

        public DiscoveryStartedEvent(@NonNull YoCbtDiscoverySession session, long timestampMillis) {
            super(YoCbtDiscoveryState.DISCOVERING, timestampMillis);
            this.session = Objects.requireNonNull(session, "session must not be null");
        }

        @NonNull
        public YoCbtDiscoverySession getSession() {
            return session;
        }

        @NonNull
        public String getOperationId() {
            return session.getOperationId();
        }
    }

    /**
     * 发现会话正常结束时触发的事件。
     */
    public static final class DiscoveryFinishedEvent extends YoCbtDiscoveryEvent {
        private final YoCbtDiscoverySession session;
        private final int deviceCount;

        public DiscoveryFinishedEvent(@NonNull YoCbtDiscoverySession session,
                                       int deviceCount,
                                       long timestampMillis) {
            super(YoCbtDiscoveryState.FINISHED, timestampMillis);
            this.session = Objects.requireNonNull(session, "session must not be null");
            this.deviceCount = deviceCount;
        }

        @NonNull
        public YoCbtDiscoverySession getSession() {
            return session;
        }

        @NonNull
        public String getOperationId() {
            return session.getOperationId();
        }

        /**
         * 返回本会话中发现的不重复设备数量。
         */
        public int getDeviceCount() {
            return deviceCount;
        }
    }

    /**
     * 发现过程中发现新设备时触发的事件。
     */
    public static final class DeviceFoundEvent extends YoCbtDiscoveryEvent {
        private final YoCbtObservedDevice observedDevice;
        private final boolean isUpdated;

        public DeviceFoundEvent(@NonNull YoCbtObservedDevice observedDevice,
                                 boolean isUpdated,
                                 long timestampMillis) {
            super(YoCbtDiscoveryState.DISCOVERING, timestampMillis);
            this.observedDevice = Objects.requireNonNull(observedDevice, "observedDevice must not be null");
            this.isUpdated = isUpdated;
        }

        /**
         * 返回发现广播中的观测设备数据。
         */
        @NonNull
        public YoCbtObservedDevice getObservedDevice() {
            return observedDevice;
        }

        /**
         * 返回设备身份（显示名、地址等）。
         */
        @NonNull
        public YoCbtDeviceIdentity getIdentity() {
            return observedDevice.getIdentity();
        }

        /**
         * 返回从观测设备提取的设备 ID。
         */
        @NonNull
        public YoCbtDeviceId getDeviceId() {
            return observedDevice.getDeviceId();
        }

        /**
         * 返回发现时刻的 RSSI（信号强度）。
         */
        public int getRssi() {
            return observedDevice.getRssi();
        }

        /**
         * 若为此前已见设备的更新（如 RSSI 刷新）则返回 {@code true}；
         * 若为首次发现则返回 {@code false}。
         */
        public boolean isUpdated() {
            return isUpdated;
        }

        /**
         * 返回由观测设备数据构建的发现结果，
         * 可传给 {@link YoCbtDiscoveryObserver#onDeviceFound}。
         */
        @NonNull
        public YoCbtDiscoveryResult toResult() {
            YoCbtDeviceSnapshot snapshot = new YoCbtDeviceSnapshot(
                    observedDevice.getIdentity(),
                    YoCbtBondState.UNKNOWN,
                    YoCbtAclLinkState.UNKNOWN,
                    Collections.emptyList(),
                    new EnumMap<YoCbtProfileType, YoCbtDeviceProfileState>(YoCbtProfileType.class),
                    Collections.emptyList(),
                    observedDevice.getRssi(),
                    getTimestampMillis(),
                    getTimestampMillis());
            return new YoCbtDiscoveryResult(snapshot, getTimestampMillis());
        }
    }

    /**
     * 发现会话失败时触发的事件。
     */
    public static final class DiscoveryFailedEvent extends YoCbtDiscoveryEvent {
        private final YoCbtDiscoverySession session;
        private final YoCbtFailure failure;

        public DiscoveryFailedEvent(@NonNull YoCbtDiscoverySession session,
                                     @NonNull YoCbtFailure failure,
                                     long timestampMillis) {
            super(YoCbtDiscoveryState.FAILED, timestampMillis);
            this.session = Objects.requireNonNull(session, "session must not be null");
            this.failure = Objects.requireNonNull(failure, "failure must not be null");
        }

        @NonNull
        public YoCbtDiscoverySession getSession() {
            return session;
        }

        @NonNull
        public String getOperationId() {
            return session.getOperationId();
        }

        /**
         * 返回失败详情，含错误码与消息。
         */
        @NonNull
        public YoCbtFailure getFailure() {
            return failure;
        }
    }
}

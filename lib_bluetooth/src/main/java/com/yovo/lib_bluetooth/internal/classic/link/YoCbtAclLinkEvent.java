package com.yovo.lib_bluetooth.internal.classic.link;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description ACL 链路状态变更事件，携带设备 ID、新/旧状态及发生时间戳。
 */
public final class YoCbtAclLinkEvent {

    @NonNull
    private final YoCbtDeviceId deviceId;

    @NonNull
    private final YoCbtAclLinkState state;

    @NonNull
    private final YoCbtAclLinkState previousState;

    private final long timestampMillis;

    /**
     * @param deviceId        ACL 状态发生变化的设备
     * @param state           新的 ACL 链路状态
     * @param previousState   变更前的 ACL 链路状态
     * @param timestampMillis 变更发生的墙钟时间（毫秒）
     */
    public YoCbtAclLinkEvent(@NonNull YoCbtDeviceId deviceId,
                              @NonNull YoCbtAclLinkState state,
                              @NonNull YoCbtAclLinkState previousState,
                              long timestampMillis) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.previousState = Objects.requireNonNull(previousState, "previousState must not be null");
        this.timestampMillis = timestampMillis;
    }

    /** 返回 ACL 状态发生变化的设备。 */
    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    /** 返回新的 ACL 链路状态。 */
    @NonNull
    public YoCbtAclLinkState getState() {
        return state;
    }

    /** 返回变更前的 ACL 链路状态。 */
    @NonNull
    public YoCbtAclLinkState getPreviousState() {
        return previousState;
    }

    /** 返回 ACL 状态变更发生的墙钟时间（毫秒）。 */
    public long getTimestampMillis() {
        return timestampMillis;
    }

    /** 若此事件表示切换到已连接状态则返回 {@code true}。 */
    public boolean isConnectEvent() {
        return state == YoCbtAclLinkState.CONNECTED
                && previousState != YoCbtAclLinkState.CONNECTED;
    }

    /** 若此事件表示切换到已断开状态则返回 {@code true}。 */
    public boolean isDisconnectEvent() {
        return state != YoCbtAclLinkState.CONNECTED
                && previousState == YoCbtAclLinkState.CONNECTED;
    }

    /**
     * 若此为断开事件，返回设备已连接的持续时间。
     * 要求前一状态为 {@link YoCbtAclLinkState#CONNECTED}。
     *
     * @return 连接时长（毫秒）；非断开事件或无时间数据时返回 -1
     */
    public long getConnectionDurationMillis() {
        if (!isDisconnectEvent()) return -1L;
        return -1L; // 时长跟踪需外部状态；事件本身仅含时间戳
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtAclLinkEvent)) return false;
        YoCbtAclLinkEvent that = (YoCbtAclLinkEvent) o;
        return timestampMillis == that.timestampMillis
                && deviceId.equals(that.deviceId)
                && state == that.state
                && previousState == that.previousState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, state, previousState, timestampMillis);
    }

    @Override
    public String toString() {
        return "YoCbtAclLinkEvent{"
                + "deviceId=" + deviceId
                + ", state=" + state
                + ", previousState=" + previousState
                + ", timestampMillis=" + timestampMillis
                + '}';
    }
}

package com.yovo.lib_bluetooth.internal.classic.link;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 某一时刻设备 ACL 链路状态的不可变快照，包含设备 ID、ACL 状态及连接时间信息。
 */
public final class YoCbtAclLinkSnapshot {

    @NonNull
    private final YoCbtDeviceId deviceId;

    @NonNull
    private final YoCbtAclLinkState state;

    private final long connectedAtMillis;

    private final long updatedAtMillis;

    /**
     * @param deviceId          所表示 ACL 状态的设备
     * @param state             当前 ACL 链路状态
     * @param connectedAtMillis 设备连接时间（毫秒），未连接时为 0
     * @param updatedAtMillis   快照最后更新时间（毫秒）
     */
    public YoCbtAclLinkSnapshot(@NonNull YoCbtDeviceId deviceId,
                                 @NonNull YoCbtAclLinkState state,
                                 long connectedAtMillis,
                                 long updatedAtMillis) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.connectedAtMillis = connectedAtMillis;
        this.updatedAtMillis = updatedAtMillis;
    }

    /** 返回此 ACL 快照对应的设备 ID。 */
    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    /** 返回当前 ACL 链路状态。 */
    @NonNull
    public YoCbtAclLinkState getState() {
        return state;
    }

    /**
     * 返回设备 ACL 链路建立连接的时间（毫秒），未连接时返回 0。
     */
    public long getConnectedAtMillis() {
        return connectedAtMillis;
    }

    /** 返回此快照最后更新的时间（毫秒）。 */
    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    /** 若 ACL 链路当前处于已连接状态则返回 {@code true}。 */
    public boolean isConnected() {
        return state == YoCbtAclLinkState.CONNECTED;
    }

    /**
     * 返回设备已连接的持续时间（毫秒）。
     *
     * @return 连接时长；未连接或 connectedAtMillis 为 0 时返回 -1
     */
    public long getConnectionDurationMillis() {
        if (!isConnected() || connectedAtMillis <= 0) {
            return -1L;
        }
        return System.currentTimeMillis() - connectedAtMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtAclLinkSnapshot)) return false;
        YoCbtAclLinkSnapshot that = (YoCbtAclLinkSnapshot) o;
        return connectedAtMillis == that.connectedAtMillis
                && updatedAtMillis == that.updatedAtMillis
                && deviceId.equals(that.deviceId)
                && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, state, connectedAtMillis, updatedAtMillis);
    }

    @Override
    public String toString() {
        return "YoCbtAclLinkSnapshot{"
                + "deviceId=" + deviceId
                + ", state=" + state
                + ", connectedAtMillis=" + connectedAtMillis
                + ", updatedAtMillis=" + updatedAtMillis
                + '}';
    }
}

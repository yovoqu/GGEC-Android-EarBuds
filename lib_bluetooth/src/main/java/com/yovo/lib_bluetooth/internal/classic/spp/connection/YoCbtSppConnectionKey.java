package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicy;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接唯一键，由设备 ID、端点与安全模式等组合标识一条连接。
 */

public final class YoCbtSppConnectionKey {
    private final YoCbtDeviceId deviceId;
    private final YoCbtSppEndpoint endpoint;
    private final YoCbtSppSecurityMode securityMode;
    private final YoCbtSppConnectionRole role;
    private final YoCbtSppConnectionPolicy connectionPolicy;

    public YoCbtSppConnectionKey(@NonNull YoCbtDeviceId deviceId,
                                 @NonNull YoCbtSppEndpoint endpoint,
                                 @NonNull YoCbtSppSecurityMode securityMode,
                                 @NonNull YoCbtSppConnectionRole role) {
        this(deviceId, endpoint, securityMode, role, YoCbtSppConnectionPolicy.defaultUserSpecified());
    }

    public YoCbtSppConnectionKey(@NonNull YoCbtDeviceId deviceId,
                                 @NonNull YoCbtSppEndpoint endpoint,
                                 @NonNull YoCbtSppSecurityMode securityMode,
                                 @NonNull YoCbtSppConnectionRole role,
                                 @NonNull YoCbtSppConnectionPolicy connectionPolicy) {
        this.deviceId = deviceId;
        this.endpoint = endpoint;
        this.securityMode = securityMode;
        this.role = role;
        this.connectionPolicy = connectionPolicy;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public YoCbtSppEndpoint getEndpoint() {
        return endpoint;
    }

    @NonNull
    public YoCbtSppSecurityMode getSecurityMode() {
        return securityMode;
    }

    @NonNull
    public YoCbtSppConnectionRole getRole() {
        return role;
    }

    @NonNull
    public YoCbtSppConnectionPolicy getConnectionPolicy() {
        return connectionPolicy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtSppConnectionKey)) return false;
        YoCbtSppConnectionKey that = (YoCbtSppConnectionKey) o;
        return deviceId.equals(that.deviceId)
                && endpoint.equals(that.endpoint)
                && securityMode == that.securityMode
                && role == that.role
                && connectionPolicy.equals(that.connectionPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, endpoint, securityMode, role, connectionPolicy);
    }
}

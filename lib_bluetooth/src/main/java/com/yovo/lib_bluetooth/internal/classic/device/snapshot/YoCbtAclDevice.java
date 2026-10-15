package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 当前已与本地适配器建立 ACL 基带链路的设备；ACL 不含 Profile 语义。
 */
public final class YoCbtAclDevice {
    private final YoCbtDeviceIdentity identity;
    private final YoCbtAclLinkState aclState;
    private final long connectedAtMillis;

    public YoCbtAclDevice(@NonNull YoCbtDeviceIdentity identity,
                          @NonNull YoCbtAclLinkState aclState,
                          long connectedAtMillis) {
        this.identity = identity;
        this.aclState = aclState;
        this.connectedAtMillis = connectedAtMillis;
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() { return identity; }

    @NonNull
    public YoCbtDeviceId getDeviceId() { return identity.getDeviceId(); }

    @NonNull
    public YoCbtAclLinkState getAclState() { return aclState; }

    public long getConnectedAtMillis() { return connectedAtMillis; }

    public boolean isConnected() {
        return aclState == YoCbtAclLinkState.CONNECTED;
    }
}

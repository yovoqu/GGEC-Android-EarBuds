package com.yovo.lib_common.system.keepalive.model;

import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;

public enum ForegroundServiceTypePolicy {
    CONNECTED_DEVICE(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE),
    DATA_SYNC(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC),
    NONE(0);

    private final int serviceType;

    ForegroundServiceTypePolicy(int serviceType) {
        this.serviceType = serviceType;
    }

    public int serviceType() {
        return serviceType;
    }

    @NonNull
    public static ForegroundServiceTypePolicy connectedDeviceDefault() {
        return CONNECTED_DEVICE;
    }
}

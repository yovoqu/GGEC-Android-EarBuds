package com.yovo.lib_bluetooth.internal.classic.service.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 实时 SDP 发现平台端口，负责发起与取消 {@code fetchUuidsWithSdp} 事务。
 */
public interface YoCbtLiveSdpPort {

    interface Listener {
        void onLiveSdpResult(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtSdpResult result);

        void onLiveSdpFailed(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtFailure failure);
    }

    void setListener(@Nullable Listener listener);

    void startDiscovery(@NonNull YoCbtDeviceId deviceId,
                        long timeoutMillis,
                        @NonNull YoTaskScheduler scheduler);

    void cancel(@NonNull YoCbtDeviceId deviceId);

    void cancelAll();
}

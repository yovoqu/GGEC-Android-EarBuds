package com.yovo.lib_bluetooth.internal.classic.spp.transport;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConfig;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import java.io.IOException;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 创建 SPP {@link android.bluetooth.BluetoothSocket} 的工厂。
 */

public interface YoCbtSppSocketFactory {

    @NonNull
    YoCbtSppSocket create(@NonNull String deviceAddress,
                          @NonNull YoCbtSppEndpoint endpoint,
                          @NonNull YoCbtSppConfig config) throws IOException;
}

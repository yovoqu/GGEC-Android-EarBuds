package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi.YoCbtHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi.YoCbtHiddenApiRfcommConnector;
import com.yovo.lib_bluetooth.internal.classic.internal.android.spp.YoCbtAndroidSppSocket;
import com.yovo.lib_bluetooth.internal.classic.spp.channel.YoCbtSppChannelPolicy;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConfig;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointType;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;
import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppSocket;
import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppSocketFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android SPP {@link YoCbtSppSocketFactory} 实现，创建标准与隐藏 API Socket。
 */

public final class YoCbtSppAndroidSocketFactory implements YoCbtSppSocketFactory {

    @NonNull
    private final BluetoothAdapter adapter;
    @NonNull
    private final YoCbtSppChannelPolicy defaultChannelPolicy;

    public YoCbtSppAndroidSocketFactory(@NonNull BluetoothAdapter adapter,
                                        @NonNull YoCbtSppChannelPolicy defaultChannelPolicy) {
        this.adapter = adapter;
        this.defaultChannelPolicy = defaultChannelPolicy;
    }

    @NonNull
    @Override
    public YoCbtSppSocket create(@NonNull String deviceAddress,
                                 @NonNull YoCbtSppEndpoint endpoint,
                                 @NonNull YoCbtSppConfig config) throws IOException {
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        BluetoothSocket socket = createAndroidSocket(device, endpoint, config);
        return new YoCbtAndroidSppSocket(socket);
    }

    @NonNull
    private BluetoothSocket createAndroidSocket(@NonNull BluetoothDevice device,
                                                  @NonNull YoCbtSppEndpoint endpoint,
                                                  @NonNull YoCbtSppConfig config) throws IOException {
        if (endpoint.getType() == YoCbtSppEndpointType.RFCOMM_CHANNEL) {
            if (!config.isAllowHiddenApiChannel() && !defaultChannelPolicy.isAllowHiddenApi()) {
                throw new IOException("RFCOMM channel connections are disabled by policy");
            }
            YoCbtHiddenApiPolicy policy = config.isAllowHiddenApiChannel()
                    || defaultChannelPolicy.isAllowHiddenApi()
                    ? YoCbtHiddenApiPolicy.EXPLICITLY_ENABLED
                    : YoCbtHiddenApiPolicy.DISABLED;
            return YoCbtHiddenApiRfcommConnector.createChannelSocket(
                    device, endpoint.getChannel(), endpoint.getSecurityMode(), policy);
        }

        UUID uuid = endpoint.getUuid();
        if (uuid == null) {
            throw new IOException("SPP UUID is null for UUID-type endpoint");
        }

        if (endpoint.getSecurityMode() == YoCbtSppSecurityMode.INSECURE) {
            return device.createInsecureRfcommSocketToServiceRecord(uuid);
        }
        return device.createRfcommSocketToServiceRecord(uuid);
    }
}

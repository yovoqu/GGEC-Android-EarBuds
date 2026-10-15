package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommand;
import com.yovo.lib_bluetooth.model.classic.CbtCommandObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommandResult;
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileAggregateState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionId;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.classic.CbtSppStateObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;

/**
 * 经典蓝牙对外 API。客户端由 {@link com.yovo.lib_bluetooth.YoBluetoothSDK#classic} 创建。
 */
public interface YoCbtClient {

    @NonNull
    CbtOperationHandle discover(@NonNull CbtDiscoveryConfig config,
                                @NonNull CbtDiscoveryObserver observer);

    @NonNull
    Result<Void> stopDiscovery();

    @NonNull
    Result<List<CbtDeviceSnapshot>> getBondedDevices();

    @NonNull
    Result<Void> createBond(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<Void> cancelBondProcess(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<Void> removeBond(@NonNull CbtDeviceId deviceId);

    @NonNull
    CbtOperationHandle observeBondState(@NonNull CbtBondStateObserver observer);

    @NonNull
    CbtOperationHandle observeAclLinkState(@NonNull CbtAclLinkObserver observer);

    @NonNull
    Result<CbtAclLinkState> getAclState(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<List<CbtDeviceId>> getAclConnectedDevices();

    @NonNull
    CbtOperationHandle queryConnectedDevices(@NonNull CbtConnectedDeviceObserver observer);

    @NonNull
    CbtOperationHandle discoverServices(@NonNull CbtDeviceId deviceId,
                                        @NonNull CbtServiceDiscoveryConfig config,
                                        @NonNull CbtServiceDiscoveryObserver observer);

    @NonNull
    Result<Void> prepareProfileProxy(@NonNull CbtProfileType profileType);

    @NonNull
    Result<CbtProfileAggregateState> getProfileAggregateState(@NonNull CbtProfileType profileType);

    @NonNull
    Result<CbtDeviceProfileState> getDeviceProfileState(@NonNull CbtDeviceId deviceId,
                                                        @NonNull CbtProfileType profileType);

    @NonNull
    Result<CbtDeviceProfileSnapshot> getDeviceProfileSnapshot(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<List<CbtProfileType>> getConnectedProfiles(@NonNull CbtDeviceId deviceId);

    @NonNull
    CbtOperationHandle observeProfileState(@NonNull CbtDeviceId deviceId,
                                           @NonNull CbtProfileStateObserver observer);

    @NonNull
    CbtOperationHandle observeProfileState(@NonNull CbtProfileStateObserver observer);

    @NonNull
    CbtOperationHandle connectSpp(@NonNull CbtDeviceId deviceId,
                                  @NonNull CbtSppEndpoint endpoint,
                                  @NonNull CbtSppConfig config,
                                  @NonNull CbtSppConnectionObserver observer);

    @NonNull
    Result<Void> disconnectSpp(@NonNull CbtSppConnectionId connectionId);

    @NonNull
    Result<Void> disconnectSppByDevice(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<Void> disconnectAllSpp();

    @NonNull
    Result<Void> send(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data);

    @NonNull
    Result<Void> sendCommand(@NonNull CbtSppConnectionId connectionId,
                             @NonNull CbtCommand command);

    @NonNull
    CbtOperationHandle observeCommandResult(@NonNull CbtSppConnectionId connectionId,
                                            @NonNull CbtCommandObserver observer);

    @NonNull
    CbtOperationHandle observeSppState(@NonNull CbtSppConnectionId connectionId,
                                       @NonNull CbtSppStateObserver observer);

    @NonNull
    Result<List<CbtSppConnectionSnapshot>> getActiveSppConnections();

    @NonNull
    Result<List<CbtSppConnectionSnapshot>> getSppConnections(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<List<CbtDeviceSnapshot>> getDeviceSnapshots();

    boolean isAdapterEnabled();

    void release();
}

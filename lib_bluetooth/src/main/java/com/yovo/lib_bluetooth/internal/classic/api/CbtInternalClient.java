package com.yovo.lib_bluetooth.internal.classic.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryConfig;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkObserver;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondStateObserver;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileAggregateState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateObserver;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommand;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandObserver;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConfig;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionObserver;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppStateObserver;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙对外 API 客户端，聚合发现、配对、Profile、SDP 与 SPP 等能力。
 */
public interface CbtInternalClient extends YoReleasable {
    CbtInternalOperationHandle discover(@NonNull YoCbtDiscoveryConfig config,
                                  @NonNull YoCbtDiscoveryObserver observer);

    YoResult<Void> stopDiscovery();

    YoResult<List<YoCbtDeviceSnapshot>> getBondedDevices();

    YoResult<Void> createBond(@NonNull YoCbtDeviceId deviceId);

    YoResult<Void> cancelBondProcess(@NonNull YoCbtDeviceId deviceId);

    YoResult<Void> removeBond(@NonNull YoCbtDeviceId deviceId);

    CbtInternalOperationHandle observeBondState(@NonNull YoCbtBondStateObserver observer);

    /** Observes BR/EDR ACL base-link changes; this does not report profile connectivity. */
    CbtInternalOperationHandle observeAclLinkState(@NonNull YoCbtAclLinkObserver observer);

    YoResult<YoCbtAclLinkState> getAclState(@NonNull YoCbtDeviceId deviceId);

    YoResult<List<YoCbtDeviceId>> getAclConnectedDevices();

    /**
     * Returns devices with a currently known ACL, Android Profile, or SDK-owned SPP connection.
     * The returned snapshot keeps those connection layers independent.
     */
    CbtInternalOperationHandle queryConnectedDevices(@NonNull YoCbtConnectedDeviceObserver observer);

    CbtInternalOperationHandle discoverServices(@NonNull YoCbtDeviceId deviceId,
                                          @NonNull YoCbtServiceDiscoveryConfig config,
                                          @NonNull YoCbtServiceDiscoveryObserver observer);

    YoResult<Void> prepareProfileProxy(@NonNull YoCbtProfileType profileType);

    YoResult<YoCbtProfileAggregateState> getProfileAggregateState(@NonNull YoCbtProfileType profileType);

    /** Queries a canonical runtime profile such as A2DP, HEADSET, or SPP. */
    YoResult<YoCbtDeviceProfileState> getDeviceProfileState(@NonNull YoCbtDeviceId deviceId,
                                                            @NonNull YoCbtProfileType profileType);

    YoResult<YoCbtDeviceProfileSnapshot> getDeviceProfileSnapshot(@NonNull YoCbtDeviceId deviceId);

    YoResult<List<YoCbtProfileType>> getConnectedProfiles(@NonNull YoCbtDeviceId deviceId);

    CbtInternalOperationHandle observeProfileState(@NonNull YoCbtDeviceId deviceId,
                                             @NonNull YoCbtProfileStateObserver observer);

    /** Observes canonical runtime-profile changes for every device known to this client. */
    CbtInternalOperationHandle observeProfileState(@NonNull YoCbtProfileStateObserver observer);

    CbtInternalOperationHandle connectSpp(@NonNull YoCbtDeviceId deviceId,
                                    @NonNull YoCbtSppEndpoint endpoint,
                                    @NonNull YoCbtSppConfig config,
                                    @NonNull YoCbtSppConnectionObserver observer);

    YoResult<Void> disconnectSpp(@NonNull YoCbtSppConnectionId connectionId);

    YoResult<Void> disconnectSppByDevice(@NonNull YoCbtDeviceId deviceId);

    YoResult<Void> disconnectAllSpp();

    YoResult<Void> send(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data);

    YoResult<Void> sendCommand(@NonNull YoCbtSppConnectionId connectionId,
                               @NonNull YoCbtCommand command);

    CbtInternalOperationHandle observeCommandResult(@NonNull YoCbtSppConnectionId connectionId,
                                              @NonNull YoCbtCommandObserver observer);

    CbtInternalOperationHandle observeSppState(@NonNull YoCbtSppConnectionId connectionId,
                                         @NonNull YoCbtSppStateObserver observer);

    YoResult<List<YoCbtSppConnectionSnapshot>> getActiveSppConnections();

    YoResult<List<YoCbtSppConnectionSnapshot>> getSppConnections(@NonNull YoCbtDeviceId deviceId);

    YoResult<List<YoCbtDeviceSnapshot>> getDeviceSnapshots();

    boolean isAdapterEnabled();
}

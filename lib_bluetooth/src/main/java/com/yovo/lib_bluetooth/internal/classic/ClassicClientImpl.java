package com.yovo.lib_bluetooth.internal.classic;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalClient;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalClientConfig;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalOperationHandle;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidClient;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryConfig;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryResult;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryState;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkObserver;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkSnapshot;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondSnapshot;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondStateObserver;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileAggregateState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateObserver;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommand;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandObserver;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandResult;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandStatus;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryState;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConfig;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicy;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionStrategy;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionObserver;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionState;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppStateObserver;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtBondState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommand;
import com.yovo.lib_bluetooth.model.classic.CbtCommandObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommandResult;
import com.yovo.lib_bluetooth.model.classic.CbtConfig;
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceIdentity;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryResult;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileAggregateState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoverySnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionStrategy;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionId;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.classic.CbtSppStateObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 经典蓝牙新门面实现：委托旧内部客户端，并在公开 model 与内部 model 之间转换。
 */
public final class ClassicClientImpl implements YoCbtClient {

    @NonNull
    private final CbtInternalClient delegate;

    public ClassicClientImpl(@NonNull Context context, @NonNull CbtConfig config) {
        CbtInternalClientConfig oldConfig = CbtInternalClientConfig.builder()
                .allowHiddenApiBondManagement(config.allowHiddenApiBondManagement())
                .allowHiddenApiSppChannel(config.allowHiddenApiSppChannel())
                .sppWriteQueueCapacity(config.sppWriteQueueCapacity())
                .build();
        this.delegate = new YoCbtAndroidClient(context, oldConfig);
    }

    @NonNull
    @Override
    public CbtOperationHandle discover(@NonNull CbtDiscoveryConfig config,
                                       @NonNull CbtDiscoveryObserver observer) {
        CbtInternalOperationHandle old = delegate.discover(
                YoCbtDiscoveryConfig.builder()
                        .timeoutMillis(config.timeoutMillis())
                        .requireName(config.requireName())
                        .build(),
                new YoCbtDiscoveryObserver() {
                    @Override
                    public void onDiscoveryStateChanged(@NonNull YoCbtDiscoveryState state) {
                        observer.onDiscoveryStateChanged(toPublic(state));
                    }

                    @Override
                    public void onDeviceFound(@NonNull YoCbtDiscoveryResult result) {
                        observer.onDeviceFound(new CbtDiscoveryResult(
                                toPublic(result.getSnapshot()),
                                result.getTimestampMillis()));
                    }

                    @Override
                    public void onDiscoveryFailed(@NonNull YoCbtFailure failure) {
                        observer.onDiscoveryFailed(toPublic(failure));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public Result<Void> stopDiscovery() {
        return toPublic(delegate.stopDiscovery());
    }

    @NonNull
    @Override
    public Result<List<CbtDeviceSnapshot>> getBondedDevices() {
        YoResult<List<YoCbtDeviceSnapshot>> old = delegate.getBondedDevices();
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(toPublicDevices(old.getValue()));
    }

    @NonNull
    @Override
    public Result<Void> createBond(@NonNull CbtDeviceId deviceId) {
        return toPublic(delegate.createBond(toInternal(deviceId)));
    }

    @NonNull
    @Override
    public Result<Void> cancelBondProcess(@NonNull CbtDeviceId deviceId) {
        return toPublic(delegate.cancelBondProcess(toInternal(deviceId)));
    }

    @NonNull
    @Override
    public Result<Void> removeBond(@NonNull CbtDeviceId deviceId) {
        return toPublic(delegate.removeBond(toInternal(deviceId)));
    }

    @NonNull
    @Override
    public CbtOperationHandle observeBondState(@NonNull CbtBondStateObserver observer) {
        CbtInternalOperationHandle old = delegate.observeBondState(
                new YoCbtBondStateObserver() {
                    @Override
                    public void onBondStateChanged(@NonNull YoCbtDeviceSnapshot snapshot) {
                        observer.onBondStateChanged(toPublic(snapshot));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public CbtOperationHandle observeAclLinkState(@NonNull CbtAclLinkObserver observer) {
        CbtInternalOperationHandle old = delegate.observeAclLinkState(
                new YoCbtAclLinkObserver() {
                    @Override
                    public void onAclLinkChanged(@NonNull YoCbtDeviceSnapshot snapshot) {
                        observer.onAclLinkChanged(toPublic(snapshot));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public Result<CbtAclLinkState> getAclState(@NonNull CbtDeviceId deviceId) {
        YoResult<YoCbtAclLinkState> old = delegate.getAclState(toInternal(deviceId));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(toPublic(old.getValue()));
    }

    @NonNull
    @Override
    public Result<List<CbtDeviceId>> getAclConnectedDevices() {
        YoResult<List<YoCbtDeviceId>> old = delegate.getAclConnectedDevices();
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        List<CbtDeviceId> result = new ArrayList<>();
        for (YoCbtDeviceId id : old.getValue()) {
            result.add(toPublic(id));
        }
        return Result.success(result);
    }

    @NonNull
    @Override
    public CbtOperationHandle queryConnectedDevices(@NonNull CbtConnectedDeviceObserver observer) {
        CbtInternalOperationHandle old = delegate.queryConnectedDevices(
                new YoCbtConnectedDeviceObserver() {
                    @Override
                    public void onConnectedDevices(@NonNull List<YoCbtDeviceSnapshot> snapshots) {
                        observer.onConnectedDevices(toPublicDevices(snapshots));
                    }

                    @Override
                    public void onConnectedDeviceQueryFailed(@NonNull YoCbtFailure failure) {
                        observer.onConnectedDeviceQueryFailed(toPublic(failure));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public CbtOperationHandle discoverServices(@NonNull CbtDeviceId deviceId,
                                               @NonNull CbtServiceDiscoveryConfig config,
                                               @NonNull CbtServiceDiscoveryObserver observer) {
        CbtInternalOperationHandle old = delegate.discoverServices(
                toInternal(deviceId),
                YoCbtServiceDiscoveryConfig.builder().timeoutMillis(config.timeoutMillis()).build(),
                new YoCbtServiceDiscoveryObserver() {
                    @Override
                    public void onServiceDiscoveryStateChanged(
                            @NonNull YoCbtServiceDiscoveryState state) {
                        observer.onStateChanged(toPublic(state));
                    }

                    @Override
                    public void onServiceDiscoveryResolved(
                            @NonNull YoCbtDeviceSnapshot snapshot) {
                        observer.onResolved(new CbtServiceDiscoverySnapshot(
                                toPublic(snapshot.getDeviceId()),
                                toPublic(snapshot),
                                snapshot.getDiscoveredUuids(),
                                toPublicEndpoints(snapshot.getSdpSppEndpoints())));
                    }

                    @Override
                    public void onServiceDiscoveryFailed(@NonNull YoCbtFailure failure) {
                        observer.onFailed(toPublic(failure));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public Result<Void> prepareProfileProxy(@NonNull CbtProfileType profileType) {
        return toPublic(delegate.prepareProfileProxy(toInternal(profileType)));
    }

    @NonNull
    @Override
    public Result<CbtProfileAggregateState> getProfileAggregateState(
            @NonNull CbtProfileType profileType) {
        YoResult<YoCbtProfileAggregateState> old =
                delegate.getProfileAggregateState(toInternal(profileType));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        YoCbtProfileAggregateState value = old.getValue();
        return Result.success(new CbtProfileAggregateState(
                toPublic(value.getProfileType()),
                toPublic(value.getState()),
                value.getConnectedCount(),
                value.getQueryTimeMillis()));
    }

    @NonNull
    @Override
    public Result<CbtDeviceProfileState> getDeviceProfileState(
            @NonNull CbtDeviceId deviceId,
            @NonNull CbtProfileType profileType) {
        YoResult<YoCbtDeviceProfileState> old = delegate.getDeviceProfileState(
                toInternal(deviceId), toInternal(profileType));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(toPublic(old.getValue()));
    }

    @NonNull
    @Override
    public Result<CbtDeviceProfileSnapshot> getDeviceProfileSnapshot(
            @NonNull CbtDeviceId deviceId) {
        YoResult<YoCbtDeviceProfileSnapshot> old =
                delegate.getDeviceProfileSnapshot(toInternal(deviceId));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        YoCbtDeviceProfileSnapshot value = old.getValue();
        java.util.Map<CbtProfileType, CbtDeviceProfileState> states = new java.util.EnumMap<>(
                CbtProfileType.class);
        for (java.util.Map.Entry<YoCbtProfileType, YoCbtDeviceProfileState> entry
                : value.getDeviceSnapshot().getProfileStates().entrySet()) {
            states.put(toPublic(entry.getKey()), toPublic(entry.getValue()));
        }
        return Result.success(new CbtDeviceProfileSnapshot(
                toPublic(value.getDeviceSnapshot()), states));
    }

    @NonNull
    @Override
    public Result<List<CbtProfileType>> getConnectedProfiles(@NonNull CbtDeviceId deviceId) {
        YoResult<List<YoCbtProfileType>> old =
                delegate.getConnectedProfiles(toInternal(deviceId));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        List<CbtProfileType> result = new ArrayList<>();
        for (YoCbtProfileType type : old.getValue()) {
            result.add(toPublic(type));
        }
        return Result.success(result);
    }

    @NonNull
    @Override
    public CbtOperationHandle observeProfileState(@NonNull CbtDeviceId deviceId,
                                                  @NonNull CbtProfileStateObserver observer) {
        CbtInternalOperationHandle old = delegate.observeProfileState(
                toInternal(deviceId),
                new YoCbtProfileStateObserver() {
                    @Override
                    public void onProfileStateChanged(@NonNull YoCbtDeviceProfileState state) {
                        observer.onProfileStateChanged(toPublic(state));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public CbtOperationHandle observeProfileState(@NonNull CbtProfileStateObserver observer) {
        CbtInternalOperationHandle old = delegate.observeProfileState(
                new YoCbtProfileStateObserver() {
                    @Override
                    public void onProfileStateChanged(@NonNull YoCbtDeviceProfileState state) {
                        observer.onProfileStateChanged(toPublic(state));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public CbtOperationHandle connectSpp(@NonNull CbtDeviceId deviceId,
                                         @NonNull CbtSppEndpoint endpoint,
                                         @NonNull CbtSppConfig config,
                                         @NonNull CbtSppConnectionObserver observer) {
        CbtInternalOperationHandle old = delegate.connectSpp(
                toInternal(deviceId),
                toInternal(endpoint),
                toInternal(config),
                new YoCbtSppConnectionObserver() {
                    private final CbtPublicSppStateGate publicStateGate = new CbtPublicSppStateGate();

                    @Override
                    public void onSppDeviceResolved(@NonNull YoCbtDeviceIdentity identity) {
                        observer.onSppDeviceResolved(toPublic(identity));
                    }

                    @Override
                    public void onSppStateChanged(@NonNull YoCbtSppConnectionSnapshot snapshot) {
                        CbtSppConnectionSnapshot publicSnapshot = toPublic(snapshot);
                        if (!publicStateGate.accept(publicSnapshot.state())) {
                            return;
                        }
                        observer.onSppStateChanged(publicSnapshot);
                    }

                    @Override
                    public void onSppDataReceived(@NonNull YoCbtSppConnectionId connectionId,
                                                  @NonNull byte[] data) {
                        observer.onSppDataReceived(toPublic(connectionId), data);
                    }

                    @Override
                    public void onSppDataSent(@NonNull YoCbtSppConnectionId connectionId,
                                              @NonNull byte[] data) {
                        observer.onSppDataSent(toPublic(connectionId), data);
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public Result<Void> disconnectSpp(@NonNull CbtSppConnectionId connectionId) {
        return toPublic(delegate.disconnectSpp(toInternal(connectionId)));
    }

    @NonNull
    @Override
    public Result<Void> disconnectSppByDevice(@NonNull CbtDeviceId deviceId) {
        return toPublic(delegate.disconnectSppByDevice(toInternal(deviceId)));
    }

    @NonNull
    @Override
    public Result<Void> disconnectAllSpp() {
        return toPublic(delegate.disconnectAllSpp());
    }

    @NonNull
    @Override
    public Result<Void> send(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
        return toPublic(delegate.send(toInternal(connectionId), data));
    }

    @NonNull
    @Override
    public Result<Void> sendCommand(@NonNull CbtSppConnectionId connectionId,
                                    @NonNull CbtCommand command) {
        YoResult<Void> old = delegate.sendCommand(
                toInternal(connectionId), new YoCbtCommand(new YoCbtPacket(command.payload())));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(null);
    }

    @NonNull
    @Override
    public CbtOperationHandle observeCommandResult(@NonNull CbtSppConnectionId connectionId,
                                                   @NonNull CbtCommandObserver observer) {
        CbtInternalOperationHandle old = delegate.observeCommandResult(
                toInternal(connectionId),
                new YoCbtCommandObserver() {
                    @Override
                    public void onCommandResult(@NonNull YoCbtSppConnectionId id,
                                                @NonNull YoCbtCommandResult result) {
                        observer.onCommandResult(toPublic(result));
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public CbtOperationHandle observeSppState(@NonNull CbtSppConnectionId connectionId,
                                              @NonNull CbtSppStateObserver observer) {
        CbtInternalOperationHandle old = delegate.observeSppState(
                toInternal(connectionId),
                new YoCbtSppStateObserver() {
                    private final CbtPublicSppStateGate publicStateGate = new CbtPublicSppStateGate();

                    @Override
                    public void onSppStateChanged(@NonNull YoCbtSppConnectionSnapshot snapshot) {
                        CbtSppConnectionSnapshot publicSnapshot = toPublic(snapshot);
                        if (!publicStateGate.accept(publicSnapshot.state())) {
                            return;
                        }
                        observer.onSppStateChanged(publicSnapshot);
                    }
                });
        return new CbtOperationHandle(old.getId(), old::cancel);
    }

    @NonNull
    @Override
    public Result<List<CbtSppConnectionSnapshot>> getActiveSppConnections() {
        YoResult<List<YoCbtSppConnectionSnapshot>> old = delegate.getActiveSppConnections();
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(toPublicSpp(old.getValue()));
    }

    @NonNull
    @Override
    public Result<List<CbtSppConnectionSnapshot>> getSppConnections(@NonNull CbtDeviceId deviceId) {
        YoResult<List<YoCbtSppConnectionSnapshot>> old =
                delegate.getSppConnections(toInternal(deviceId));
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(toPublicSpp(old.getValue()));
    }

    @NonNull
    @Override
    public Result<List<CbtDeviceSnapshot>> getDeviceSnapshots() {
        YoResult<List<YoCbtDeviceSnapshot>> old = delegate.getDeviceSnapshots();
        if (old.isFailure()) {
            return Result.failure(toPublic(old.getFailure()));
        }
        return Result.success(toPublicDevices(old.getValue()));
    }

    @Override
    public boolean isAdapterEnabled() {
        return delegate.isAdapterEnabled();
    }

    @Override
    public void release() {
        delegate.release();
    }

    // ── 转换：公开 → 内部 ────────────────────────────────────────

    @NonNull
    private static YoCbtDeviceId toInternal(@NonNull CbtDeviceId id) {
        return new YoCbtDeviceId(id.value());
    }

    @NonNull
    private static YoCbtSppConnectionId toInternal(@NonNull CbtSppConnectionId id) {
        return new YoCbtSppConnectionId(id.value());
    }

    @NonNull
    private static YoCbtProfileType toInternal(@NonNull CbtProfileType type) {
        switch (type) {
            case A2DP:
                return YoCbtProfileType.A2DP;
            case HEADSET:
                return YoCbtProfileType.HEADSET;
            case SPP:
                return YoCbtProfileType.SPP;
            case AVRCP:
                return YoCbtProfileType.AVRCP;
            case HID:
                return YoCbtProfileType.HID_HOST;
            case PAN:
                return YoCbtProfileType.PAN;
            case PBAP:
                return YoCbtProfileType.PBAP;
            case MAP:
                return YoCbtProfileType.MAP;
            case OPP:
                return YoCbtProfileType.OPP;
            case LE_AUDIO:
                return YoCbtProfileType.LE_AUDIO;
            case HEARING_AID:
                return YoCbtProfileType.HEARING_AID;
            default:
                return YoCbtProfileType.UNKNOWN;
        }
    }

    @NonNull
    private static YoCbtSppEndpoint toInternal(@NonNull CbtSppEndpoint endpoint) {
        switch (endpoint.type()) {
            case UUID:
                return YoCbtSppEndpoint.uuid(
                        endpoint.uuid(),
                        YoCbtSppEndpointSource.USER_SPECIFIED,
                        YoCbtSppSecurityMode.SECURE);
            case CHANNEL:
                return YoCbtSppEndpoint.channel(
                        endpoint.channel(),
                        YoCbtSppEndpointSource.USER_SPECIFIED,
                        YoCbtSppSecurityMode.SECURE);
            case DEFAULT:
            default:
                return YoCbtSppEndpoint.defaultSpp();
        }
    }

    @NonNull
    private static YoCbtSppConfig toInternal(@NonNull CbtSppConfig config) {
        return YoCbtSppConfig.builder()
                .connectTimeoutMillis(config.connectTimeoutMillis())
                .maxConnectAttempts(config.maxConnectAttempts())
                .retryDelayMillis(config.retryDelayMillis())
                .allowHiddenApiChannel(config.allowHiddenApiChannel())
                .connectionPolicy(toInternal(config.connectionStrategy()))
                .writeQueueCapacity(256)
                .build();
    }

    @NonNull
    private static YoCbtSppConnectionPolicy toInternal(@NonNull CbtSppConnectionStrategy strategy) {
        YoCbtSppConnectionStrategy internal;
        switch (strategy) {
            case UUID_ONLY:
                internal = YoCbtSppConnectionStrategy.UUID_ONLY;
                break;
            case CHANNEL_ONLY:
                internal = YoCbtSppConnectionStrategy.CHANNEL_ONLY;
                break;
            case UUID_THEN_CHANNEL:
                internal = YoCbtSppConnectionStrategy.UUID_THEN_CHANNEL;
                break;
            case CHANNEL_THEN_UUID:
                internal = YoCbtSppConnectionStrategy.CHANNEL_THEN_UUID;
                break;
            case SDP_UUID_ONLY:
                internal = YoCbtSppConnectionStrategy.SDP_UUID_ONLY;
                break;
            case SDP_CHANNEL_ONLY:
                internal = YoCbtSppConnectionStrategy.SDP_CHANNEL_ONLY;
                break;
            case USER_SPECIFIED_ONLY:
            default:
                internal = YoCbtSppConnectionStrategy.USER_SPECIFIED_ONLY;
                break;
        }
        boolean allowFallback = strategy == CbtSppConnectionStrategy.UUID_THEN_CHANNEL
                || strategy == CbtSppConnectionStrategy.CHANNEL_THEN_UUID;
        return new YoCbtSppConnectionPolicy(internal, allowFallback);
    }

    // ── 转换：内部 → 公开 ────────────────────────────────────────

    @NonNull
    private static CbtDeviceId toPublic(@NonNull YoCbtDeviceId id) {
        return new CbtDeviceId(id.value());
    }

    @NonNull
    private static CbtSppConnectionId toPublic(@NonNull YoCbtSppConnectionId id) {
        return new CbtSppConnectionId(id.value());
    }

    @NonNull
    private static CbtDeviceIdentity toPublic(@NonNull YoCbtDeviceIdentity identity) {
        return new CbtDeviceIdentity(
                toPublic(identity.getDeviceId()),
                identity.getAddress(),
                identity.getDisplayName());
    }

    @NonNull
    private static CbtBondState toPublic(@NonNull YoCbtBondState state) {
        switch (state) {
            case BONDED:
                return CbtBondState.BONDED;
            case BONDING:
                return CbtBondState.BONDING;
            case NONE:
            case UNKNOWN:
            case REMOVED:
            case FAILED:
            default:
                return CbtBondState.NONE;
        }
    }

    @NonNull
    private static CbtAclLinkState toPublic(@NonNull YoCbtAclLinkState state) {
        switch (state) {
            case CONNECTED:
                return CbtAclLinkState.CONNECTED;
            case DISCONNECTED:
            case UNKNOWN:
            case DISCONNECT_REQUESTED:
            default:
                return CbtAclLinkState.DISCONNECTED;
        }
    }

    @NonNull
    private static CbtSppConnectionState toPublic(@NonNull YoCbtSppConnectionState state) {
        switch (state) {
            case CONNECTED:
                return CbtSppConnectionState.CONNECTED;
            case DISCONNECTING:
                return CbtSppConnectionState.DISCONNECTING;
            case DISCONNECTED:
            case RELEASED:
                return CbtSppConnectionState.DISCONNECTED;
            case FAILED:
                return CbtSppConnectionState.FAILED;
            case IDLE:
                return CbtSppConnectionState.IDLE;
            case PREPARING:
            case RESOLVING_SERVICE:
            case CREATING_SOCKET:
            case CONNECTING_SOCKET:
            case OPENING_STREAM:
            case STARTING_READER:
            case STARTING_WRITER:
            default:
                return CbtSppConnectionState.CONNECTING;
        }
    }

    @NonNull
    private static CbtProfileType toPublic(@NonNull YoCbtProfileType type) {
        switch (type) {
            case A2DP:
                return CbtProfileType.A2DP;
            case HEADSET:
                return CbtProfileType.HEADSET;
            case SPP:
                return CbtProfileType.SPP;
            case AVRCP:
                return CbtProfileType.AVRCP;
            case HID_HOST:
                return CbtProfileType.HID;
            case PAN:
                return CbtProfileType.PAN;
            case PBAP:
                return CbtProfileType.PBAP;
            case MAP:
                return CbtProfileType.MAP;
            case OPP:
                return CbtProfileType.OPP;
            case LE_AUDIO:
                return CbtProfileType.LE_AUDIO;
            case HEARING_AID:
                return CbtProfileType.HEARING_AID;
            case HFP:
            case HSP:
            case UNKNOWN:
            default:
                return CbtProfileType.SPP;
        }
    }

    @NonNull
    private static CbtProfileConnectionState toPublic(@NonNull YoCbtProfileConnectionState state) {
        switch (state) {
            case CONNECTED:
                return CbtProfileConnectionState.CONNECTED;
            case CONNECTING:
                return CbtProfileConnectionState.CONNECTING;
            case DISCONNECTING:
                return CbtProfileConnectionState.DISCONNECTING;
            case DISCONNECTED:
                return CbtProfileConnectionState.DISCONNECTED;
            case ERROR:
            case UNKNOWN:
            default:
                return CbtProfileConnectionState.UNKNOWN;
        }
    }

    @NonNull
    private static CbtDiscoveryState toPublic(@NonNull YoCbtDiscoveryState state) {
        switch (state) {
            case PREPARING:
                return CbtDiscoveryState.STARTING;
            case DISCOVERING:
                return CbtDiscoveryState.DISCOVERING;
            case FINISHED:
                return CbtDiscoveryState.FINISHED;
            case FAILED:
                return CbtDiscoveryState.FAILED;
            case STOPPING:
            case RELEASED:
            case IDLE:
            default:
                return CbtDiscoveryState.STOPPED;
        }
    }

    @NonNull
    private static CbtServiceDiscoveryState toPublic(@NonNull YoCbtServiceDiscoveryState state) {
        switch (state) {
            case RESOLVED:
                return CbtServiceDiscoveryState.RESOLVED;
            case EMPTY:
                return CbtServiceDiscoveryState.EMPTY;
            case FAILED:
            case TIMEOUT:
            case RELEASED:
                return CbtServiceDiscoveryState.FAILED;
            case PREPARING:
            case DISCOVERING:
            case IDLE:
            default:
                return CbtServiceDiscoveryState.STARTED;
        }
    }

    @NonNull
    private static CbtDeviceSnapshot toPublic(@NonNull YoCbtDeviceSnapshot snapshot) {
        java.util.Map<CbtProfileType, CbtDeviceProfileState> states = new java.util.EnumMap<>(
                CbtProfileType.class);
        for (java.util.Map.Entry<YoCbtProfileType, YoCbtDeviceProfileState> entry
                : snapshot.getProfileStates().entrySet()) {
            states.put(toPublic(entry.getKey()), toPublic(entry.getValue()));
        }
        return new CbtDeviceSnapshot(
                toPublic(snapshot.getIdentity()),
                toPublic(snapshot.getBondState()),
                toPublic(snapshot.getAclLinkState()),
                snapshot.getRssi(),
                snapshot.getLastSeenMillis(),
                snapshot.getLastUpdatedMillis(),
                toPublicSpp(snapshot.getSppConnections()),
                states,
                snapshot.getDiscoveredUuids(),
                toPublicEndpoints(snapshot.getSdpSppEndpoints()));
    }

    @NonNull
    private static CbtSppConnectionSnapshot toPublic(
            @NonNull YoCbtSppConnectionSnapshot snapshot) {
        return new CbtSppConnectionSnapshot(
                toPublic(snapshot.getConnectionId()),
                toPublic(snapshot.getDeviceId()),
                toPublic(snapshot.getState()),
                toPublic(snapshot.getEndpoint()),
                snapshot.getFailure() != null ? toPublic(snapshot.getFailure()) : null,
                snapshot.getResolvedChannel());
    }

    @NonNull
    private static CbtBondSnapshot toPublic(@NonNull YoCbtBondSnapshot snapshot) {
        return new CbtBondSnapshot(
                toPublic(snapshot.getDeviceId()),
                toPublic(snapshot.getBondState()),
                snapshot.getTimestampMillis());
    }

    @NonNull
    private static CbtAclLinkSnapshot toPublic(@NonNull YoCbtAclLinkSnapshot snapshot) {
        return new CbtAclLinkSnapshot(
                toPublic(snapshot.getDeviceId()),
                toPublic(snapshot.getState()),
                snapshot.getUpdatedAtMillis());
    }

    @NonNull
    private static CbtDeviceProfileState toPublic(@NonNull YoCbtDeviceProfileState state) {
        return new CbtDeviceProfileState(
                toPublic(state.getDeviceId()),
                toPublic(state.getProfileType()),
                toPublic(state.getConnectionState()),
                state.getLastChangedAtMillis());
    }

    @NonNull
    private static CbtCommandResult toPublic(@NonNull YoCbtCommandResult result) {
        byte[] response = result.getResponse() == null
                ? null
                : result.getResponse().getPacket().getPayload();
        boolean success = result.getStatus() == YoCbtCommandStatus.SENT
                || result.getStatus() == YoCbtCommandStatus.RESPONSE_RECEIVED;
        return new CbtCommandResult(success, response, result.getMessage());
    }

    @NonNull
    private static CbtFailure toPublic(
            @NonNull com.yovo.lib_bluetooth.internal.common.result.YoFailure failure) {
        return new CbtFailure(
                mapErrorCode(failure.getErrorCode().code()),
                failure.getMessage(),
                failure.getCause(),
                failure.isRecoverable(),
                failure.isRetryable(),
                failure.getSourceModule(),
                null);
    }

    @NonNull
    private static CbtFailure toPublic(@NonNull YoCbtFailure failure) {
        return new CbtFailure(
                mapErrorCode(failure.getErrorCode().code()),
                failure.getMessage(),
                failure.getCause(),
                failure.isRecoverable(),
                failure.isRetryable(),
                failure.getSourceModule(),
                failure.getDeviceId() == null ? null : toPublic(failure.getDeviceId()));
    }

    @NonNull
    private static CbtErrorCode mapErrorCode(@NonNull String code) {
        switch (code) {
            case "YO_CBT_BLUETOOTH_NOT_SUPPORTED":
                return CbtErrorCode.BLUETOOTH_NOT_SUPPORTED;
            case "YO_CBT_BLUETOOTH_DISABLED":
                return CbtErrorCode.BLUETOOTH_DISABLED;
            case "YO_CBT_PERMISSION_SCAN_DENIED":
            case "YO_CBT_PERMISSION_CONNECT_DENIED":
            case "YO_CBT_PERMISSION_LOCATION_DENIED":
                return CbtErrorCode.PERMISSION_DENIED;
            case "YO_CBT_DISCOVERY_START_FAILED":
            case "YO_CBT_DISCOVERY_ALREADY_RUNNING":
            case "YO_CBT_DISCOVERY_STOP_FAILED":
            case "YO_CBT_DISCOVERY_TIMEOUT":
            case "YO_CBT_DISCOVERY_RECEIVER_ERROR":
                return CbtErrorCode.DISCOVERY_START_FAILED;
            case "YO_CBT_BOND_START_FAILED":
            case "YO_CBT_BOND_CANCEL_FAILED":
            case "YO_CBT_BOND_REMOVE_FAILED":
            case "YO_CBT_BONDED_DEVICE_NOT_FOUND":
            case "YO_CBT_BOND_HIDDEN_API_BLOCKED":
            case "YO_CBT_BOND_DEVICE_NOT_CONNECTABLE":
                return CbtErrorCode.BOND_FAILED;
            case "YO_CBT_ACL_STATE_QUERY_FAILED":
            case "YO_CBT_ACL_STATE_UNKNOWN":
            case "YO_CBT_ACL_RECEIVER_ERROR":
                return CbtErrorCode.ACL_QUERY_FAILED;
            case "YO_CBT_SDP_START_FAILED":
            case "YO_CBT_SDP_TIMEOUT":
            case "YO_CBT_SDP_UUID_EMPTY":
            case "YO_CBT_SDP_UUID_NOT_MATCHED":
            case "YO_CBT_SDP_ACTION_UUID_NOT_RECEIVED":
            case "YO_CBT_SDP_CHANNEL_NOT_RESOLVED":
                return CbtErrorCode.SDP_FAILED;
            case "YO_CBT_PROFILE_PROXY_GET_FAILED":
            case "YO_CBT_PROFILE_PROXY_NOT_READY":
            case "YO_CBT_PROFILE_PROXY_ALREADY_REQUESTED":
            case "YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR":
            case "YO_CBT_PROFILE_UNSUPPORTED":
            case "YO_CBT_PROFILE_TYPE_UNSUPPORTED":
                return CbtErrorCode.PROFILE_PROXY_FAILED;
            case "YO_CBT_PROFILE_STATE_QUERY_FAILED":
            case "YO_CBT_PROFILE_CAPABILITY_UNKNOWN":
            case "YO_CBT_A2DP_STATE_QUERY_FAILED":
            case "YO_CBT_HEADSET_STATE_QUERY_FAILED":
                return CbtErrorCode.PROFILE_STATE_QUERY_FAILED;
            case "YO_CBT_SPP_CONNECT_FAILED":
            case "YO_CBT_SPP_ALREADY_CONNECTED":
                return CbtErrorCode.SPP_CONNECT_FAILED;
            case "YO_CBT_SPP_CONNECT_TIMEOUT":
                return CbtErrorCode.SPP_CONNECT_TIMEOUT;
            case "YO_CBT_SPP_SOCKET_CREATE_FAILED":
                return CbtErrorCode.SPP_SOCKET_CREATE_FAILED;
            case "YO_CBT_SPP_CHANNEL_CONNECT_FAILED":
                return CbtErrorCode.SPP_CHANNEL_CONNECT_FAILED;
            case "YO_CBT_SPP_CHANNEL_INVALID":
                return CbtErrorCode.SPP_CHANNEL_INVALID;
            case "YO_CBT_SPP_CHANNEL_HIDDEN_API_BLOCKED":
                return CbtErrorCode.SPP_CHANNEL_HIDDEN_API_BLOCKED;
            case "YO_CBT_SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE":
                return CbtErrorCode.SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE;
            case "YO_CBT_SPP_CHANNEL_SOCKET_CREATE_FAILED":
                return CbtErrorCode.SPP_CHANNEL_SOCKET_CREATE_FAILED;
            case "YO_CBT_SPP_CHANNEL_REFLECTION_FAILED":
                return CbtErrorCode.SPP_CHANNEL_REFLECTION_FAILED;
            case "YO_CBT_SPP_DEVICE_INVALID":
                return CbtErrorCode.SPP_DEVICE_INVALID;
            case "YO_CBT_SPP_UUID_INVALID":
                return CbtErrorCode.SPP_UUID_INVALID;
            case "YO_CBT_SPP_LOCAL_CLOSED":
                return CbtErrorCode.SPP_LOCAL_CLOSED;
            case "YO_CBT_SPP_REMOTE_CLOSED":
                return CbtErrorCode.SPP_REMOTE_CLOSED;
            case "YO_CBT_SPP_STREAM_OPEN_FAILED":
                return CbtErrorCode.SPP_STREAM_OPEN_FAILED;
            case "YO_CBT_OPERATION_CANCELLED":
                return CbtErrorCode.OPERATION_CANCELLED;
            case "YO_CBT_SPP_WRITE_FAILED":
                return CbtErrorCode.SPP_WRITE_FAILED;
            case "YO_CBT_SPP_READ_FAILED":
                return CbtErrorCode.SPP_READ_FAILED;
            case "YO_CBT_COMMAND_TIMEOUT":
            case "YO_CBT_COMMAND_CANCELLED":
            case "YO_CBT_COMMAND_QUEUE_FULL":
            case "YO_CBT_RESPONSE_NOT_MATCHED":
            case "YO_CBT_PACKET_ENCODE_FAILED":
            case "YO_CBT_PACKET_DECODE_FAILED":
                return CbtErrorCode.COMMAND_FAILED;
            case "YO_CBT_RELEASED":
            case "YO_CBT_ALREADY_RELEASED":
            case "YO_CBT_RELEASE_IN_PROGRESS":
                return CbtErrorCode.RELEASED;
            default:
                return CbtErrorCode.SPP_CONNECT_FAILED;
        }
    }

    @NonNull
    private static <T> Result<T> toPublic(@NonNull YoResult<T> result) {
        if (result.isFailure()) {
            return Result.failure(toPublic(result.getFailure()));
        }
        return Result.success(result.getValue());
    }

    @NonNull
    private static List<CbtDeviceSnapshot> toPublicDevices(
            @NonNull List<YoCbtDeviceSnapshot> snapshots) {
        List<CbtDeviceSnapshot> result = new ArrayList<>();
        for (YoCbtDeviceSnapshot snapshot : snapshots) {
            result.add(toPublic(snapshot));
        }
        return result;
    }

    @NonNull
    private static List<CbtSppConnectionSnapshot> toPublicSpp(
            @NonNull List<YoCbtSppConnectionSnapshot> snapshots) {
        List<CbtSppConnectionSnapshot> result = new ArrayList<>();
        for (YoCbtSppConnectionSnapshot snapshot : snapshots) {
            result.add(toPublic(snapshot));
        }
        return result;
    }

    @NonNull
    private static List<CbtSppEndpoint> toPublicEndpoints(
            @NonNull List<YoCbtSppEndpoint> endpoints) {
        List<CbtSppEndpoint> result = new ArrayList<>();
        for (YoCbtSppEndpoint endpoint : endpoints) {
            result.add(toPublic(endpoint));
        }
        return result;
    }

    @NonNull
    private static CbtSppEndpoint toPublic(@NonNull YoCbtSppEndpoint endpoint) {
        if (endpoint.isChannel()) {
            return CbtSppEndpoint.channel(endpoint.getChannel());
        }
        if (endpoint.getUuid() != null) {
            return CbtSppEndpoint.uuid(endpoint.getUuid());
        }
        return CbtSppEndpoint.defaultSpp();
    }
}

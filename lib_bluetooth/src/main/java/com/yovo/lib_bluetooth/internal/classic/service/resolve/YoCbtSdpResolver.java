package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.service.cache.YoCbtServiceCache;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRecord;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRequest;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtServiceResolvedSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 验证 SDP 结果、丰富设备快照，并推导 SPP 端点候选。
 */
public final class YoCbtSdpResolver {

    private static final String TAG = "YoCbtSdpResolver";

    private final YoCbtProfileUuidMapper profileUuidMapper;
    private final YoCbtUuidSelector uuidSelector;
    private final YoCbtSppEndpointResolver sppEndpointResolver;
    private final YoCbtDeviceSnapshotStore snapshotStore;
    private final YoCbtServiceCache serviceCache;

    public YoCbtSdpResolver(@NonNull YoCbtProfileUuidMapper profileUuidMapper,
                            @NonNull YoCbtUuidSelector uuidSelector,
                            @NonNull YoCbtSppEndpointResolver sppEndpointResolver,
                            @NonNull YoCbtDeviceSnapshotStore snapshotStore,
                            @NonNull YoCbtServiceCache serviceCache) {
        this.profileUuidMapper = Objects.requireNonNull(profileUuidMapper, "profileUuidMapper must not be null");
        this.uuidSelector = Objects.requireNonNull(uuidSelector, "uuidSelector must not be null");
        this.sppEndpointResolver = Objects.requireNonNull(sppEndpointResolver, "sppEndpointResolver must not be null");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore must not be null");
        this.serviceCache = Objects.requireNonNull(serviceCache, "serviceCache must not be null");
    }

    @NonNull
    public YoResult<YoCbtServiceResolvedSnapshot> resolve(@NonNull YoCbtSdpRequest request,
                                                          @NonNull YoCbtSdpResult result) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(result, "result must not be null");

        if (request.isFailOnEmpty() && result.isEmpty()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_SDP_UUID_EMPTY,
                    "SDP returned no service UUIDs for " + result.getDeviceId(),
                    TAG));
        }

        UUID preferred = request.getPreferredUuid();
        if (request.isFailOnPreferredMismatch() && preferred != null && !result.hasUuid(preferred)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_SDP_UUID_NOT_MATCHED,
                    "Preferred UUID not found in SDP result: " + preferred,
                    TAG));
        }

        UUID selectedUuid = uuidSelector.select(result, request.getUuidPolicy());
        if (preferred != null && result.hasUuid(preferred)) {
            selectedUuid = preferred;
        }

        serviceCache.put(result);

        Set<YoCbtProfileType> profiles = profileUuidMapper.mapProfiles(result.getUuids());
        List<YoCbtSppEndpoint> sppEndpoints = sppEndpointResolver.resolve(result);
        YoCbtSdpRecord sdpRecord = YoCbtSdpRecord.of(result);

        YoCbtDeviceIdentity identity = resolveIdentity(result);
        long now = result.getResolvedAtMillis() > 0 ? result.getResolvedAtMillis() : System.currentTimeMillis();

        YoCbtCapabilitySource capabilitySource = result.isFromCache()
                ? YoCbtCapabilitySource.BONDED_CACHE
                : YoCbtCapabilitySource.SDP;

        YoCbtDeviceSnapshot snapshot = snapshotStore.applySdpResolution(
                identity,
                sdpRecord,
                sppEndpoints,
                profiles,
                capabilitySource,
                now);

        return YoResult.success(new YoCbtServiceResolvedSnapshot(
                snapshot, sdpRecord, profiles, sppEndpoints, selectedUuid));
    }

    @NonNull
    private YoCbtDeviceIdentity resolveIdentity(@NonNull YoCbtSdpResult result) {
        YoCbtDeviceSnapshot existing = snapshotStore.get(result.getDeviceId());
        if (existing != null) {
            return existing.getIdentity();
        }
        return new YoCbtDeviceIdentity(
                result.getDeviceId(),
                result.getDeviceId().value(),
                com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType.CLASSIC,
                com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType.UNKNOWN,
                null);
    }
}

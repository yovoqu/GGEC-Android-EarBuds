package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileResolver;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpSource;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtServiceRecord;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 从完整的 SDP 结果推导 SPP 连接端点候选列表。
 */
public final class YoCbtSppEndpointResolver {

    private static final UUID SERIAL_PORT_CLASS_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @NonNull
    public List<YoCbtSppEndpoint> resolve(@NonNull YoCbtSdpResult result) {
        List<YoCbtSppEndpoint> endpoints = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();

        YoCbtSppEndpointSource uuidSource = mapUuidSource(result.getSource());

        for (YoCbtServiceRecord record : result.getServiceRecords()) {
            UUID uuid = record.getUuid();
            if (!seen.add(uuid)) continue;
            if (!isSppCandidate(uuid)) continue;

            endpoints.add(YoCbtSppEndpoint.uuid(uuid, uuidSource, YoCbtSppSecurityMode.SECURE));

            if (record.hasChannel()) {
                endpoints.add(YoCbtSppEndpoint.channel(
                        record.getChannel(),
                        mapChannelSource(result.getSource()),
                        YoCbtSppSecurityMode.SECURE));
            }
        }

        if (endpoints.isEmpty()) {
            endpoints.add(YoCbtSppEndpoint.defaultSpp());
        }
        return endpoints;
    }

    private static boolean isSppCandidate(@NonNull UUID uuid) {
        if (SERIAL_PORT_CLASS_UUID.equals(uuid)) return true;
        return YoCbtProfileResolver.resolveFromUuids(Collections.singletonList(uuid))
                .contains(YoCbtProfileType.SPP);
    }

    @NonNull
    private static YoCbtSppEndpointSource mapUuidSource(@NonNull YoCbtSdpSource source) {
        switch (source) {
            case BONDED_CACHE:
                return YoCbtSppEndpointSource.BONDED_CACHE;
            case LIVE_SDP:
            case CACHE_FALLBACK:
                return YoCbtSppEndpointSource.SDP_RESOLVED;
            case CONFIG_INJECTED:
                return YoCbtSppEndpointSource.VENDOR_CONFIG;
            default:
                return YoCbtSppEndpointSource.UNKNOWN;
        }
    }

    @NonNull
    private static YoCbtSppEndpointSource mapChannelSource(@NonNull YoCbtSdpSource source) {
        switch (source) {
            case CONFIG_INJECTED:
                return YoCbtSppEndpointSource.VENDOR_CONFIG;
            case LIVE_SDP:
            case CACHE_FALLBACK:
                return YoCbtSppEndpointSource.SDP_RESOLVED;
            case BONDED_CACHE:
                return YoCbtSppEndpointSource.BONDED_CACHE;
            default:
                return YoCbtSppEndpointSource.UNKNOWN;
        }
    }
}

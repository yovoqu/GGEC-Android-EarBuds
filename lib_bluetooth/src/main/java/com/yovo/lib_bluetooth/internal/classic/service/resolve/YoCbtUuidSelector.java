package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import java.util.List;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 根据策略从 SDP 结果中选择最佳服务 UUID。
 */
public final class YoCbtUuidSelector {

    @Nullable
    public UUID select(@NonNull YoCbtSdpResult result, @NonNull YoCbtSppUuidPolicy policy) {
        List<UUID> uuids = result.getUuids();
        if (uuids.isEmpty()) return null;

        switch (policy.getStrategy()) {
            case STANDARD_SPP_ONLY:
                if (result.hasUuid(YoCbtSppEndpoint.STANDARD_SPP_UUID)) {
                    return YoCbtSppEndpoint.STANDARD_SPP_UUID;
                }
                return null;
            case PREFERRED_THEN_SPP:
                UUID preferred = policy.getPreferredUuid();
                if (preferred != null && result.hasUuid(preferred)) {
                    return preferred;
                }
                if (result.hasUuid(YoCbtSppEndpoint.STANDARD_SPP_UUID)) {
                    return YoCbtSppEndpoint.STANDARD_SPP_UUID;
                }
                return null;
            case SDP_RESOLVED_ONLY:
                return uuids.get(0);
            case ANY_AVAILABLE:
            default:
                if (result.hasUuid(YoCbtSppEndpoint.STANDARD_SPP_UUID)) {
                    return YoCbtSppEndpoint.STANDARD_SPP_UUID;
                }
                return uuids.get(0);
        }
    }

    @Nullable
    public UUID select(@NonNull YoCbtSdpResult result, @Nullable UUID preferredUuid) {
        YoCbtSppUuidPolicy policy = preferredUuid != null
                ? YoCbtSppUuidPolicy.preferredThenSpp(preferredUuid)
                : YoCbtSppUuidPolicy.standardSpp();
        return select(result, policy);
    }

    public boolean contains(@NonNull YoCbtSdpResult result, @NonNull UUID uuid) {
        return result.hasUuid(uuid);
    }
}

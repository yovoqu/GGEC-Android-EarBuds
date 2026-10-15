package com.yovo.lib_bluetooth.internal.classic.spp.policy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConfig;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 根据连接策略构建有序 SPP 端点尝试列表，回退端点受 allowFallback 控制。
 */
public final class YoCbtSppConnectionAttemptPlanner {

    private YoCbtSppConnectionAttemptPlanner() {
    }

    @NonNull
    public static List<YoCbtSppEndpoint> plan(@NonNull YoCbtSppConfig config,
                                              @NonNull YoCbtSppEndpoint primary) {
        return plan(config, primary, null);
    }

    @NonNull
    public static List<YoCbtSppEndpoint> plan(@NonNull YoCbtSppConfig config,
                                              @NonNull YoCbtSppEndpoint primary,
                                              @Nullable List<YoCbtSppEndpoint> sdpResolvedEndpoints) {
        List<YoCbtSppEndpoint> attempts = new ArrayList<>();
        attempts.add(primary);

        YoCbtSppConnectionPolicy policy = config.getConnectionPolicy();
        if (!policy.isAllowFallback()) {
            return Collections.unmodifiableList(attempts);
        }

        YoCbtSppConnectionStrategy strategy = policy.getStrategy();
        switch (strategy) {
            case CHANNEL_THEN_UUID:
                if (primary.isChannel()) {
                    attempts.add(YoCbtSppEndpoint.uuid(
                            YoCbtSppEndpoint.STANDARD_SPP_UUID,
                            YoCbtSppEndpointSource.DEFAULT_SPP,
                            primary.getSecurityMode()));
                }
                break;
            case UUID_THEN_CHANNEL:
                if (primary.isUuid() && config.isAllowHiddenApiChannel()) {
                    YoCbtSppEndpoint channelFallback = findChannelFallback(sdpResolvedEndpoints);
                    if (channelFallback != null) {
                        attempts.add(channelFallback);
                    }
                }
                break;
            default:
                break;
        }
        return Collections.unmodifiableList(attempts);
    }

    @Nullable
    private static YoCbtSppEndpoint findChannelFallback(@Nullable List<YoCbtSppEndpoint> sdpResolvedEndpoints) {
        if (sdpResolvedEndpoints == null || sdpResolvedEndpoints.isEmpty()) return null;
        for (YoCbtSppEndpoint endpoint : sdpResolvedEndpoints) {
            if (endpoint.isChannel()) return endpoint;
        }
        return null;
    }
}

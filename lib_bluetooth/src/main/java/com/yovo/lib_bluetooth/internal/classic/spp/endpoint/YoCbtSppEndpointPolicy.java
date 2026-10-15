package com.yovo.lib_bluetooth.internal.classic.spp.endpoint;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 控制 SPP 端点连接前解析方式的策略（SDP、回退与隐藏 API 通道等）。
 */
public final class YoCbtSppEndpointPolicy {

    private final YoCbtSppConnectStrategy strategy;
    private final boolean allowChannelFallback;
    private final boolean requireSdpResolution;

    public YoCbtSppEndpointPolicy(@NonNull YoCbtSppConnectStrategy strategy,
                                   boolean allowChannelFallback,
                                   boolean requireSdpResolution) {
        this.strategy = strategy;
        this.allowChannelFallback = allowChannelFallback;
        this.requireSdpResolution = requireSdpResolution;
    }

    @NonNull
    public YoCbtSppConnectStrategy getStrategy() { return strategy; }
    public boolean isAllowChannelFallback() { return allowChannelFallback; }
    public boolean isRequireSdpResolution() { return requireSdpResolution; }

    /**
     * 默认：仅 UUID，无通道回退，不要求 SDP。
     */
    @NonNull
    public static YoCbtSppEndpointPolicy defaultPolicy() {
        return new YoCbtSppEndpointPolicy(YoCbtSppConnectStrategy.UUID_ONLY, false, false);
    }

    /**
     * 优先 UUID，可选以 SDP 解析的通道作为回退。
     */
    @NonNull
    public static YoCbtSppEndpointPolicy uuidThenChannel() {
        return new YoCbtSppEndpointPolicy(YoCbtSppConnectStrategy.UUID_THEN_CHANNEL, true, false);
    }

    /**
     * 仅使用调用方指定的端点——不进行 SDP，也不回退。
     */
    @NonNull
    public static YoCbtSppEndpointPolicy userSpecifiedOnly() {
        return new YoCbtSppEndpointPolicy(YoCbtSppConnectStrategy.USER_SPECIFIED_ONLY, false, false);
    }
}

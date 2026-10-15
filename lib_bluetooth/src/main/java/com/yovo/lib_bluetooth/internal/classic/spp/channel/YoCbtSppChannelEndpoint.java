package com.yovo.lib_bluetooth.internal.classic.spp.channel;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description RFCOMM 通道端点，用于隐藏 API 直连通道 SPP 连接。
 */
public final class YoCbtSppChannelEndpoint {
    private final int channel;
    private final YoCbtSppEndpointSource source;
    private final String description;

    public YoCbtSppChannelEndpoint(int channel,
                                    @NonNull YoCbtSppEndpointSource source,
                                    @NonNull String description) {
        if (channel <= 0 || channel > 30) {
            throw new IllegalArgumentException("RFCOMM channel out of valid range [1,30]: " + channel);
        }
        this.channel = channel;
        this.source = source;
        this.description = description;
    }

    public int getChannel() { return channel; }
    @NonNull public YoCbtSppEndpointSource getSource() { return source; }
    @NonNull public String getDescription() { return description; }
}

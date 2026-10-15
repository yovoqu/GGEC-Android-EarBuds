package com.yovo.lib_bluetooth.internal.classic.spp.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointType;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;

import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 传输端点，可为 SDP 解析的 UUID 或 RFCOMM 通道号，并记录端点来源。
 */
public final class YoCbtSppEndpoint {

    public static final UUID STANDARD_SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final YoCbtSppEndpointType type;
    private final UUID uuid;
    private final int channel;
    private final YoCbtProfileType profileType;
    private final String serviceName;
    private final YoCbtSppSecurityMode securityMode;
    private final YoCbtSppEndpointSource source;
    private final String metadata;

    private YoCbtSppEndpoint(Builder builder) {
        this.type = builder.type;
        this.uuid = builder.uuid;
        this.channel = builder.channel;
        this.profileType = builder.profileType;
        this.serviceName = builder.serviceName;
        this.securityMode = builder.securityMode;
        this.source = builder.source;
        this.metadata = builder.metadata;
    }

    // ── 工厂方法 ────────────────────────────────────────────────

    @NonNull
    public static YoCbtSppEndpoint defaultSpp() {
        return new Builder(YoCbtSppEndpointType.UUID, STANDARD_SPP_UUID)
                .securityMode(YoCbtSppSecurityMode.SECURE)
                .source(YoCbtSppEndpointSource.DEFAULT_SPP)
                .build();
    }

    @NonNull
    public static YoCbtSppEndpoint uuid(@NonNull UUID uuid) {
        return new Builder(YoCbtSppEndpointType.UUID, uuid)
                .securityMode(YoCbtSppSecurityMode.SECURE)
                .source(YoCbtSppEndpointSource.USER_SPECIFIED)
                .build();
    }

    @NonNull
    public static YoCbtSppEndpoint uuid(@NonNull UUID uuid,
                                         @NonNull YoCbtSppEndpointSource source,
                                         @NonNull YoCbtSppSecurityMode securityMode) {
        return new Builder(YoCbtSppEndpointType.UUID, uuid)
                .securityMode(securityMode)
                .source(source)
                .build();
    }

    @NonNull
    public static YoCbtSppEndpoint channel(int channel,
                                            @NonNull YoCbtSppEndpointSource source,
                                            @NonNull YoCbtSppSecurityMode securityMode) {
        return new Builder(YoCbtSppEndpointType.RFCOMM_CHANNEL)
                .channel(channel)
                .securityMode(securityMode)
                .source(source)
                .build();
    }

    // ── 访问器 ──────────────────────────────────────────────────

    @NonNull public YoCbtSppEndpointType getType() { return type; }
    @Nullable public UUID getUuid() { return uuid; }
    public int getChannel() { return channel; }
    @NonNull public YoCbtProfileType getProfileType() { return profileType; }
    @NonNull public String getServiceName() { return serviceName; }
    @NonNull public YoCbtSppSecurityMode getSecurityMode() { return securityMode; }
    @NonNull public YoCbtSppEndpointSource getSource() { return source; }
    @NonNull public String getMetadata() { return metadata; }

    public boolean isUuid() { return type == YoCbtSppEndpointType.UUID; }
    public boolean isChannel() { return type == YoCbtSppEndpointType.RFCOMM_CHANNEL; }
    public boolean isDefaultSpp() { return STANDARD_SPP_UUID.equals(uuid); }

    // ── equals / hashCode / toString 方法 ─────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtSppEndpoint)) return false;
        YoCbtSppEndpoint that = (YoCbtSppEndpoint) o;
        return channel == that.channel
                && type == that.type
                && Objects.equals(uuid, that.uuid)
                && securityMode == that.securityMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uuid, channel, securityMode);
    }

    @Override
    public String toString() {
        if (type == YoCbtSppEndpointType.UUID) {
            return "SPPEndpoint{uuid=" + uuid + ", security=" + securityMode + ", source=" + source + "}";
        }
        return "SPPEndpoint{channel=" + channel + ", security=" + securityMode + ", source=" + source + "}";
    }

    // ── 构建器 ──────────────────────────────────────────────────

    public static final class Builder {
        private final YoCbtSppEndpointType type;
        private final UUID uuid;
        private int channel;
        private YoCbtProfileType profileType = YoCbtProfileType.SPP;
        private String serviceName = "";
        private YoCbtSppSecurityMode securityMode = YoCbtSppSecurityMode.SECURE;
        private YoCbtSppEndpointSource source = YoCbtSppEndpointSource.USER_SPECIFIED;
        private String metadata = "";

        public Builder(@NonNull YoCbtSppEndpointType type) {
            this.type = type;
            this.uuid = null;
        }

        public Builder(@NonNull YoCbtSppEndpointType type, @NonNull UUID uuid) {
            this.type = type;
            this.uuid = uuid;
        }

        public Builder channel(int channel) { this.channel = channel; return this; }
        public Builder profileType(YoCbtProfileType pt) { this.profileType = pt; return this; }
        public Builder serviceName(String name) { this.serviceName = name; return this; }
        public Builder securityMode(YoCbtSppSecurityMode mode) { this.securityMode = mode; return this; }
        public Builder source(YoCbtSppEndpointSource src) { this.source = src; return this; }
        public Builder metadata(String meta) { this.metadata = meta; return this; }

        public YoCbtSppEndpoint build() { return new YoCbtSppEndpoint(this); }
    }
}

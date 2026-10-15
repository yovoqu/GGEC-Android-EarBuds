package com.yovo.lib_bluetooth.internal.classic.service.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 表示通过 SDP 发现的单个服务记录，包含 UUID、RFCOMM 信道与服务名称等字段。
 */
public final class YoCbtServiceRecord {

    @NonNull
    private final UUID uuid;

    private final int channel;

    @NonNull
    private final String name;

    @NonNull
    private final String provider;

    /**
     * @param uuid     标识 profile 或服务的 service UUID
     * @param channel  RFCOMM 信道号，若未知则为 -1
     * @param name     人类可读的服务名称（可能为空）
     * @param provider 提供者名称（可能为空）
     */
    public YoCbtServiceRecord(@NonNull UUID uuid,
                               int channel,
                               @NonNull String name,
                               @NonNull String provider) {
        this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
        this.channel = channel;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    /**
     * 返回标识 profile 或服务的 service UUID。
     */
    @NonNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * 返回 RFCOMM 信道号；若信道未知则返回 -1。
     */
    public int getChannel() {
        return channel;
    }

    /**
     * 返回该服务的人类可读名称。若远程设备未提供名称，可能为空。
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * 返回提供者名称（例如注册该服务的厂商或应用）。可能为空。
     */
    @NonNull
    public String getProvider() {
        return provider;
    }

    /**
     * 若该记录具有有效（非负）的 RFCOMM 信道号，则返回 {@code true}。
     */
    public boolean hasChannel() {
        return channel >= 1 && channel <= 30;
    }

    /**
     * 若该记录具有非空的服务名称，则返回 {@code true}。
     */
    public boolean hasName() {
        return !name.isEmpty();
    }

    /**
     * 若该记录具有非空的提供者名称，则返回 {@code true}。
     */
    public boolean hasProvider() {
        return !provider.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtServiceRecord)) return false;
        YoCbtServiceRecord that = (YoCbtServiceRecord) o;
        return channel == that.channel
                && uuid.equals(that.uuid)
                && name.equals(that.name)
                && provider.equals(that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, channel, name, provider);
    }

    @Override
    public String toString() {
        return "YoCbtServiceRecord{"
                + "uuid=" + uuid
                + ", channel=" + channel
                + ", name='" + name + '\''
                + ", provider='" + provider + '\''
                + '}';
    }
}

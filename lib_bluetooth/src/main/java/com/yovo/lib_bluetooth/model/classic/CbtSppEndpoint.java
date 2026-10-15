package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * SPP 连接端点。
 */
public final class CbtSppEndpoint {

    public static final UUID STANDARD_SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public enum Type {
        DEFAULT,
        UUID,
        CHANNEL
    }

    @NonNull
    private final Type type;
    @Nullable
    private final UUID uuid;
    private final int channel;

    private CbtSppEndpoint(@NonNull Type type, @Nullable UUID uuid, int channel) {
        this.type = type;
        this.uuid = uuid;
        this.channel = channel;
    }

    @NonNull
    public static CbtSppEndpoint defaultSpp() {
        return new CbtSppEndpoint(Type.DEFAULT, STANDARD_SPP_UUID, 0);
    }

    @NonNull
    public static CbtSppEndpoint uuid(@NonNull UUID uuid) {
        return new CbtSppEndpoint(Type.UUID, uuid, 0);
    }

    @NonNull
    public static CbtSppEndpoint channel(int channel) {
        return new CbtSppEndpoint(Type.CHANNEL, null, channel);
    }

    @NonNull
    public Type type() {
        return type;
    }

    @Nullable
    public UUID uuid() {
        return uuid;
    }

    public int channel() {
        return channel;
    }

    @NonNull
    @Override
    public String toString() {
        return "CbtSppEndpoint{type=" + type + ", uuid=" + uuid + ", channel=" + channel + '}';
    }
}

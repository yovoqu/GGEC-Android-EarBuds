package com.yovo.lib_bluetooth.internal.classic.service.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 维护 Android 公共 SDP 无法提供的 UUID 到 RFCOMM 信道映射。
 */
public final class YoCbtServiceChannelRegistry {

    private final Map<MappingKey, YoCbtServiceChannelMapping> mappings = new LinkedHashMap<>();

    public synchronized void register(@NonNull YoCbtServiceChannelMapping mapping) {
        Objects.requireNonNull(mapping, "mapping must not be null");
        mappings.put(new MappingKey(mapping.getDeviceId(), mapping.getServiceUuid()), mapping);
    }

    public synchronized void unregister(@NonNull YoCbtDeviceId deviceId, @NonNull UUID serviceUuid) {
        mappings.remove(new MappingKey(deviceId, serviceUuid));
    }

    public synchronized void clear(@NonNull YoCbtDeviceId deviceId) {
        mappings.keySet().removeIf(key -> key.deviceId.equals(deviceId));
    }

    public synchronized void clearAll() {
        mappings.clear();
    }

    public synchronized int resolveChannel(@NonNull YoCbtDeviceId deviceId, @NonNull UUID serviceUuid) {
        YoCbtServiceChannelMapping mapping = mappings.get(new MappingKey(deviceId, serviceUuid));
        return mapping == null ? -1 : mapping.getChannel();
    }

    @Nullable
    public synchronized YoCbtServiceChannelMapping getMapping(@NonNull YoCbtDeviceId deviceId,
                                                              @NonNull UUID serviceUuid) {
        return mappings.get(new MappingKey(deviceId, serviceUuid));
    }

    private static final class MappingKey {
        private final YoCbtDeviceId deviceId;
        private final UUID serviceUuid;

        MappingKey(YoCbtDeviceId deviceId, UUID serviceUuid) {
            this.deviceId = deviceId;
            this.serviceUuid = serviceUuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MappingKey)) return false;
            MappingKey that = (MappingKey) o;
            return deviceId.equals(that.deviceId) && serviceUuid.equals(that.serviceUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, serviceUuid);
        }
    }
}

package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.service.channel.YoCbtServiceChannelRegistry;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpSource;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtServiceRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 从 Android UUID 列表和信道注册表构建 {@link YoCbtSdpResult} 实例。
 */
public final class YoCbtServiceRecordFactory {

    private YoCbtServiceRecordFactory() {
    }

    @NonNull
    public static YoCbtSdpResult fromUuidList(@NonNull YoCbtDeviceId deviceId,
                                              @NonNull List<UUID> uuids,
                                              @NonNull YoCbtSdpSource source,
                                              @NonNull YoCbtServiceChannelRegistry channelRegistry,
                                              long resolvedAtMillis) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(uuids, "uuids must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(channelRegistry, "channelRegistry must not be null");

        List<YoCbtServiceRecord> records = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            if (uuid == null) continue;
            int channel = channelRegistry.resolveChannel(deviceId, uuid);
            records.add(new YoCbtServiceRecord(uuid, channel, "", "L2CAP/RFCOMM"));
        }
        return new YoCbtSdpResult(deviceId, records, source, resolvedAtMillis);
    }
}

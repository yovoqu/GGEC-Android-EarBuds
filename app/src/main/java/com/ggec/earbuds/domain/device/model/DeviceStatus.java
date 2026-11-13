package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 一条设备"现在怎样"。
 *
 * <p>{@code lastSeenMillis} 是这台设备最后一次有事实的时刻（连接就绪、上报、断开），
 * 冷启动读档案时原样带回，不用"当前时间"冒充。</p>
 */
public record DeviceStatus(RecordOrigin origin, ConnectionState connection, long lastSeenMillis) {

    public DeviceStatus {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(connection, "connection");
        if (lastSeenMillis < 0) {
            throw new IllegalArgumentException("lastSeenMillis must not be negative");
        }
    }

    public static DeviceStatus archived(long lastSeenMillis) {
        return new DeviceStatus(RecordOrigin.ARCHIVED, ConnectionState.DISCONNECTED, lastSeenMillis);
    }

    public boolean live() {
        return origin == RecordOrigin.LIVE;
    }

    public boolean ready() {
        return connection == ConnectionState.READY;
    }
}

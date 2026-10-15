package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;

public final class NotificationUpdatePolicy {

    public enum Type {
        NO_THROTTLE,
        PROGRESS_THROTTLE,
        STATE_KEYED_THROTTLE
    }

    private final Type type;
    private final float minProgressDeltaPercent;
    private final long minIntervalMs;

    private NotificationUpdatePolicy(@NonNull Type type,
                                     float minProgressDeltaPercent,
                                     long minIntervalMs) {
        this.type = type;
        this.minProgressDeltaPercent = minProgressDeltaPercent;
        this.minIntervalMs = Math.max(0L, minIntervalMs);
    }

    @NonNull
    public static NotificationUpdatePolicy noThrottle() {
        return new NotificationUpdatePolicy(Type.NO_THROTTLE, 0f, 0L);
    }

    @NonNull
    public static NotificationUpdatePolicy progressThrottle(float minDeltaPercent,
                                                            long minIntervalMs) {
        return new NotificationUpdatePolicy(Type.PROGRESS_THROTTLE, minDeltaPercent, minIntervalMs);
    }

    @NonNull
    public static NotificationUpdatePolicy stateKeyedThrottle(float minDeltaPercent,
                                                              long minIntervalMs) {
        return new NotificationUpdatePolicy(Type.STATE_KEYED_THROTTLE, minDeltaPercent, minIntervalMs);
    }

    @NonNull
    public Type type() {
        return type;
    }

    public float minProgressDeltaPercent() {
        return minProgressDeltaPercent;
    }

    public long minIntervalMs() {
        return minIntervalMs;
    }
}

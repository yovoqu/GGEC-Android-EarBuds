package com.yovo.lib_common.system.keepalive.internal.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.NotificationUpdatePolicy;

public final class KeepAliveNotificationThrottle {

    @Nullable private String lastThrottleKey;
    private float lastProgressPercent = -1f;
    private long lastNotifyAtMs;

    public boolean shouldSkip(@NonNull NotificationUpdatePolicy policy,
                              @NonNull KeepAliveNotificationSpec spec) {
        if (policy.type() == NotificationUpdatePolicy.Type.NO_THROTTLE) {
            return false;
        }
        String key = spec.throttleKey();
        long now = System.currentTimeMillis();
        if (policy.type() == NotificationUpdatePolicy.Type.STATE_KEYED_THROTTLE
                && key != null
                && !key.equals(lastThrottleKey)) {
            lastThrottleKey = key;
            lastProgressPercent = spec.progressValue();
            lastNotifyAtMs = now;
            return false;
        }
        if (!spec.showProgress()) {
            return false;
        }
        float percent = spec.progressValue();
        float delta = Math.abs(percent - lastProgressPercent);
        // minDelta <= 0 表示“不按进度差限流”，只由 interval 门控。
        boolean deltaPassed = policy.minProgressDeltaPercent() > 0f
                && delta >= policy.minProgressDeltaPercent();
        boolean intervalPassed = now - lastNotifyAtMs >= policy.minIntervalMs();
        // delta 与 interval 是“或”关系：minDelta=0 时由 interval 单独限流；
        // 只有两项都未满足才跳过。
        if (deltaPassed || intervalPassed) {
            lastProgressPercent = percent;
            lastNotifyAtMs = now;
            return false;
        }
        return true;
    }

    public void reset() {
        lastThrottleKey = null;
        lastProgressPercent = -1f;
        lastNotifyAtMs = 0L;
    }
}

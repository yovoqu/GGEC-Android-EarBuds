package com.yovo.lib_common.system.keepalive.notification;

import com.yovo.lib_common.system.keepalive.internal.notification.KeepAliveNotificationThrottle;

import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.NotificationUpdatePolicy;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepAliveNotificationThrottleTest {

    @Test
    public void stateKeyedThrottle_keyChangeBypassesInterval() {
        KeepAliveNotificationThrottle throttle = new KeepAliveNotificationThrottle();
        NotificationUpdatePolicy policy =
                NotificationUpdatePolicy.stateKeyedThrottle(1f, 60_000L);

        KeepAliveNotificationSpec first = KeepAliveNotificationSpec.builder()
                .title("t")
                .text("a")
                .smallIconResId(1)
                .progress(true, false, 100, 10)
                .throttleKey("OTA_RUNNING")
                .build();
        KeepAliveNotificationSpec second = KeepAliveNotificationSpec.builder()
                .title("t")
                .text("b")
                .smallIconResId(1)
                .progress(true, false, 100, 11)
                .throttleKey("OTA_VERIFYING")
                .build();

        assertFalse(throttle.shouldSkip(policy, first));
        assertFalse(throttle.shouldSkip(policy, second));
    }

    @Test
    public void progressThrottle_skipsSmallDeltaWithinInterval() {
        KeepAliveNotificationThrottle throttle = new KeepAliveNotificationThrottle();
        NotificationUpdatePolicy policy =
                NotificationUpdatePolicy.progressThrottle(5f, 60_000L);

        KeepAliveNotificationSpec first = KeepAliveNotificationSpec.builder()
                .title("t")
                .text("a")
                .smallIconResId(1)
                .progress(true, false, 100, 10)
                .build();
        KeepAliveNotificationSpec second = KeepAliveNotificationSpec.builder()
                .title("t")
                .text("b")
                .smallIconResId(1)
                .progress(true, false, 100, 11)
                .build();

        assertFalse(throttle.shouldSkip(policy, first));
        assertTrue(throttle.shouldSkip(policy, second));
    }

    @Test
    public void progressThrottle_zeroDelta_intervalGatesUpdates() {
        KeepAliveNotificationThrottle throttle = new KeepAliveNotificationThrottle();
        NotificationUpdatePolicy policy =
                NotificationUpdatePolicy.progressThrottle(0f, 60_000L);

        KeepAliveNotificationSpec first = KeepAliveNotificationSpec.builder()
                .title("t")
                .text("a")
                .smallIconResId(1)
                .progress(true, false, 100, 10)
                .build();
        KeepAliveNotificationSpec second = KeepAliveNotificationSpec.builder()
                .title("t")
                .text("a")
                .smallIconResId(1)
                .progress(true, false, 100, 10)
                .build();

        assertFalse(throttle.shouldSkip(policy, first));
        assertTrue(throttle.shouldSkip(policy, second));
    }
}

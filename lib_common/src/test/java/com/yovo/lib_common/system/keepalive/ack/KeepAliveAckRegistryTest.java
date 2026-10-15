package com.yovo.lib_common.system.keepalive.ack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import com.yovo.lib_common.system.keepalive.internal.ack.KeepAliveAckRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class KeepAliveAckRegistryTest {

    private static void idle(long millis) {
        ShadowLooper shadowLooper = Shadows.shadowOf(Looper.getMainLooper());
        shadowLooper.idleFor(millis, TimeUnit.MILLISECONDS);
    }

    @Test
    public void complete_deliversSuccess() {
        KeepAliveAckRegistry registry = KeepAliveAckRegistry.get();
        List<String> events = new ArrayList<>();
        registry.await("s1", (sessionId, success, detail) ->
                events.add(sessionId + ":" + success));

        registry.complete("s1", true, null);

        assertEquals(List.of("s1:true"), events);
    }

    @Test
    public void timeout_deliversFailure() {
        KeepAliveAckRegistry registry = KeepAliveAckRegistry.get();
        List<String> events = new ArrayList<>();
        registry.await("s2", (sessionId, success, detail) ->
                events.add(sessionId + ":" + success + ":" + detail), 50L);

        idle(100);

        assertEquals(List.of("s2:false:ack timeout"), events);
    }

    @Test
    public void cancel_suppressesCallback() {
        KeepAliveAckRegistry registry = KeepAliveAckRegistry.get();
        List<String> events = new ArrayList<>();
        registry.await("s3", (sessionId, success, detail) ->
                events.add(sessionId + ":" + success), 50L);

        registry.cancel("s3");
        idle(100);

        assertTrue(events.isEmpty());
    }
}

package com.yovo.lib_common.system.keepalive.internal.power;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;

public final class PartialWakeLockController {

    private final PowerManager.WakeLock wakeLock;
    private final Handler handler;
    private final Runnable clearHeld = () -> held = false;
    private boolean held;

    public PartialWakeLockController(@NonNull Context context,
                                     @NonNull String tag,
                                     long timeoutMs) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        wakeLock.setReferenceCounted(false);
        handler = new Handler(Looper.getMainLooper());
    }

    public void acquire(long timeoutMs) {
        long timeout = Math.max(1L, timeoutMs);
        wakeLock.acquire(timeout);
        held = true;
        handler.removeCallbacks(clearHeld);
        handler.postDelayed(clearHeld, timeout);
    }

    public void sync(boolean shouldHold, long timeoutMs) {
        if (shouldHold) {
            acquire(timeoutMs);
        } else {
            release();
        }
    }

    public void release() {
        if (!held) return;
        handler.removeCallbacks(clearHeld);
        try {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (RuntimeException ignored) {
        }
        held = false;
    }

    public boolean isHeld() {
        return held;
    }
}

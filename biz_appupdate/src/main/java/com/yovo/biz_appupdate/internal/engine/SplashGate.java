package com.yovo.biz_appupdate.internal.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.interactor.ResolveLaunchUpdateAction;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SplashGate {

    public interface Callback {
        void onReady(@NonNull LaunchDecision decision);
    }

    public interface Session {
        void cancel();
    }

    private final ResolveLaunchUpdateAction resolveLaunchUpdateAction;
    private final UpdatePolicy policy;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SplashGate(@NonNull ResolveLaunchUpdateAction resolveLaunchUpdateAction,
                      @NonNull UpdatePolicy policy) {
        this.resolveLaunchUpdateAction = resolveLaunchUpdateAction;
        this.policy = policy;
    }

    @NonNull
    public Session run(@NonNull Callback callback) {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final AtomicBoolean checkDone = new AtomicBoolean(false);
        final LaunchDecision[] holder = new LaunchDecision[]{LaunchDecision.none()};
        final long startMs = SystemClock.elapsedRealtime();
        final Runnable[] timeoutRunnable = new Runnable[1];
        final Runnable[] delayedComplete = new Runnable[1];

        Runnable finish = () -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            mainHandler.removeCallbacks(timeoutRunnable[0]);
            if (delayedComplete[0] != null) {
                mainHandler.removeCallbacks(delayedComplete[0]);
            }
            callback.onReady(holder[0]);
        };

        Runnable tryFinish = () -> {
            if (finished.get() || !checkDone.get()) {
                return;
            }
            long remaining = policy.splashMinDelayMs() - (SystemClock.elapsedRealtime() - startMs);
            if (remaining <= 0) {
                finish.run();
            } else {
                delayedComplete[0] = finish;
                mainHandler.postDelayed(delayedComplete[0], remaining);
            }
        };

        timeoutRunnable[0] = () -> {
            checkDone.set(true);
            tryFinish.run();
        };
        mainHandler.postDelayed(timeoutRunnable[0], policy.splashCheckTimeoutMs());

        resolveLaunchUpdateAction.run(decision -> {
            if (finished.get()) {
                return;
            }
            holder[0] = decision;
            checkDone.set(true);
            mainHandler.removeCallbacks(timeoutRunnable[0]);
            tryFinish.run();
        });

        return () -> {
            finished.set(true);
            mainHandler.removeCallbacks(timeoutRunnable[0]);
            if (delayedComplete[0] != null) {
                mainHandler.removeCallbacks(delayedComplete[0]);
            }
        };
    }
}

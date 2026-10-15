package com.yovo.lib_common.system.refresh_rate.internal.session;

import android.app.Activity;
import android.os.Looper;
import android.view.Choreographer;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.internal.diagnostics.RefreshRateFrameSampler;
import com.yovo.lib_common.system.refresh_rate.internal.window.RefreshRateWindowApplier;
import com.yovo.lib_common.system.refresh_rate.internal.window.RefreshRateWindowSnapshot;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateFrameSample;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateRequest;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateResult;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateTestResult;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateTestSession;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * 基于真实 UI VSYNC 回调的单次诊断会话实现。
 */
public final class RefreshRateTestSessionImpl
        implements RefreshRateTestSession, Choreographer.FrameCallback {

    private static final int MIN_SAMPLE_INTERVALS = 10;
    private static final int MAX_SAMPLE_INTERVALS = 600;

    private final WeakReference<Activity> activityRef;
    private final Choreographer choreographer;
    private final RefreshRateFrameSampler sampler;
    private RefreshRateWindowSnapshot original;
    private RefreshRateResult request;
    private Listener listener;
    private int settleFramesRemaining;
    private int sampleIntervals;
    private boolean running;
    private boolean restored;

    @MainThread
    public RefreshRateTestSessionImpl(@NonNull Activity activity) {
        requireMainThread();
        activityRef = new WeakReference<>(Objects.requireNonNull(activity, "activity"));
        choreographer = Choreographer.getInstance();
        sampler = new RefreshRateFrameSampler();
    }

    @MainThread
    @Override
    public void start(@NonNull RefreshRatePolicy policy, @NonNull Listener listener) {
        start(policy, DEFAULT_SETTLE_FRAMES, DEFAULT_SAMPLE_INTERVALS, listener);
    }

    @MainThread
    @Override
    public void start(@NonNull RefreshRatePolicy policy,
                      int settleFrames,
                      int sampleIntervals,
                      @NonNull Listener listener) {
        requireMainThread();
        if (running) {
            throw new IllegalStateException("refresh-rate test session is already running");
        }
        if (settleFrames < 1 || settleFrames > MAX_SAMPLE_INTERVALS) {
            throw new IllegalArgumentException("settleFrames must be in [1, 600]");
        }
        if (sampleIntervals < MIN_SAMPLE_INTERVALS
                || sampleIntervals > MAX_SAMPLE_INTERVALS) {
            throw new IllegalArgumentException(
                    "sampleIntervals must be in [10, 600]");
        }
        Activity activity = requireActivity();
        original = RefreshRateWindowApplier.readWindowSnapshot(activity);
        request = RefreshRateWindowApplier.apply(activity, new RefreshRateRequest(policy));
        this.listener = Objects.requireNonNull(listener, "listener");
        this.settleFramesRemaining = settleFrames;
        this.sampleIntervals = sampleIntervals;
        restored = false;
        running = true;
        choreographer.postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (--settleFramesRemaining > 0) {
            choreographer.postFrameCallback(this);
            return;
        }
        sampler.start(sampleIntervals, this::complete);
    }

    @MainThread
    @Override
    public void cancel() {
        requireMainThread();
        if (!running) {
            return;
        }
        choreographer.removeFrameCallback(this);
        sampler.cancel();
        running = false;
        restore();
        listener = null;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void complete(@NonNull RefreshRateFrameSample sample) {
        running = false;
        restore();
        Listener completed = listener;
        listener = null;
        RefreshRateResult applied = Objects.requireNonNull(request, "request");
        float requestedHz = applied.selectedRefreshRateHz();
        boolean observed = requestedHz > 0f && sample.matches(requestedHz);
        if (completed != null) {
            completed.onComplete(new RefreshRateTestResult(
                    applied, sample, observed, restored));
        }
    }

    private void restore() {
        Activity activity = activityRef.get();
        if (!restored && activity != null && original != null) {
            RefreshRateWindowApplier.restoreWindowSnapshot(activity, original);
            restored = true;
        }
    }

    @NonNull
    private Activity requireActivity() {
        Activity activity = activityRef.get();
        if (activity == null) {
            throw new IllegalStateException("Activity was destroyed");
        }
        return activity;
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "RefreshRateTestSession must run on the main thread");
        }
    }
}

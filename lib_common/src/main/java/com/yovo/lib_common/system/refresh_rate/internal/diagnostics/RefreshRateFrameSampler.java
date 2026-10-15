package com.yovo.lib_common.system.refresh_rate.internal.diagnostics;

import android.os.Looper;
import android.view.Choreographer;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateFrameSample;

import java.util.Arrays;
import java.util.Objects;

/** Single-shot, allocation-free-during-sampling measurement of actual UI vsync cadence. */
public final class RefreshRateFrameSampler implements Choreographer.FrameCallback {

    public interface Listener {
        void onSampleComplete(@NonNull RefreshRateFrameSample sample);
    }

    private static final int WARMUP_INTERVALS = 4;
    private static final int MIN_SAMPLE_COUNT = 10;
    private static final int MAX_SAMPLE_COUNT = 600;

    private final Choreographer choreographer;
    private long[] intervals = new long[0];
    private Listener listener;
    private int index;
    private int warmupRemaining;
    private long lastFrameNanos;
    private boolean running;

    @MainThread
    public RefreshRateFrameSampler() {
        requireMainThread();
        choreographer = Choreographer.getInstance();
    }

    @MainThread
    public void start(int sampleCount, @NonNull Listener listener) {
        requireMainThread();
        if (sampleCount < MIN_SAMPLE_COUNT || sampleCount > MAX_SAMPLE_COUNT) {
            throw new IllegalArgumentException("sampleCount must be in [10, 600]");
        }
        cancel();
        this.intervals = new long[sampleCount];
        this.listener = Objects.requireNonNull(listener, "listener");
        this.index = 0;
        this.warmupRemaining = WARMUP_INTERVALS;
        this.lastFrameNanos = -1L;
        this.running = true;
        choreographer.postFrameCallback(this);
    }

    @MainThread
    public void cancel() {
        requireMainThread();
        if (running) {
            choreographer.removeFrameCallback(this);
        }
        running = false;
        listener = null;
        index = 0;
        lastFrameNanos = -1L;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (lastFrameNanos >= 0L && frameTimeNanos > lastFrameNanos) {
            long interval = frameTimeNanos - lastFrameNanos;
            if (warmupRemaining > 0) {
                warmupRemaining--;
            } else {
                intervals[index++] = interval;
            }
        }
        lastFrameNanos = frameTimeNanos;
        if (index >= intervals.length) {
            complete();
        } else {
            choreographer.postFrameCallback(this);
        }
    }

    private void complete() {
        running = false;
        Listener completedListener = listener;
        listener = null;

        long[] sorted = intervals.clone();
        Arrays.sort(sorted);
        long median = percentile(sorted, 0.50d);
        long p95 = percentile(sorted, 0.95d);
        int primary = 0;
        int multiples = 0;
        for (long interval : intervals) {
            double ratio = (double) interval / median;
            long nearest = Math.max(1L, Math.min(3L, Math.round(ratio)));
            double error = Math.abs(ratio - nearest) / nearest;
            if (nearest == 1L && error <= 0.12d) {
                primary++;
            }
            if (error <= 0.12d) {
                multiples++;
            }
        }
        float primaryRatio = (float) primary / sorted.length;
        float multipleRatio = (float) multiples / sorted.length;
        RefreshRateFrameSample.Cadence cadence =
                primaryRatio >= .90f ? RefreshRateFrameSample.Cadence.STABLE
                        : multipleRatio >= .90f
                        ? RefreshRateFrameSample.Cadence.MIXED_MULTIPLE
                        : RefreshRateFrameSample.Cadence.UNSTABLE;
        RefreshRateFrameSample sample = new RefreshRateFrameSample(
                sorted.length, median, p95, sorted[0], sorted[sorted.length - 1],
                primaryRatio, multipleRatio, cadence);
        if (completedListener != null) {
            completedListener.onSampleComplete(sample);
        }
    }

    private static long percentile(long[] sorted, double fraction) {
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("RefreshRateFrameSampler must run on the main thread");
        }
    }
}

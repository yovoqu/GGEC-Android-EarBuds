package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Actual UI callback cadence measured from consecutive Choreographer pulses.
 * This complements display-mode diagnostics: a requested mode is only a preference,
 * while this sample describes the cadence the application really received.
 */
public final class RefreshRateFrameSample {
    public enum Cadence {
        EMPTY,
        STABLE,
        MIXED_MULTIPLE,
        UNSTABLE
    }

    private final int sampleCount;
    private final long medianIntervalNanos;
    private final long p95IntervalNanos;
    private final long minIntervalNanos;
    private final long maxIntervalNanos;
    private final float primaryCadenceRatio;
    private final float multipleCadenceRatio;
    @NonNull
    private final Cadence cadence;

    public RefreshRateFrameSample(int sampleCount,
                                  long medianIntervalNanos,
                                  long p95IntervalNanos,
                                  long minIntervalNanos,
                                  long maxIntervalNanos) {
        this(sampleCount, medianIntervalNanos, p95IntervalNanos,
                minIntervalNanos, maxIntervalNanos,
                sampleCount == 0 ? 0f : 1f,
                sampleCount == 0 ? 0f : 1f,
                sampleCount == 0 ? Cadence.EMPTY : Cadence.STABLE);
    }

    public RefreshRateFrameSample(int sampleCount,
                                  long medianIntervalNanos,
                                  long p95IntervalNanos,
                                  long minIntervalNanos,
                                  long maxIntervalNanos,
                                  float primaryCadenceRatio,
                                  float multipleCadenceRatio,
                                  @NonNull Cadence cadence) {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        boolean empty = sampleCount == 0 && medianIntervalNanos == 0L
                && p95IntervalNanos == 0L && minIntervalNanos == 0L
                && maxIntervalNanos == 0L;
        if (!empty && (sampleCount == 0 || minIntervalNanos <= 0L
                || medianIntervalNanos < minIntervalNanos
                || p95IntervalNanos < medianIntervalNanos
                || maxIntervalNanos < p95IntervalNanos)) {
            throw new IllegalArgumentException("frame intervals must be positive and ordered");
        }
        this.sampleCount = sampleCount;
        this.medianIntervalNanos = medianIntervalNanos;
        this.p95IntervalNanos = p95IntervalNanos;
        this.minIntervalNanos = minIntervalNanos;
        this.maxIntervalNanos = maxIntervalNanos;
        if (!Float.isFinite(primaryCadenceRatio) || primaryCadenceRatio < 0f
                || primaryCadenceRatio > 1f
                || !Float.isFinite(multipleCadenceRatio)
                || multipleCadenceRatio < primaryCadenceRatio
                || multipleCadenceRatio > 1f) {
            throw new IllegalArgumentException("cadence ratios must be ordered in [0, 1]");
        }
        this.primaryCadenceRatio = primaryCadenceRatio;
        this.multipleCadenceRatio = multipleCadenceRatio;
        this.cadence = java.util.Objects.requireNonNull(cadence, "cadence");
    }

    public int sampleCount() {
        return sampleCount;
    }

    public long medianIntervalNanos() {
        return medianIntervalNanos;
    }

    public long p95IntervalNanos() {
        return p95IntervalNanos;
    }

    public long minIntervalNanos() {
        return minIntervalNanos;
    }

    public long maxIntervalNanos() {
        return maxIntervalNanos;
    }

    public float estimatedRefreshRateHz() {
        return medianIntervalNanos > 0L
                ? 1_000_000_000f / medianIntervalNanos
                : 0f;
    }

    /** Ratio of intervals close to the dominant one-frame period. */
    public float primaryCadenceRatio() {
        return primaryCadenceRatio;
    }

    /** Ratio close to one, two, or three dominant periods (missed callbacks remain classifiable). */
    public float multipleCadenceRatio() {
        return multipleCadenceRatio;
    }

    @NonNull
    public Cadence cadence() {
        return cadence;
    }

    public boolean isStable() {
        return cadence == Cadence.STABLE;
    }

    /** Whether measured cadence matches a requested rate within a device-tolerant error band. */
    public boolean matches(float requestedHz) {
        if (!Float.isFinite(requestedHz) || requestedHz <= 0f || sampleCount == 0) {
            return false;
        }
        float relativeError = Math.abs(estimatedRefreshRateHz() - requestedHz) / requestedHz;
        return relativeError <= 0.08f && primaryCadenceRatio >= 0.80f;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "FrameSample{count=%d, estimated=%.2fHz, median=%.3fms, p95=%.3fms, "
                        + "primary=%.1f%%, multiples=%.1f%%, cadence=%s}",
                sampleCount,
                estimatedRefreshRateHz(),
                medianIntervalNanos / 1_000_000d,
                p95IntervalNanos / 1_000_000d,
                primaryCadenceRatio * 100f,
                multipleCadenceRatio * 100f,
                cadence);
    }
}

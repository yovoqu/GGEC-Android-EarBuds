package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;

/**
 * Immutable caller intent for refresh-rate behavior. Contains no Android framework access.
 */
public final class RefreshRatePolicy {

    private final RefreshRatePolicyType type;
    private final float targetHz;

    private RefreshRatePolicy(@NonNull RefreshRatePolicyType type, float targetHz) {
        this.type = type;
        this.targetHz = targetHz;
    }

    @NonNull
    public static RefreshRatePolicy highest() {
        return new RefreshRatePolicy(RefreshRatePolicyType.HIGHEST, 0f);
    }

    @NonNull
    public static RefreshRatePolicy target(float hz) {
        if (!Float.isFinite(hz) || hz <= 0f) {
            throw new IllegalArgumentException("target Hz must be finite and positive");
        }
        return new RefreshRatePolicy(RefreshRatePolicyType.TARGET, hz);
    }

    @NonNull
    public static RefreshRatePolicy atLeast(float hz) {
        if (!Float.isFinite(hz) || hz <= 0f) {
            throw new IllegalArgumentException("atLeast Hz must be finite and positive");
        }
        return new RefreshRatePolicy(RefreshRatePolicyType.AT_LEAST, hz);
    }

    @NonNull
    public static RefreshRatePolicy systemDefault() {
        return new RefreshRatePolicy(RefreshRatePolicyType.SYSTEM_DEFAULT, 0f);
    }

    @NonNull
    public RefreshRatePolicyType type() {
        return type;
    }

    /**
     * Meaningful only for {@link RefreshRatePolicyType#TARGET} and
     * {@link RefreshRatePolicyType#AT_LEAST}.
     */
    public float targetHz() {
        return targetHz;
    }

    @Override
    public String toString() {
        switch (type) {
            case HIGHEST:
                return "RefreshRatePolicy{highest}";
            case TARGET:
                return "RefreshRatePolicy{target=" + targetHz + "Hz}";
            case AT_LEAST:
                return "RefreshRatePolicy{atLeast=" + targetHz + "Hz}";
            case SYSTEM_DEFAULT:
                return "RefreshRatePolicy{systemDefault}";
            default:
                return "RefreshRatePolicy{" + type + "}";
        }
    }
}

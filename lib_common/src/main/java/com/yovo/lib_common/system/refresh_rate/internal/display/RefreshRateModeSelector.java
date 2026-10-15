package com.yovo.lib_common.system.refresh_rate.internal.display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.refresh_rate.internal.math.RefreshRateMath;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicyType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure mode-selection logic, unit-testable without Activity/Window dependencies.
 */
public final class RefreshRateModeSelector {

    private RefreshRateModeSelector() {
    }

    @Nullable
    public static RefreshRateMode select(@NonNull RefreshRatePolicy policy,
                                         @NonNull RefreshRateModeSnapshot snapshot) {
        if (policy.type() == RefreshRatePolicyType.SYSTEM_DEFAULT) {
            return null;
        }

        List<RefreshRateMode> valid = filterValid(snapshot.supportedModes());
        if (valid.isEmpty()) {
            return null;
        }

        List<RefreshRateMode> resolutionMatched = filterResolutionMatched(
                valid, snapshot.currentWidth(), snapshot.currentHeight());
        // A UI refresh-rate request must never silently trade away the current resolution.
        // If the display cannot expose a same-resolution mode, the caller receives no
        // selection instead of a potentially destructive resolution switch.
        if (resolutionMatched.isEmpty()) {
            return null;
        }
        List<RefreshRateMode> pool = resolutionMatched;

        switch (policy.type()) {
            case HIGHEST:
                return selectHighest(pool);
            case TARGET:
                return selectTarget(pool, policy.targetHz());
            case AT_LEAST:
                return selectAtLeast(pool, policy.targetHz());
            default:
                return null;
        }
    }

    public static boolean hasHighRefreshMode(@NonNull RefreshRateModeSnapshot snapshot) {
        List<RefreshRateMode> matched = filterResolutionMatched(
                filterValid(snapshot.supportedModes()),
                snapshot.currentWidth(), snapshot.currentHeight());
        for (RefreshRateMode mode : matched) {
            if (mode.isHighRefresh()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static List<RefreshRateMode> filterValid(@NonNull List<RefreshRateMode> modes) {
        List<RefreshRateMode> result = new ArrayList<>();
        for (RefreshRateMode mode : modes) {
            if (mode.isValid()) {
                result.add(mode);
            }
        }
        return result;
    }

    @NonNull
    private static List<RefreshRateMode> filterResolutionMatched(@NonNull List<RefreshRateMode> modes,
                                                                 int currentWidth,
                                                                 int currentHeight) {
        List<RefreshRateMode> result = new ArrayList<>();
        for (RefreshRateMode mode : modes) {
            if (mode.matchesResolution(currentWidth, currentHeight)) {
                result.add(mode);
            }
        }
        return result;
    }

    @Nullable
    private static RefreshRateMode selectHighest(@NonNull List<RefreshRateMode> pool) {
        RefreshRateMode best = null;
        for (RefreshRateMode mode : pool) {
            if (best == null || compareByRefreshThenArea(mode, best) > 0) {
                best = mode;
            }
        }
        return best;
    }

    @Nullable
    private static RefreshRateMode selectTarget(@NonNull List<RefreshRateMode> pool, float targetHz) {
        RefreshRateMode best = null;
        float bestDistance = Float.MAX_VALUE;
        for (RefreshRateMode mode : pool) {
            float distance = RefreshRateMath.distance(targetHz, mode.refreshRateHz());
            if (best == null
                    || distance < bestDistance
                    || (RefreshRateMath.nearlyEqual(distance, bestDistance)
                    && mode.refreshRateHz() > best.refreshRateHz())) {
                best = mode;
                bestDistance = distance;
            } else if (RefreshRateMath.nearlyEqual(distance, bestDistance)
                    && mode.refreshRateHz() == best.refreshRateHz()
                    && compareByArea(mode, best) > 0) {
                best = mode;
            }
        }
        return best;
    }

    @Nullable
    private static RefreshRateMode selectAtLeast(@NonNull List<RefreshRateMode> pool, float targetHz) {
        List<RefreshRateMode> eligible = new ArrayList<>();
        for (RefreshRateMode mode : pool) {
            if (mode.refreshRateHz() + RefreshRateMath.REFRESH_RATE_EPSILON >= targetHz) {
                eligible.add(mode);
            }
        }
        if (eligible.isEmpty()) {
            return selectHighest(pool);
        }
        eligible.sort(Comparator.comparingDouble(RefreshRateMode::refreshRateHz));
        return eligible.get(0);
    }

    private static int compareByRefreshThenArea(@NonNull RefreshRateMode left,
                                                @NonNull RefreshRateMode right) {
        if (!RefreshRateMath.nearlyEqual(left.refreshRateHz(), right.refreshRateHz())) {
            return Float.compare(left.refreshRateHz(), right.refreshRateHz());
        }
        return compareByArea(left, right);
    }

    private static int compareByArea(@NonNull RefreshRateMode left,
                                     @NonNull RefreshRateMode right) {
        return Long.compare(left.pixelArea(), right.pixelArea());
    }

    @NonNull
    public static List<Float> supportedRefreshRates(@NonNull RefreshRateModeSnapshot snapshot) {
        List<RefreshRateMode> valid = filterValid(snapshot.supportedModes());
        List<RefreshRateMode> resolutionMatched = filterResolutionMatched(
                valid, snapshot.currentWidth(), snapshot.currentHeight());
        List<RefreshRateMode> pool = resolutionMatched.isEmpty()
                ? Collections.emptyList()
                : resolutionMatched;
        List<Float> rates = new ArrayList<>();
        for (RefreshRateMode mode : pool) {
            boolean duplicate = false;
            for (float rate : rates) {
                if (RefreshRateMath.nearlyEqual(rate, mode.refreshRateHz())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                rates.add(mode.refreshRateHz());
            }
        }
        Collections.sort(rates);
        return rates;
    }
}

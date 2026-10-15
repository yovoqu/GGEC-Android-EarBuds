package com.yovo.lib_common.system.refresh_rate.internal.diagnostics;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateDisplayResolver;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSelector;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSnapshot;
import com.yovo.lib_common.system.refresh_rate.internal.sdk.RefreshRateSdk;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateDiagnostics;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateSystemModeSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects lightweight system state for QA without mutating display or window settings.
 */
public final class RefreshRateDiagnosticsCollector {

    private RefreshRateDiagnosticsCollector() {
    }

    @NonNull
    public static RefreshRateDiagnostics collect(@NonNull Activity activity) {
        return collect(activity, null, null);
    }

    @NonNull
    public static RefreshRateDiagnostics collect(@NonNull Activity activity,
                                               @Nullable RefreshRateMode selectedMode,
                                               @Nullable String note) {
        RefreshRateModeSnapshot snapshot = RefreshRateDisplayResolver.snapshot(activity);
        int displayId = snapshot != null ? snapshot.displayId() : -1;
        List<Float> supportedRates = snapshot != null
                ? RefreshRateModeSelector.supportedRefreshRates(snapshot)
                : new ArrayList<>();

        RefreshRateSystemModeSnapshot currentModeSnapshot = null;
        Display display = RefreshRateDisplayResolver.resolveDisplay(activity);
        if (display != null && RefreshRateSdk.supportsDisplayModes()) {
            Display.Mode current = display.getMode();
            if (current != null) {
                currentModeSnapshot = toSystemSnapshot(current);
            }
        }

        RefreshRateSystemModeSnapshot selectedSnapshot = selectedMode != null
                ? toSystemSnapshot(selectedMode)
                : null;

        PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        boolean powerSave = powerManager != null && powerManager.isPowerSaveMode();

        int thermalStatus = 0;
        boolean thermalAvailable = false;
        if (powerManager != null && RefreshRateSdk.supportsThermalStatus()) {
            thermalAvailable = true;
            thermalStatus = powerManager.getCurrentThermalStatus();
        }

        boolean multiWindow = activity.isInMultiWindowMode();

        return new RefreshRateDiagnostics(
                Build.VERSION.SDK_INT,
                safeString(Build.BRAND),
                safeString(Build.MANUFACTURER),
                safeString(Build.MODEL),
                displayId,
                supportedRates,
                selectedSnapshot,
                currentModeSnapshot,
                powerSave,
                thermalStatus,
                thermalAvailable,
                multiWindow,
                note);
    }

    @NonNull
    private static RefreshRateSystemModeSnapshot toSystemSnapshot(@NonNull RefreshRateMode mode) {
        return new RefreshRateSystemModeSnapshot(
                mode.modeId(), mode.width(), mode.height(), mode.refreshRateHz());
    }

    @NonNull
    private static RefreshRateSystemModeSnapshot toSystemSnapshot(@NonNull Display.Mode mode) {
        return new RefreshRateSystemModeSnapshot(
                mode.getModeId(),
                mode.getPhysicalWidth(),
                mode.getPhysicalHeight(),
                mode.getRefreshRate());
    }

    @NonNull
    private static String safeString(@Nullable String value) {
        return value != null ? value : "";
    }
}

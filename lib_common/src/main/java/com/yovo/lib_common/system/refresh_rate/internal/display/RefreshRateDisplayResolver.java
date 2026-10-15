package com.yovo.lib_common.system.refresh_rate.internal.display;

import android.app.Activity;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.yovo.lib_common.system.refresh_rate.internal.sdk.RefreshRateSdk;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@link Display} capabilities without mutating window state.
 */
public final class RefreshRateDisplayResolver {

    private RefreshRateDisplayResolver() {
    }

    @Nullable
    public static Display resolveDisplay(@NonNull Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Display display = Api30.getDisplay(activity);
            if (display != null) {
                return display;
            }
        }
        return activity.getWindowManager().getDefaultDisplay();
    }

    private static final class Api30 {
        private Api30() {
        }

        @RequiresApi(android.os.Build.VERSION_CODES.R)
        @Nullable
        static Display getDisplay(@NonNull Activity activity) {
            return activity.getDisplay();
        }
    }

    @Nullable
    public static RefreshRateModeSnapshot snapshot(@NonNull Activity activity) {
        Display display = resolveDisplay(activity);
        if (display == null) {
            return null;
        }
        return snapshot(display);
    }

    @Nullable
    public static RefreshRateModeSnapshot snapshot(@NonNull Display display) {
        if (!RefreshRateSdk.supportsDisplayModes()) {
            return null;
        }

        int displayId = display.getDisplayId();
        Display.Mode current = display.getMode();
        int currentWidth = current != null ? current.getPhysicalWidth() : 0;
        int currentHeight = current != null ? current.getPhysicalHeight() : 0;
        float currentRefresh = current != null ? current.getRefreshRate() : display.getRefreshRate();

        Display.Mode[] modes = display.getSupportedModes();
        List<RefreshRateMode> supported = new ArrayList<>();
        if (modes != null) {
            for (Display.Mode mode : modes) {
                if (mode == null) {
                    continue;
                }
                RefreshRateMode candidate = fromDisplayMode(mode);
                if (candidate.isValid()) {
                    supported.add(candidate);
                }
            }
        }

        return new RefreshRateModeSnapshot(
                displayId, currentWidth, currentHeight, currentRefresh, supported);
    }

    @NonNull
    public static RefreshRateMode fromDisplayMode(@NonNull Display.Mode mode) {
        return new RefreshRateMode(
                mode.getModeId(),
                mode.getPhysicalWidth(),
                mode.getPhysicalHeight(),
                mode.getRefreshRate());
    }
}

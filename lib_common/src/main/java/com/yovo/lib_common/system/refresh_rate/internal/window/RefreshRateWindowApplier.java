package com.yovo.lib_common.system.refresh_rate.internal.window;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.yovo.lib_common.system.refresh_rate.internal.diagnostics.RefreshRateDiagnosticsCollector;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateDisplayResolver;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSelector;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSnapshot;
import com.yovo.lib_common.system.refresh_rate.internal.math.RefreshRateMath;
import com.yovo.lib_common.system.refresh_rate.internal.sdk.RefreshRateSdk;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateRequest;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateResult;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateStatus;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicyType;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateDiagnostics;

/**
 * Applies or clears refresh-rate preferences on an Activity window.
 *
 * <p>Surface 帧率按 API 分层选用最优策略（非统一降级）：
 * <ul>
 *   <li>API 36+：{@code FRAME_RATE_COMPATIBILITY_AT_LEAST} + seamless change</li>
 *   <li>API 31–35：DEFAULT + seamless change</li>
 *   <li>API 30：DEFAULT + 双参数 setFrameRate</li>
 * </ul>
 */
public final class RefreshRateWindowApplier {

    private RefreshRateWindowApplier() {
    }

    @NonNull
    public static RefreshRateResult apply(@NonNull Activity activity,
                                          @NonNull RefreshRateRequest request) {
        RefreshRatePolicy policy = request.policy();
        if (policy.type() == RefreshRatePolicyType.SYSTEM_DEFAULT) {
            return clear(activity);
        }

        if (!RefreshRateSdk.supportsDisplayModes()) {
            RefreshRateDiagnostics diagnostics =
                    RefreshRateDiagnosticsCollector.collect(activity, null, "unsupported_api");
            return new RefreshRateResult(
                    policy, 0, 0f, 0f, RefreshRateStatus.UNSUPPORTED_API, diagnostics);
        }

        try {
            RefreshRateModeSnapshot snapshot = RefreshRateDisplayResolver.snapshot(activity);
            if (snapshot == null) {
                return RefreshRateResult.noDisplay(policy);
            }
            if (!snapshot.hasSupportedModes()) {
                RefreshRateDiagnostics diagnostics =
                        RefreshRateDiagnosticsCollector.collect(activity, null, "no_supported_modes");
                return new RefreshRateResult(
                        policy, 0, 0f, snapshot.currentRefreshRateHz(),
                        RefreshRateStatus.NO_SUPPORTED_MODES, diagnostics);
            }

            RefreshRateMode selected = RefreshRateModeSelector.select(policy, snapshot);
            if (selected == null) {
                RefreshRateDiagnostics diagnostics =
                        RefreshRateDiagnosticsCollector.collect(activity, null, "no_selected_mode");
                return RefreshRateResult.error(policy, diagnostics);
            }

            // Refresh-rate-only requests must not set preferredDisplayModeId. Android ignores
            // preferredRefreshRate when a mode id is present, and a mode id may also imply a
            // resolution preference. Keep the mode solely as diagnostic selection evidence.
            writeWindowPreferences(activity, 0, selected.refreshRateHz());

            float observed = readObservedRefreshRate(activity, snapshot);
            RefreshRateStatus status = resolveStatus(policy, selected, observed, snapshot);
            String note = buildNote(status, snapshot);
            RefreshRateDiagnostics diagnostics =
                    RefreshRateDiagnosticsCollector.collect(activity, selected, note);

            return new RefreshRateResult(
                    policy,
                    selected.modeId(),
                    selected.refreshRateHz(),
                    observed,
                    status,
                    diagnostics);
        } catch (Exception e) {
            RefreshRateDiagnostics diagnostics =
                    RefreshRateDiagnosticsCollector.collect(activity, null, e.getMessage());
            return RefreshRateResult.error(policy, diagnostics);
        }
    }

    @NonNull
    public static RefreshRateResult clear(@NonNull Activity activity) {
        RefreshRatePolicy policy = RefreshRatePolicy.systemDefault();
        try {
            writeWindowPreferences(activity, 0, 0f);
            float observed = 0f;
            RefreshRateModeSnapshot snapshot = RefreshRateDisplayResolver.snapshot(activity);
            if (snapshot != null) {
                observed = snapshot.currentRefreshRateHz();
            }
            RefreshRateDiagnostics diagnostics =
                    RefreshRateDiagnosticsCollector.collect(activity, null, "cleared");
            return new RefreshRateResult(
                    policy, 0, 0f, observed, RefreshRateStatus.CLEARED, diagnostics);
        } catch (Exception e) {
            RefreshRateDiagnostics diagnostics =
                    RefreshRateDiagnosticsCollector.collect(activity, null, e.getMessage());
            return RefreshRateResult.error(policy, diagnostics);
        }
    }

    /**
     * Optional API 30+ hook for owned rendering surfaces.
     */
    public static void applySurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        if (!RefreshRateSdk.supportsSurfaceFrameRate() || frameRateHz <= 0f) {
            return;
        }
        if (!Float.isFinite(frameRateHz)) {
            return;
        }
        applyWithBestStrategy(surface, frameRateHz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    /**
     * UI/动画 Surface：API 36+ 优先 AT_LEAST；更低版本使用该版本最优 setFrameRate 策略。
     */
    public static void applyUiSurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        if (!RefreshRateSdk.supportsSurfaceFrameRate()
                || !Float.isFinite(frameRateHz)
                || frameRateHz <= 0f) {
            return;
        }
        int compatibility = Build.VERSION.SDK_INT >= 36
                ? Api36.frameRateCompatibilityAtLeast()
                : Surface.FRAME_RATE_COMPATIBILITY_DEFAULT;
        applyWithBestStrategy(surface, frameRateHz, compatibility);
    }

    public static void clearSurfaceFrameRate(@NonNull Surface surface) {
        if (!RefreshRateSdk.supportsSurfaceFrameRate()) {
            return;
        }
        applyWithBestStrategy(surface, 0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    /**
     * 按当前系统 API 选择最优 setFrameRate 调用形态。
     */
    @RequiresApi(android.os.Build.VERSION_CODES.R)
    private static void applyWithBestStrategy(@NonNull Surface surface,
                                              float frameRateHz,
                                              int compatibility) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31.apply(surface, frameRateHz, compatibility);
        } else {
            Api30.apply(surface, frameRateHz, compatibility);
        }
    }

    @NonNull
    public static RefreshRateWindowSnapshot readWindowSnapshot(@NonNull Activity activity) {
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        return new RefreshRateWindowSnapshot(
                params.preferredDisplayModeId,
                params.preferredRefreshRate);
    }

    /** Restores the exact window preference captured before a diagnostic test session. */
    public static void restoreWindowSnapshot(
            @NonNull Activity activity,
            @NonNull RefreshRateWindowSnapshot snapshot) {
        writeWindowPreferences(activity,
                snapshot.preferredDisplayModeId(),
                snapshot.preferredRefreshRateHz());
    }

    private static void writeWindowPreferences(@NonNull Activity activity,
                                               int modeId,
                                               float refreshRateHz) {
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.preferredDisplayModeId = modeId;
        params.preferredRefreshRate = refreshRateHz;
        window.setAttributes(params);
    }

    private static float readObservedRefreshRate(@NonNull Activity activity,
                                                 @NonNull RefreshRateModeSnapshot snapshot) {
        Display display = RefreshRateDisplayResolver.resolveDisplay(activity);
        if (display == null) {
            return snapshot.currentRefreshRateHz();
        }
        Display.Mode current = display.getMode();
        if (current != null) {
            return current.getRefreshRate();
        }
        return display.getRefreshRate();
    }

    @NonNull
    private static RefreshRateStatus resolveStatus(@NonNull RefreshRatePolicy policy,
                                                   @NonNull RefreshRateMode selected,
                                                   float observed,
                                                   @NonNull RefreshRateModeSnapshot snapshot) {
        if (policy.type() == RefreshRatePolicyType.HIGHEST
                && !selected.isHighRefresh()
                && !RefreshRateModeSelector.hasHighRefreshMode(snapshot)) {
            return RefreshRateStatus.NO_HIGH_REFRESH_MODE;
        }

        if (RefreshRateMath.nearlyEqual(selected.refreshRateHz(), observed)) {
            return RefreshRateStatus.APPLIED;
        }
        return RefreshRateStatus.REQUESTED;
    }

    @Nullable
    private static String buildNote(@NonNull RefreshRateStatus status,
                                    @NonNull RefreshRateModeSnapshot snapshot) {
        if (status == RefreshRateStatus.REQUESTED) {
            return "preference_written_switch_pending_or_system_constrained";
        }
        if (status == RefreshRateStatus.NO_HIGH_REFRESH_MODE) {
            return "only_standard_refresh_available";
        }
        if (!RefreshRateModeSelector.hasHighRefreshMode(snapshot)) {
            return "display_reports_no_high_refresh_modes";
        }
        return null;
    }

    /** API 30：双参数 setFrameRate。 */
    @RequiresApi(android.os.Build.VERSION_CODES.R)
    private static final class Api30 {
        private Api30() {
        }

        static void apply(@NonNull Surface surface, float frameRateHz, int compatibility) {
            surface.setFrameRate(frameRateHz, compatibility);
        }
    }

    /** API 31+：带 CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS 的最优三参数重载。 */
    @RequiresApi(android.os.Build.VERSION_CODES.S)
    private static final class Api31 {
        private Api31() {
        }

        static void apply(@NonNull Surface surface, float frameRateHz, int compatibility) {
            surface.setFrameRate(frameRateHz, compatibility,
                    Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        }
    }

    /** API 36+：直接使用平台 {@link Surface#FRAME_RATE_COMPATIBILITY_AT_LEAST}。 */
    private static final class Api36 {
        private Api36() {
        }

        static int frameRateCompatibilityAtLeast() {
            return Surface.FRAME_RATE_COMPATIBILITY_AT_LEAST;
        }
    }
}

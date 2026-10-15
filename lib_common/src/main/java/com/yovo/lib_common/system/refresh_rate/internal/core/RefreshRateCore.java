package com.yovo.lib_common.system.refresh_rate.internal.core;

import android.app.Activity;
import android.app.Application;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.YoRefreshRate;
import com.yovo.lib_common.system.refresh_rate.internal.core.RefreshRateLifecycleCallbacks;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateDisplayResolver;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSelector;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSnapshot;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateRequest;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateResult;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateTestSession;
import com.yovo.lib_common.system.refresh_rate.internal.window.RefreshRateWindowApplier;
import com.yovo.lib_common.system.refresh_rate.internal.session.RefreshRateTestSessionImpl;
import com.yovo.lib_common.log.YoLog;

/**
 * Public facade for requesting high refresh-rate behavior from app-layer pages.
 *
 * <p>Typical integration:</p>
 * <pre>{@code
 * RefreshRateCore.installHighRefreshForAllActivities(application);
 * }</pre>
 */
public final class RefreshRateCore {

    private static final String TAG = "RefreshRateCore";

    private static Application installedApplication;
    private static RefreshRateLifecycleCallbacks lifecycleCallbacks;

    private RefreshRateCore() {
    }

    /**
     * Request the highest supported refresh rate for one Activity window.
     */
    @NonNull
    public static RefreshRateResult applyHighRefresh(@NonNull Activity activity) {
        return apply(activity, RefreshRatePolicy.highest());
    }

    /**
     * Register once in {@code Application.onCreate()} to cover all Activities.
     */
    public static void installHighRefreshForAllActivities(@NonNull Application application) {
        install(application, RefreshRatePolicy.highest());
    }

    /**
     * Register lifecycle callbacks with a custom policy.
     */
    public static void install(@NonNull Application application,
                               @NonNull RefreshRatePolicy policy) {
        synchronized (RefreshRateCore.class) {
            if (installedApplication == application && lifecycleCallbacks != null) {
                lifecycleCallbacks.setPolicy(policy);
                return;
            }
            if (installedApplication != null && lifecycleCallbacks != null) {
                installedApplication.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            }
            lifecycleCallbacks = new RefreshRateLifecycleCallbacks(policy);
            installedApplication = application;
            application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
            YoLog.i(TAG, "刷新率生命周期已安装: " + policy);
        }
    }

    public static void uninstall(@NonNull Application application) {
        synchronized (RefreshRateCore.class) {
            if (installedApplication != application || lifecycleCallbacks == null) {
                return;
            }
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            installedApplication = null;
            lifecycleCallbacks = null;
            YoLog.i(TAG, "刷新率生命周期已卸载");
        }
    }

    @NonNull
    public static RefreshRateResult apply(@NonNull Activity activity,
                                          @NonNull RefreshRatePolicy policy) {
        RefreshRateResult result =
                RefreshRateWindowApplier.apply(activity, new RefreshRateRequest(policy));
        if (!result.isRequestAccepted()) {
            YoLog.w(TAG, "刷新率应用未被接受: " + result);
        } else {
            YoLog.d(TAG, "刷新率已应用: " + result.status());
        }
        return result;
    }

    @NonNull
    public static RefreshRateResult applyTarget(@NonNull Activity activity, float hertz) {
        return apply(activity, RefreshRatePolicy.target(hertz));
    }

    @NonNull
    public static java.util.List<Float> supportedRefreshRates(@NonNull Activity activity) {
        RefreshRateModeSnapshot snapshot = RefreshRateDisplayResolver.snapshot(activity);
        return snapshot == null
                ? java.util.Collections.emptyList()
                : RefreshRateModeSelector.supportedRefreshRates(snapshot);
    }

    @NonNull
    public static YoRefreshRate.Controller bind(@NonNull Activity activity,
                                                @NonNull RefreshRatePolicy policy) {
        return new RefreshRateControllerImpl(activity, policy);
    }

    /** Creates an asynchronous request/settle/sample/restore diagnostic session. */
    @NonNull
    public static RefreshRateTestSession testSession(@NonNull Activity activity) {
        return new RefreshRateTestSessionImpl(activity);
    }

    /**
     * Optional API 30+ surface hook for custom rendering pipelines.
     */
    public static void applySurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        RefreshRateWindowApplier.applySurfaceFrameRate(surface, frameRateHz);
    }

    public static void applyUiSurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        RefreshRateWindowApplier.applyUiSurfaceFrameRate(surface, frameRateHz);
    }

    public static void clearSurfaceFrameRate(@NonNull Surface surface) {
        RefreshRateWindowApplier.clearSurfaceFrameRate(surface);
    }
}

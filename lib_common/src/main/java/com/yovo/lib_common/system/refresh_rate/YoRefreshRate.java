package com.yovo.lib_common.system.refresh_rate;

import com.yovo.lib_common.system.refresh_rate.internal.core.RefreshRateCore;

import android.app.Activity;
import android.app.Application;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateResult;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateTestSession;

import java.util.List;

/**
 * 刷新率域对外门面。
 */
public final class YoRefreshRate {

    private YoRefreshRate() {
    }

    @NonNull
    public static RefreshRateResult applyHighRefresh(@NonNull Activity activity) {
        return RefreshRateCore.applyHighRefresh(activity);
    }

    @NonNull
    public static RefreshRateResult applyTarget(@NonNull Activity activity, float hertz) {
        return RefreshRateCore.applyTarget(activity, hertz);
    }

    @NonNull
    public static RefreshRateResult apply(@NonNull Activity activity,
                                          @NonNull RefreshRatePolicy policy) {
        return RefreshRateCore.apply(activity, policy);
    }

    public static void installHighRefreshForAllActivities(@NonNull Application application) {
        RefreshRateCore.installHighRefreshForAllActivities(application);
    }

    public static void install(@NonNull Application application,
                               @NonNull RefreshRatePolicy policy) {
        RefreshRateCore.install(application, policy);
    }

    public static void uninstall(@NonNull Application application) {
        RefreshRateCore.uninstall(application);
    }

    @NonNull
    public static Controller bind(@NonNull Activity activity,
                                  @NonNull RefreshRatePolicy policy) {
        return RefreshRateCore.bind(activity, policy);
    }

    @NonNull
    public static List<Float> supportedRefreshRates(@NonNull Activity activity) {
        return RefreshRateCore.supportedRefreshRates(activity);
    }

    @NonNull
    public static RefreshRateTestSession testSession(@NonNull Activity activity) {
        return RefreshRateCore.testSession(activity);
    }

    public static void applySurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        RefreshRateCore.applySurfaceFrameRate(surface, frameRateHz);
    }

    public static void applyUiSurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        RefreshRateCore.applyUiSurfaceFrameRate(surface, frameRateHz);
    }

    public static void clearSurfaceFrameRate(@NonNull Surface surface) {
        RefreshRateCore.clearSurfaceFrameRate(surface);
    }

    public interface Controller {
        @NonNull
        RefreshRatePolicy policy();

        void setPolicy(@NonNull RefreshRatePolicy policy);

        @NonNull
        RefreshRateResult apply();

        @NonNull
        RefreshRateResult apply(@NonNull RefreshRatePolicy policy);

        @NonNull
        RefreshRateResult clear();

        void onWindowFocusChanged(boolean hasFocus);
    }
}

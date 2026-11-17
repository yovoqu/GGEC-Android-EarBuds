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
 *
 * <p>门面持有唯一的 {@link RefreshRateCore} 实例，装配状态属于该实例，
 * 不再散落为静态可写字段。</p>
 */
public final class YoRefreshRate {

    private static final RefreshRateCore CORE = new RefreshRateCore();

    private YoRefreshRate() {
    }

    @NonNull
    public static RefreshRateResult applyHighRefresh(@NonNull Activity activity) {
        return CORE.applyHighRefresh(activity);
    }

    @NonNull
    public static RefreshRateResult applyTarget(@NonNull Activity activity, float hertz) {
        return CORE.applyTarget(activity, hertz);
    }

    @NonNull
    public static RefreshRateResult apply(@NonNull Activity activity,
                                          @NonNull RefreshRatePolicy policy) {
        return CORE.apply(activity, policy);
    }

    public static void installHighRefreshForAllActivities(@NonNull Application application) {
        CORE.installHighRefreshForAllActivities(application);
    }

    public static void install(@NonNull Application application,
                               @NonNull RefreshRatePolicy policy) {
        CORE.install(application, policy);
    }

    public static void uninstall(@NonNull Application application) {
        CORE.uninstall(application);
    }

    @NonNull
    public static Controller bind(@NonNull Activity activity,
                                  @NonNull RefreshRatePolicy policy) {
        return CORE.bind(activity, policy);
    }

    @NonNull
    public static List<Float> supportedRefreshRates(@NonNull Activity activity) {
        return CORE.supportedRefreshRates(activity);
    }

    @NonNull
    public static RefreshRateTestSession testSession(@NonNull Activity activity) {
        return CORE.testSession(activity);
    }

    public static void applySurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        CORE.applySurfaceFrameRate(surface, frameRateHz);
    }

    public static void applyUiSurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        CORE.applyUiSurfaceFrameRate(surface, frameRateHz);
    }

    public static void clearSurfaceFrameRate(@NonNull Surface surface) {
        CORE.clearSurfaceFrameRate(surface);
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

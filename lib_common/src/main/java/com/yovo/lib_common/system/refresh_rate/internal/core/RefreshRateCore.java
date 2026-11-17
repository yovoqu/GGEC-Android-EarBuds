package com.yovo.lib_common.system.refresh_rate.internal.core;

import android.app.Activity;
import android.app.Application;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import java.util.Collections;
import java.util.List;

/**
 * 刷新率内部装配实现：一次 {@code install} 拥有的 Activity 生命周期回调是本实例的状态，
 * 由门面 {@link YoRefreshRate} 构造持有唯一实例；目标 Application 与策略一律由
 * 调用参数显式传入，不再借静态可写字段传递装配状态。需要隔离安装状态的调用方可
 * 自行 {@code new RefreshRateCore()}。
 *
 * <p>典型集成：</p>
 * <pre>{@code
 * YoRefreshRate.installHighRefreshForAllActivities(application);
 * }</pre>
 */
public final class RefreshRateCore {

    private static final String TAG = "RefreshRateCore";

    private final Object lock = new Object();

    /** 已注册回调的 Application；未安装时为 null。 */
    @Nullable
    private Application installedApplication;

    @Nullable
    private RefreshRateLifecycleCallbacks lifecycleCallbacks;

    public RefreshRateCore() {
    }

    /**
     * Request the highest supported refresh rate for one Activity window.
     */
    @NonNull
    public RefreshRateResult applyHighRefresh(@NonNull Activity activity) {
        return apply(activity, RefreshRatePolicy.highest());
    }

    /**
     * Register once in {@code Application.onCreate()} to cover all Activities.
     */
    public void installHighRefreshForAllActivities(@NonNull Application application) {
        install(application, RefreshRatePolicy.highest());
    }

    /**
     * Register lifecycle callbacks with a custom policy.
     */
    public void install(@NonNull Application application,
                        @NonNull RefreshRatePolicy policy) {
        synchronized (lock) {
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

    public void uninstall(@NonNull Application application) {
        synchronized (lock) {
            if (installedApplication != application || lifecycleCallbacks == null) {
                return;
            }
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            installedApplication = null;
            lifecycleCallbacks = null;
            YoLog.i(TAG, "刷新率生命周期已卸载");
        }
    }

    /** 当前是否已在本实例上安装回调。 */
    public boolean isInstalled() {
        synchronized (lock) {
            return lifecycleCallbacks != null;
        }
    }

    @NonNull
    public RefreshRateResult apply(@NonNull Activity activity,
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
    public RefreshRateResult applyTarget(@NonNull Activity activity, float hertz) {
        return apply(activity, RefreshRatePolicy.target(hertz));
    }

    @NonNull
    public List<Float> supportedRefreshRates(@NonNull Activity activity) {
        RefreshRateModeSnapshot snapshot = RefreshRateDisplayResolver.snapshot(activity);
        return snapshot == null
                ? Collections.emptyList()
                : RefreshRateModeSelector.supportedRefreshRates(snapshot);
    }

    @NonNull
    public YoRefreshRate.Controller bind(@NonNull Activity activity,
                                          @NonNull RefreshRatePolicy policy) {
        return new RefreshRateControllerImpl(activity, policy);
    }

    /** Creates an asynchronous request/settle/sample/restore diagnostic session. */
    @NonNull
    public RefreshRateTestSession testSession(@NonNull Activity activity) {
        return new RefreshRateTestSessionImpl(activity);
    }

    /**
     * Optional API 30+ surface hook for custom rendering pipelines.
     */
    public void applySurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        RefreshRateWindowApplier.applySurfaceFrameRate(surface, frameRateHz);
    }

    public void applyUiSurfaceFrameRate(@NonNull Surface surface, float frameRateHz) {
        RefreshRateWindowApplier.applyUiSurfaceFrameRate(surface, frameRateHz);
    }

    public void clearSurfaceFrameRate(@NonNull Surface surface) {
        RefreshRateWindowApplier.clearSurfaceFrameRate(surface);
    }
}

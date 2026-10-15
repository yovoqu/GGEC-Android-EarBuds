package com.yovo.lib_common.system.permission.internal.core;

import com.yovo.lib_common.system.permission.internal.PermissionChecker;
import com.yovo.lib_common.system.permission.internal.PermissionRationale;
import com.yovo.lib_common.system.permission.internal.PermissionRequester;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionResult;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.yovo.lib_common.system.permission.internal.android.AndroidPermissionChecker;
import com.yovo.lib_common.system.permission.internal.android.AndroidPermissionRationale;
import com.yovo.lib_common.system.permission.internal.android.AndroidPermissionRequester;
import com.yovo.lib_common.system.permission.internal.android.SpecialAccessLifecycleObserver;
import com.yovo.lib_common.log.YoLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一权限管理服务（lib_common 底层能力）。
 *
 * <p>上层不再各自检查/请求权限，统一调用本服务：</p>
 * <ul>
 *     <li>检查：{@link #check(Permission)} / {@link #isGranted(Permission)}；</li>
 *     <li>请求：运行时权限弹窗或特殊访问设置页；</li>
 *     <li>结果：{@link #handleRequestPermissionsResult(int, String[], int[])}。</li>
 * </ul>
 */
public final class PermissionCore {

    private static final String TAG = "PermissionCore";

    /** 默认运行时权限组请求码（未指定请求码时使用）。 */
    public static final int REQUEST_CODE_GROUP = 0xB0F0;

    private static volatile PermissionCore instance;

    /**
     * 创建独立实例（测试/多租户注入场景）。
     *
     * <p>普通业务统一使用 {@link #with(Context)} 的单例门面；
     * 需要隔离 pending 状态或注入 checker/requester/rationale 时使用本方法。</p>
     */
    @NonNull
    public static PermissionCore create(@NonNull Context context,
                                           @NonNull PermissionChecker checker,
                                           @NonNull PermissionRequester requester,
                                           @NonNull PermissionRationale rationale) {
        return new PermissionCore(context, checker, requester, rationale);
    }

    private final Context appContext;
    private final PermissionChecker checker;
    private final PermissionRequester requester;
    private final PermissionRationale rationale;
    /**
     * 运行时权限 pending 表：以 Activity 实例 + requestCode 为键。
     * 同一 Activity 不同请求码互不干扰，不同 Activity 也不会串扰；
     * Activity 销毁时通过生命周期观察者清理，避免结果永不回调导致的状态泄漏。
     */
    private final Map<Activity, Map<Integer, List<Permission>>> pendingByActivity =
            new HashMap<>();
    private final Set<Activity> observedActivities = new HashSet<>();

    private PermissionCore(@NonNull Context context,
                              @NonNull PermissionChecker checker,
                              @NonNull PermissionRequester requester,
                              @NonNull PermissionRationale rationale) {
        this.appContext = context.getApplicationContext();
        this.checker = checker;
        this.requester = requester;
        this.rationale = rationale;
    }

    @NonNull
    public static PermissionCore with(@NonNull Context context) {
        PermissionCore local = instance;
        if (local == null) {
            synchronized (PermissionCore.class) {
                local = instance;
                if (local == null) {
                    local = new PermissionCore(
                            context.getApplicationContext(),
                            new AndroidPermissionChecker(),
                            new AndroidPermissionRequester(),
                            new AndroidPermissionRationale());
                    instance = local;
                }
            }
        }
        return local;
    }

    @NonNull
    public static PermissionCore get() {
        PermissionCore local = instance;
        if (local == null) {
            throw new IllegalStateException("PermissionCore is not initialized.");
        }
        return local;
    }

    @Nullable
    public static PermissionCore peek() {
        return instance;
    }

    @NonNull
    public PermissionStatus check(@NonNull Permission permission) {
        return checker.check(appContext, permission);
    }

    public boolean isGranted(@NonNull Permission permission) {
        PermissionStatus status = check(permission);
        return status == PermissionStatus.GRANTED
                || status == PermissionStatus.NOT_REQUIRED;
    }

    public boolean isGranted(@NonNull List<Permission> permissions) {
        for (Permission permission : permissions) {
            if (!isGranted(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 运行时权限被拒绝后，系统是否还愿意再次弹出授权框。
     *
     * @return true 表示应展示 rationale 说明后再请求；false 表示首次请求或已“不再询问”。
     */
    public boolean shouldShowRationale(@NonNull Activity activity,
                                       @NonNull Permission permission) {
        return rationale.shouldShow(activity, permission);
    }

    /**
     * 请求单个权限。
     *
     * @return true 表示已满足（无需请求）；false 表示已发起运行时弹窗或特殊访问设置页。
     */
    public boolean requestIfNeeded(@NonNull Activity activity,
                                   @NonNull Permission permission) {
        return requestIfNeeded(activity, Collections.singletonList(permission));
    }

    /** 请求单个权限并指定业务请求码。 */
    public boolean requestIfNeeded(@NonNull Activity activity,
                                   @NonNull Permission permission,
                                   int requestCode) {
        return requestIfNeeded(activity, Collections.singletonList(permission), requestCode);
    }

    /**
     * 请求一组权限（运行时权限合并为一个弹窗，特殊访问权限逐项拉起设置页）。
     *
     * @return true 表示全部已满足；false 表示已发起请求。
     */
    public boolean requestIfNeeded(@NonNull Activity activity,
                                   @NonNull List<Permission> permissions) {
        return requestIfNeeded(activity, permissions, REQUEST_CODE_GROUP);
    }

    /**
     * 请求一组权限并使用调用方指定的请求码（便于在 onRequestPermissionsResult 中区分业务）。
     */
    public boolean requestIfNeeded(@NonNull Activity activity,
                                   @NonNull List<Permission> permissions,
                                   int requestCode) {
        List<Permission> missingRuntime = new ArrayList<>();
        boolean launched = false;
        boolean unrequestableMissing = false;
        for (Permission permission : permissions) {
            if (!permission.isRequired(Build.VERSION.SDK_INT)
                    || isGranted(permission)) {
                continue;
            }
            if (permission.isInstallTime()) {
                // 安装时权限声明即授予；未授予说明 manifest 未声明，无法运行时请求。
                // 如实标记“未满足”，避免上层误以为已全部满足。
                unrequestableMissing = true;
                continue;
            }
            if (permission.isRuntime()) {
                missingRuntime.add(permission);
            } else {
                YoLog.d(TAG, "请求特殊访问权限: " + permission);
                launched = requester.requestSpecialAccess(activity, permission) || launched;
            }
        }
        if (!missingRuntime.isEmpty()) {
            storePending(activity, requestCode, missingRuntime);
            requester.requestRuntime(activity, missingRuntime, requestCode);
            launched = true;
            YoLog.d(TAG, "请求运行时权限: " + missingRuntime
                    + "，请求码=" + requestCode);
        }
        return !launched && !unrequestableMissing;
    }

    /**
     * 逐个拉起特殊访问设置页，并在 Activity 回到前台时回调授权结果。
     *
     * <p>特殊访问权限（如“所有文件访问”“安装未知应用”）没有
     * onRequestPermissionsResult，唯一可靠的回程信号是 onResume。</p>
     */
    public void requestSpecialAccess(@NonNull Activity activity,
                                     @NonNull List<Permission> permissions,
                                     @NonNull SpecialAccessCallback callback) {
        List<Permission> missing = new ArrayList<>();
        boolean launched = false;
        for (Permission permission : permissions) {
            if (!permission.isRequired(Build.VERSION.SDK_INT)
                    || isGranted(permission)) {
                continue;
            }
            missing.add(permission);
            launched = requester.requestSpecialAccess(activity, permission) || launched;
        }
        if (missing.isEmpty()) {
            callback.onResult(true, Collections.emptyList());
            return;
        }
        YoLog.d(TAG, "请求特殊访问权限: " + missing);
        if (!launched) {
            callback.onResult(false, new ArrayList<>(missing));
            return;
        }
        SpecialAccessLifecycleObserver.observe(
                activity, missing, callback);
    }

    /** 打开应用详情设置页（用户“不再询问”后引导手动开启）。 */
    public void openAppSettings(@NonNull Activity activity) {
        YoLog.d(TAG, "打开应用设置: " + activity.getClass().getSimpleName());
        requester.openAppSettings(activity);
    }

    /**
     * 特殊访问权限设置页返回后的回调。
     */
    public interface SpecialAccessCallback {
        void onResult(boolean allGranted, @NonNull List<Permission> denied);
    }

    /**
     * 处理 Activity#onRequestPermissionsResult；非本服务发起的请求返回 ignored。
     */
    @NonNull
    public PermissionResult handleRequestPermissionsResult(
            int requestCode, @Nullable String[] permissions, @Nullable int[] grantResults) {
        return handleRequestPermissionsResult(null, requestCode, permissions, grantResults);
    }

    /**
     * 处理 Activity#onRequestPermissionsResult；非本服务发起的请求返回 ignored。
     *
     * <p>传入 Activity 时，额外识别“不再询问”的运行时权限，
     * 便于上层引导用户前往设置页手动开启。</p>
     */
    @NonNull
    public PermissionResult handleRequestPermissionsResult(
            @Nullable Activity activity,
            int requestCode, @Nullable String[] permissions, @Nullable int[] grantResults) {
        List<Permission> pending = takePending(activity, requestCode);
        if (pending == null || pending.isEmpty()) {
            return PermissionResult.ignored();
        }
        YoLog.d(TAG, "权限结果：请求码=" + requestCode
                + "，权限=" + pending);
        PermissionResult base = PermissionResult.of(pending, grantResults);
        Map<Permission, Boolean> permanentlyDenied = new EnumMap<>(Permission.class);
        if (activity != null) {
            for (Permission permission : pending) {
                if (permission.isRuntime()
                        && base.statusOf(permission) == PermissionStatus.DENIED
                        && !rationale.shouldShow(activity, permission)) {
                    permanentlyDenied.put(permission, true);
                }
            }
        }
        return PermissionResult.of(pending, grantResults, permanentlyDenied);
    }

    private void storePending(@NonNull Activity activity,
                              int requestCode,
                              @NonNull List<Permission> permissions) {
        synchronized (pendingByActivity) {
            Map<Integer, List<Permission>> byCode =
                    pendingByActivity.computeIfAbsent(activity, key -> new HashMap<>());
            byCode.put(requestCode, new ArrayList<>(permissions));
        }
        observePendingCleanup(activity);
    }

    /**
     * 取出 pending 请求。activity 为 null 时仅在全局唯一命中时处理，
     * 避免多 Activity 同请求码的歧义。
     */
    @Nullable
    private List<Permission> takePending(@Nullable Activity activity, int requestCode) {
        synchronized (pendingByActivity) {
            if (activity != null) {
                Map<Integer, List<Permission>> byCode = pendingByActivity.get(activity);
                if (byCode == null) {
                    return null;
                }
                List<Permission> pending = byCode.remove(requestCode);
                if (byCode.isEmpty()) {
                    pendingByActivity.remove(activity);
                }
                return pending;
            }
            List<Permission> match = null;
            Activity matchedActivity = null;
            for (Map.Entry<Activity, Map<Integer, List<Permission>>> entry
                    : pendingByActivity.entrySet()) {
                List<Permission> pending = entry.getValue().get(requestCode);
                if (pending != null) {
                    if (match != null) {
                        return null; // 多个 Activity 使用同一请求码，无法消歧
                    }
                    match = pending;
                    matchedActivity = entry.getKey();
                }
            }
            if (matchedActivity != null) {
                Map<Integer, List<Permission>> byCode = pendingByActivity.get(matchedActivity);
                byCode.remove(requestCode);
                if (byCode.isEmpty()) {
                    pendingByActivity.remove(matchedActivity);
                }
            }
            return match;
        }
    }

    private void observePendingCleanup(@NonNull Activity activity) {
        if (!(activity instanceof LifecycleOwner)) {
            throw new IllegalArgumentException(
                    "运行时权限请求需要 LifecycleOwner Activity");
        }
        synchronized (pendingByActivity) {
            if (!observedActivities.add(activity)) {
                return;
            }
        }
        ((LifecycleOwner) activity).getLifecycle().addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source,
                                       @NonNull Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    source.getLifecycle().removeObserver(this);
                    synchronized (pendingByActivity) {
                        pendingByActivity.remove(activity);
                        observedActivities.remove(activity);
                    }
                }
            }
        });
    }
}

package com.yovo.lib_common.system.permission.internal.core;

import com.yovo.lib_common.system.permission.internal.PermissionChecker;
import com.yovo.lib_common.system.permission.internal.PermissionRationale;
import com.yovo.lib_common.system.permission.internal.PermissionRequester;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionIgnoreReason;
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
 * <p>上层不再各自检查/请求权限，统一调用域门面 {@code YoPermission}：</p>
 * <ul>
 *     <li>检查：{@link #check(Context, Permission)} / {@link #isGranted(Context, Permission)}；</li>
 *     <li>请求：运行时权限弹窗或特殊访问设置页；</li>
 *     <li>结果：{@link #handleRequestPermissionsResult(Activity, int, String[], int[])}。</li>
 * </ul>
 *
 * <p>本类不再持有静态单例：平台实现由 {@link #create} 构造注入，
 * pending 表是实例状态，门面持有唯一实例。</p>
 */
public final class PermissionCore {

    private static final String TAG = "PermissionCore";

    /** 默认运行时权限组请求码（未指定请求码时使用）。 */
    public static final int REQUEST_CODE_GROUP = 0xB0F0;

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

    private PermissionCore(@NonNull PermissionChecker checker,
                           @NonNull PermissionRequester requester,
                           @NonNull PermissionRationale rationale) {
        this.checker = checker;
        this.requester = requester;
        this.rationale = rationale;
    }

    /**
     * 创建独立实例（平台实现构造注入；测试可注入桩实现）。
     *
     * <p>普通业务统一使用门面 {@code YoPermission} 持有的实例；
     * 需要隔离 pending 状态或注入 checker/requester/rationale 时使用本方法。</p>
     */
    @NonNull
    public static PermissionCore create(@NonNull PermissionChecker checker,
                                        @NonNull PermissionRequester requester,
                                        @NonNull PermissionRationale rationale) {
        return new PermissionCore(checker, requester, rationale);
    }

    @NonNull
    public PermissionStatus check(@NonNull Context context,
                                  @NonNull Permission permission) {
        return checker.check(context, permission);
    }

    public boolean isGranted(@NonNull Context context,
                             @NonNull Permission permission) {
        PermissionStatus status = check(context, permission);
        return status == PermissionStatus.GRANTED
                || status == PermissionStatus.NOT_REQUIRED;
    }

    public boolean isGranted(@NonNull Context context,
                             @NonNull List<Permission> permissions) {
        for (Permission permission : permissions) {
            if (!isGranted(context, permission)) {
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
                    || isGranted(activity, permission)) {
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
                    || isGranted(activity, permission)) {
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
        SpecialAccessLifecycleObserver.observe(activity, missing, callback, this);
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
     * 处理 Activity#onRequestPermissionsResult；非本服务发起的请求返回带原因分类的 ignored。
     *
     * <p>传入 Activity 时，额外识别“不再询问”的运行时权限，
     * 便于上层引导用户前往设置页手动开启。</p>
     */
    @NonNull
    public PermissionResult handleRequestPermissionsResult(
            @Nullable Activity activity,
            int requestCode, @Nullable String[] permissions, @Nullable int[] grantResults) {
        PendingLookup lookup = takePending(activity, requestCode);
        if (!lookup.isFound()) {
            YoLog.d(TAG, "权限结果被忽略：请求码=" + requestCode
                    + "，原因=" + lookup.reason());
            return PermissionResult.ignored(lookup.reason());
        }
        List<Permission> pending = lookup.pending();
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
     * 取出 pending 请求，并给出「未命中」的原因分类。
     *
     * <p>三种未命中不再折叠成同一个 null：Activity 未知、requestCode 未知、
     * 以及不传 Activity 时多 Activity 撞码的歧义，各自带原因返回。</p>
     */
    @NonNull
    private PendingLookup takePending(@Nullable Activity activity, int requestCode) {
        synchronized (pendingByActivity) {
            if (pendingByActivity.isEmpty()) {
                return PendingLookup.ignored(PermissionIgnoreReason.NO_PENDING_REQUEST);
            }
            if (activity != null) {
                Map<Integer, List<Permission>> byCode = pendingByActivity.get(activity);
                if (byCode == null) {
                    return PendingLookup.ignored(PermissionIgnoreReason.UNKNOWN_ACTIVITY);
                }
                List<Permission> pending = byCode.remove(requestCode);
                if (byCode.isEmpty()) {
                    pendingByActivity.remove(activity);
                }
                if (pending == null) {
                    return PendingLookup.ignored(PermissionIgnoreReason.UNKNOWN_REQUEST_CODE);
                }
                return PendingLookup.found(pending);
            }
            List<Permission> match = null;
            Activity matchedActivity = null;
            boolean ambiguous = false;
            for (Map.Entry<Activity, Map<Integer, List<Permission>>> entry
                    : pendingByActivity.entrySet()) {
                List<Permission> pending = entry.getValue().get(requestCode);
                if (pending != null) {
                    if (match != null) {
                        ambiguous = true;
                        break;
                    }
                    match = pending;
                    matchedActivity = entry.getKey();
                }
            }
            if (ambiguous) {
                return PendingLookup.ignored(PermissionIgnoreReason.AMBIGUOUS_ACTIVITY);
            }
            if (matchedActivity == null || match == null) {
                return PendingLookup.ignored(PermissionIgnoreReason.UNKNOWN_REQUEST_CODE);
            }
            Map<Integer, List<Permission>> byCode = pendingByActivity.get(matchedActivity);
            byCode.remove(requestCode);
            if (byCode.isEmpty()) {
                pendingByActivity.remove(matchedActivity);
            }
            return PendingLookup.found(match);
        }
    }

    /** pending 查找结果：要么命中权限列表，要么是带原因的忽略。 */
    private static final class PendingLookup {

        @Nullable private final List<Permission> pending;
        @Nullable private final PermissionIgnoreReason reason;

        private PendingLookup(@Nullable List<Permission> pending,
                              @Nullable PermissionIgnoreReason reason) {
            this.pending = pending;
            this.reason = reason;
        }

        @NonNull
        static PendingLookup found(@NonNull List<Permission> pending) {
            return new PendingLookup(pending, null);
        }

        @NonNull
        static PendingLookup ignored(@NonNull PermissionIgnoreReason reason) {
            return new PendingLookup(null, reason);
        }

        boolean isFound() {
            return pending != null;
        }

        @NonNull
        List<Permission> pending() {
            if (pending == null) {
                throw new IllegalStateException("PendingLookup 未命中，不能读取权限列表");
            }
            return pending;
        }

        @NonNull
        PermissionIgnoreReason reason() {
            if (reason == null) {
                throw new IllegalStateException("PendingLookup 已命中，没有忽略原因");
            }
            return reason;
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

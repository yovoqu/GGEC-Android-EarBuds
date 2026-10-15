package com.yovo.yotheme.dialog.core;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.yovo.yotheme.dialog.api.lifecycle.DialogHandle;
import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.host.BackDispatcher;
import com.yovo.yotheme.dialog.host.OwnerLifecycleObserver;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 在 Activity 上展示与管理 YoDialog 实例的入口
 */
public final class YoDialogController {

    private static final DialogRegistry REGISTRY = new DialogRegistry();
    private static final Map<Activity, YoDialogController> CONTROLLERS = new WeakHashMap<>();
    private static final Map<Activity, BackDispatcher> BACK_DISPATCHERS = new WeakHashMap<>();
    private static final Set<Activity> LIFECYCLE_HOOKED =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());

    @NonNull
    private final Activity activity;

    private YoDialogController(@NonNull Activity activity) {
        this.activity = activity;
    }

    @NonNull
    public static YoDialogController from(@NonNull Activity activity) {
        YoDialogController controller = CONTROLLERS.computeIfAbsent(
                activity, YoDialogController::new);
        ensureLifecycleHook(activity);
        return controller;
    }

    @NonNull
    public static YoDialogController from(@NonNull Context context) {
        return from(requireActivity(context));
    }

    @NonNull
    public static Activity requireActivity(@NonNull Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        if (current instanceof Activity) {
            return (Activity) current;
        }
        throw new IllegalArgumentException("YoDialog requires an Activity context");
    }

    @NonNull
    public DialogHandle show(@NonNull YoDialogSpec spec) {
        DialogInstance instance = new DialogInstance(activity, REGISTRY, spec);
        instance.show();
        return instance;
    }

    public void dismissAll() {
        dismissAll(activity);
    }

    @Nullable
    public DialogHandle top() {
        return top(activity);
    }

    public static void dismissAll(@NonNull Activity activity) {
        REGISTRY.dismissAll(activity, DialogResult.Reason.PROGRAMMATIC);
    }

    @Nullable
    public static DialogHandle top(@NonNull Activity activity) {
        return REGISTRY.top(activity);
    }

    static void refreshBackDispatcher(@NonNull Activity activity) {
        BackDispatcher dispatcher = BACK_DISPATCHERS.get(activity);
        if (dispatcher != null) {
            dispatcher.refreshEnabled();
        }
    }

    private static void ensureLifecycleHook(@NonNull Activity activity) {
        if (LIFECYCLE_HOOKED.contains(activity)) {
            return;
        }
        LIFECYCLE_HOOKED.add(activity);
        if (activity instanceof LifecycleOwner) {
            OwnerLifecycleObserver observer = new OwnerLifecycleObserver(activity, REGISTRY);
            ((LifecycleOwner) activity).getLifecycle().addObserver(observer);
        }
        BackDispatcher backDispatcher = new BackDispatcher(activity, REGISTRY);
        BACK_DISPATCHERS.put(activity, backDispatcher);
        backDispatcher.install();
    }
}


package com.yovo.yotheme.dialog.core;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 按 Activity 维护的活动对话框实例栈
 */
public final class DialogRegistry {

    private final Map<Activity, Deque<DialogInstance>> stacks = new WeakHashMap<>();

    public void push(@NonNull Activity activity, @NonNull DialogInstance instance) {
        Deque<DialogInstance> stack = stacks.computeIfAbsent(activity, ignored -> new ArrayDeque<>());
        stack.push(instance);
    }

    public void remove(@NonNull Activity activity, @NonNull DialogInstance instance) {
        Deque<DialogInstance> stack = stacks.get(activity);
        if (stack == null) {
            return;
        }
        stack.remove(instance);
        if (stack.isEmpty()) {
            stacks.remove(activity);
        }
    }

    @Nullable
    public DialogInstance top(@NonNull Activity activity) {
        Deque<DialogInstance> stack = stacks.get(activity);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    public void dismissAll(@NonNull Activity activity, @NonNull DialogResult.Reason reason) {
        Deque<DialogInstance> stack = stacks.get(activity);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        DialogInstance[] instances = stack.toArray(new DialogInstance[0]);
        for (DialogInstance instance : instances) {
            instance.dismiss(reason);
        }
    }
}


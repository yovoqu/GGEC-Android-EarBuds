package com.yovo.yotheme.dialog;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.lifecycle.DialogHandle;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.core.YoDialogController;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description YoDialog 公共入口，用于展示与管理对话框实例
 */
public final class YoDialog {

    private YoDialog() {}

    @NonNull
    public static Scope with(@NonNull Context context) {
        return new Scope(YoDialogController.from(context));
    }

    @NonNull
    public static Scope with(@NonNull Activity activity) {
        return new Scope(YoDialogController.from(activity));
    }

    @NonNull
    public static DialogHandle show(@NonNull Context context, @NonNull YoDialogSpec spec) {
        return with(context).show(spec);
    }

    @NonNull
    public static DialogHandle show(@NonNull Activity activity, @NonNull YoDialogSpec spec) {
        return with(activity).show(spec);
    }

    public static void dismissAll(@NonNull Context context) {
        with(context).dismissAll();
    }

    public static void dismissAll(@NonNull Activity activity) {
        with(activity).dismissAll();
    }

    @Nullable
    public static DialogHandle top(@NonNull Context context) {
        return with(context).top();
    }

    @Nullable
    public static DialogHandle top(@NonNull Activity activity) {
        return with(activity).top();
    }

    @NonNull
    public static Activity requireActivity(@NonNull Context context) {
        return YoDialogController.requireActivity(context);
    }

    public static final class Scope {
        @NonNull
        private final YoDialogController controller;

        private Scope(@NonNull YoDialogController controller) {
            this.controller = controller;
        }

        @NonNull
        public DialogHandle show(@NonNull YoDialogSpec spec) {
            return controller.show(spec);
        }

        public void dismissAll() {
            controller.dismissAll();
        }

        @Nullable
        public DialogHandle top() {
            return controller.top();
        }
    }
}


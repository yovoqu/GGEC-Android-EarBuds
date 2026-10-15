package com.yovo.yotheme.dialog.core;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.lifecycle.DialogHandle;
import com.yovo.yotheme.dialog.api.callback.DialogLifecycleListener;
import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.api.lifecycle.DialogState;
import com.yovo.yotheme.dialog.api.YoDialogSpec;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 单个对话框实例，含生命周期、状态与平台绑定
 */
public final class DialogInstance implements DialogHandle {

    @NonNull
    private final String id;

    @NonNull
    private final Activity activity;

    @NonNull
    private final DialogRegistry registry;

    @NonNull
    private YoDialogSpec spec;

    @NonNull
    private final DialogStateStore stateStore;

    @NonNull
    private DialogState state = DialogState.IDLE;

    @Nullable
    private DialogLifecycleListener lifecycleListener;

    @Nullable
    private DialogPlatformDialog platformDialog;

    @Nullable
    private String pendingActionId;

    DialogInstance(@NonNull Activity activity,
                   @NonNull DialogRegistry registry,
                   @NonNull YoDialogSpec spec) {
        this.id = UUID.randomUUID().toString();
        this.activity = activity;
        this.registry = registry;
        this.spec = spec;
        this.stateStore = new DialogStateStore();
        this.stateStore.syncFromSpec(spec);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public YoDialogSpec getSpec() {
        return spec;
    }

    @NonNull
    public DialogStateStore getStateStore() {
        return stateStore;
    }

    @NonNull
    public DialogState getState() {
        return state;
    }

    public void setLifecycleListener(@Nullable DialogLifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener;
    }

    public boolean isShowing() {
        return state == DialogState.SHOWN
                || state == DialogState.SHOWING
                || (platformDialog != null && platformDialog.isShowing());
    }

    public void show() {
        if (state != DialogState.IDLE && state != DialogState.DISMISSED) {
            return;
        }
        transitionTo(DialogState.CREATING);
        notifyLifecycle(DialogLifecycleListener::onWillShow);

        platformDialog = new DialogPlatformDialog(activity, spec, this);
        registry.push(activity, this);
        YoDialogController.refreshBackDispatcher(activity);

        transitionTo(DialogState.SHOWING);
        platformDialog.show();
    }

    public void dismiss(@NonNull DialogResult.Reason reason) {
        dismiss(reason, null);
    }

    public void dismiss(@NonNull DialogResult.Reason reason, @Nullable String actionId) {
        if (state == DialogState.DISMISSED || state == DialogState.DISMISSING) {
            return;
        }
        pendingActionId = actionId;
        transitionTo(DialogState.DISMISSING);
        DialogResult result = buildResult(reason, actionId);
        notifyLifecycle(listener -> listener.onWillDismiss(result));
        if (platformDialog != null) {
            platformDialog.dismissWithAnimation(reason, actionId);
        } else {
            finishDismiss(result);
        }
    }

    public void update(@NonNull Consumer<YoDialogSpec.Builder> patch) {
        YoDialogSpec.Builder builder = spec.toBuilder();
        patch.accept(builder);
        spec = builder.build();
        stateStore.syncFromSpec(spec);
        if (platformDialog != null && platformDialog.isShowing()) {
            platformDialog.applySpec(spec);
        }
    }

    @Override
    public void setActionEnabled(@NonNull String actionId, boolean enabled) {
        stateStore.setActionEnabled(actionId, enabled);
    }

    void onPlatformShown() {
        if (state == DialogState.SHOWING) {
            transitionTo(DialogState.SHOWN);
            notifyLifecycle(DialogLifecycleListener::onShown);
        }
    }

    void onPlatformDismissed(@NonNull DialogResult.Reason reason, @Nullable String actionId) {
        finishDismiss(buildResult(reason, actionId != null ? actionId : pendingActionId));
    }

    void handleOutsideDismiss() {
        if (spec.getBehavior().isCancelOnTouchOutside()) {
            dismiss(DialogResult.Reason.OUTSIDE);
        }
    }

    void handleActionClick(@NonNull String actionId) {
        if (!stateStore.isActionEnabled(actionId)) {
            return;
        }
        DialogAction action = findAction(actionId);
        if (action == null) {
            return;
        }
        DialogResult result = buildResult(DialogResult.Reason.ACTION, actionId);
        if (action.getOnClick() != null) {
            action.getOnClick().onClick(result);
        }
        if (spec.getBehavior().isDismissOnAction() && action.isAutoDismiss()) {
            dismiss(DialogResult.Reason.ACTION, actionId);
        }
    }

    @Nullable
    private DialogAction findAction(@NonNull String actionId) {
        for (DialogAction action : spec.getActions()) {
            if (action.getId().equals(actionId)) {
                return action;
            }
        }
        return null;
    }

    @NonNull
    private DialogResult buildResult(@NonNull DialogResult.Reason reason,
                                     @Nullable String actionId) {
        return new DialogResult(
                reason,
                actionId,
                new ArrayList<>(stateStore.getSelectedIds()));
    }

    private void finishDismiss(@NonNull DialogResult result) {
        registry.remove(activity, this);
        YoDialogController.refreshBackDispatcher(activity);
        platformDialog = null;
        transitionTo(DialogState.DISMISSED);
        notifyLifecycle(listener -> listener.onDismissed(result));
    }

    private void transitionTo(@NonNull DialogState newState) {
        state = newState;
        notifyLifecycle(listener -> listener.onStateChanged(newState));
    }

    private void notifyLifecycle(@NonNull LifecycleCallback callback) {
        if (lifecycleListener != null) {
            callback.dispatch(lifecycleListener);
        }
    }

    @FunctionalInterface
    private interface LifecycleCallback {
        void dispatch(@NonNull DialogLifecycleListener listener);
    }
}


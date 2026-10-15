package com.yovo.yotheme.dialog.core;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.animation.DialogAnimator;
import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.content.CustomContentBinder;
import com.yovo.yotheme.dialog.api.content.DialogContentSpec;
import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.api.content.OptionListContent;
import com.yovo.yotheme.dialog.api.callback.SelectionConfirmCallback;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.api.option.OptionListSpec;
import com.yovo.yotheme.dialog.host.DialogMaskHost;
import com.yovo.yotheme.dialog.host.DialogWindowHost;
import com.yovo.yotheme.dialog.render.DialogActionCallback;
import com.yovo.yotheme.dialog.render.DialogRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 内部 Android {@link Dialog}，串联 host、animator 与 renderer 层
 */
final class DialogPlatformDialog extends Dialog {

    @NonNull
    private final DialogInstance instance;

    @NonNull
    private YoDialogSpec spec;

    @NonNull
    private final DialogWindowHost windowHost = new DialogWindowHost();

    @Nullable
    private DialogMaskHost maskHost;

    @Nullable
    private DialogAnimator animator;

    @Nullable
    private CustomContentBinding customContentBinding;

    private boolean dismissAnimating;

    DialogPlatformDialog(@NonNull Activity activity,
                         @NonNull YoDialogSpec spec,
                         @NonNull DialogInstance instance) {
        super(activity, com.yovo.yotheme.R.style.YoTheme_FloatingWindow);
        this.spec = spec;
        this.instance = instance;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        windowHost.configure(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuildContentTree();
    }

    void applySpec(@NonNull YoDialogSpec newSpec) {
        YoDialogSpec oldSpec = this.spec;
        this.spec = newSpec;
        applyBehavior();
        if (!isBehaviorOnlyUpdate(oldSpec, newSpec)) {
            rebuildContentTree();
        }
    }

    void dismissWithAnimation(@NonNull DialogResult.Reason reason, @Nullable String actionId) {
        if (!isShowing() || dismissAnimating) {
            return;
        }
        if (maskHost == null || animator == null) {
            releaseCustomContent();
            super.dismiss();
            instance.onPlatformDismissed(reason, actionId);
            return;
        }
        dismissAnimating = true;
        animator.animateDismiss(maskHost, () -> {
            dismissAnimating = false;
            if (animator != null) {
                animator.cancel();
            }
            releaseCustomContent();
            DialogPlatformDialog.super.dismiss();
            instance.onPlatformDismissed(reason, actionId);
        });
    }

    @Override
    public void show() {
        windowHost.configure(this);
        Window window = getWindow();
        if (window != null) {
            window.getDecorView().setAlpha(0f);
        }
        super.show();
        windowHost.configure(this);
        if (maskHost == null) {
            if (window != null) {
                window.getDecorView().setAlpha(1f);
            }
            instance.onPlatformShown();
            return;
        }
        maskHost.prepareForShow();
        windowHost.bindContentInsets(this, maskHost);
        setCanceledOnTouchOutside(spec.getBehavior().isCancelOnTouchOutside());
        maskHost.setOutsideDismissEnabled(spec.getBehavior().isCancelOnTouchOutside());

        View animationHost = window != null ? window.getDecorView() : maskHost;
        animationHost.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                animationHost.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!isShowing() || maskHost == null || animator == null) {
                    return true;
                }
                if (window != null) {
                    window.getDecorView().setAlpha(1f);
                }
                animator.animateShow(maskHost, () -> instance.onPlatformShown());
                return true;
            }
        });
    }

    @Override
    public void dismiss() {
        instance.dismiss(DialogResult.Reason.PROGRAMMATIC);
    }

    private void rebuildContentTree() {
        releaseCustomContent();
        DialogActionCallback callback = new DialogActionCallback() {
            @Override
            public void onAction(@NonNull DialogResult result) {
                if (DialogAction.ID_POSITIVE.equals(result.getActionId())) {
                    dispatchSelectionConfirm();
                }
            }

            @Override
            public void requestDismiss(@NonNull DialogResult result) {
                instance.dismiss(result.getReason(), result.getActionId());
            }
        };

        View panel = DialogRenderer.buildContentView(
                getContext(),
                spec,
                instance.getStateStore(),
                callback,
                this::bindCustomContent);

        Runnable onOutsideDismiss = () -> instance.handleOutsideDismiss();
        maskHost = windowHost.createMaskedRoot(
                getContext(),
                spec.getStyle().getMaskAlpha(),
                panel,
                onOutsideDismiss);
        maskHost.setOutsideDismissEnabled(spec.getBehavior().isCancelOnTouchOutside());
        animator = new DialogAnimator(spec.getAnimation());
        setContentView(maskHost);
        windowHost.configure(this);
        windowHost.bindContentInsets(this, maskHost);
        setCancelable(spec.getBehavior().isCancelableByBack());
    }

    private void bindCustomContent(@NonNull CustomContentBinder binder, @NonNull View contentView) {
        customContentBinding = new CustomContentBinding(binder, contentView);
    }

    private void releaseCustomContent() {
        if (customContentBinding == null) {
            return;
        }
        customContentBinding.binder.unbind(customContentBinding.contentView);
        customContentBinding = null;
    }

    private void applyBehavior() {
        if (maskHost != null) {
            maskHost.setOutsideDismissEnabled(spec.getBehavior().isCancelOnTouchOutside());
        }
        setCanceledOnTouchOutside(spec.getBehavior().isCancelOnTouchOutside());
        setCancelable(spec.getBehavior().isCancelableByBack());
    }

    private static boolean isBehaviorOnlyUpdate(@NonNull YoDialogSpec oldSpec,
                                                @NonNull YoDialogSpec newSpec) {
        return Objects.equals(oldSpec.getTitle(), newSpec.getTitle())
                && Objects.equals(oldSpec.getSubtitle(), newSpec.getSubtitle())
                && oldSpec.getContentSpec() == newSpec.getContentSpec()
                && oldSpec.getActions().equals(newSpec.getActions())
                && oldSpec.getStyle() == newSpec.getStyle()
                && oldSpec.getAnimation() == newSpec.getAnimation()
                && oldSpec.getPerformancePolicy() == newSpec.getPerformancePolicy()
                && oldSpec.getHeaderLeading() == newSpec.getHeaderLeading()
                && oldSpec.getHeaderTrailing() == newSpec.getHeaderTrailing();
    }

    private void dispatchSelectionConfirm() {
        DialogContentSpec contentSpec = spec.getContentSpec();
        if (!(contentSpec instanceof OptionListContent)) {
            return;
        }
        SelectionConfirmCallback callback =
                ((OptionListContent) contentSpec).getSelectionConfirmCallback();
        if (callback == null) {
            return;
        }
        OptionListSpec optionList = ((OptionListContent) contentSpec).getOptionList();
        List<String> selectedIds = new ArrayList<>(instance.getStateStore().getSelectedIds());
        List<OptionItemSpec> selectedItems = new ArrayList<>();
        for (OptionItemSpec item : optionList.getItems()) {
            if (selectedIds.contains(item.getId())) {
                selectedItems.add(item);
            }
        }
        callback.onConfirm(selectedIds, selectedItems);
    }

    private static final class CustomContentBinding {
        @NonNull
        final CustomContentBinder binder;
        @NonNull
        final View contentView;

        CustomContentBinding(@NonNull CustomContentBinder binder, @NonNull View contentView) {
            this.binder = binder;
            this.contentView = contentView;
        }
    }
}


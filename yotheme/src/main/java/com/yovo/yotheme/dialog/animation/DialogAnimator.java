package com.yovo.yotheme.dialog.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.style.DialogAnimationSpec;
import com.yovo.yotheme.dialog.host.DialogMaskHost;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框面板的显示与关闭变换/透明度动画
 */
public final class DialogAnimator {

    private static final PathInterpolator SHOW_INTERPOLATOR =
            new PathInterpolator(0.22f, 1f, 0.36f, 1f);
    private static final PathInterpolator DISMISS_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.6f, 1f);
    private static final float SHOW_SCALE_START = 0.86f;

    @NonNull
    private final DialogAnimationSpec animationSpec;

    @Nullable
    private ValueAnimator runningAnimator;

    public DialogAnimator(@NonNull DialogAnimationSpec animationSpec) {
        this.animationSpec = animationSpec;
    }

    public void animateShow(@NonNull DialogMaskHost maskHost, @Nullable Runnable onEnd) {
        cancel();

        View maskView = maskHost.getMaskView();
        View transformTarget = maskHost.getAnimationTransformTarget();
        float targetMaskAlpha = maskHost.getMaskViewTargetAlpha();
        if (transformTarget == null) {
            maskView.setAlpha(targetMaskAlpha);
            if (onEnd != null) {
                onEnd.run();
            }
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(animationSpec.getShowDurationMs());
        animator.setInterpolator(SHOW_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            maskView.setAlpha(progress * targetMaskAlpha);
            float scale = SHOW_SCALE_START + (1f - SHOW_SCALE_START) * progress;
            maskHost.applyAnimationTransform(progress, scale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                maskHost.resetAfterAnimation();
                runningAnimator = null;
                if (onEnd != null) {
                    onEnd.run();
                }
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                runningAnimator = null;
            }
        });
        runningAnimator = animator;
        animator.start();
    }

    public void animateDismiss(@NonNull DialogMaskHost maskHost, @NonNull Runnable onEnd) {
        cancel();

        View maskView = maskHost.getMaskView();
        View transformTarget = maskHost.getAnimationTransformTarget();
        float startMaskAlpha = maskView.getAlpha();
        if (transformTarget == null) {
            onEnd.run();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(animationSpec.getDismissDurationMs());
        animator.setInterpolator(DISMISS_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            float remain = 1f - progress;
            maskView.setAlpha(startMaskAlpha * remain);
            float scale = 1f - (1f - SHOW_SCALE_START) * progress;
            maskHost.applyAnimationTransform(remain, scale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                runningAnimator = null;
                onEnd.run();
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                runningAnimator = null;
            }
        });
        runningAnimator = animator;
        animator.start();
    }

    public void cancel() {
        if (runningAnimator != null) {
            runningAnimator.cancel();
            runningAnimator = null;
        }
    }
}

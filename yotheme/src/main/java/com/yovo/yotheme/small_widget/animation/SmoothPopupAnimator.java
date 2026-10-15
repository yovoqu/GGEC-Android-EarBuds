package com.yovo.yotheme.small_widget.animation;

import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.MenuConstants;

/**
 * @author tingfeng.yang
 * @date 2025-11-21
 * @description Popup 弹窗内容的通用显隐动画控制器，支持向上/向下方向。
 */
public class SmoothPopupAnimator {

    public enum PopupDirection {
        UPWARD,
        DOWNWARD
    }

    private final View targetView;
    private PopupDirection direction;
    private long durationMs;
    private float translationDistancePx;
    private final TimeInterpolator showInterpolator;
    private final TimeInterpolator dismissInterpolator;

    public SmoothPopupAnimator(View targetView, PopupDirection direction) {
        this.targetView = targetView;
        this.direction = direction;
        this.durationMs = MenuConstants.POPUP_ANIMATION_DURATION_MS.getIntValue();
        this.translationDistancePx = Math.max(0f, dpToPx(MenuConstants.POPUP_ANIMATION_TRANSLATION_DP.getFloatValue()));
        this.showInterpolator = new DecelerateInterpolator();
        this.dismissInterpolator = new DecelerateInterpolator();
    }

    public SmoothPopupAnimator setDirection(PopupDirection direction) {
        if (direction != null) {
            this.direction = direction;
        }
        return this;
    }

    public SmoothPopupAnimator setDuration(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        return this;
    }

    public SmoothPopupAnimator setTranslationDistanceDp(float distanceDp) {
        this.translationDistancePx = Math.max(0f, dpToPx(distanceDp));
        return this;
    }

    public void prepareForShow() {
        if (targetView == null) {
            return;
        }

        targetView.animate().cancel();
        targetView.setAlpha(0f);
        targetView.setTranslationY(calculateShowStartTranslation());
    }

    public void animateShow(@Nullable Runnable endAction) {
        if (targetView == null) {
            return;
        }

        targetView.animate().cancel();

        float startTranslation = calculateShowStartTranslation();

        targetView.setAlpha(0f);
        targetView.setTranslationY(startTranslation);
        enableHardwareLayer();

        targetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(durationMs)
                .setInterpolator(showInterpolator)
                .withEndAction(() -> {
                    if (endAction != null) {
                        endAction.run();
                    }
                    resetState();
                })
                .start();
    }

    public void animateDismiss(@Nullable Runnable endAction) {
        if (targetView == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        targetView.animate().cancel();

        float endTranslation = calculateDismissEndTranslation();

        enableHardwareLayer();

        targetView.animate()
                .alpha(0f)
                .translationY(endTranslation)
                .setDuration(durationMs)
                .setInterpolator(dismissInterpolator)
                .withEndAction(() -> {
                    if (endAction != null) {
                        endAction.run();
                    }
                    if (targetView != null) {
                        targetView.post(this::resetState);
                    } else {
                        resetState();
                    }
                })
                .start();
    }

    public void cancelAnimations() {
        if (targetView != null) {
            targetView.animate().cancel();
            disableHardwareLayer();
        }
    }

    public void resetState() {
        if (targetView != null) {
            targetView.setAlpha(1f);
            targetView.setTranslationY(0f);
            disableHardwareLayer();
        }
    }

    private float calculateShowStartTranslation() {
        return direction == PopupDirection.UPWARD
                ? translationDistancePx
                : -translationDistancePx;
    }

    private float calculateDismissEndTranslation() {
        return calculateShowStartTranslation();
    }

    private void enableHardwareLayer() {
        if (targetView != null && targetView.getLayerType() != View.LAYER_TYPE_HARDWARE) {
            targetView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    private void disableHardwareLayer() {
        if (targetView != null && targetView.getLayerType() != View.LAYER_TYPE_NONE) {
            targetView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private float dpToPx(float dp) {
        if (targetView == null) {
            return dp;
        }
        return dp * targetView.getResources().getDisplayMetrics().density;
    }
}


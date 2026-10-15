package com.yovo.yotheme.small_widget.animation;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description 平滑缩放动画工具
 * @note 负责对目标 View 进行 scaleX/scaleY 的丝滑缩放过渡
 */
public class SmoothScaleAnimator {

    private final View targetView;
    private AnimatorSet animatorSet;
    private long durationMs = 150L; // 默认动画时长
    private Interpolator interpolator = new DecelerateInterpolator(); // 默认更丝滑的减速插值器

    public SmoothScaleAnimator(View targetView) {
        this.targetView = targetView;
    }

    /**
     * 设置动画时长
     */
    public SmoothScaleAnimator setDuration(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        return this;
    }

    /**
     * 设置插值器
     */
    public SmoothScaleAnimator setInterpolator(Interpolator interpolator) {
        if (interpolator != null) {
            this.interpolator = interpolator;
        }
        return this;
    }

    /**
     * 动画到指定缩放比例（X/Y 等比）
     */
    public void animateTo(float targetScale) {
        if (targetView == null) return;

        float currentScaleX = targetView.getScaleX();
        float currentScaleY = targetView.getScaleY();

        // 已经在目标值则直接返回，避免重复启动动画
        if (Float.compare(currentScaleX, targetScale) == 0 && Float.compare(currentScaleY, targetScale) == 0) {
            return;
        }

        // 取消当前动画
        if (animatorSet != null && animatorSet.isRunning()) {
            animatorSet.cancel();
        }

        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(targetView, "scaleX", currentScaleX, targetScale);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(targetView, "scaleY", currentScaleY, targetScale);

        animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleXAnim, scaleYAnim);
        animatorSet.setDuration(durationMs);
        animatorSet.setInterpolator(interpolator);
        animatorSet.start();
    }

    /**
     * 是否正在运行动画
     */
    public boolean isRunning() {
        return animatorSet != null && animatorSet.isRunning();
    }

    /**
     * 取消动画
     */
    public void cancel() {
        if (animatorSet != null && animatorSet.isRunning()) {
            animatorSet.cancel();
        }
    }
}



package com.yovo.yotheme.small_widget.animation;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * 平滑宽度变化动画工具
 * 负责对目标 View 进行宽度的丝滑过渡
 * 支持响应式布局的宽度变化动画
 * 
 * @author tingfeng.yang
 * @date 2025-10-23
 */
public class SmoothWidthAnimator {

    private final View targetView;
    private ValueAnimator widthAnimator;
    private long durationMs = 200L; // 默认动画时长
    private Interpolator interpolator = new DecelerateInterpolator(); // 默认减速插值器
    private boolean enableAnimation = true; // 是否启用动画

    public SmoothWidthAnimator(View targetView) {
        this.targetView = targetView;
    }

    /**
     * 设置动画时长
     */
    public SmoothWidthAnimator setDuration(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        return this;
    }

    /**
     * 设置插值器
     */
    public SmoothWidthAnimator setInterpolator(Interpolator interpolator) {
        if (interpolator != null) {
            this.interpolator = interpolator;
        }
        return this;
    }

    /**
     * 启用或禁用动画
     */
    public SmoothWidthAnimator setAnimationEnabled(boolean enabled) {
        this.enableAnimation = enabled;
        return this;
    }

    /**
     * 动画到指定宽度（像素值）
     */
    public void animateToWidth(int targetWidthPx) {
        if (targetView == null) return;

        ViewGroup.LayoutParams params = targetView.getLayoutParams();
        if (params == null) return;

        int currentWidth = targetView.getWidth();
        
        // 如果禁用动画或目标宽度和当前宽度相同，直接设置
        if (!enableAnimation || currentWidth == targetWidthPx) {
            params.width = targetWidthPx;
            targetView.setLayoutParams(params);
            return;
        }

        // 取消当前动画
        if (widthAnimator != null && widthAnimator.isRunning()) {
            widthAnimator.cancel();
        }

        // 创建宽度变化动画
        widthAnimator = ValueAnimator.ofInt(currentWidth, targetWidthPx);
        widthAnimator.setDuration(durationMs);
        widthAnimator.setInterpolator(interpolator);
        
        widthAnimator.addUpdateListener(animation -> {
            if (targetView.getLayoutParams() != null) {
                int animatedValue = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = targetView.getLayoutParams();
                layoutParams.width = animatedValue;
                targetView.setLayoutParams(layoutParams);
            }
        });
        
        widthAnimator.start();
    }

    /**
     * 动画到 WRAP_CONTENT
     * 会先测量目标宽度，然后执行动画
     */
    public void animateToWrapContent() {
        if (targetView == null) return;

        // 先测量目标宽度
        targetView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int targetWidth = targetView.getMeasuredWidth();
        
        animateToWidth(targetWidth);
    }

    /**
     * 动画到 MATCH_PARENT
     */
    public void animateToMatchParent() {
        if (targetView == null || targetView.getParent() == null) return;

        View parent = (View) targetView.getParent();
        int targetWidth = parent.getWidth();
        
        animateToWidth(targetWidth);
    }

    /**
     * 是否正在运行动画
     */
    public boolean isRunning() {
        return widthAnimator != null && widthAnimator.isRunning();
    }

    /**
     * 取消动画
     */
    public void cancel() {
        if (widthAnimator != null && widthAnimator.isRunning()) {
            widthAnimator.cancel();
        }
    }
}


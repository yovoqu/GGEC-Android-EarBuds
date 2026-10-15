package com.yovo.yotheme.small_widget.animation;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * @author tingfeng.yang
 * @date 2025-10-25
 * @description 平滑左右滑动动画工具
 * @note 负责对目标 View 进行 X 轴位置的丝滑滑动过渡，用于分段按钮的选中指示器动画
 */
public class SmoothSlideAnimator {

    private final View targetView;
    private ValueAnimator slideAnimator;
    private long durationMs = 300L; // 默认动画时长
    private Interpolator interpolator = new DecelerateInterpolator(); // 默认减速插值器

    public SmoothSlideAnimator(View targetView) {
        this.targetView = targetView;
    }

    /**
     * 设置动画时长
     */
    public SmoothSlideAnimator setDuration(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        return this;
    }

    /**
     * 设置插值器
     */
    public SmoothSlideAnimator setInterpolator(Interpolator interpolator) {
        if (interpolator != null) {
            this.interpolator = interpolator;
        }
        return this;
    }

    /**
     * 动画到指定X坐标位置（像素值）
     */
    public void animateToX(float targetX) {
        if (targetView == null) return;

        float currentX = targetView.getX();
        
        // 如果目标位置和当前位置相同，直接设置
        if (Float.compare(currentX, targetX) == 0) {
            targetView.setX(targetX);
            return;
        }

        // 取消当前动画
        if (slideAnimator != null && slideAnimator.isRunning()) {
            slideAnimator.cancel();
        }

        // 创建X坐标变化动画
        slideAnimator = ValueAnimator.ofFloat(currentX, targetX);
        slideAnimator.setDuration(durationMs);
        slideAnimator.setInterpolator(interpolator);
        
        slideAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            targetView.setX(animatedValue);
        });
        
        slideAnimator.start();
    }

    /**
     * 同时动画到指定X坐标位置和宽度
     * 用于分段按钮在不同宽度选项之间切换
     */
    public void animateToXAndWidth(float targetX, int targetWidth) {
        if (targetView == null) return;

        float currentX = targetView.getX();
        int currentWidth = targetView.getWidth();
        
        // 如果目标位置和宽度都相同，直接设置
        if (Float.compare(currentX, targetX) == 0 && currentWidth == targetWidth) {
            targetView.setX(targetX);
            ViewGroup.LayoutParams params = targetView.getLayoutParams();
            if (params != null) {
                params.width = targetWidth;
                targetView.setLayoutParams(params);
            }
            return;
        }

        // 取消当前动画
        if (slideAnimator != null && slideAnimator.isRunning()) {
            slideAnimator.cancel();
        }

        // 创建同时变化X和宽度的动画
        slideAnimator = ValueAnimator.ofFloat(0f, 1f);
        slideAnimator.setDuration(durationMs);
        slideAnimator.setInterpolator(interpolator);
        
        slideAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            
            // 更新X坐标
            float animatedX = currentX + (targetX - currentX) * fraction;
            targetView.setX(animatedX);
            
            // 更新宽度
            ViewGroup.LayoutParams params = targetView.getLayoutParams();
            if (params != null) {
                int animatedWidth = (int) (currentWidth + (targetWidth - currentWidth) * fraction);
                params.width = animatedWidth;
                targetView.setLayoutParams(params);
            }
        });
        
        slideAnimator.start();
    }

    /**
     * 是否正在运行动画
     */
    public boolean isRunning() {
        return slideAnimator != null && slideAnimator.isRunning();
    }

    /**
     * 取消动画
     */
    public void cancel() {
        if (slideAnimator != null && slideAnimator.isRunning()) {
            slideAnimator.cancel();
        }
    }
}


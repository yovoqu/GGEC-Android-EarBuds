package com.yovo.yotheme.small_widget.animation;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2025-01-XX
 * @description ActionCard 卡片显隐动画工具
 * @note 负责对目标 View 进行从右侧滑入/滑出的平滑过渡动画
 * 支持平移和透明度变化的组合动画
 */
public class SmoothActionCardAnimator {
    
    public enum SlideDirection {
        FROM_RIGHT,  // 从右侧滑入/滑出
        FROM_LEFT,   // 从左侧滑入/滑出
        FROM_TOP,    // 从顶部滑入/滑出
        FROM_BOTTOM  // 从底部滑入/滑出
    }
    
    private final View targetView;
    private SlideDirection direction;
    private long durationMs = 300L;
    private TimeInterpolator showInterpolator;
    private TimeInterpolator dismissInterpolator;
    private AnimatorSet showAnimatorSet;
    private AnimatorSet dismissAnimatorSet;
    
    public SmoothActionCardAnimator(View targetView) {
        this(targetView, SlideDirection.FROM_RIGHT);
    }
    
    public SmoothActionCardAnimator(View targetView, SlideDirection direction) {
        this.targetView = targetView;
        this.direction = direction != null ? direction : SlideDirection.FROM_RIGHT;
        // 滑入使用减速插值器（decelerate_cubic）
        this.showInterpolator = new DecelerateInterpolator();
        // 滑出使用加速插值器（accelerate_cubic）
        this.dismissInterpolator = new AccelerateInterpolator();
    }
    
    /**
     * 设置滑动方向
     */
    public SmoothActionCardAnimator setDirection(SlideDirection direction) {
        if (direction != null) {
            this.direction = direction;
        }
        return this;
    }
    
    /**
     * 设置动画时长
     */
    public SmoothActionCardAnimator setDuration(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        return this;
    }
    
    /**
     * 设置显示动画插值器
     */
    public SmoothActionCardAnimator setShowInterpolator(TimeInterpolator interpolator) {
        if (interpolator != null) {
            this.showInterpolator = interpolator;
        }
        return this;
    }
    
    /**
     * 设置隐藏动画插值器
     */
    public SmoothActionCardAnimator setDismissInterpolator(TimeInterpolator interpolator) {
        if (interpolator != null) {
            this.dismissInterpolator = interpolator;
        }
        return this;
    }
    
    /**
     * 准备显示动画（设置初始状态）
     */
    public void prepareForShow() {
        if (targetView == null) {
            return;
        }
        
        cancelAllAnimations();
        
        // 设置初始位置和透明度
        float startTranslationX = calculateShowStartTranslationX();
        float startTranslationY = calculateShowStartTranslationY();
        
        targetView.setTranslationX(startTranslationX);
        targetView.setTranslationY(startTranslationY);
        targetView.setAlpha(0f);
    }
    
    /**
     * 执行显示动画
     */
    public void animateShow(@Nullable Runnable endAction) {
        if (targetView == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        
        cancelAllAnimations();
        
        // 获取初始位置
        float startTranslationX = calculateShowStartTranslationX();
        float startTranslationY = calculateShowStartTranslationY();
        
        // 设置初始状态
        targetView.setTranslationX(startTranslationX);
        targetView.setTranslationY(startTranslationY);
        targetView.setAlpha(0f);
        targetView.setVisibility(View.VISIBLE);
        
        // 创建平移动画
        ObjectAnimator translateXAnim = ObjectAnimator.ofFloat(
            targetView, "translationX", startTranslationX, 0f
        );
        ObjectAnimator translateYAnim = ObjectAnimator.ofFloat(
            targetView, "translationY", startTranslationY, 0f
        );
        
        // 创建透明度动画
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(
            targetView, "alpha", 0f, 1f
        );
        
        // 组合动画
        showAnimatorSet = new AnimatorSet();
        showAnimatorSet.playTogether(translateXAnim, translateYAnim, alphaAnim);
        showAnimatorSet.setDuration(durationMs);
        showAnimatorSet.setInterpolator(showInterpolator);
        
        if (endAction != null) {
            showAnimatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    endAction.run();
                }
            });
        }
        
        showAnimatorSet.start();
    }
    
    /**
     * 执行隐藏动画
     */
    public void animateDismiss(@Nullable Runnable endAction) {
        if (targetView == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        
        cancelAllAnimations();
        
        // 获取目标位置
        float endTranslationX = calculateDismissEndTranslationX();
        float endTranslationY = calculateDismissEndTranslationY();
        
        // 创建平移动画
        ObjectAnimator translateXAnim = ObjectAnimator.ofFloat(
            targetView, "translationX", targetView.getTranslationX(), endTranslationX
        );
        ObjectAnimator translateYAnim = ObjectAnimator.ofFloat(
            targetView, "translationY", targetView.getTranslationY(), endTranslationY
        );
        
        // 创建透明度动画
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(
            targetView, "alpha", targetView.getAlpha(), 0f
        );
        
        // 组合动画
        dismissAnimatorSet = new AnimatorSet();
        dismissAnimatorSet.playTogether(translateXAnim, translateYAnim, alphaAnim);
        dismissAnimatorSet.setDuration(durationMs);
        dismissAnimatorSet.setInterpolator(dismissInterpolator);
        
        dismissAnimatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (targetView != null) {
                    targetView.setVisibility(View.GONE);
                }
                if (endAction != null) {
                    endAction.run();
                }
            }
        });
        
        dismissAnimatorSet.start();
    }
    
    /**
     * 取消所有动画
     */
    public void cancelAllAnimations() {
        if (showAnimatorSet != null && showAnimatorSet.isRunning()) {
            showAnimatorSet.cancel();
        }
        if (dismissAnimatorSet != null && dismissAnimatorSet.isRunning()) {
            dismissAnimatorSet.cancel();
        }
    }
    
    /**
     * 重置状态
     */
    public void resetState() {
        if (targetView != null) {
            targetView.setTranslationX(0f);
            targetView.setTranslationY(0f);
            targetView.setAlpha(1f);
        }
    }
    
    /**
     * 计算显示动画的起始 X 坐标
     */
    private float calculateShowStartTranslationX() {
        if (targetView == null) {
            return 0f;
        }
        
        int width = targetView.getWidth();
        if (width == 0) {
            // 如果 View 还没有测量，使用父容器宽度或屏幕宽度
            View parent = (View) targetView.getParent();
            if (parent != null) {
                width = parent.getWidth();
            }
            if (width == 0) {
                width = targetView.getResources().getDisplayMetrics().widthPixels;
            }
        }
        
        switch (direction) {
            case FROM_RIGHT:
                return width;
            case FROM_LEFT:
                return -width;
            default:
                return 0f;
        }
    }
    
    /**
     * 计算显示动画的起始 Y 坐标
     */
    private float calculateShowStartTranslationY() {
        if (targetView == null) {
            return 0f;
        }
        
        int height = targetView.getHeight();
        if (height == 0) {
            // 如果 View 还没有测量，使用父容器高度或屏幕高度
            View parent = (View) targetView.getParent();
            if (parent != null) {
                height = parent.getHeight();
            }
            if (height == 0) {
                height = targetView.getResources().getDisplayMetrics().heightPixels;
            }
        }
        
        switch (direction) {
            case FROM_TOP:
                return -height;
            case FROM_BOTTOM:
                return height;
            default:
                return 0f;
        }
    }
    
    /**
     * 计算隐藏动画的结束 X 坐标
     */
    private float calculateDismissEndTranslationX() {
        return calculateShowStartTranslationX();
    }
    
    /**
     * 计算隐藏动画的结束 Y 坐标
     */
    private float calculateDismissEndTranslationY() {
        return calculateShowStartTranslationY();
    }
    
    /**
     * 是否正在运行动画
     */
    public boolean isRunning() {
        return (showAnimatorSet != null && showAnimatorSet.isRunning()) ||
               (dismissAnimatorSet != null && dismissAnimatorSet.isRunning());
    }
}


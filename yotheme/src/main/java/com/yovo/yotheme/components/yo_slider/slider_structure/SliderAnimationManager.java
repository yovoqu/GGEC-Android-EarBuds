package com.yovo.yotheme.components.yo_slider.slider_structure;

import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import com.yovo.yotheme.constants.SliderConstants;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条动画管理器
 * @note 负责管理滑动条的动画效果，包括数值变化动画
 */
public class SliderAnimationManager {
    
    private ValueAnimator valueAnimator;
    private boolean animationEnabled;
    private int animationDuration;
    private Interpolator interpolator;
    
    // 动画监听器接口
    public interface AnimationUpdateListener {
        void onAnimationUpdate(int animatedValue);
    }
    
    public interface AnimationStateListener {
        void onAnimationStart();
        void onAnimationEnd();
        void onAnimationCancel();
    }
    
    private AnimationUpdateListener updateListener;
    private AnimationStateListener stateListener;
    
    public SliderAnimationManager() {
        init();
    }
    
    private void init() {
        animationEnabled = true;
        animationDuration = SliderConstants.ANIMATION_DURATION.getIntValue();
        interpolator = new AccelerateDecelerateInterpolator();
        
        setupAnimator();
    }
    
    private void setupAnimator() {
        valueAnimator = new ValueAnimator();
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.setDuration(animationDuration);
        
        valueAnimator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            if (updateListener != null) {
                updateListener.onAnimationUpdate(animatedValue);
            }
        });
        
        valueAnimator.addListener(new ValueAnimator.AnimatorListener() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {
                if (stateListener != null) {
                    stateListener.onAnimationStart();
                }
            }
            
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (stateListener != null) {
                    stateListener.onAnimationEnd();
                }
            }
            
            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                if (stateListener != null) {
                    stateListener.onAnimationCancel();
                }
            }
            
            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                // 不需要实现
            }
        });
    }
    
    /**
     * 启动数值动画
     * @param fromValue 起始值
     * @param toValue 目标值
     */
    public void animateValue(int fromValue, int toValue) {
        if (!animationEnabled || fromValue == toValue) {
            return;
        }
        
        if (valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
        
        valueAnimator.setIntValues(fromValue, toValue);
        valueAnimator.start();
    }
    
    /**
     * 取消当前动画
     */
    public void cancelAnimation() {
        if (valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
    }
    
    /**
     * 检查动画是否正在运行
     * @return 是否运行中
     */
    public boolean isAnimationRunning() {
        return valueAnimator.isRunning();
    }
    
    /**
     * 设置动画开启状态
     * @param enabled 是否开启
     */
    public void setAnimationEnabled(boolean enabled) {
        this.animationEnabled = enabled;
        if (!enabled && valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
    }
    
    /**
     * 获取动画开启状态
     * @return 是否开启
     */
    public boolean isAnimationEnabled() {
        return animationEnabled;
    }
    
    /**
     * 设置动画持续时间
     * @param duration 持续时间（毫秒）
     */
    public void setAnimationDuration(int duration) {
        if (duration < SliderConstants.MIN_ANIMATION_DURATION.getIntValue()) {
            duration = SliderConstants.MIN_ANIMATION_DURATION.getIntValue();
        }
        
        this.animationDuration = duration;
        valueAnimator.setDuration(duration);
    }
    
    /**
     * 获取动画持续时间
     * @return 持续时间（毫秒）
     */
    public int getAnimationDuration() {
        return animationDuration;
    }
    
    /**
     * 设置动画插值器
     * @param interpolator 插值器
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
        valueAnimator.setInterpolator(interpolator);
    }
    
    /**
     * 获取动画插值器
     * @return 插值器
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }
    
    /**
     * 设置动画更新监听器
     * @param listener 监听器
     */
    public void setAnimationUpdateListener(AnimationUpdateListener listener) {
        this.updateListener = listener;
    }
    
    /**
     * 设置动画状态监听器
     * @param listener 监听器
     */
    public void setAnimationStateListener(AnimationStateListener listener) {
        this.stateListener = listener;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (valueAnimator != null) {
            if (valueAnimator.isRunning()) {
                valueAnimator.cancel();
            }
            valueAnimator.removeAllUpdateListeners();
            valueAnimator.removeAllListeners();
        }
        updateListener = null;
        stateListener = null;
    }
}

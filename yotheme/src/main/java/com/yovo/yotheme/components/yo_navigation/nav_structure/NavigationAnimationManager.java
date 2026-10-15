package com.yovo.yotheme.components.yo_navigation.nav_structure;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import com.yovo.yotheme.constants.NavigationConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航栏动画管理器
 * @note 专门负责导航项的各种动画效果，包括状态切换动画、按压动画等
 * @note 单一职责：只处理动画相关的逻辑，不涉及状态管理
 */
public class NavigationAnimationManager {
    
    /**
     * 动画配置类
     */
    public static class AnimationConfig {
        public long colorTransitionDuration = NavigationConstants.ANIMATION_DURATION_NORMAL.getIntValue();
        public long scaleAnimationDuration = NavigationConstants.ANIMATION_DURATION_FAST.getIntValue();
        public long badgeAnimationDuration = NavigationConstants.BADGE_ANIMATION_DURATION_MS.getIntValue();
        public float pressedScale = NavigationConstants.SCALE_PRESSED.getFloatValue();
        public float normalScale = NavigationConstants.SCALE_NORMAL.getFloatValue();
        public boolean enableColorAnimation = true;
        public boolean enableScaleAnimation = true;
        public boolean enableBadgeAnimation = true;
        
        public AnimationConfig setColorTransitionDuration(long duration) {
            this.colorTransitionDuration = Math.max(0, duration);
            return this;
        }
        
        public AnimationConfig setScaleAnimationDuration(long duration) {
            this.scaleAnimationDuration = Math.max(0, duration);
            return this;
        }
        
        public AnimationConfig setBadgeAnimationDuration(long duration) {
            this.badgeAnimationDuration = Math.max(0, duration);
            return this;
        }
        
        public AnimationConfig setPressedScale(float scale) {
            this.pressedScale = Math.max(0.1f, Math.min(2.0f, scale));
            return this;
        }
        
        public AnimationConfig setNormalScale(float scale) {
            this.normalScale = Math.max(0.1f, Math.min(2.0f, scale));
            return this;
        }
        
        public AnimationConfig setEnableColorAnimation(boolean enable) {
            this.enableColorAnimation = enable;
            return this;
        }
        
        public AnimationConfig setEnableScaleAnimation(boolean enable) {
            this.enableScaleAnimation = enable;
            return this;
        }
        
        public AnimationConfig setEnableBadgeAnimation(boolean enable) {
            this.enableBadgeAnimation = enable;
            return this;
        }
    }
    
    /**
     * 动画监听器
     */
    public interface AnimationListener {
        void onAnimationStart();
        void onAnimationEnd();
        void onAnimationProgress(float progress);
    }
    
    private View targetView;
    private AnimationConfig config;
    private ValueAnimator colorAnimator;
    private AnimatorSet scaleAnimator;
    private AnimatorSet badgeAnimator;
    private AnimationListener animationListener;
    
    // 当前动画状态
    private boolean isColorAnimating = false;
    private boolean isScaleAnimating = false;
    private boolean isBadgeAnimating = false;
    
    public NavigationAnimationManager(View targetView) {
        this(targetView, new AnimationConfig());
    }
    
    public NavigationAnimationManager(View targetView, AnimationConfig config) {
        this.targetView = targetView;
        this.config = config;
        initAnimators();
    }
    
    private void initAnimators() {
        initColorAnimator();
        initScaleAnimator();
        initBadgeAnimator();
    }
    
    /**
     * 初始化颜色过渡动画
     */
    private void initColorAnimator() {
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(config.colorTransitionDuration);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            notifyAnimationProgress(progress);
        });
    }
    
    /**
     * 初始化缩放动画
     */
    private void initScaleAnimator() {
        scaleAnimator = new AnimatorSet();
        scaleAnimator.setDuration(config.scaleAnimationDuration);
        scaleAnimator.setInterpolator(new DecelerateInterpolator());
    }
    
    /**
     * 初始化徽章动画
     */
    private void initBadgeAnimator() {
        badgeAnimator = new AnimatorSet();
        badgeAnimator.setDuration(config.badgeAnimationDuration);
        badgeAnimator.setInterpolator(new OvershootInterpolator(NavigationConstants.ANIMATION_BADGE_OVERSHOOT_TENSION.getFloatValue()));
    }
    
    /**
     * 设置动画配置
     * @param config 动画配置
     */
    public void setAnimationConfig(AnimationConfig config) {
        this.config = config;
        initAnimators();
    }
    
    /**
     * 设置动画监听器
     * @param listener 动画监听器
     */
    public void setAnimationListener(AnimationListener listener) {
        this.animationListener = listener;
    }
    
    /**
     * 动画到指定状态
     * @param targetState 目标状态
     */
    public void animateToState(NavigationStateManager.NavigationState targetState) {
        if (!config.enableColorAnimation) {
            return;
        }
        
        if (isColorAnimating) {
            colorAnimator.cancel();
        }
        
        isColorAnimating = true;
        notifyAnimationStart();
        
        colorAnimator.removeAllUpdateListeners();
        colorAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            
            // 通知子组件更新颜色过渡
            if (targetView instanceof NavigationItemContainer) {
                NavigationItemContainer container = (NavigationItemContainer) targetView;
                animateContainerColorTransition(container, targetState, progress);
            }
            
            notifyAnimationProgress(progress);
        });
        
		colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isColorAnimating = false;
				// 动画结束时提交最终状态，确保标题粗细与最终选中状态一致
				if (targetView instanceof NavigationItemContainer) {
					NavigationItemContainer container = (NavigationItemContainer) targetView;
					NavigationStateManager stateManager = container.getStateManager();
					if (stateManager != null && targetState != null) {
						stateManager.setState(targetState);
					}
				}
                notifyAnimationEnd();
            }
        });
        
        colorAnimator.start();
    }
    
    /**
     * 开始按压动画
     */
    public void startPressAnimation() {
        if (!config.enableScaleAnimation || isScaleAnimating) {
            return;
        }
        
        isScaleAnimating = true;
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(targetView, "scaleX", config.pressedScale);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(targetView, "scaleY", config.pressedScale);
        
        scaleAnimator = new AnimatorSet();
        scaleAnimator.playTogether(scaleX, scaleY);
        scaleAnimator.setDuration(config.scaleAnimationDuration);
        scaleAnimator.setInterpolator(new DecelerateInterpolator());
        scaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isScaleAnimating = false;
            }
        });
        
        scaleAnimator.start();
    }
    
    /**
     * 结束按压动画
     */
    public void endPressAnimation() {
        if (!config.enableScaleAnimation) {
            return;
        }
        
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        
        isScaleAnimating = true;
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(targetView, "scaleX", config.normalScale);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(targetView, "scaleY", config.normalScale);
        
        scaleAnimator = new AnimatorSet();
        scaleAnimator.playTogether(scaleX, scaleY);
        scaleAnimator.setDuration(config.scaleAnimationDuration);
        scaleAnimator.setInterpolator(new DecelerateInterpolator());
        scaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isScaleAnimating = false;
            }
        });
        
        scaleAnimator.start();
    }
    
    /**
     * 取消按压动画
     */
    public void cancelPressAnimation() {
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        
        targetView.setScaleX(config.normalScale);
        targetView.setScaleY(config.normalScale);
        isScaleAnimating = false;
    }
    
    /**
     * 开始徽章显示动画
     * @param badgeView 徽章视图
     */
    public void startBadgeShowAnimation(View badgeView) {
        if (!config.enableBadgeAnimation || badgeView == null || isBadgeAnimating) {
            return;
        }
        
        isBadgeAnimating = true;
        
        // 初始状态：缩放为0，透明
        badgeView.setScaleX(0f);
        badgeView.setScaleY(0f);
        badgeView.setAlpha(0f);
        badgeView.setVisibility(View.VISIBLE);
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(badgeView, "scaleX", 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(badgeView, "scaleY", 0f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(badgeView, "alpha", 0f, 1f);
        
        badgeAnimator = new AnimatorSet();
        badgeAnimator.playTogether(scaleX, scaleY, alpha);
        badgeAnimator.setDuration(config.badgeAnimationDuration);
        badgeAnimator.setInterpolator(new OvershootInterpolator(NavigationConstants.ANIMATION_BADGE_OVERSHOOT_TENSION.getFloatValue()));
        badgeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isBadgeAnimating = false;
            }
        });
        
        badgeAnimator.start();
    }
    
    /**
     * 开始徽章隐藏动画
     * @param badgeView 徽章视图
     */
    public void startBadgeHideAnimation(View badgeView) {
        if (!config.enableBadgeAnimation || badgeView == null || isBadgeAnimating) {
            if (badgeView != null) {
                badgeView.setVisibility(View.GONE);
            }
            return;
        }
        
        isBadgeAnimating = true;
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(badgeView, "scaleX", 1f, 0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(badgeView, "scaleY", 1f, 0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(badgeView, "alpha", 1f, 0f);
        
        badgeAnimator = new AnimatorSet();
        badgeAnimator.playTogether(scaleX, scaleY, alpha);
        badgeAnimator.setDuration(config.badgeAnimationDuration / NavigationConstants.BADGE_HIDE_DURATION_DIVISOR.getIntValue()); // 隐藏动画更快
        badgeAnimator.setInterpolator(new LinearInterpolator());
        badgeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                badgeView.setVisibility(View.GONE);
                isBadgeAnimating = false;
            }
        });
        
        badgeAnimator.start();
    }
    
    /**
     * 停止所有动画
     */
    public void stopAllAnimations() {
        if (colorAnimator != null && colorAnimator.isRunning()) {
            colorAnimator.cancel();
        }
        
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        
        if (badgeAnimator != null && badgeAnimator.isRunning()) {
            badgeAnimator.cancel();
        }
        
        isColorAnimating = false;
        isScaleAnimating = false;
        isBadgeAnimating = false;
    }
    
    /**
     * 是否有动画正在运行
     * @return 是否有动画
     */
    public boolean isAnimating() {
        return isColorAnimating || isScaleAnimating || isBadgeAnimating;
    }
    
    /**
     * 容器颜色过渡动画
     */
    private void animateContainerColorTransition(NavigationItemContainer container, 
                                               NavigationStateManager.NavigationState targetState, 
                                               float progress) {
        
        // 更新图标颜色过渡
        NavigationIcon iconComponent = container.getIconComponent();
        if (iconComponent != null) {
            NavigationIcon.IconConfig iconConfig = iconComponent.getIconConfig();
            int fromColor = targetState.isSelected ? iconConfig.normalColor : iconConfig.selectedColor;
            int toColor = targetState.isSelected ? iconConfig.selectedColor : iconConfig.normalColor;
            iconComponent.setColorTransition(fromColor, toColor, progress);
        }
        
        // 更新标题颜色过渡
        NavigationTitle titleComponent = container.getTitleComponent();
        if (titleComponent != null) {
            NavigationTitle.TitleConfig titleConfig = titleComponent.getTitleConfig();
            int fromColor = targetState.isSelected ? titleConfig.normalColor : titleConfig.selectedColor;
            int toColor = targetState.isSelected ? titleConfig.selectedColor : titleConfig.normalColor;
            titleComponent.setColorTransition(fromColor, toColor, progress);
        }
    }
    
    /**
     * 通知动画开始
     */
    private void notifyAnimationStart() {
        if (animationListener != null) {
            animationListener.onAnimationStart();
        }
    }
    
    /**
     * 通知动画结束
     */
    private void notifyAnimationEnd() {
        if (animationListener != null) {
            animationListener.onAnimationEnd();
        }
    }
    
    /**
     * 通知动画进度
     */
    private void notifyAnimationProgress(float progress) {
        if (animationListener != null) {
            animationListener.onAnimationProgress(progress);
        }
    }
    
    /**
     * 预设动画配置
     */
    public static class Presets {
        
        /**
         * 快速动画配置
         */
        public static AnimationConfig fast() {
            return new AnimationConfig()
                    .setColorTransitionDuration(150)
                    .setScaleAnimationDuration(100)
                    .setBadgeAnimationDuration(200);
        }
        
        /**
         * 慢速动画配置
         */
        public static AnimationConfig slow() {
            return new AnimationConfig()
                    .setColorTransitionDuration(500)
                    .setScaleAnimationDuration(300)
                    .setBadgeAnimationDuration(600);
        }
        
        /**
         * 无动画配置
         */
        public static AnimationConfig none() {
            return new AnimationConfig()
                    .setEnableColorAnimation(false)
                    .setEnableScaleAnimation(false)
                    .setEnableBadgeAnimation(false);
        }
        
        /**
         * 仅颜色动画配置
         */
        public static AnimationConfig colorOnly() {
            return new AnimationConfig()
                    .setEnableScaleAnimation(false)
                    .setEnableBadgeAnimation(false);
        }
        
        /**
         * 仅缩放动画配置
         */
        public static AnimationConfig scaleOnly() {
            return new AnimationConfig()
                    .setEnableColorAnimation(false)
                    .setEnableBadgeAnimation(false);
        }
    }
}

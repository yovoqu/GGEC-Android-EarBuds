package com.yovo.yotheme.components.yo_model_panel.panel_structure;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.yovo.yotheme.constants.ModalPanelConstants;
import com.yovo.yotheme.components.yo_model_panel.YoModalPanelInterface;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 面板动画管理器
 * @note 负责管理面板的显示、隐藏、拖拽等动画效果
 * 
 */
public class PanelAnimationManager {

    private ValueAnimator showAnimator;
    private ValueAnimator hideAnimator;
    private ValueAnimator bounceAnimator;
    
    private View panelView;
    private View maskView;
    private YoModalPanelInterface.PanelConfig config;
    private PanelStateManager stateManager;
    
    // 动画监听器
    private OnAnimationListener animationListener;

    public interface OnAnimationListener {
        void onShowStart();
        void onShowEnd();
        void onHideStart();
        void onHideEnd();
        void onBounceStart();
        void onBounceEnd();
    }

    public PanelAnimationManager(View panelView, View maskView, 
                                YoModalPanelInterface.PanelConfig config,
                                PanelStateManager stateManager) {
        this.panelView = panelView;
        this.maskView = maskView;
        this.config = config;
        this.stateManager = stateManager;
    }

    /**
     * 设置动画监听器
     */
    public void setAnimationListener(OnAnimationListener listener) {
        this.animationListener = listener;
    }

    /**
     * 显示动画
     */
    public void animateShow() {
        if (panelView == null || stateManager == null) return;
        
        // 取消正在进行的动画
        cancelAllAnimations();
        
        // 更新状态
        stateManager.setState(YoModalPanelInterface.PanelState.SHOWING);
        stateManager.setAnimating(true);
        
        // 通知监听器
        if (animationListener != null) {
            animationListener.onShowStart();
        }
        
        // 确保起始位置
        setupInitialPosition();
        
        // 确保面板可见（可能在布局完成前被设为INVISIBLE）
        if (panelView.getVisibility() != View.VISIBLE) {
            panelView.setVisibility(View.VISIBLE);
        }
        
        // 创建动画
        showAnimator = ValueAnimator.ofFloat(0f, 1f);
        showAnimator.setDuration(config.animationDuration);
        showAnimator.setInterpolator(new DecelerateInterpolator());
        
        float startTranslation = getHideTranslation();
        
        showAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            
            // 面板移动
            float currentTranslation = startTranslation * (1f - progress);
            panelView.setTranslationY(currentTranslation);
            
            // 遮罩渐入
            if (maskView != null) {
                maskView.setAlpha(progress * config.maskAlpha);
            }
        });
        
        showAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stateManager.setState(YoModalPanelInterface.PanelState.SHOWN);
                stateManager.setAnimating(false);
                
                if (animationListener != null) {
                    animationListener.onShowEnd();
                }
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                stateManager.setAnimating(false);
            }
        });
        
        showAnimator.start();
    }

    /**
     * 隐藏动画
     */
    public void animateHide() {
        if (panelView == null || stateManager == null) return;
        
        // 取消正在进行的动画
        cancelAllAnimations();
        
        // 更新状态
        stateManager.setState(YoModalPanelInterface.PanelState.HIDING);
        stateManager.setAnimating(true);
        
        // 通知监听器
        if (animationListener != null) {
            animationListener.onHideStart();
        }
        
        // 创建动画
        hideAnimator = ValueAnimator.ofFloat(0f, 1f);
        hideAnimator.setDuration(config.animationDuration);
        hideAnimator.setInterpolator(new DecelerateInterpolator());
        
        float startTranslation = panelView.getTranslationY();
        float targetTranslation = getHideTranslation();
        float startAlpha = maskView != null ? maskView.getAlpha() : 0f;
        
        hideAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            
            // 面板移动
            float currentTranslation = startTranslation + (targetTranslation - startTranslation) * progress;
            panelView.setTranslationY(currentTranslation);
            
            // 遮罩渐出
            if (maskView != null) {
                maskView.setAlpha(startAlpha * (1f - progress));
            }
        });
        
        hideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stateManager.setState(YoModalPanelInterface.PanelState.HIDDEN);
                stateManager.setAnimating(false);
                
                if (animationListener != null) {
                    animationListener.onHideEnd();
                }
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                stateManager.setAnimating(false);
            }
        });
        
        hideAnimator.start();
    }

    /**
     * 回弹到显示位置
     */
    public void animateBounceToShow() {
        if (panelView == null || stateManager == null) return;
        
        // 取消正在进行的动画
        cancelAllAnimations();
        
        // 通知监听器
        if (animationListener != null) {
            animationListener.onBounceStart();
        }
        
        // 创建回弹动画
        bounceAnimator = ValueAnimator.ofFloat(panelView.getTranslationY(), 0f);
        bounceAnimator.setDuration(ModalPanelConstants.BOUNCE_ANIMATION_DURATION.getLongValue());
        bounceAnimator.setInterpolator(new OvershootInterpolator(
            ModalPanelConstants.BOUNCE_OVERSHOOT_TENSION.getFloatValue()
        ));
        
        float startAlpha = maskView != null ? maskView.getAlpha() : 0f;
        
        bounceAnimator.addUpdateListener(animation -> {
            float translationY = (float) animation.getAnimatedValue();
            panelView.setTranslationY(translationY);
            
            // 恢复遮罩透明度
            if (maskView != null) {
                float progress = 1f - Math.abs(translationY) / Math.abs(getHideTranslation());
                maskView.setAlpha(progress * config.maskAlpha);
            }
        });
        
        bounceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stateManager.setState(YoModalPanelInterface.PanelState.SHOWN);
                
                if (animationListener != null) {
                    animationListener.onBounceEnd();
                }
            }
        });
        
        bounceAnimator.start();
    }

    /**
     * 立即隐藏（无动画）
     */
    public void hideImmediately() {
        cancelAllAnimations();
        
        panelView.setTranslationY(getHideTranslation());
        if (maskView != null) {
            maskView.setAlpha(0f);
        }
        
        stateManager.setState(YoModalPanelInterface.PanelState.HIDDEN);
        stateManager.setAnimating(false);
    }

    /**
     * 立即显示（无动画）
     */
    public void showImmediately() {
        cancelAllAnimations();
        
        panelView.setTranslationY(0f);
        panelView.setVisibility(View.VISIBLE);
        if (maskView != null) {
            maskView.setAlpha(config.maskAlpha);
        }
        
        stateManager.setState(YoModalPanelInterface.PanelState.SHOWN);
        stateManager.setAnimating(false);
    }

    /**
     * 更新拖拽位置
     */
    public void updateDragPosition(float translationY, float progress) {
        if (panelView == null) return;
        
        panelView.setTranslationY(translationY);
        
        // 更新遮罩透明度
        if (maskView != null) {
            float alpha = config.maskAlpha * (1f - progress * ModalPanelConstants.MASK_DRAG_FADE_FACTOR.getFloatValue());
            maskView.setAlpha(Math.max(0f, alpha));
        }
    }

    /**
     * 取消所有动画
     */
    public void cancelAllAnimations() {
        if (showAnimator != null && showAnimator.isRunning()) {
            showAnimator.cancel();
        }
        if (hideAnimator != null && hideAnimator.isRunning()) {
            hideAnimator.cancel();
        }
        if (bounceAnimator != null && bounceAnimator.isRunning()) {
            bounceAnimator.cancel();
        }
        
        if (stateManager != null) {
            stateManager.setAnimating(false);
        }
    }

    /**
     * 检查是否有动画正在运行
     */
    public boolean isAnimating() {
        return (showAnimator != null && showAnimator.isRunning()) ||
               (hideAnimator != null && hideAnimator.isRunning()) ||
               (bounceAnimator != null && bounceAnimator.isRunning());
    }

    /**
     * 设置初始位置
     */
    private void setupInitialPosition() {
        if (panelView == null) return;
        
        // 确保面板在隐藏位置
        float hideTranslation = getHideTranslation();
        if (panelView.getTranslationY() == 0f) {
            panelView.setTranslationY(hideTranslation);
        }
        
        // 确保遮罩透明
        if (maskView != null && maskView.getAlpha() > 0f) {
            maskView.setAlpha(0f);
        }
    }

    /**
     * 获取隐藏时的偏移量
     */
    private float getHideTranslation() {
        if (panelView == null) return 0f;
        
        int panelHeight = panelView.getHeight();
        if (panelHeight == 0) {
            // 如果还没有测量，使用屏幕高度的估算值
            int screenHeight = panelView.getContext().getResources().getDisplayMetrics().heightPixels;
            panelHeight = (int) (screenHeight * config.heightRatio);
        }
        
        return config.direction == YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP 
            ? panelHeight : -panelHeight;
    }

    /**
     * 更新配置
     */
    public void updateConfig(YoModalPanelInterface.PanelConfig config) {
        this.config = config;
    }

    /**
     * 销毁动画管理器
     */
    public void destroy() {
        cancelAllAnimations();
        animationListener = null;
        panelView = null;
        maskView = null;
        config = null;
        stateManager = null;
    }
}

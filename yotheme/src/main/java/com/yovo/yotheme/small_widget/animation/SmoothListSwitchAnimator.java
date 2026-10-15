package com.yovo.yotheme.small_widget.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * @author tingfeng.yang
 * @date 2025-11-04
 * @description 平滑列表切换动画工具
 * @note 负责实现列表间的平滑切换效果，包含：
 *       1. 滑动切换：支持左右滑动手势切换列表
 *       2. 淡入淡出：列表切换时的透明度过渡
 *       3. 收紧展开：列表项在切换过程中的高度动画
 *       4. 边界控制：确保动画不超过圆角弧度边界
 */
public class SmoothListSwitchAnimator {

    private final ViewGroup containerView;
    private View currentListView;
    private View nextListView;
    
    private AnimatorSet switchAnimatorSet;
    private long durationMs = 350L; // 默认动画时长
    private Interpolator interpolator = new DecelerateInterpolator(1.5f); // 减速插值器
    
    private OnListSwitchListener listener;
    
    // 圆角半径（dp），用于边界裁剪
    private float cornerRadiusDp = 16f;
    private int cornerRadiusPx;
    
    // 是否启用高度动画
    private boolean enableHeightAnimation = true;
    
    // 是否启用边界裁剪
    private boolean enableBoundaryClipping = true;
    
    // 滑动方向
    public enum SwipeDirection {
        LEFT,  // 向左滑动（显示下一个）
        RIGHT  // 向右滑动（显示上一个）
    }

    public SmoothListSwitchAnimator(ViewGroup containerView) {
        this.containerView = containerView;
        this.cornerRadiusPx = dp2px(containerView.getContext(), cornerRadiusDp);
        initBoundaryClipping();
    }
    
    /**
     * 初始化边界裁剪
     */
    private void initBoundaryClipping() {
        if (enableBoundaryClipping && containerView != null) {
            containerView.setClipToOutline(true);
            containerView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
        }
    }
    
    /**
     * dp转px
     */
    private int dp2px(android.content.Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 设置动画时长
     */
    public SmoothListSwitchAnimator setDuration(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        return this;
    }

    /**
     * 设置插值器
     */
    public SmoothListSwitchAnimator setInterpolator(Interpolator interpolator) {
        if (interpolator != null) {
            this.interpolator = interpolator;
        }
        return this;
    }

    /**
     * 设置列表切换监听器
     */
    public SmoothListSwitchAnimator setOnListSwitchListener(OnListSwitchListener listener) {
        this.listener = listener;
        return this;
    }
    
    /**
     * 设置圆角半径
     */
    public SmoothListSwitchAnimator setCornerRadius(float radiusDp) {
        this.cornerRadiusDp = radiusDp;
        this.cornerRadiusPx = dp2px(containerView.getContext(), radiusDp);
        initBoundaryClipping();
        return this;
    }
    
    /**
     * 设置是否启用高度动画
     */
    public SmoothListSwitchAnimator setEnableHeightAnimation(boolean enable) {
        this.enableHeightAnimation = enable;
        return this;
    }
    
    /**
     * 设置是否启用边界裁剪
     */
    public SmoothListSwitchAnimator setEnableBoundaryClipping(boolean enable) {
        this.enableBoundaryClipping = enable;
        initBoundaryClipping();
        return this;
    }

    /**
     * 执行列表切换动画（带方向）
     * @param from 当前显示的列表
     * @param to 要切换到的列表
     * @param direction 滑动方向
     */
    public void switchList(View from, View to, SwipeDirection direction) {
        if (from == null || to == null || containerView == null) {
            return;
        }

        // 如果正在运行动画，先取消
        if (switchAnimatorSet != null && switchAnimatorSet.isRunning()) {
            switchAnimatorSet.cancel();
        }

        this.currentListView = from;
        this.nextListView = to;

        // 确保两个列表都在容器中
        if (to.getParent() == null) {
            containerView.addView(to);
        }

        // 测量目标列表的高度
        to.measure(
            View.MeasureSpec.makeMeasureSpec(containerView.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int targetHeight = to.getMeasuredHeight();
        int currentHeight = from.getHeight();

        // 设置初始状态
        int containerWidth = containerView.getWidth();
        float translationStart = direction == SwipeDirection.LEFT ? containerWidth : -containerWidth;
        
        to.setTranslationX(translationStart);
        to.setAlpha(0f);
        to.setVisibility(View.VISIBLE);

        // 创建动画集合
        switchAnimatorSet = new AnimatorSet();
        
        // 当前列表的退出动画
        ValueAnimator currentExitAnimator = createListExitAnimator(from, direction);
        
        // 新列表的进入动画
        ValueAnimator nextEnterAnimator = createListEnterAnimator(to, direction);
        
        // 如果启用高度动画且高度不同，添加容器高度动画
        if (enableHeightAnimation && currentHeight != targetHeight && currentHeight > 0) {
            ValueAnimator heightAnimator = createHeightAnimator(currentHeight, targetHeight);
            switchAnimatorSet.playTogether(currentExitAnimator, nextEnterAnimator, heightAnimator);
        } else {
            switchAnimatorSet.playTogether(currentExitAnimator, nextEnterAnimator);
        }
        
        switchAnimatorSet.setDuration(durationMs);
        switchAnimatorSet.setInterpolator(interpolator);
        
        switchAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (listener != null) {
                    listener.onSwitchStart();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // 隐藏旧列表
                from.setVisibility(View.GONE);
                from.setTranslationX(0);
                from.setAlpha(1f);
                
                // 确保新列表完全显示
                to.setTranslationX(0);
                to.setAlpha(1f);
                to.setVisibility(View.VISIBLE);
                
                // 恢复容器高度为wrap_content
                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    containerView.setLayoutParams(params);
                }
                
                if (listener != null) {
                    listener.onSwitchEnd();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // 重置状态
                from.setTranslationX(0);
                from.setAlpha(1f);
                to.setTranslationX(0);
                to.setAlpha(1f);
                
                // 恢复容器高度
                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    containerView.setLayoutParams(params);
                }
            }
        });
        
        switchAnimatorSet.start();
    }

    /**
     * 创建容器高度动画
     */
    private ValueAnimator createHeightAnimator(int startHeight, int endHeight) {
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, endHeight);
        
        animator.addUpdateListener(animation -> {
            int animatedHeight = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams params = containerView.getLayoutParams();
            if (params != null) {
                params.height = animatedHeight;
                containerView.setLayoutParams(params);
            }
        });
        
        return animator;
    }

    /**
     * 创建列表退出动画
     */
    private ValueAnimator createListExitAnimator(View view, SwipeDirection direction) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        
        final float startTranslationX = view.getTranslationX();
        final float endTranslationX = direction == SwipeDirection.LEFT 
            ? -containerView.getWidth() 
            : containerView.getWidth();
        
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            
            // 平移动画
            float translationX = startTranslationX + (endTranslationX - startTranslationX) * fraction;
            view.setTranslationX(translationX);
            
            // 透明度渐变（从1到0）
            view.setAlpha(1f - fraction * 0.5f); // 最低不会完全透明，保持0.5
        });
        
        return animator;
    }

    /**
     * 创建列表进入动画
     */
    private ValueAnimator createListEnterAnimator(View view, SwipeDirection direction) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        
        final float startTranslationX = view.getTranslationX();
        final float endTranslationX = 0f;
        
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            
            // 平移动画
            float translationX = startTranslationX + (endTranslationX - startTranslationX) * fraction;
            view.setTranslationX(translationX);
            
            // 透明度渐变（从0到1）
            view.setAlpha(fraction);
        });
        
        return animator;
    }

    /**
     * 快速切换（无方向动画，仅淡入淡出）
     * @param from 当前显示的列表
     * @param to 要切换到的列表
     */
    public void switchListFade(View from, View to) {
        if (from == null || to == null || containerView == null) {
            return;
        }

        // 如果正在运行动画，先取消
        if (switchAnimatorSet != null && switchAnimatorSet.isRunning()) {
            switchAnimatorSet.cancel();
        }

        this.currentListView = from;
        this.nextListView = to;

        // 确保两个列表都在容器中
        if (to.getParent() == null) {
            containerView.addView(to);
        }

        // 测量目标列表的高度
        to.measure(
            View.MeasureSpec.makeMeasureSpec(containerView.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int targetHeight = to.getMeasuredHeight();
        int currentHeight = from.getHeight();

        // 设置初始状态
        to.setAlpha(0f);
        to.setVisibility(View.VISIBLE);
        to.setTranslationX(0);

        // 创建淡入淡出动画
        ValueAnimator fadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeAnimator.setDuration(durationMs / 2); // 淡入淡出时间减半
        fadeAnimator.setInterpolator(interpolator);
        
        fadeAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            from.setAlpha(1f - fraction);
            to.setAlpha(fraction);
        });
        
        switchAnimatorSet = new AnimatorSet();
        
        // 如果启用高度动画且高度不同，添加容器高度动画
        if (enableHeightAnimation && currentHeight != targetHeight && currentHeight > 0) {
            ValueAnimator heightAnimator = createHeightAnimator(currentHeight, targetHeight);
            heightAnimator.setDuration(durationMs / 2);
            switchAnimatorSet.playTogether(fadeAnimator, heightAnimator);
        } else {
            switchAnimatorSet.play(fadeAnimator);
        }
        
        switchAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (listener != null) {
                    listener.onSwitchStart();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                from.setVisibility(View.GONE);
                from.setAlpha(1f);
                to.setAlpha(1f);
                
                // 恢复容器高度为wrap_content
                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    containerView.setLayoutParams(params);
                }
                
                if (listener != null) {
                    listener.onSwitchEnd();
                }
            }
        });
        
        switchAnimatorSet.start();
    }

    /**
     * 是否正在运行动画
     */
    public boolean isRunning() {
        return switchAnimatorSet != null && switchAnimatorSet.isRunning();
    }

    /**
     * 取消动画
     */
    public void cancel() {
        if (switchAnimatorSet != null && switchAnimatorSet.isRunning()) {
            switchAnimatorSet.cancel();
        }
    }

    /**
     * 列表切换监听器接口
     */
    public interface OnListSwitchListener {
        /**
         * 切换开始
         */
        void onSwitchStart();

        /**
         * 切换结束
         */
        void onSwitchEnd();
    }
}


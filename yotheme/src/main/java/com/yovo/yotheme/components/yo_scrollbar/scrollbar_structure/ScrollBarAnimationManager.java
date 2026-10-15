package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.ScrollBarConstants;

/**
 * 滚动条显隐淡入淡出动画。
 */
public final class ScrollBarAnimationManager {

    public interface Callback {
        void onAlphaChanged(float alpha);

        void onVisibilityChanged(boolean visible);
    }

    private final Callback callback;
    private final ValueAnimator fadeInAnimator;
    private final ValueAnimator fadeOutAnimator;
    private float currentAlpha;

    public ScrollBarAnimationManager(@NonNull Callback callback) {
        this.callback = callback;
        currentAlpha = ScrollBarConstants.ZERO_ALPHA.getFloatValue();

        fadeInAnimator = ValueAnimator.ofFloat(
                ScrollBarConstants.ZERO_ALPHA.getFloatValue(),
                ScrollBarConstants.FULL_ALPHA.getFloatValue());
        fadeInAnimator.setDuration(ScrollBarConstants.ANIMATION_DURATION_FADE_IN.getIntValue());
        fadeInAnimator.setInterpolator(new DecelerateInterpolator());
        fadeInAnimator.addUpdateListener(animation -> applyAlpha((float) animation.getAnimatedValue()));

        fadeOutAnimator = ValueAnimator.ofFloat(
                ScrollBarConstants.FULL_ALPHA.getFloatValue(),
                ScrollBarConstants.ZERO_ALPHA.getFloatValue());
        fadeOutAnimator.setDuration(ScrollBarConstants.ANIMATION_DURATION_FADE_OUT.getIntValue());
        fadeOutAnimator.setInterpolator(new DecelerateInterpolator());
        fadeOutAnimator.addUpdateListener(animation -> applyAlpha((float) animation.getAnimatedValue()));
        fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                callback.onVisibilityChanged(false);
            }
        });
    }

    public float getCurrentAlpha() {
        return currentAlpha;
    }

    public void showImmediate(float targetAlpha) {
        cancelAll();
        applyAlpha(targetAlpha);
        callback.onVisibilityChanged(targetAlpha > ScrollBarConstants.ZERO_ALPHA.getFloatValue());
    }

    public void fadeIn(float targetAlpha) {
        if (fadeOutAnimator.isRunning()) {
            fadeOutAnimator.cancel();
        }
        fadeInAnimator.setFloatValues(currentAlpha, targetAlpha);
        fadeInAnimator.start();
        callback.onVisibilityChanged(true);
    }

    public void fadeOut() {
        if (fadeInAnimator.isRunning()) {
            fadeInAnimator.cancel();
        }
        fadeOutAnimator.setFloatValues(currentAlpha, ScrollBarConstants.ZERO_ALPHA.getFloatValue());
        fadeOutAnimator.start();
    }

    public void cancelAll() {
        if (fadeInAnimator.isRunning()) {
            fadeInAnimator.cancel();
        }
        if (fadeOutAnimator.isRunning()) {
            fadeOutAnimator.cancel();
        }
    }

    public void cleanup() {
        cancelAll();
    }

    private void applyAlpha(float alpha) {
        currentAlpha = alpha;
        callback.onAlphaChanged(alpha);
    }
}

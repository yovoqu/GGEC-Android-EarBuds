package com.yovo.yotheme.components.yo_loading.loading_structure;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import com.yovo.yotheme.constants.LoadingConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 旋转动画管理
 */
public final class LoadingAnimationManager {

    public interface Callback {
        void onRotationChanged(float rotationDegrees);
    }

    private final ValueAnimator animator;
    private Callback callback;
    private float rotationDegrees;

    public LoadingAnimationManager() {
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(LoadingConstants.ROTATION_DURATION_MS.getIntValue());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            rotationDegrees = (float) animation.getAnimatedValue();
            if (callback != null) {
                callback.onRotationChanged(rotationDegrees);
            }
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        if (!animator.isStarted()) {
            animator.start();
        }
    }

    public void stop() {
        animator.cancel();
        rotationDegrees = 0f;
    }

    public float getRotationDegrees() {
        return rotationDegrees;
    }
}

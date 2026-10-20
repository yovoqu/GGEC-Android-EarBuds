package com.ggec.earbuds.ui.ai.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * 三个依次起伏的小圆点，表示"已发出请求、还没收到第一个字"。只在可见时才跑动画。
 */
public final class AiTypingDotsView extends View {

    private static final int DOTS = 3;
    private static final long CYCLE_MILLIS = 1100L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final float gap;
    private ValueAnimator animator;
    private float phase;

    public AiTypingDotsView(@NonNull Context context, @ColorInt int color, float density) {
        super(context);
        paint.setColor(color);
        radius = 3.5f * density;
        gap = 6f * density;
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) Math.ceil(DOTS * radius * 2 + (DOTS - 1) * gap);
        int height = (int) Math.ceil(radius * 2 + 2 * radius);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cy = getHeight() / 2f;
        float x = radius;
        for (int i = 0; i < DOTS; i++) {
            float t = phase - i * 0.16f;
            t -= (float) Math.floor(t);
            float wave = (float) Math.max(0, Math.sin(Math.PI * t * 1.6));
            paint.setAlpha((int) (255 * (0.35f + 0.65f * wave)));
            canvas.drawCircle(x, cy - radius * 0.8f * wave, radius * (0.8f + 0.25f * wave), paint);
            x += radius * 2 + gap;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    private void updateAnimation() {
        if (isAttachedToWindow() && getVisibility() == VISIBLE) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(CYCLE_MILLIS);
                animator.setInterpolator(new LinearInterpolator());
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.addUpdateListener(animation -> {
                    phase = (float) animation.getAnimatedValue();
                    invalidate();
                });
                animator.start();
            }
        } else {
            stopAnimation();
        }
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}

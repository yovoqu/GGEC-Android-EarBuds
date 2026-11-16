package com.ggec.earbuds.ui.device.record;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceTheme;

/**
 * 实时录音动态音频波形指示器组件。
 */
public final class WaveformView extends View {

    private static final int BAR_COUNT = 7;
    private static final float[] BAR_WEIGHTS = {0.35f, 0.65f, 0.9f, 1.0f, 0.85f, 0.6f, 0.35f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final DeviceTheme theme;

    private float currentAmplitude = 0f;
    private float animatedPhase = 0f;
    private boolean isRecording = false;
    private ValueAnimator phaseAnimator;

    public WaveformView(@NonNull Context context, @NonNull DeviceTheme theme) {
        super(context);
        this.theme = theme;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.brand);

        phaseAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        phaseAnimator.setDuration(1200);
        phaseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        phaseAnimator.setInterpolator(new LinearInterpolator());
        phaseAnimator.addUpdateListener(anim -> {
            if (isRecording) {
                animatedPhase = (float) anim.getAnimatedValue();
                invalidate();
            }
        });
    }

    public void setRecording(boolean recording) {
        if (this.isRecording != recording) {
            this.isRecording = recording;
            if (recording) {
                paint.setColor(theme.brand);
                if (!phaseAnimator.isStarted()) {
                    phaseAnimator.start();
                }
            } else {
                paint.setColor(theme.hint);
                phaseAnimator.cancel();
                currentAmplitude = 0f;
                invalidate();
            }
        }
    }

    public void setAmplitude(float amplitude) {
        this.currentAmplitude = Math.max(0.05f, Math.min(1.0f, amplitude));
        if (isRecording) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }

        float barWidth = theme.dpf(4f);
        float barSpacing = theme.dpf(6f);
        float totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * barSpacing;
        float startX = (width - totalWidth) / 2f;
        float centerY = height / 2f;
        float minBarHeight = theme.dpf(6f);
        float maxBarHeight = height * 0.85f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float weight = BAR_WEIGHTS[i];
            float barHeight;
            if (isRecording) {
                float wave = (float) Math.sin(animatedPhase + i * 0.8f);
                float ampFactor = currentAmplitude * (0.6f + 0.4f * wave);
                barHeight = minBarHeight + (maxBarHeight - minBarHeight) * weight * ampFactor;
            } else {
                barHeight = minBarHeight;
            }

            float left = startX + i * (barWidth + barSpacing);
            float top = centerY - barHeight / 2f;
            float right = left + barWidth;
            float bottom = centerY + barHeight / 2f;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (phaseAnimator != null) {
            phaseAnimator.cancel();
        }
    }
}

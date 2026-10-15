package com.yovo.yotheme.components.yo_loading.loading_structure;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.LoadingConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 图形绘制器
 */
public final class LoadingRenderer {

    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    public LoadingRenderer(@NonNull LoadingConfig config) {
        panelPaint.setColor(config.panelColor);
        panelPaint.setStyle(Paint.Style.FILL);

        ringTrackPaint.setColor(config.ringTrackColor);
        ringTrackPaint.setStyle(Paint.Style.STROKE);
        ringTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        ringPaint.setColor(config.ringColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setColor(config.dotColor);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void draw(@NonNull Canvas canvas, int width, int height, float rotationDegrees) {
        float size = Math.min(width, height);
        if (size <= 0f) {
            return;
        }
        float cornerRadius = size * LoadingConstants.PANEL_CORNER_FACTOR.getFloatValue();
        canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, panelPaint);

        float centerX = width / 2f;
        float centerY = height / 2f;
        float strokeWidth = Math.max(
                LoadingConstants.RING_STROKE_MIN_PX.getIntValue(),
                size * LoadingConstants.RING_STROKE_FACTOR.getFloatValue());
        ringTrackPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeWidth(strokeWidth);

        float radius = size * LoadingConstants.RING_RADIUS_FACTOR.getFloatValue();
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        canvas.drawArc(arcBounds, 0f, 360f, false, ringTrackPaint);
        canvas.drawArc(arcBounds,
                rotationDegrees,
                LoadingConstants.RING_SWEEP_DEGREES.getIntValue(),
                false,
                ringPaint);

        float dotRadius = size * LoadingConstants.DOT_RADIUS_FACTOR.getFloatValue();
        float dotY = centerY + radius + size * LoadingConstants.DOT_Y_OFFSET_FACTOR.getFloatValue();
        float gap = size * LoadingConstants.DOT_GAP_FACTOR.getFloatValue();
        drawDot(canvas, centerX - gap, dotY, dotRadius, rotationDegrees, 0);
        drawDot(canvas, centerX, dotY, dotRadius, rotationDegrees,
                LoadingConstants.DOT_PHASE_DEGREES.getIntValue());
        drawDot(canvas, centerX + gap, dotY, dotRadius, rotationDegrees,
                LoadingConstants.DOT_PHASE_DEGREES.getIntValue() * 2);
    }

    private void drawDot(Canvas canvas,
                         float cx,
                         float cy,
                         float radius,
                         float rotationDegrees,
                         int phaseDegrees) {
        float alphaPhase = (float) ((Math.sin(Math.toRadians(rotationDegrees + phaseDegrees)) + 1f) / 2f);
        dotPaint.setAlpha((int) (LoadingConstants.DOT_ALPHA_MIN.getIntValue()
                + alphaPhase * LoadingConstants.DOT_ALPHA_RANGE.getIntValue()));
        canvas.drawCircle(cx, cy, radius, dotPaint);
    }
}

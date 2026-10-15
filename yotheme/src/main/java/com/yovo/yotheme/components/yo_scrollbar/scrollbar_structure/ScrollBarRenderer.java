package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * 滚动条滑块绘制。
 */
public final class ScrollBarRenderer {

    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbRect = new RectF();
    @ColorInt
    private int thumbColor;

    public ScrollBarRenderer(@ColorInt int thumbColor) {
        thumbPaint.setStyle(Paint.Style.FILL);
        setThumbColor(thumbColor);
    }

    public void setThumbColor(@ColorInt int color) {
        thumbColor = color;
        thumbPaint.setColor(color);
    }

    @ColorInt
    public int getThumbColor() {
        return thumbColor;
    }

    public void drawThumb(@NonNull Canvas canvas,
                          @NonNull ScrollBarMetrics.ThumbGeometry geometry,
                          float alphaMultiplier) {
        if (!geometry.visible || alphaMultiplier <= 0f) {
            return;
        }
        thumbRect.set(geometry.left, geometry.top, geometry.right, geometry.bottom);
        int alpha = Math.max(0, Math.min(255, Math.round(alphaMultiplier * 255f)));
        thumbPaint.setAlpha(alpha);
        canvas.drawRoundRect(thumbRect, geometry.cornerRadius, geometry.cornerRadius, thumbPaint);
    }
}

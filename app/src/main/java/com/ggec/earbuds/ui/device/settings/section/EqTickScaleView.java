package com.ggec.earbuds.ui.device.settings.section;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

/** EQ 左侧刻度尺：只画短横刻度线（+6/0/-6 各主刻度更长）。 */
final class EqTickScaleView extends View {

    private static final int SEGMENTS = 12;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    EqTickScaleView(@NonNull Context context, int color) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float step = height / (float) SEGMENTS;
        float inset = Math.max(1f, density);
        for (int index = 0; index <= SEGMENTS; index++) {
            float y = Math.min(height - inset, Math.max(inset, index * step));
            boolean major = index % (SEGMENTS / 2) == 0;
            float length = (major ? 6f : 4f) * density;
            canvas.drawLine(width - length, y, width, y, paint);
        }
    }
}

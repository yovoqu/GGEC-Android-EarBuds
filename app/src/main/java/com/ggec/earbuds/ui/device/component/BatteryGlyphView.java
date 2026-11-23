package com.ggec.earbuds.ui.device.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * 电量电池图形（公共组件层）：外框 + 右侧电芯帽 + 按电量填充的内条。
 *
 * <p>纯绘制原语，不持有业务：未知电量传 {@code level < 0}，只画空心外框、不填充。</p>
 */
public final class BatteryGlyphView extends View {

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final RectF cap = new RectF();
    private final RectF inner = new RectF();
    private final float density;

    private int level = -1;

    public BatteryGlyphView(@NonNull Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(1.0f));
        outline.setStrokeCap(Paint.Cap.ROUND);
        outline.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setState(int level, boolean charging, @ColorInt int tint) {
        this.level = level;
        outline.setColor(withAlpha(tint, level < 0 ? 0x5A : 0xB8));
        fill.setColor(tint);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = dp(0.7f);
        float capW = dp(2.0f);
        float capGap = dp(1.4f);
        float bodyRight = w - pad - capGap - capW;

        body.set(pad, pad, bodyRight, h - pad);
        float radius = Math.min(body.height() * 0.26f, dp(3.4f));
        canvas.drawRoundRect(body, radius, radius, outline);

        cap.set(bodyRight + capGap, h * 0.5f - h * 0.18f, w - pad, h * 0.5f + h * 0.18f);
        float capRadius = capW * 0.5f;
        canvas.drawRoundRect(cap, capRadius, capRadius, outline);

        if (level < 0) {
            return;
        }
        float innerPad = dp(2.1f);
        float available = body.width() - innerPad * 2f;
        if (available <= 0f) {
            return;
        }
        float ratio = Math.max(0, Math.min(100, level)) / 100f;
        float fillWidth = Math.max(dp(1.6f), available * ratio);
        inner.set(body.left + innerPad, body.top + innerPad,
                body.left + innerPad + Math.min(fillWidth, available), body.bottom - innerPad);
        float innerRadius = Math.min((body.height() - innerPad * 2f) * 0.3f, fillWidth * 0.45f);
        canvas.drawRoundRect(inner, innerRadius, innerRadius, fill);
    }

    private float dp(float value) {
        return value * density;
    }

    @ColorInt
    private static int withAlpha(@ColorInt int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

package com.yovo.yotheme.components.yo_input.input_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.InputConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 输入框圆角边框与背景绘制
 */
public class InputFieldRenderer {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private int fillColor;
    private int borderColorNormal;
    private int borderColorFocused;
    private float cornerRadiusPx;
    private float borderWidthPx;

    public InputFieldRenderer(Context context) {
        fillColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY);
        borderColorNormal = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_LIGHT_GRAY);
        borderColorFocused = ColorMapper.map(YoThemeColor.BRAND);

        float density = context.getResources().getDisplayMetrics().density;
        cornerRadiusPx = InputConstants.CORNER_RADIUS.getIntValue() * density;
        borderWidthPx = InputConstants.BORDER_WIDTH.getIntValue() * density;

        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidthPx);
    }

    public void draw(Canvas canvas, int width, int height, boolean focused) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float halfBorder = borderWidthPx * InputConstants.ROUNDING_FLOAT.getFloatValue();
        bounds.set(halfBorder, halfBorder, width - halfBorder, height - halfBorder);

        fillPaint.setColor(fillColor);
        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, fillPaint);

        borderPaint.setColor(focused ? borderColorFocused : borderColorNormal);
        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, borderPaint);
    }

    public void setFillColor(int color) {
        fillColor = color;
    }

    public void setBorderColorNormal(int color) {
        borderColorNormal = color;
    }

    public void setBorderColorFocused(int color) {
        borderColorFocused = color;
    }

    public void setCornerRadiusPx(float cornerRadiusPx) {
        this.cornerRadiusPx = cornerRadiusPx;
    }
}

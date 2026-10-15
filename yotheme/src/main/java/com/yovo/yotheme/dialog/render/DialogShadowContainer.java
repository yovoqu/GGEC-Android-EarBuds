package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 软件层阴影 fallback：在 padding 内绘制阴影，避免窗口裁剪
 */
public final class DialogShadowContainer extends FrameLayout {

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shadowBounds = new RectF();
    private final float cornerRadiusPx;
    private final float shadowDyPx;
    private final int verticalShadowInsetPx;
    private int horizontalContentMarginPx;

    public DialogShadowContainer(@NonNull Context context,
                                 float cornerRadiusPx,
                                 float shadowRadiusPx,
                                 float shadowDyPx,
                                 int shadowAlpha,
                                 int verticalShadowInsetPx,
                                 int horizontalContentMarginPx) {
        super(context);
        this.cornerRadiusPx = cornerRadiusPx;
        this.shadowDyPx = shadowDyPx;
        this.verticalShadowInsetPx = verticalShadowInsetPx;
        this.horizontalContentMarginPx = Math.max(0, horizontalContentMarginPx);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(1, 0, 0, 0));
        shadowPaint.setShadowLayer(
                shadowRadiusPx,
                0f,
                shadowDyPx,
                Color.argb(shadowAlpha, 0, 0, 0));
    }

    public void setHorizontalContentMarginPx(int horizontalContentMarginPx) {
        this.horizontalContentMarginPx = Math.max(0, horizontalContentMarginPx);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxChildWidth = Math.max(0, parentWidth - horizontalContentMarginPx * 2);
        int measuredHeight = verticalShadowInsetPx * 2;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                continue;
            }
            int childWidthSpec = MeasureSpec.makeMeasureSpec(maxChildWidth, MeasureSpec.EXACTLY);
            int childHeightSpec = getChildMeasureSpec(
                    heightMeasureSpec,
                    verticalShadowInsetPx * 2,
                    child.getLayoutParams().height);
            child.measure(childWidthSpec, childHeightSpec);
            measuredHeight = Math.max(
                    measuredHeight, child.getMeasuredHeight() + verticalShadowInsetPx * 2);
        }

        setMeasuredDimension(
                resolveSize(parentWidth, widthMeasureSpec),
                resolveSize(measuredHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int parentWidth = right - left;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                continue;
            }
            int childLeft = horizontalContentMarginPx;
            int childTop = verticalShadowInsetPx;
            int childRight = parentWidth - horizontalContentMarginPx;
            int childBottom = childTop + child.getMeasuredHeight();
            child.layout(childLeft, childTop, childRight, childBottom);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child != null && child.getVisibility() != GONE) {
            float x = event.getX();
            float y = event.getY();
            if (x >= child.getLeft()
                    && x <= child.getRight()
                    && y >= child.getTop()
                    && y <= child.getBottom()) {
                return false;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child != null && child.getVisibility() != GONE) {
            shadowBounds.set(
                    child.getLeft(),
                    child.getTop() - shadowDyPx,
                    child.getRight(),
                    child.getBottom() - shadowDyPx);
            canvas.drawRoundRect(shadowBounds, cornerRadiusPx, cornerRadiusPx, shadowPaint);
        }
        super.onDraw(canvas);
    }
}

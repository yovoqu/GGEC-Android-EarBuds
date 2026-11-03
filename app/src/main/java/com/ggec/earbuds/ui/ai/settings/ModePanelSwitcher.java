package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 两个模式面板的切换宿主：只测量/绘制/触摸"当前那一块"，另一块保持已测量状态并挪到屏外。
 *
 * <p>为什么不用 {@code GONE / VISIBLE}：那会在切换的那一帧触发一次整棵控件树的
 * measure + layout（面板里是 YoList/多层 LinearLayout），在低端机上就是一次几十毫秒的掉帧。
 * 这里两块都保留测量结果，切换只改 {@link #activeIndex}：宿主只重新上报自己的高度、
 * 只绘制当前块，另一块用 translation 挪出可视区，既不计入高度也不接收触摸。</p>
 */
final class ModePanelSwitcher extends FrameLayout {

    private int activeIndex = -1;

    public ModePanelSwitcher(@NonNull Context context) {
        super(context);
    }

    public ModePanelSwitcher(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    void addPanel(@NonNull View panel) {
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (activeIndex < 0) {
            activeIndex = 0;
            panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        applyTranslations();
    }

    void setActive(int index) {
        if (index < 0 || index >= getChildCount() || index == activeIndex) {
            return;
        }
        activeIndex = index;
        applyTranslations();
        requestLayout();
        invalidate();
    }

    void setActiveView(@NonNull View panel) {
        int index = indexOfChild(panel);
        if (index >= 0) {
            setActive(index);
        }
    }

    private void applyTranslations() {
        int width = getWidth();
        float offscreen = width > 0 ? -width * 2f : -10000f;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            boolean active = i == activeIndex;
            child.setTranslationX(active ? 0f : offscreen);
            child.setImportantForAccessibility(active
                    ? IMPORTANT_FOR_ACCESSIBILITY_YES
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyTranslations();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int count = getChildCount();
        if (activeIndex < 0 || activeIndex >= count) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        View active = getChildAt(activeIndex);
        int width = MeasureSpec.getSize(widthSpec);
        int childWidthSpec = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                : MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        active.measure(childWidthSpec, childHeightSpec);
        setMeasuredDimension(
                resolveSize(active.getMeasuredWidth(), widthSpec),
                resolveSize(active.getMeasuredHeight(), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        int count = getChildCount();
        if (activeIndex < 0 || activeIndex >= count) {
            super.dispatchDraw(canvas);
            return;
        }
        drawChild(canvas, getChildAt(activeIndex), getDrawingTime());
    }
}

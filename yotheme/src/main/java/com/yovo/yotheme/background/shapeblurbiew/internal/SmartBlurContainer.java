package com.yovo.yotheme.background.shapeblurbiew.internal;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2025-11-27
 * @description 智能模糊容器，确保模糊背景不影响整体高度测量
 */
public class SmartBlurContainer extends FrameLayout {
    
    private View blurView;
    
    public SmartBlurContainer(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }
    
    public void setBlurView(@Nullable View blurView) {
        this.blurView = blurView;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (blurView == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        
        // 只测量非模糊视图的子视图来决定容器大小
        int maxWidth = 0;
        int maxHeight = 0;
        
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE || child == blurView) {
                continue; // 跳过模糊视图
            }
            
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();
        
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        
        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(maxHeight, heightMeasureSpec)
        );
        
        // 单独测量模糊视图，使其匹配容器大小
        int blurWidthSpec = MeasureSpec.makeMeasureSpec(
            Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight()),
            MeasureSpec.EXACTLY
        );
        int blurHeightSpec = MeasureSpec.makeMeasureSpec(
            Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom()),
            MeasureSpec.EXACTLY
        );
        blurView.measure(blurWidthSpec, blurHeightSpec);
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();
        
        // 先布局模糊视图（填充整个容器）
        if (blurView != null && blurView.getVisibility() != GONE) {
            blurView.layout(
                paddingLeft,
                paddingTop,
                right - left - paddingRight,
                bottom - top - paddingBottom
            );
        }
        
        // 再布局其他子视图
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child == blurView || child.getVisibility() == GONE) {
                continue;
            }
            
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int childLeft = paddingLeft + lp.leftMargin;
            int childTop = paddingTop + lp.topMargin;
            int childRight = childLeft + child.getMeasuredWidth();
            int childBottom = childTop + child.getMeasuredHeight();
            child.layout(childLeft, childTop, childRight, childBottom);
        }
    }
}

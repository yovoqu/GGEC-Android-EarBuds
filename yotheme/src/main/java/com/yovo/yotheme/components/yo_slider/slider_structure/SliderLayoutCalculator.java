package com.yovo.yotheme.components.yo_slider.slider_structure;

import android.graphics.RectF;

import com.yovo.yotheme.constants.SliderConstants;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条布局计算器
 * @note 负责计算滑动条各部分的位置和尺寸
 */
public class SliderLayoutCalculator {
    
    // 视图尺寸
    private int viewWidth;
    private int viewHeight;
    
    // 组件尺寸
    private float trackHeight;
    private float thumbRadius;
    
    // 计算结果
    private RectF trackRect;
    private RectF progressRect;
    private float thumbCenterX;
    private float thumbCenterY;
    
    // 布局参数
    private float uniformSpacing; // 统一间距
    private float minThumbCenterX; // 滑块中心最小X位置
    private float maxThumbCenterX; // 滑块中心最大X位置
    
    public SliderLayoutCalculator() {
        trackRect = new RectF();
        progressRect = new RectF();
    }
    
    /**
     * 设置视图尺寸
     * @param width 宽度
     * @param height 高度
     */
    public void setViewSize(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
        this.thumbCenterY = height / 2f;
        calculateLayout();
    }
    
    /**
     * 设置组件尺寸
     * @param trackHeight 轨道高度
     * @param thumbRadius 滑块半径
     */
    public void setComponentSize(float trackHeight, float thumbRadius) {
        this.trackHeight = trackHeight;
        this.thumbRadius = thumbRadius;
        this.uniformSpacing = (trackHeight / 2f) - thumbRadius;
        calculateLayout();
    }
    
    /**
     * 计算基础布局
     */
    private void calculateLayout() {
        if (viewWidth <= 0 || viewHeight <= 0 || trackHeight <= 0) {
            return;
        }
        
        float centerY = viewHeight / 2f;
        float trackRadius = trackHeight / 2f;
        
        // 计算轨道矩形
        trackRect.set(trackRadius, centerY - trackRadius,
                     viewWidth - trackRadius, centerY + trackRadius);
        
        // 计算滑块中心点的有效范围
        minThumbCenterX = trackRadius + uniformSpacing + thumbRadius;
        maxThumbCenterX = viewWidth - trackRadius - uniformSpacing - thumbRadius;
        
        // 确保有效范围
        if (maxThumbCenterX < minThumbCenterX) {
            maxThumbCenterX = minThumbCenterX;
        }
    }
    
    /**
     * 根据进度计算滑块和进度条位置
     * @param progress 进度（0.0 - 1.0）
     */
    public void calculatePositions(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        
        // 计算滑块中心X位置
        thumbCenterX = minThumbCenterX + progress * (maxThumbCenterX - minThumbCenterX);
        
        // 计算进度条右边界（滑块右边界 + uniformSpacing）
        float progressRight = thumbCenterX + thumbRadius + uniformSpacing;
        progressRect.set(trackRect.left, trackRect.top, progressRight, trackRect.bottom);
    }
    
    /**
     * 根据触摸位置计算进度
     * @param touchX 触摸X坐标
     * @return 进度（0.0 - 1.0）
     */
    public float calculateProgressFromTouch(float touchX) {
        if (maxThumbCenterX <= minThumbCenterX) {
            return 0f;
        }
        
        float progress = (touchX - minThumbCenterX) / (maxThumbCenterX - minThumbCenterX);
        return Math.max(0f, Math.min(1f, progress));
    }
    
    /**
     * 检查触摸点是否在滑块区域内
     * @param touchX 触摸X坐标
     * @param touchY 触摸Y坐标
     * @param touchSlop 触摸容差
     * @return 是否在滑块区域内
     */
    public boolean isPointInThumbArea(float touchX, float touchY, float touchSlop) {
        float dx = touchX - thumbCenterX;
        float dy = touchY - thumbCenterY;
        float touchRadius = Math.max(thumbRadius, touchSlop);
        return Math.sqrt(dx * dx + dy * dy) <= touchRadius;
    }
    
    /**
     * 检查触摸点是否在轨道区域内
     * @param touchX 触摸X坐标
     * @param touchY 触摸Y坐标
     * @return 是否在轨道区域内
     */
    public boolean isPointInTrackArea(float touchX, float touchY) {
        return trackRect.contains(touchX, touchY);
    }
    
    /**
     * 获取轨道矩形区域
     * @return 轨道矩形
     */
    public RectF getTrackRect() {
        return new RectF(trackRect);
    }
    
    /**
     * 获取进度矩形区域
     * @return 进度矩形
     */
    public RectF getProgressRect() {
        return new RectF(progressRect);
    }
    
    /**
     * 获取滑块中心X坐标
     * @return X坐标
     */
    public float getThumbCenterX() {
        return thumbCenterX;
    }
    
    /**
     * 获取滑块中心Y坐标
     * @return Y坐标
     */
    public float getThumbCenterY() {
        return thumbCenterY;
    }
    
    /**
     * 获取轨道高度
     * @return 轨道高度
     */
    public float getTrackHeight() {
        return trackHeight;
    }
    
    /**
     * 获取滑块半径
     * @return 滑块半径
     */
    public float getThumbRadius() {
        return thumbRadius;
    }
    
    /**
     * 获取统一间距
     * @return 统一间距
     */
    public float getUniformSpacing() {
        return uniformSpacing;
    }
    
    /**
     * 获取滑块有效移动范围
     * @return 移动范围长度
     */
    public float getThumbMovementRange() {
        return Math.max(0, maxThumbCenterX - minThumbCenterX);
    }
    
    /**
     * 检查布局是否有效
     * @return 是否有效
     */
    public boolean isLayoutValid() {
        return viewWidth > 0 && viewHeight > 0 && 
               trackHeight > 0 && thumbRadius > 0 &&
               maxThumbCenterX >= minThumbCenterX;
    }
    
    /**
     * 获取推荐的最小视图宽度
     * @return 最小宽度
     */
    public float getRecommendedMinWidth() {
        float trackRadius = trackHeight / 2f;
        float minContentWidth = (trackRadius + uniformSpacing + thumbRadius) * 2;
        float minInteractionWidth = thumbRadius * 4; // 保证足够的交互区域
        return Math.max(minContentWidth, minInteractionWidth);
    }
    
    /**
     * 获取布局信息描述（用于调试）
     * @return 布局信息字符串
     */
    public String getLayoutInfo() {
        return String.format(
            "SliderLayout{view=[%dx%d], track=[%.1fx%.1f], thumb=[%.1f,%.1f,r%.1f], range=[%.1f-%.1f]}",
            viewWidth, viewHeight, trackRect.width(), trackRect.height(),
            thumbCenterX, thumbCenterY, thumbRadius,
            minThumbCenterX, maxThumbCenterX
        );
    }
}

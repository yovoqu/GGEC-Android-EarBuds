package com.yovo.yotheme.components.yo_slider.slider_structure;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条滑块渲染器
 * @note 负责绘制滑动条的滑块部分
 */
public class SliderThumbRenderer {
    
    private Paint thumbPaint;
    private int thumbColor;
    private float thumbCenterX;
    private float thumbCenterY;
    private float thumbRadius;
    
    public SliderThumbRenderer() {
        init();
    }
    
    private void init() {
        thumbColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY);
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(thumbColor);
        thumbPaint.setStyle(Paint.Style.FILL);
    }
    
    /**
     * 设置滑块位置
     * @param centerX 中心点X坐标
     * @param centerY 中心点Y坐标
     */
    public void setThumbPosition(float centerX, float centerY) {
        this.thumbCenterX = centerX;
        this.thumbCenterY = centerY;
    }
    
    /**
     * 设置滑块半径
     * @param radius 滑块半径
     */
    public void setThumbRadius(float radius) {
        this.thumbRadius = radius;
    }
    
    /**
     * 绘制滑块
     * @param canvas 画布
     */
    public void drawThumb(Canvas canvas) {
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius, thumbPaint);
    }
    
    /**
     * 设置滑块颜色
     * @param color 颜色值
     */
    public void setThumbColor(int color) {
        this.thumbColor = color;
        thumbPaint.setColor(color);
    }
    
    /**
     * 获取滑块颜色
     * @return 滑块颜色
     */
    public int getThumbColor() {
        return thumbColor;
    }
    
    /**
     * 获取滑块中心点X坐标
     * @return X坐标
     */
    public float getThumbCenterX() {
        return thumbCenterX;
    }
    
    /**
     * 获取滑块中心点Y坐标
     * @return Y坐标
     */
    public float getThumbCenterY() {
        return thumbCenterY;
    }
    
    /**
     * 获取滑块半径
     * @return 滑块半径
     */
    public float getThumbRadius() {
        return thumbRadius;
    }
    
    /**
     * 检查点是否在滑块范围内
     * @param x X坐标
     * @param y Y坐标
     * @return 是否在滑块内
     */
    public boolean isPointInThumb(float x, float y) {
        float dx = x - thumbCenterX;
        float dy = y - thumbCenterY;
        return Math.sqrt(dx * dx + dy * dy) <= thumbRadius;
    }
    
    /**
     * 检查点是否在滑块触摸范围内（扩展的触摸区域）
     * @param x X坐标
     * @param y Y坐标
     * @param touchSlop 触摸容差
     * @return 是否在触摸范围内
     */
    public boolean isPointInTouchArea(float x, float y, float touchSlop) {
        float dx = x - thumbCenterX;
        float dy = y - thumbCenterY;
        float touchRadius = Math.max(thumbRadius, touchSlop);
        return Math.sqrt(dx * dx + dy * dy) <= touchRadius;
    }
}

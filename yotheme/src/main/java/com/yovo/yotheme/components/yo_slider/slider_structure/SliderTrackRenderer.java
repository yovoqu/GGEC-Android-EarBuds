package com.yovo.yotheme.components.yo_slider.slider_structure;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条轨道渲染器
 * @note 负责绘制滑动条的背景轨道部分
 */
public class SliderTrackRenderer {
    
    private Paint trackPaint;
    private int trackColor;
    private RectF trackRect;
    
    public SliderTrackRenderer() {
        init();
    }
    
    private void init() {
        trackColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_TERTIARY);
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.FILL);
        trackRect = new RectF();
    }
    
    /**
     * 设置轨道区域
     * @param left 左边界
     * @param top 上边界
     * @param right 右边界
     * @param bottom 下边界
     */
    public void setTrackBounds(float left, float top, float right, float bottom) {
        trackRect.set(left, top, right, bottom);
    }
    
    /**
     * 绘制轨道
     * @param canvas 画布
     * @param cornerRadius 圆角半径
     */
    public void drawTrack(Canvas canvas, float cornerRadius) {
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);
    }
    
    /**
     * 设置轨道颜色
     * @param color 颜色值
     */
    public void setTrackColor(int color) {
        this.trackColor = color;
        trackPaint.setColor(color);
    }
    
    /**
     * 获取轨道颜色
     * @return 轨道颜色
     */
    public int getTrackColor() {
        return trackColor;
    }
    
    /**
     * 获取轨道矩形区域
     * @return 轨道矩形
     */
    public RectF getTrackRect() {
        return new RectF(trackRect);
    }
    
    /**
     * 检查点是否在轨道内
     * @param x X坐标
     * @param y Y坐标
     * @return 是否在轨道内
     */
    public boolean isPointInTrack(float x, float y) {
        return trackRect.contains(x, y);
    }
}

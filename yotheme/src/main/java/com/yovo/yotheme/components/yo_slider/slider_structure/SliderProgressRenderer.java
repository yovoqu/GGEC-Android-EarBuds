package com.yovo.yotheme.components.yo_slider.slider_structure;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条进度渲染器
 * @note 负责绘制滑动条的进度部分
 */
public class SliderProgressRenderer {
    
    private Paint progressPaint;
    private int progressColor;
    private RectF progressRect;
    
    public SliderProgressRenderer() {
        init();
    }
    
    private void init() {
        progressColor = ColorMapper.map(YoThemeColor.BRAND);
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.FILL);
        progressRect = new RectF();
    }
    
    /**
     * 设置进度区域
     * @param left 左边界
     * @param top 上边界
     * @param right 右边界
     * @param bottom 下边界
     */
    public void setProgressBounds(float left, float top, float right, float bottom) {
        progressRect.set(left, top, right, bottom);
    }
    
    /**
     * 绘制进度
     * @param canvas 画布
     * @param cornerRadius 圆角半径
     */
    public void drawProgress(Canvas canvas, float cornerRadius) {
        if (progressRect.width() > 0) {
            canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint);
        }
    }
    
    /**
     * 设置进度颜色
     * @param color 颜色值
     */
    public void setProgressColor(int color) {
        this.progressColor = color;
        progressPaint.setColor(color);
    }
    
    /**
     * 获取进度颜色
     * @return 进度颜色
     */
    public int getProgressColor() {
        return progressColor;
    }
    
    /**
     * 获取进度矩形区域
     * @return 进度矩形
     */
    public RectF getProgressRect() {
        return new RectF(progressRect);
    }
    
    /**
     * 获取进度宽度
     * @return 进度宽度
     */
    public float getProgressWidth() {
        return progressRect.width();
    }
    
    /**
     * 检查是否有可见进度
     * @return 是否有进度
     */
    public boolean hasVisibleProgress() {
        return progressRect.width() > 0;
    }
}

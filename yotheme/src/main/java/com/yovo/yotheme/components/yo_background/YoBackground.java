package com.yovo.yotheme.components.yo_background;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2025-11-11
 * @description 通用背景组件
 * @note 提供毛玻璃效果背景，包含背景层、高光渐变层、描边层
 * 可用于卡片、面板、按钮等需要背景效果的组件
 */
public class YoBackground {
    
    // ==================== 默认样式参数 ====================
    
    private static final float DEFAULT_CORNER_RADIUS_DP = 32f;
    private static final int DEFAULT_BACKGROUND_COLOR = 0xCCFFFFFF;  // 半透明白色
    private static final int DEFAULT_HIGHLIGHT_START_COLOR = 0x40FFFFFF;
    private static final int DEFAULT_HIGHLIGHT_CENTER_COLOR = 0x10FFFFFF;
    private static final int DEFAULT_HIGHLIGHT_END_COLOR = 0x00FFFFFF;
    private static final float DEFAULT_STROKE_WIDTH_DP = 1f;
    private static final int DEFAULT_STROKE_COLOR = 0x4DFFFFFF;
    
    // Context用于单位转换
    private final Context context;
    
    // 样式参数
    private float cornerRadiusDp = DEFAULT_CORNER_RADIUS_DP;
    private int backgroundColor = DEFAULT_BACKGROUND_COLOR;
    private int highlightStartColor = DEFAULT_HIGHLIGHT_START_COLOR;
    private int highlightCenterColor = DEFAULT_HIGHLIGHT_CENTER_COLOR;
    private int highlightEndColor = DEFAULT_HIGHLIGHT_END_COLOR;
    private float strokeWidthDp = DEFAULT_STROKE_WIDTH_DP;
    private int strokeColor = DEFAULT_STROKE_COLOR;
    
    /**
     * 构造函数
     */
    public YoBackground(@NonNull Context context) {
        this.context = context;
    }
    
    /**
     * 创建背景 Drawable
     */
    public Drawable createDrawable() {
        float cornerRadiusPx = dpToPx(cornerRadiusDp);
        float[] radii = {
            cornerRadiusPx, cornerRadiusPx,
            cornerRadiusPx, cornerRadiusPx,
            cornerRadiusPx, cornerRadiusPx,
            cornerRadiusPx, cornerRadiusPx
        };
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        
        // 主背景层：半透明颜色
        ShapeDrawable backgroundLayer = new ShapeDrawable(roundRectShape);
        backgroundLayer.getPaint().setColor(backgroundColor);
        
        // 高光层：顶部渐变
        GradientDrawable highlightLayer = new GradientDrawable();
        highlightLayer.setShape(GradientDrawable.RECTANGLE);
        highlightLayer.setCornerRadius(cornerRadiusPx);
        highlightLayer.setColors(new int[]{
            highlightStartColor,
            highlightCenterColor,
            highlightEndColor
        });
        highlightLayer.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        
        // 描边层：增强边缘质感
        ShapeDrawable strokeLayer = new ShapeDrawable(roundRectShape);
        strokeLayer.getPaint().setStyle(android.graphics.Paint.Style.STROKE);
        strokeLayer.getPaint().setStrokeWidth(dpToPx(strokeWidthDp));
        strokeLayer.getPaint().setColor(strokeColor);
        
        // 组合成 LayerDrawable
        Drawable[] layers = {
            backgroundLayer,
            highlightLayer,
            strokeLayer
        };
        
        return new LayerDrawable(layers);
    }
    
    /**
     * 将背景应用到指定视图
     */
    public void applyToView(@NonNull View view) {
        view.setBackground(createDrawable());
    }
    
    // ==================== 样式设置方法 ====================
    
    /**
     * 设置圆角半径（dp）
     */
    public YoBackground setCornerRadiusDp(float radiusDp) {
        this.cornerRadiusDp = radiusDp;
        return this;
    }
    
    /**
     * 设置背景颜色
     */
    public YoBackground setBackgroundColor(@ColorInt int color) {
        this.backgroundColor = color;
        return this;
    }
    
    /**
     * 设置高光渐变颜色
     */
    public YoBackground setHighlightColors(@ColorInt int startColor, @ColorInt int centerColor, @ColorInt int endColor) {
        this.highlightStartColor = startColor;
        this.highlightCenterColor = centerColor;
        this.highlightEndColor = endColor;
        return this;
    }
    
    /**
     * 设置描边宽度（dp）
     */
    public YoBackground setStrokeWidthDp(float widthDp) {
        this.strokeWidthDp = widthDp;
        return this;
    }
    
    /**
     * 设置描边颜色
     */
    public YoBackground setStrokeColor(@ColorInt int color) {
        this.strokeColor = color;
        return this;
    }
    
    // ==================== Getter 方法 ====================
    
    public float getCornerRadiusDp() {
        return cornerRadiusDp;
    }
    
    public int getBackgroundColor() {
        return backgroundColor;
    }
    
    public int getHighlightStartColor() {
        return highlightStartColor;
    }
    
    public int getHighlightCenterColor() {
        return highlightCenterColor;
    }
    
    public int getHighlightEndColor() {
        return highlightEndColor;
    }
    
    public float getStrokeWidthDp() {
        return strokeWidthDp;
    }
    
    public int getStrokeColor() {
        return strokeColor;
    }
    
    /**
     * dp转px
     */
    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
        );
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 创建默认样式的背景
     */
    public static YoBackground createDefault(@NonNull Context context) {
        return new YoBackground(context);
    }
    
    /**
     * 创建自定义圆角的背景
     */
    public static YoBackground createWithCornerRadius(@NonNull Context context, float cornerRadiusDp) {
        return new YoBackground(context).setCornerRadiusDp(cornerRadiusDp);
    }
    
    /**
     * 创建自定义颜色的背景
     */
    public static YoBackground createWithColor(@NonNull Context context, @ColorInt int backgroundColor) {
        return new YoBackground(context).setBackgroundColor(backgroundColor);
    }
}


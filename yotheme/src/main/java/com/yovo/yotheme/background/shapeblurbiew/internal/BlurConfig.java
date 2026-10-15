package com.yovo.yotheme.background.shapeblurbiew.internal;

import android.content.res.ColorStateList;
import android.graphics.Color;

import com.yovo.yotheme.background.shapeblurbiew.enums.BlurMode;

/**
 * @author tingfeng.yang
 * @date 2025-11-27
 * @description 模糊配置类，管理所有模糊相关参数
 */
public class BlurConfig {
    
    // 默认值
    public static final float DEFAULT_DOWN_SAMPLE = 4f;
    public static final int DEFAULT_OVERLAY_COLOR = 0x000000;
    public static final float DEFAULT_BLUR_RADIUS = 10f;
    public static final float DEFAULT_CORNER_RADIUS = 0f;
    public static final float DEFAULT_BORDER_WIDTH = 0f;
    public static final int DEFAULT_BORDER_COLOR = Color.WHITE;
    
    // 模糊参数
    public float downSampleFactor = DEFAULT_DOWN_SAMPLE;
    public int overlayColor = DEFAULT_OVERLAY_COLOR;
    public float blurRadius = DEFAULT_BLUR_RADIUS;
    
    // 形状参数
    @BlurMode
    public int blurMode = BlurMode.MODE_RECTANGLE;
    public final float[] cornerRadii = new float[4]; // [TL, TR, BR, BL]
    
    // 边框参数
    public float borderWidth = DEFAULT_BORDER_WIDTH;
    public ColorStateList borderColor = ColorStateList.valueOf(DEFAULT_BORDER_COLOR);
    
    public BlurConfig() {
        resetCornerRadii();
    }
    
    public void resetCornerRadii() {
        for (int i = 0; i < cornerRadii.length; i++) {
            cornerRadii[i] = DEFAULT_CORNER_RADIUS;
        }
    }
    
    public void setAllCornerRadius(float radius) {
        for (int i = 0; i < cornerRadii.length; i++) {
            cornerRadii[i] = radius;
        }
    }
    
    public BlurConfig copy() {
        BlurConfig config = new BlurConfig();
        config.downSampleFactor = this.downSampleFactor;
        config.overlayColor = this.overlayColor;
        config.blurRadius = this.blurRadius;
        config.blurMode = this.blurMode;
        System.arraycopy(this.cornerRadii, 0, config.cornerRadii, 0, 4);
        config.borderWidth = this.borderWidth;
        config.borderColor = this.borderColor;
        return config;
    }
}

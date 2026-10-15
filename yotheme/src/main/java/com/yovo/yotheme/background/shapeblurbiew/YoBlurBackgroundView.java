package com.yovo.yotheme.background.shapeblurbiew;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.yovo.yotheme.R;
import com.yovo.yotheme.background.shapeblurbiew.enums.BlurCorner;
import com.yovo.yotheme.background.shapeblurbiew.enums.BlurMode;
import com.yovo.yotheme.background.shapeblurbiew.internal.BlurConfig;
import com.yovo.yotheme.background.shapeblurbiew.internal.BlurRenderer;
import com.yovo.yotheme.background.shapeblurbiew.internal.ShapeDrawer;
import com.yovo.yotheme.background.shapeblurbiew.internal.SmartBlurContainer;
import com.yovo.yotheme.constants.BlurConstants;

/**
 * @author tingfeng.yang
 * @date 2025-11-26
 * @description 高斯模糊背景
 */
public class YoBlurBackgroundView extends View {

    private final BlurConfig config;
    private final BlurRenderer renderer;
    private final ShapeDrawer drawer;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    public YoBlurBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        config = new BlurConfig();
        renderer = new BlurRenderer(context, config);
        drawer = new ShapeDrawer();
        
        parseAttributes(context, attrs);
    }

    /**
     * 解析XML属性
     */
    private void parseAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.YoBlurBackgroundView);
        try {
            // 模糊参数
            config.blurRadius = a.getDimension(
                R.styleable.YoBlurBackgroundView_blur_radius,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 
                    BlurConfig.DEFAULT_BLUR_RADIUS, 
                    context.getResources().getDisplayMetrics())
            );
            config.downSampleFactor = a.getFloat(
                R.styleable.YoBlurBackgroundView_blur_down_sample, 
                BlurConfig.DEFAULT_DOWN_SAMPLE
            );
            config.overlayColor = a.getColor(
                R.styleable.YoBlurBackgroundView_blur_overlay_color, 
                BlurConfig.DEFAULT_OVERLAY_COLOR
            );
            
            // 形状模式
            config.blurMode = a.getInt(
                R.styleable.YoBlurBackgroundView_blur_mode, 
                BlurMode.MODE_RECTANGLE
            );
            
            // 圆角
            float cornerAll = a.getDimensionPixelSize(
                R.styleable.YoBlurBackgroundView_blur_corner_radius, -1
            );
            
            if (cornerAll >= 0) {
                config.setAllCornerRadius(cornerAll);
            } else {
                config.cornerRadii[BlurCorner.TOP_LEFT] = a.getDimensionPixelSize(
                    R.styleable.YoBlurBackgroundView_blur_corner_radius_top_left, 0
                );
                config.cornerRadii[BlurCorner.TOP_RIGHT] = a.getDimensionPixelSize(
                    R.styleable.YoBlurBackgroundView_blur_corner_radius_top_right, 0
                );
                config.cornerRadii[BlurCorner.BOTTOM_RIGHT] = a.getDimensionPixelSize(
                    R.styleable.YoBlurBackgroundView_blur_corner_radius_bottom_right, 0
                );
                config.cornerRadii[BlurCorner.BOTTOM_LEFT] = a.getDimensionPixelSize(
                    R.styleable.YoBlurBackgroundView_blur_corner_radius_bottom_left, 0
                );
            }
            
            // 边框
            config.borderWidth = a.getDimensionPixelSize(
                R.styleable.YoBlurBackgroundView_blur_border_width, 
                (int) BlurConfig.DEFAULT_BORDER_WIDTH
            );
            
            ColorStateList borderColor = a.getColorStateList(
                R.styleable.YoBlurBackgroundView_blur_border_color
            );
            if (borderColor != null) {
                config.borderColor = borderColor;
            }
        } finally {
            a.recycle();
        }
    }

    // ==================== 生命周期方法 ====================
    

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        renderer.attachToWindow(this);
        
        preDrawListener = renderer.createPreDrawListener(this);
        View decorView = renderer.getDecorView();
        
        if (decorView != null) {
            decorView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        View decorView = renderer.getDecorView();
        if (decorView != null && preDrawListener != null) {
            decorView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        }
        
        renderer.release();
        drawer.recycle();
        renderer.detachFromWindow();
        
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        if (renderer.isRendering()) {
            throw BlurRenderer.STOP_EXCEPTION;
        } else if (BlurRenderer.hasActiveRendering()) {
            // 防止嵌套渲染
        } else {
            super.draw(canvas);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawer.draw(canvas, renderer.getBlurredBitmap(), config, getWidth(), getHeight());
    }

    // ==================== 公共API ====================
    
    /**
     * 设置模糊半径 (0-25dp)
     */
    public void setBlurRadius(@FloatRange(from = 0, to = 25) float radius) {
        if (config.blurRadius != radius) {
            config.blurRadius = radius;
            renderer.markDirty();
            invalidate();
        }
    }
    
    public float getBlurRadius() {
        return config.blurRadius;
    }
    
    /**
     * 设置下采样因子
     */
    public void setDownSampleFactor(float factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("DownSample factor must be greater than 0.");
        }
        if (config.downSampleFactor != factor) {
            config.downSampleFactor = factor;
            renderer.markDirty();
            invalidate();
        }
    }
    
    public float getDownSampleFactor() {
        return config.downSampleFactor;
    }
    
    /**
     * 设置遮罩颜色
     */
    public void setOverlayColor(@ColorInt int color) {
        if (config.overlayColor != color) {
            config.overlayColor = color;
            invalidate();
        }
    }
    
    public int getOverlayColor() {
        return config.overlayColor;
    }
    
    /**
     * 设置模糊模式
     */
    public void setBlurMode(@BlurMode int mode) {
        if (config.blurMode != mode) {
            config.blurMode = mode;
            invalidate();
        }
    }
    
    @BlurMode
    public int getBlurMode() {
        return config.blurMode;
    }
    
    /**
     * 设置所有圆角半径
     */
    public void setCornerRadius(float radius) {
        config.setAllCornerRadius(radius);
        invalidate();
    }
    
    /**
     * 设置单个圆角半径
     */
    public void setCornerRadius(@BlurCorner int corner, float radius) {
        if (config.cornerRadii[corner] != radius) {
            config.cornerRadii[corner] = radius;
            invalidate();
        }
    }
    
    public float getCornerRadius(@BlurCorner int corner) {
        return config.cornerRadii[corner];
    }
    
    /**
     * 设置边框宽度
     */
    public void setBorderWidth(float width) {
        if (config.borderWidth != width) {
            config.borderWidth = width;
            invalidate();
        }
    }
    
    public float getBorderWidth() {
        return config.borderWidth;
    }
    
    /**
     * 设置边框颜色
     */
    public void setBorderColor(@ColorInt int color) {
        config.borderColor = ColorStateList.valueOf(color);
        invalidate();
    }
    
    /**
     * 设置边框颜色（资源ID）
     */
    public void setBorderColorRes(@ColorRes int colorRes) {
        config.borderColor = ColorStateList.valueOf(
            ContextCompat.getColor(getContext(), colorRes)
        );
        invalidate();
    }
    
    @ColorInt
    public int getBorderColor() {
        return config.borderColor.getDefaultColor();
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 快速创建模糊背景视图
     * @param context 上下文
     * @param blurRadius 模糊半径 (dp)
     * @param cornerRadius 圆角半径 (dp) 
     * @param overlayColor 遮罩颜色
     * @return 配置好的模糊视图
     */
    public static YoBlurBackgroundView create(@NonNull Context context, 
                                              float blurRadius,
                                              float cornerRadius,
                                              @ColorInt int overlayColor) {
        YoBlurBackgroundView view = new YoBlurBackgroundView(context, null);
        view.setBlurRadius(blurRadius);
        view.setCornerRadius(cornerRadius);
        view.setOverlayColor(overlayColor);
        return view;
    }
    
    /**
     * 创建带容器的模糊背景，自动包装内容视图
     * @param context 上下文
     * @param contentView 要包装的内容视图
     * @param blurRadius 模糊半径 (dp)
     * @param cornerRadius 圆角半径 (dp)
     * @param overlayColor 遮罩颜色
     * @return 包含模糊背景的容器
     */
    public static FrameLayout createWithContent(@NonNull Context context,
                                                @NonNull View contentView,
                                                float blurRadius,
                                                float cornerRadius,
                                                @ColorInt int overlayColor) {
        return createWithContent(context, contentView, blurRadius, 
            cornerRadius, cornerRadius, cornerRadius, cornerRadius, overlayColor);
    }
    
    /**
     * 创建带容器的模糊背景，支持单独设置四个圆角
     */
    public static FrameLayout createWithContent(@NonNull Context context,
                                                @NonNull View contentView,
                                                float blurRadius,
                                                float topLeftRadius,
                                                float topRightRadius,
                                                float bottomLeftRadius,
                                                float bottomRightRadius,
                                                @ColorInt int overlayColor) {
        
        // 创建智能模糊容器
        SmartBlurContainer container = new SmartBlurContainer(context);
        
        // 创建模糊背景
        YoBlurBackgroundView blurView = new YoBlurBackgroundView(context, null);
        blurView.setBlurRadius(blurRadius);
        blurView.setCornerRadius(BlurCorner.TOP_LEFT, topLeftRadius);
        blurView.setCornerRadius(BlurCorner.TOP_RIGHT, topRightRadius);
        blurView.setCornerRadius(BlurCorner.BOTTOM_LEFT, bottomLeftRadius);
        blurView.setCornerRadius(BlurCorner.BOTTOM_RIGHT, bottomRightRadius);
        blurView.setOverlayColor(overlayColor);
        
        // 保存原内容视图的布局参数
        ViewGroup.LayoutParams originalParams = contentView.getLayoutParams();
        
        // 设置模糊视图的布局参数（总是填充整个容器）
        FrameLayout.LayoutParams blurParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        
        // 为内容视图创建新的布局参数，保持原有约束
        FrameLayout.LayoutParams contentParams;
        if (originalParams instanceof FrameLayout.LayoutParams) {
            // 如果原来就是FrameLayout.LayoutParams，直接复制
            contentParams = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) originalParams);
        } else if (originalParams != null) {
            // 保持原有的宽高约束
            contentParams = new FrameLayout.LayoutParams(originalParams.width, originalParams.height);
        } else {
            // 默认情况
            contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            );
        }
        
        // 从原父视图中移除内容视图
        if (contentView.getParent() instanceof ViewGroup) {
            ((ViewGroup) contentView.getParent()).removeView(contentView);
        }
        
        // 添加到容器
        container.setBlurView(blurView);
        container.addView(blurView, blurParams);
        container.addView(contentView, contentParams);
        
        return container;
    }
    
    /**
     * 便捷方法：创建对话框样式的模糊背景
     */
    public static FrameLayout createForDialog(@NonNull Context context,
                                              @NonNull View contentView,
                                              @ColorInt int baseColor) {
        int overlayColor = ColorUtils.setAlphaComponent(baseColor, BlurConstants.OVERLAY_ALPHA_DEFAULT.getValue());
        float cornerRadius = dpToPx(context, BlurConstants.CORNER_RADIUS_DIALOG.getValue());
        return createWithContent(context, contentView, BlurConstants.BLUR_RADIUS_DEFAULT.getFloatValue(), cornerRadius, overlayColor);
    }
    
    /**
     * 便捷方法：创建卡片样式的模糊背景
     */
    public static FrameLayout createForCard(@NonNull Context context,
                                            @NonNull View contentView,
                                            @ColorInt int baseColor) {
        int overlayColor = ColorUtils.setAlphaComponent(baseColor, BlurConstants.OVERLAY_ALPHA_DEFAULT.getValue());
        float cornerRadius = dpToPx(context, BlurConstants.CORNER_RADIUS_CARD.getValue());
        return createWithContent(context, contentView, BlurConstants.BLUR_RADIUS_LIGHT.getFloatValue(), cornerRadius, overlayColor);
    }
    
    /**
     * 便捷方法：创建底部面板样式的模糊背景
     */
    public static FrameLayout createForBottomSheet(@NonNull Context context,
                                                   @NonNull View contentView,
                                                   @ColorInt int baseColor) {
        int overlayColor = ColorUtils.setAlphaComponent(baseColor, BlurConstants.OVERLAY_ALPHA_DEFAULT.getValue());
        float topCorner = dpToPx(context, BlurConstants.CORNER_RADIUS_BOTTOM_SHEET.getValue());
        return createWithContent(context, contentView, BlurConstants.BLUR_RADIUS_STRONG.getFloatValue(), 
            topCorner, topCorner, 0f, 0f, overlayColor);
    }
    
    // ==================== 内部实现已移至 internal 包 ====================
    
    /**
     * dp转px辅助方法
     */
    private static float dpToPx(@NonNull Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}

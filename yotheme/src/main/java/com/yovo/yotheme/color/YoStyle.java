package com.yovo.yotheme.color;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description YoTheme 样式应用服务
 * @note 此类的实例代表一个具体的颜色样式，并提供了将此样式应用到不同视图上的方法。
 * @note 实例只能通过 YoUI.getStyle() 工厂方法获取。
 * 
 */
public class YoStyle {

    private final int colorValue;

    /**
     * 公开的构造函数，允许在模块内不同包之间创建实例
     * @param colorValue 具体的颜色int值
     */
    public YoStyle(int colorValue) {
        this.colorValue = colorValue;
    }

    /**
     * 获取此样式的原始颜色值
     * @return 颜色的int值
     */
    public int getColorValue() {
        return colorValue;
    }

    // ==================== 基本应用方法 ====================

    /**
     * 将样式应用为指定视图的背景色
     * @param view 目标视图
     */
    public void applyToBackground(View view) {
        if (view != null) {
            view.setBackgroundColor(this.colorValue);
        }
    }

    /**
     * 将样式应用为指定TextView的文本颜色
     * @param textView 目标TextView
     */
    public void applyToText(TextView textView) {
        if (textView != null) {
            textView.setTextColor(this.colorValue);
        }
    }

    /**
     * 将样式应用为指定TextView的提示文本颜色
     * @param textView 目标TextView
     */
    public void applyToHintText(TextView textView) {
        if (textView != null) {
            textView.setHintTextColor(this.colorValue);
        }
    }

    /**
     * 将样式应用为指定EditText的光标颜色（Android 8.0及以上）
     * @param editText 目标EditText
     */
    public void applyToCursor(EditText editText) {
        if (editText != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.setTextCursorDrawable(createCursorDrawable(2)); // 2dp宽度的光标
        }
    }

    // ==================== 图标和Drawable应用方法 ====================

    /**
     * 将样式应用为指定ImageView的着色颜色
     * @param imageView 目标ImageView
     */
    public void applyToImageTint(ImageView imageView) {
        if (imageView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                imageView.setImageTintList(ColorStateList.valueOf(colorValue));
            } else {
                // 兼容低版本Android
                imageView.setColorFilter(colorValue, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /**
     * 将样式应用为指定Drawable的着色颜色
     * @param drawable 目标Drawable
     * @return 着色后的Drawable (注意：这会修改原始Drawable)
     */
    public Drawable applyToDrawable(Drawable drawable) {
        if (drawable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                drawable.setTintList(ColorStateList.valueOf(colorValue));
            } else {
                drawable.setColorFilter(colorValue, PorterDuff.Mode.SRC_IN);
            }
        }
        return drawable;
    }

    // ==================== 进度条和指示器 ====================

    /**
     * 将样式应用为指定ProgressBar的进度颜色
     * @param progressBar 目标ProgressBar
     */
    public void applyToProgressBar(ProgressBar progressBar) {
        if (progressBar != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setProgressTintList(ColorStateList.valueOf(colorValue));
        }
    }

    // ==================== 边框和形状 ====================

    /**
     * 创建一个带有此样式颜色边框的背景
     * @param view 目标View
     * @param strokeWidthDp 边框宽度(dp)
     * @param cornerRadiusDp 圆角半径(dp)
     * @param fillColor 填充颜色，-1表示透明
     */
    public void applyAsBorder(View view, float strokeWidthDp, float cornerRadiusDp, int fillColor) {
        if (view != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setStroke(dpToPx(view, strokeWidthDp), colorValue);
            
            if (fillColor != -1) {
                drawable.setColor(fillColor);
            }
            
            if (cornerRadiusDp > 0) {
                drawable.setCornerRadius(dpToPx(view, cornerRadiusDp));
            }
            
            view.setBackground(drawable);
        }
    }

    /**
     * 创建一个带有此样式颜色边框的背景（透明填充）
     * @param view 目标View
     * @param strokeWidthDp 边框宽度(dp)
     * @param cornerRadiusDp 圆角半径(dp)
     */
    public void applyAsBorder(View view, float strokeWidthDp, float cornerRadiusDp) {
        applyAsBorder(view, strokeWidthDp, cornerRadiusDp, -1);
    }

    /**
     * 应用为带圆角的背景
     * @param view 目标视图
     * @param cornerRadiusDp 圆角半径(dp)
     */
    public void applyAsRoundedBackground(View view, float cornerRadiusDp) {
        if (view != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(colorValue);
            drawable.setCornerRadius(dpToPx(view, cornerRadiusDp));
            view.setBackground(drawable);
        }
    }

    // ==================== 波纹效果 ====================

    /**
     * 将样式应用为视图的波纹效果颜色（Android 5.0及以上）
     * @param view 目标视图
     * @param cornerRadiusDp 圆角半径(dp)
     * @param backgroundColorState 背景颜色状态列表，null表示透明背景
     */
    public void applyAsRipple(View view, float cornerRadiusDp, ColorStateList backgroundColorState) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        // 创建圆角形状
        float radiusPx = dpToPx(view, cornerRadiusDp);
        float[] outerRadii = new float[8];
        for (int i = 0; i < 8; i++) {
            outerRadii[i] = radiusPx;
        }
        RoundRectShape roundRectShape = new RoundRectShape(outerRadii, null, null);
        ShapeDrawable mask = new ShapeDrawable(roundRectShape);

        // 创建波纹效果
        RippleDrawable rippleDrawable = new RippleDrawable(
                ColorStateList.valueOf(colorValue),
                backgroundColorState != null ? 
                        new ColorDrawable(backgroundColorState) : null,
                mask);
        
        view.setBackground(rippleDrawable);
    }

    /**
     * 将样式应用为视图的波纹效果颜色（Android 5.0及以上），使用透明背景
     * @param view 目标视图
     * @param cornerRadiusDp 圆角半径(dp)
     */
    public void applyAsRipple(View view, float cornerRadiusDp) {
        applyAsRipple(view, cornerRadiusDp, null);
    }

    // ==================== 按钮特定方法 ====================

    /**
     * 将样式应用为按钮的文本和背景颜色
     * @param button 目标按钮
     */
    public void applyToButton(Button button) {
        if (button != null) {
            button.setBackgroundColor(colorValue);
            button.setTextColor(getContrastColor());
        }
    }

    // ==================== 颜色变体方法 ====================

    /**
     * 创建此颜色的半透明变体
     * @param alpha 透明度 (0-255)
     * @return 新的YoStyle实例
     */
    public YoStyle withAlpha(int alpha) {
        int alphaColor = Color.argb(
                alpha, 
                Color.red(colorValue),
                Color.green(colorValue),
                Color.blue(colorValue)
        );
        return new YoStyle(alphaColor);
    }

    /**
     * 创建此颜色的更深变体
     * @param factor 加深因子 (0.0-1.0)，0.0表示不变，1.0表示黑色
     * @return 新的YoStyle实例
     */
    public YoStyle darker(float factor) {
        int r = Math.max(0, Math.round(Color.red(colorValue) * (1 - factor)));
        int g = Math.max(0, Math.round(Color.green(colorValue) * (1 - factor)));
        int b = Math.max(0, Math.round(Color.blue(colorValue) * (1 - factor)));
        return new YoStyle(Color.rgb(r, g, b));
    }

    /**
     * 创建此颜色的更浅变体
     * @param factor 变浅因子 (0.0-1.0)，0.0表示不变，1.0表示白色
     * @return 新的YoStyle实例
     */
    public YoStyle lighter(float factor) {
        int r = Math.min(255, Math.round(Color.red(colorValue) + (255 - Color.red(colorValue)) * factor));
        int g = Math.min(255, Math.round(Color.green(colorValue) + (255 - Color.green(colorValue)) * factor));
        int b = Math.min(255, Math.round(Color.blue(colorValue) + (255 - Color.blue(colorValue)) * factor));
        return new YoStyle(Color.rgb(r, g, b));
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取与当前颜色对比的颜色（白色或黑色）
     * @return 白色或黑色
     */
    public int getContrastColor() {
        // 计算颜色的亮度
        double r = Color.red(colorValue) / 255.0;
        double g = Color.green(colorValue) / 255.0;
        double b = Color.blue(colorValue) / 255.0;
        
        // 使用感知亮度公式计算亮度
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        
        // 亮度 > 0.5 被认为是亮色，应返回黑色；反之返回白色
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    /**
     * 创建光标Drawable
     * @param widthDp 光标宽度(dp)
     * @return 光标Drawable
     */
    private Drawable createCursorDrawable(int widthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(colorValue);
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setSize(dpToPx(null, widthDp), 0); // 高度将由系统自动设置
        return drawable;
    }

    /**
     * dp转px
     * @param view 上下文视图，可为null
     * @param dp dp值
     * @return px值
     */
    private int dpToPx(View view, float dp) {
        float density = 3.0f; // 默认密度为3.0 (xxhdpi)
        
        if (view != null && view.getContext() != null) {
            density = view.getContext().getResources().getDisplayMetrics().density;
        }
        
        return Math.round(dp * density);
    }

    /**
     * 内部ColorDrawable类，简单包装一个ColorStateList
     */
    private static class ColorDrawable extends android.graphics.drawable.ColorDrawable {
        public ColorDrawable(ColorStateList colorStateList) {
            super(colorStateList.getDefaultColor());
        }
    }
} 
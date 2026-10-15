package com.yovo.yotheme.components.yo_card;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.components.yo_background.YoBackground;

/**
 * @author tingfeng.yang
 * @date 2025-01-XX
 * @description 图标操作按钮卡片组件（DeviceAction）
 * @note 集成背景层和内容层，独立配置DeviceAction卡片样式
 * 背景颜色：font_tertiary颜色（#66000000，40%透明度黑色）
 */
public class IconActionButtonCard extends FrameLayout {
    
    // DeviceAction卡片样式配置
    private static final float CORNER_RADIUS_DP = 32f;
    private static final float ELEVATION_DP = 8f;
    
    // 背景颜色：font_tertiary颜色（#66000000，40%透明度黑色）
    private static final int BACKGROUND_COLOR = 0x66000000;  // font_tertiary颜色
    private static final int HIGHLIGHT_START_COLOR = 0x40000000;
    private static final int HIGHLIGHT_CENTER_COLOR = 0x10000000;
    private static final int HIGHLIGHT_END_COLOR = 0x00000000;
    private static final float STROKE_WIDTH_DP = 1f;
    private static final int STROKE_COLOR = 0x4D000000;
    
    // 内容容器
    private LinearLayout contentContainer;
    
    // 背景组件
    private YoBackground yoBackground;
    
    public IconActionButtonCard(@NonNull Context context) {
        super(context);
        init(context);
    }
    
    public IconActionButtonCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public IconActionButtonCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    /**
     * 初始化组件
     */
    private void init(Context context) {
        // 设置裁剪属性，允许阴影和背景超出边界显示
        setClipToPadding(false);
        setClipChildren(false);
        
        // 创建并配置DeviceAction卡片背景（独立配置）
        yoBackground = new YoBackground(context)
                .setCornerRadiusDp(CORNER_RADIUS_DP)
                .setBackgroundColor(BACKGROUND_COLOR)
                .setHighlightColors(
                        HIGHLIGHT_START_COLOR,
                        HIGHLIGHT_CENTER_COLOR,
                        HIGHLIGHT_END_COLOR
                )
                .setStrokeWidthDp(STROKE_WIDTH_DP)
                .setStrokeColor(STROKE_COLOR);
        
        // 应用背景
        yoBackground.applyToView(this);
        
        // 创建内容容器（垂直布局，水平居中）
        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        
        // 设置内容容器布局参数
        LayoutParams containerParams = new LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        );
        contentContainer.setLayoutParams(containerParams);
        
        // 添加内容容器到根布局
        addView(contentContainer);
        
        // 设置阴影
        setElevation(dpToPx(ELEVATION_DP));
    }
    
    /**
     * 更新背景
     */
    private void updateBackground() {
        if (yoBackground != null) {
            yoBackground.applyToView(this);
        }
    }
    
    /**
     * 添加子视图到内容容器
     */
    public void addContentView(View view) {
        if (contentContainer != null && view != null) {
            contentContainer.addView(view);
        }
    }
    
    /**
     * 添加子视图到内容容器（带布局参数）
     */
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (contentContainer != null && view != null) {
            contentContainer.addView(view, params);
        }
    }
    
    /**
     * 移除内容视图
     */
    public void removeContentView(View view) {
        if (contentContainer != null && view != null) {
            contentContainer.removeView(view);
        }
    }
    
    /**
     * 移除所有内容视图
     */
    public void removeAllContentViews() {
        if (contentContainer != null) {
            contentContainer.removeAllViews();
        }
    }
    
    /**
     * 设置内容容器内边距
     */
    public void setContentPadding(int left, int top, int right, int bottom) {
        if (contentContainer != null) {
            contentContainer.setPadding(left, top, right, bottom);
        }
    }
    
    /**
     * 设置内容容器内边距（dp）
     */
    public void setContentPaddingDp(float leftDp, float topDp, float rightDp, float bottomDp) {
        setContentPadding(
            dpToPx(leftDp),
            dpToPx(topDp),
            dpToPx(rightDp),
            dpToPx(bottomDp)
        );
    }
    
    /**
     * 设置内容容器方向
     */
    public void setContentOrientation(int orientation) {
        if (contentContainer != null) {
            contentContainer.setOrientation(orientation);
        }
    }
    
    /**
     * 设置内容容器对齐方式
     */
    public void setContentGravity(int gravity) {
        if (contentContainer != null) {
            contentContainer.setGravity(gravity);
        }
    }
    
    /**
     * 获取内容容器
     */
    public LinearLayout getContentContainer() {
        return contentContainer;
    }
    
    // ==================== 样式设置方法 ====================
    
    /**
     * 设置圆角半径（dp）
     */
    public void setCornerRadiusDp(float radiusDp) {
        if (yoBackground != null) {
            yoBackground.setCornerRadiusDp(radiusDp);
            updateBackground();
        }
    }
    
    /**
     * 设置背景颜色
     */
    public void setCardBackgroundColor(int color) {
        if (yoBackground != null) {
            yoBackground.setBackgroundColor(color);
            updateBackground();
        }
    }
    
    /**
     * 设置高光渐变颜色
     */
    public void setHighlightColors(int startColor, int centerColor, int endColor) {
        if (yoBackground != null) {
            yoBackground.setHighlightColors(startColor, centerColor, endColor);
            updateBackground();
        }
    }
    
    /**
     * 设置描边宽度（dp）
     */
    public void setStrokeWidthDp(float widthDp) {
        if (yoBackground != null) {
            yoBackground.setStrokeWidthDp(widthDp);
            updateBackground();
        }
    }
    
    /**
     * 设置描边颜色
     */
    public void setStrokeColor(int color) {
        if (yoBackground != null) {
            yoBackground.setStrokeColor(color);
            updateBackground();
        }
    }
    
    /**
     * 设置阴影高度（dp）
     */
    public void setElevationDp(float elevationDp) {
        setElevation(dpToPx(elevationDp));
    }
    
    /**
     * 获取背景组件
     */
    public YoBackground getYoBackground() {
        return yoBackground;
    }
    
    /**
     * dp转px
     */
    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}


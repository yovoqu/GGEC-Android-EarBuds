package com.yovo.yotheme.components.yo_model_panel.panel_structure;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.yovo.yotheme.constants.ModalPanelConstants;
import com.yovo.yotheme.components.yo_model_panel.YoModalPanelInterface;
import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 面板拖拽条组件
 * @note 提供视觉指示和拖拽手柄功能
 */
public class PanelDragBar extends FrameLayout {

    private View dragIndicator;
    private YoModalPanelInterface.PanelConfig config;
    private boolean enableTouchScale = true;

    public PanelDragBar(Context context) {
        this(context, null);
    }

    public PanelDragBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PanelDragBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 创建默认配置
        config = new YoModalPanelInterface.PanelConfig();
        
        initDragIndicator();
        applyConfig(config);
    }

    /**
     * 初始化拖拽指示器
     */
    private void initDragIndicator() {
        dragIndicator = new View(getContext());
        
        // 设置拖拽指示器的布局参数
        LayoutParams params = new LayoutParams(
            dpToPx(ModalPanelConstants.DRAG_BAR_WIDTH_DP.getFloatValue()),
            dpToPx(ModalPanelConstants.DRAG_BAR_HEIGHT_DP.getFloatValue())
        );
        params.gravity = Gravity.CENTER;
        
        dragIndicator.setLayoutParams(params);
        addView(dragIndicator);
        
        // 设置容器的内边距
        int paddingVertical = dpToPx(ModalPanelConstants.DRAG_BAR_CONTAINER_PADDING_VERTICAL_DP.getFloatValue());
        setPadding(0, paddingVertical, 0, paddingVertical);
    }

    /**
     * 应用配置
     */
    public void applyConfig(YoModalPanelInterface.PanelConfig config) {
        this.config = config != null ? config : new YoModalPanelInterface.PanelConfig();
        // 应用触摸放缩开关（默认保持之前行为: 开启）
        this.enableTouchScale = this.config.enableDragBarTouchScale;
        updateAppearance();
    }

    /**
     * 更新外观
     */
    private void updateAppearance() {
        if (dragIndicator == null) return;
        
        // 创建拖拽条背景
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));
        background.setCornerRadius(dpToPx(ModalPanelConstants.DRAG_BAR_CORNER_RADIUS_DP.getFloatValue()));
        
        dragIndicator.setBackground(background);
        
        // 设置阴影效果
        dragIndicator.setElevation(dpToPx(ModalPanelConstants.DRAG_BAR_ELEVATION_DP.getFloatValue()));
        
        // 根据开关更新触摸效果
        updateTouchEffect();
    }

    /**
     * 设置点击效果
     */
    private void updateTouchEffect() {
        if (!enableTouchScale) {
            resetAppearance();
        }
    }

    /**
     * 按压动画效果
     */
    private void animatePress(boolean pressed) {
        dragIndicator.animate()
            .scaleX(pressed ? 0.95f : 1.0f)
            .scaleY(pressed ? 0.95f : 1.0f)
            .alpha(pressed ? 0.7f : 1.0f)
            .setDuration(150)
            .start();
    }

    /**
     * 设置拖拽条的透明度
     */
    public void setDragBarAlpha(float alpha) {
        dragIndicator.setAlpha(Math.max(0.1f, Math.min(1.0f, alpha)));
    }

    /**
     * 设置拖拽条的颜色
     */
    public void setDragBarColor(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dpToPx(ModalPanelConstants.DRAG_BAR_CORNER_RADIUS_DP.getFloatValue()));
        dragIndicator.setBackground(background);
    }

    /**
     * 设置拖拽条是否可见
     */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        
        // 当拖拽条不可见时，移除内边距
        if (visibility == GONE || visibility == INVISIBLE) {
            setPadding(0, 0, 0, 0);
        } else {
            int paddingVertical = dpToPx(ModalPanelConstants.DRAG_BAR_CONTAINER_PADDING_VERTICAL_DP.getFloatValue());
            setPadding(0, paddingVertical, 0, paddingVertical);
        }
    }

    /**
     * 获取拖拽条的有效高度（包含内边距）
     */
    public int getEffectiveHeight() {
        if (getVisibility() != VISIBLE) {
            return 0;
        }
        
        int dragBarHeight = dpToPx(ModalPanelConstants.DRAG_BAR_HEIGHT_DP.getFloatValue());
        int paddingVertical = dpToPx(ModalPanelConstants.DRAG_BAR_CONTAINER_PADDING_VERTICAL_DP.getFloatValue());
        return dragBarHeight + paddingVertical * 2;
    }

    /**
     * 设置拖拽进度（可以用于动画效果）
     */
    public void setDragProgress(float progress) {
        // 根据拖拽进度调整拖拽条的外观
        float alpha = 1.0f - progress * 0.3f; // 拖拽时稍微变透明
        setDragBarAlpha(alpha);
        
        // 可以添加更多基于进度的动画效果
        float scale = 1.0f + progress * 0.1f; // 轻微放大效果
        dragIndicator.setScaleX(scale);
    }

    /**
     * 重置拖拽条外观
     */
    public void resetAppearance() {
        setDragBarAlpha(1.0f);
        dragIndicator.setScaleX(1.0f);
        dragIndicator.setScaleY(1.0f);
    }

    /**
     * 获取拖拽指示器视图（供外部动画使用）
     */
    public View getDragIndicator() {
        return dragIndicator;
    }

    /**
     * 设置是否启用触碰放缩动画
     */
    public void setEnableTouchScale(boolean enable) {
        this.enableTouchScale = enable;
        updateTouchEffect();
    }

    /**
     * 外部触摸事件驱动的按压视觉效果
     */
    public void onPressStateChanged(boolean pressed) {
        if (!enableTouchScale) return;
        animatePress(pressed);
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

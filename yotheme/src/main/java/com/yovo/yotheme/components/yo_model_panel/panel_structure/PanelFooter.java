package com.yovo.yotheme.components.yo_model_panel.panel_structure;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.yovo.yotheme.components.yo_model_panel.YoModalPanelInterface;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 面板底部组件
 * @note 用于放置操作按钮、额外信息等内容
 * 
 */
public class PanelFooter extends FrameLayout {

    private YoModalPanelInterface.PanelConfig config;

    public PanelFooter(Context context) {
        this(context, null);
    }

    public PanelFooter(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PanelFooter(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 创建默认配置
        config = new YoModalPanelInterface.PanelConfig();
        
        // 默认隐藏
        setVisibility(GONE);
        
        applyConfig(config);
    }

    /**
     * 应用配置
     */
    public void applyConfig(YoModalPanelInterface.PanelConfig config) {
        this.config = config != null ? config : new YoModalPanelInterface.PanelConfig();
        updateAppearance();
        updateContent();
    }

    /**
     * 更新外观
     */
    private void updateAppearance() {
        // 设置内边距
        int paddingHorizontal = dpToPx(config.contentPadding);
        int paddingVertical = dpToPx(config.contentPadding);
        setPadding(paddingHorizontal, 0, paddingHorizontal, paddingVertical);
    }

    /**
     * 更新内容
     */
    private void updateContent() {
        // 设置自定义底部视图
        if (config.footerView != null) {
            setCustomView(config.footerView);
        }
    }

    /**
     * 设置自定义视图
     */
    public void setCustomView(android.view.View view) {
        removeAllViews();
        if (view != null) {
            // 确保移除之前的父容器
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            
            addView(view);
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
    }

    /**
     * 检查是否有内容
     */
    public boolean hasContent() {
        return getVisibility() == VISIBLE && getChildCount() > 0;
    }

    /**
     * 清空内容
     */
    public void clearContent() {
        removeAllViews();
        setVisibility(GONE);
    }

    /**
     * 获取第一个子视图（如果存在）
     */
    public android.view.View getCustomView() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    /**
     * 设置背景颜色（如果需要与面板背景不同）
     */
    public void setFooterBackgroundColor(int color) {
        setBackgroundColor(color);
    }

    /**
     * 设置底部间距
     */
    public void setBottomMargin(int marginDp) {
        LayoutParams params = (LayoutParams) getLayoutParams();
        if (params != null) {
            params.bottomMargin = dpToPx(marginDp);
            setLayoutParams(params);
        }
    }

    /**
     * 设置顶部间距
     */
    public void setTopMargin(int marginDp) {
        LayoutParams params = (LayoutParams) getLayoutParams();
        if (params != null) {
            params.topMargin = dpToPx(marginDp);
            setLayoutParams(params);
        }
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

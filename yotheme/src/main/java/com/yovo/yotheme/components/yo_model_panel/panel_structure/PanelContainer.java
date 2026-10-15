package com.yovo.yotheme.components.yo_model_panel.panel_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.yovo.yotheme.constants.ModalPanelConstants;
import com.yovo.yotheme.components.yo_model_panel.YoModalPanelInterface;
import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 面板主容器
 * @note 负责面板的整体布局和背景绘制
 */
public class PanelContainer extends LinearLayout {

    private YoModalPanelInterface.PanelConfig config;
    private PanelHeader headerView;
    private FrameLayout contentContainer;
    private PanelFooter footerView;
    private PanelDragBar dragBar;

    public PanelContainer(Context context) {
        this(context, null);
    }

    public PanelContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PanelContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        
        // 创建默认配置
        config = new YoModalPanelInterface.PanelConfig();
        
        initChildViews();
        applyConfig();
    }

    /**
     * 初始化子视图
     */
    private void initChildViews() {
        // 头部视图
        headerView = new PanelHeader(getContext());
        headerView.setVisibility(GONE);
        
        // 内容容器
        contentContainer = new FrameLayout(getContext());
        
        // 底部视图
        footerView = new PanelFooter(getContext());
        footerView.setVisibility(GONE);
        
        // 拖拽条
        dragBar = new PanelDragBar(getContext());
        
        // 根据方向添加子视图
        addChildViewsByDirection();
    }

    /**
     * 根据方向添加子视图
     */
    private void addChildViewsByDirection() {
        removeAllViews();
        
        if (config.direction == YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP) {
            // 从底部向上：拖拽条 -> 头部 -> 内容 -> 底部
            if (config.showDragBar) {
                addView(dragBar, createWrapContentParams());
            }
            addView(headerView, createWrapContentParams());
            addView(contentContainer, createMatchParentParams());
            addView(footerView, createWrapContentParams());
        } else {
            // 从顶部向下：头部 -> 内容 -> 底部 -> 拖拽条
            addView(headerView, createWrapContentParams());
            addView(contentContainer, createMatchParentParams());
            addView(footerView, createWrapContentParams());
            if (config.showDragBar) {
                addView(dragBar, createWrapContentParams());
            }
        }
    }

    /**
     * 应用配置
     */
    public void applyConfig() {
        if (config == null) return;
        
        // 更新背景
        updateBackground();
        
        // 更新拖拽条显示状态
        updateDragBarVisibility();
        
        // 重新排列子视图
        addChildViewsByDirection();
        
        // 更新子视图配置
        if (headerView != null) {
            headerView.applyConfig(config);
        }
        if (footerView != null) {
            footerView.applyConfig(config);
        }
        if (dragBar != null) {
            dragBar.applyConfig(config);
            dragBar.setEnableTouchScale(config.enableDragBarTouchScale);
        }
        
        requestLayout();
    }

    /**
     * 更新背景
     */
    private void updateBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorMapper.map(YoThemeColor.BACKGROUND_PRIMARY));
        
        // 设置圆角
        float radius = dpToPx(config.cornerRadius);
        if (config.direction == YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP) {
            // 从底部向上：上边圆角
            background.setCornerRadii(new float[]{
                radius, radius, radius, radius, // 上左、上右
                0, 0, 0, 0 // 下左、下右
            });
        } else {
            // 从顶部向下：下边圆角
            background.setCornerRadii(new float[]{
                0, 0, 0, 0, // 上左、上右
                radius, radius, radius, radius // 下左、下右
            });
        }
        
        setBackground(background);
        
        // 设置阴影
        setElevation(dpToPx(ModalPanelConstants.PANEL_ELEVATION_DP.getFloatValue()));
    }

    /**
     * 更新拖拽条显示状态
     */
    private void updateDragBarVisibility() {
        if (dragBar != null) {
            dragBar.setVisibility(config.showDragBar ? VISIBLE : GONE);
        }
    }

    /**
     * 设置配置
     */
    public void setConfig(YoModalPanelInterface.PanelConfig config) {
        this.config = config != null ? config : new YoModalPanelInterface.PanelConfig();
        applyConfig();
    }

    /**
     * 获取配置
     */
    public YoModalPanelInterface.PanelConfig getConfig() {
        return config;
    }

    /**
     * 设置头部视图
     */
    public void setHeaderView(View view) {
        if (headerView != null) {
            headerView.setCustomView(view);
            headerView.setVisibility(view != null ? VISIBLE : GONE);
        }
    }

    /**
     * 设置底部视图
     */
    public void setFooterView(View view) {
        if (footerView != null) {
            footerView.setCustomView(view);
            footerView.setVisibility(view != null ? VISIBLE : GONE);
        }
    }

    /**
     * 添加内容视图
     */
    public void addContentView(View view) {
        if (contentContainer != null && view != null) {
            contentContainer.addView(view);
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
     * 清空内容
     */
    public void clearContent() {
        if (contentContainer != null) {
            contentContainer.removeAllViews();
        }
    }

    /**
     * 获取内容容器
     */
    public FrameLayout getContentContainer() {
        return contentContainer;
    }

    /**
     * 获取拖拽条
     */
    public PanelDragBar getDragBar() {
        return dragBar;
    }

    /**
     * 获取头部视图
     */
    public PanelHeader getHeaderView() {
        return headerView;
    }

    /**
     * 获取底部视图
     */
    public PanelFooter getFooterView() {
        return footerView;
    }

    /**
     * 设置标题
     */
    public void setTitle(String title) {
        if (headerView != null) {
            headerView.setTitle(title);
            headerView.setVisibility(title != null && !title.isEmpty() ? VISIBLE : GONE);
        }
    }

    /**
     * 设置消息
     */
    public void setMessage(String message) {
        if (headerView != null) {
            headerView.setMessage(message);
        }
    }

    /**
     * 创建WRAP_CONTENT布局参数
     */
    private LayoutParams createWrapContentParams() {
        return new LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        );
    }

    /**
     * 创建MATCH_PARENT布局参数（权重为1）
     */
    private LayoutParams createMatchParentParams() {
        LayoutParams params = new LayoutParams(
            LayoutParams.MATCH_PARENT,
            0
        );
        params.weight = 1f;
        return params;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // 可以在这里添加额外的绘制逻辑，如阴影、边框等
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        // 根据配置限制高度
        if (config != null && config.heightRatio > 0 && config.heightRatio < 1) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int maxHeight = (int) (screenHeight * config.heightRatio);
            
            if (getMeasuredHeight() > maxHeight) {
                int newHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, newHeightSpec);
            }
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

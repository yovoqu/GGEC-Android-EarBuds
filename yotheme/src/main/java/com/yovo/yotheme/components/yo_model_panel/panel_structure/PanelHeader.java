package com.yovo.yotheme.components.yo_model_panel.panel_structure;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yovo.yotheme.constants.ModalPanelConstants;
import com.yovo.yotheme.components.yo_model_panel.YoModalPanelInterface;
import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 面板头部组件
 * @note 包含标题、副标题和自定义内容区域
 */
public class PanelHeader extends LinearLayout {

    private TextView titleView;
    private TextView messageView;
    private FrameLayout customContainer;
    private YoModalPanelInterface.PanelConfig config;

    public PanelHeader(Context context) {
        this(context, null);
    }

    public PanelHeader(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PanelHeader(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        
        // 创建默认配置
        config = new YoModalPanelInterface.PanelConfig();
        
        initViews();
        applyConfig(config);
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        // 标题视图
        titleView = new TextView(getContext());
        titleView.setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxLines(ModalPanelConstants.TITLE_MAX_LINES_DEFAULT.getIntValue());
        titleView.setVisibility(GONE);
        
        // 消息视图
        messageView = new TextView(getContext());
        messageView.setTextColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));
        messageView.setEllipsize(TextUtils.TruncateAt.END);
        messageView.setMaxLines(ModalPanelConstants.MESSAGE_MAX_LINES_DEFAULT.getIntValue());
        messageView.setVisibility(GONE);
        
        // 自定义内容容器
        customContainer = new FrameLayout(getContext());
        customContainer.setVisibility(GONE);
        
        // 添加到布局
        addView(titleView);
        addView(messageView);
        addView(customContainer);
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
        int padding = dpToPx(config.contentPadding);
        setPadding(padding, padding, padding, 0); // 底部不设置内边距，由内容间距控制
        
        // 更新标题样式
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.titleTextSize);
        
        // 更新消息样式
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.messageTextSize);
        messageView.setLineSpacing(
            dpToPx(ModalPanelConstants.HEADER_LINE_SPACING_EXTRA_DP.getFloatValue()), 
            ModalPanelConstants.LINE_SPACING_MULTIPLIER.getFloatValue()
        );
        
        // 更新布局参数
        updateLayoutParams();
    }

    /**
     * 更新布局参数
     */
    private void updateLayoutParams() {
        // 标题布局参数
        LayoutParams titleParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleView.setLayoutParams(titleParams);
        
        // 消息布局参数
        LayoutParams messageParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dpToPx(config.titleMessageGap);
        messageView.setLayoutParams(messageParams);
        
        // 自定义容器布局参数
        LayoutParams customParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        customParams.topMargin = dpToPx(config.titleMessageGap);
        customContainer.setLayoutParams(customParams);
    }

    /**
     * 更新内容
     */
    private void updateContent() {
        // 设置标题
        if (!TextUtils.isEmpty(config.title)) {
            titleView.setText(config.title);
            titleView.setVisibility(VISIBLE);
        } else {
            titleView.setVisibility(GONE);
        }
        
        // 设置消息
        if (!TextUtils.isEmpty(config.message)) {
            messageView.setText(config.message);
            messageView.setVisibility(VISIBLE);
        } else {
            messageView.setVisibility(GONE);
        }
        
        // 设置自定义头部视图
        if (config.headerView != null) {
            setCustomView(config.headerView);
        }
        
        // 更新整体可见性
        updateVisibility();
    }

    /**
     * 设置标题
     */
    public void setTitle(String title) {
        if (titleView != null) {
            if (!TextUtils.isEmpty(title)) {
                titleView.setText(title);
                titleView.setVisibility(VISIBLE);
            } else {
                titleView.setVisibility(GONE);
            }
            updateVisibility();
        }
    }

    /**
     * 设置消息
     */
    public void setMessage(String message) {
        if (messageView != null) {
            if (!TextUtils.isEmpty(message)) {
                messageView.setText(message);
                messageView.setVisibility(VISIBLE);
            } else {
                messageView.setVisibility(GONE);
            }
            updateVisibility();
        }
    }

    /**
     * 设置自定义视图
     */
    public void setCustomView(android.view.View view) {
        if (customContainer != null) {
            customContainer.removeAllViews();
            if (view != null) {
                customContainer.addView(view);
                customContainer.setVisibility(VISIBLE);
            } else {
                customContainer.setVisibility(GONE);
            }
            updateVisibility();
        }
    }

    /**
     * 获取自定义容器
     */
    public FrameLayout getCustomContainer() {
        return customContainer;
    }

    /**
     * 获取标题视图
     */
    public TextView getTitleView() {
        return titleView;
    }

    /**
     * 获取消息视图
     */
    public TextView getMessageView() {
        return messageView;
    }

    /**
     * 更新整体可见性
     */
    private void updateVisibility() {
        boolean hasContent = (titleView.getVisibility() == VISIBLE) ||
                           (messageView.getVisibility() == VISIBLE) ||
                           (customContainer.getVisibility() == VISIBLE);
        
        setVisibility(hasContent ? VISIBLE : GONE);
    }

    /**
     * 检查是否有内容
     */
    public boolean hasContent() {
        return getVisibility() == VISIBLE;
    }

    /**
     * 设置标题的最大行数
     */
    public void setTitleMaxLines(int maxLines) {
        titleView.setMaxLines(Math.max(1, maxLines));
    }

    /**
     * 设置消息的最大行数
     */
    public void setMessageMaxLines(int maxLines) {
        messageView.setMaxLines(Math.max(1, maxLines));
    }

    /**
     * 设置标题文字大小
     */
    public void setTitleTextSize(float sizeSp) {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /**
     * 设置消息文字大小
     */
    public void setMessageTextSize(float sizeSp) {
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /**
     * 设置标题颜色
     */
    public void setTitleTextColor(int color) {
        titleView.setTextColor(color);
    }

    /**
     * 设置消息颜色
     */
    public void setMessageTextColor(int color) {
        messageView.setTextColor(color);
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

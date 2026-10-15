package com.yovo.yotheme.components.yo_model_panel;

import android.content.Context;
import android.view.View;

import com.yovo.yotheme.constants.ModalPanelConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 模态面板构建器
 * @note 参考 YoListBuilder 设计，提供流式API构建模态面板
 */
public class YoModalPanelBuilder implements YoModalPanelInterface.YoModalPanelBuilder {

    private final Context context;
    private final YoModalPanelInterface.PanelConfig config;
    private final List<View> contentViews = new ArrayList<>();
    
    // 监听器
    private YoModalPanelInterface.OnPanelStateChangeListener stateChangeListener;
    private YoModalPanelInterface.OnPanelInteractionListener interactionListener;
    private YoModalPanelInterface.OnPanelLifecycleListener lifecycleListener;

    public YoModalPanelBuilder(Context context) {
        this.context = context;
        this.config = new YoModalPanelInterface.PanelConfig();
    }

    @Override
    public YoModalPanelBuilder setConfig(YoModalPanelInterface.PanelConfig config) {
        if (config != null) {
            // 复制配置属性
            this.config.direction = config.direction;
            this.config.heightRatio = config.heightRatio;
            this.config.cornerRadius = config.cornerRadius;
            this.config.maskAlpha = config.maskAlpha;
            this.config.showDragBar = config.showDragBar;
            this.config.cancelableOnTouchOutside = config.cancelableOnTouchOutside;
            this.config.cancelable = config.cancelable;
            this.config.enableDamping = config.enableDamping;
            this.config.enableDragBarTouchScale = config.enableDragBarTouchScale;
            this.config.topOffset = config.topOffset;
            this.config.animationDuration = config.animationDuration;
            this.config.type = config.type;
            this.config.style = config.style;
            this.config.customContentView = config.customContentView;
            this.config.title = config.title;
            this.config.message = config.message;
            this.config.headerView = config.headerView;
            this.config.footerView = config.footerView;
            this.config.titleTextSize = config.titleTextSize;
            this.config.messageTextSize = config.messageTextSize;
            this.config.contentPadding = config.contentPadding;
            this.config.titleMessageGap = config.titleMessageGap;
        }
        return this;
    }

    @Override
    public YoModalPanelBuilder setDirection(YoModalPanelInterface.PanelDirection direction) {
        config.direction = direction;
        return this;
    }

    @Override
    public YoModalPanelBuilder setHeightRatio(float ratio) {
        config.heightRatio = ModalPanelConstants.clampRatio(ratio);
        return this;
    }

    @Override
    public YoModalPanelBuilder setType(YoModalPanelInterface.PanelType type) {
        config.type = type;
        applyTypeDefaults(type);
        return this;
    }

    @Override
    public YoModalPanelBuilder setStyle(YoModalPanelInterface.PanelStyle style) {
        config.style = style;
        applyStyleDefaults(style);
        return this;
    }

    @Override
    public YoModalPanelBuilder setTitle(String title) {
        config.title = title;
        return this;
    }

    @Override
    public YoModalPanelBuilder setMessage(String message) {
        config.message = message;
        return this;
    }

    @Override
    public YoModalPanelBuilder addContentView(View view) {
        if (view != null) {
            contentViews.add(view);
        }
        return this;
    }

    @Override
    public YoModalPanelBuilder setOnStateChangeListener(YoModalPanelInterface.OnPanelStateChangeListener listener) {
        this.stateChangeListener = listener;
        return this;
    }

    @Override
    public YoModalPanelBuilder setOnInteractionListener(YoModalPanelInterface.OnPanelInteractionListener listener) {
        this.interactionListener = listener;
        return this;
    }

    @Override
    public YoModalPanelBuilder setOnLifecycleListener(YoModalPanelInterface.OnPanelLifecycleListener listener) {
        this.lifecycleListener = listener;
        return this;
    }

    @Override
    public YoModalPanelInterface.YoModalPanel build() {
        // 创建面板
        YoModalPanel panel = new YoModalPanel(context, config);
        
        // 设置监听器
        if (stateChangeListener != null) {
            panel.setOnPanelStateChangeListener(stateChangeListener);
        }
        if (interactionListener != null) {
            panel.setOnPanelInteractionListener(interactionListener);
        }
        if (lifecycleListener != null) {
            panel.setOnPanelLifecycleListener(lifecycleListener);
        }
        
        // 添加内容视图
        for (View view : contentViews) {
            panel.addContentView(view);
        }
        
        return panel;
    }

    // ==================== 便利方法 ====================

    /**
     * 设置圆角半径
     */
    public YoModalPanelBuilder setCornerRadius(float radiusDp) {
        config.cornerRadius = Math.max(0f, radiusDp);
        return this;
    }

    /**
     * 设置遮罩透明度
     */
    public YoModalPanelBuilder setMaskAlpha(float alpha) {
        config.maskAlpha = ModalPanelConstants.clampRatio(alpha);
        return this;
    }

    /**
     * 设置是否显示拖拽条
     */
    public YoModalPanelBuilder setShowDragBar(boolean show) {
        config.showDragBar = show;
        return this;
    }

    /**
     * 设置是否可通过点击外部关闭
     */
    public YoModalPanelBuilder setCancelableOnTouchOutside(boolean cancelable) {
        config.cancelableOnTouchOutside = cancelable;
        return this;
    }

    /**
     * 设置是否可通过返回键关闭
     */
    public YoModalPanelBuilder setCancelable(boolean cancelable) {
        config.cancelable = cancelable;
        return this;
    }

    /**
     * 设置是否启用阻尼效果
     */
    public YoModalPanelBuilder setEnableDamping(boolean enable) {
        config.enableDamping = enable;
        return this;
    }
    
    /**
     * 设置是否启用拖拽条触碰放缩动画
     */
    public YoModalPanelBuilder setEnableDragBarTouchScale(boolean enable) {
        config.enableDragBarTouchScale = enable;
        return this;
    }

    /**
     * 设置顶部偏移
     */
    public YoModalPanelBuilder setTopOffset(float offsetDp) {
        config.topOffset = Math.max(0f, offsetDp);
        return this;
    }

    /**
     * 设置动画持续时间
     */
    public YoModalPanelBuilder setAnimationDuration(long duration) {
        config.animationDuration = ModalPanelConstants.clampDuration(duration);
        return this;
    }

    /**
     * 设置自定义内容视图
     */
    public YoModalPanelBuilder setCustomContentView(View view) {
        config.customContentView = view;
        return this;
    }

    /**
     * 设置头部视图
     */
    public YoModalPanelBuilder setHeaderView(View view) {
        config.headerView = view;
        return this;
    }

    /**
     * 设置底部视图
     */
    public YoModalPanelBuilder setFooterView(View view) {
        config.footerView = view;
        return this;
    }

    /**
     * 设置标题文字大小
     */
    public YoModalPanelBuilder setTitleTextSize(float sizeSp) {
        config.titleTextSize = Math.max(ModalPanelConstants.TITLE_TEXT_SIZE_SMALL_SP.getFloatValue(), sizeSp);
        return this;
    }

    /**
     * 设置消息文字大小
     */
    public YoModalPanelBuilder setMessageTextSize(float sizeSp) {
        config.messageTextSize = Math.max(ModalPanelConstants.CONTENT_TEXT_SIZE_SMALL_SP.getFloatValue(), sizeSp);
        return this;
    }

    /**
     * 设置内容内边距
     */
    public YoModalPanelBuilder setContentPadding(float paddingDp) {
        config.contentPadding = Math.max(0f, paddingDp);
        return this;
    }

    /**
     * 设置标题与消息间距
     */
    public YoModalPanelBuilder setTitleMessageGap(float gapDp) {
        config.titleMessageGap = Math.max(0f, gapDp);
        return this;
    }

    // ==================== 根据类型应用默认配置 ====================

    /**
     * 根据面板类型应用默认配置
     */
    private void applyTypeDefaults(YoModalPanelInterface.PanelType type) {
        switch (type) {
            case NOTIFICATION:
                config.direction = YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM;
                config.heightRatio = ModalPanelConstants.NOTIFICATION_HEIGHT_RATIO.getFloatValue();
                config.showDragBar = false;
                config.maskAlpha = ModalPanelConstants.LOW_MASK_ALPHA.getFloatValue();
                config.animationDuration = ModalPanelConstants.FAST_ANIMATION_DURATION.getLongValue();
                break;
                
            case ALERT:
                config.direction = YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM;
                config.heightRatio = ModalPanelConstants.SMALL_HEIGHT_RATIO.getFloatValue();
                config.showDragBar = false;
                config.maskAlpha = ModalPanelConstants.HIGH_MASK_ALPHA.getFloatValue();
                config.animationDuration = ModalPanelConstants.SLOW_ANIMATION_DURATION.getLongValue();
                break;
                
            case SELECTION:
                config.direction = YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP;
                config.heightRatio = ModalPanelConstants.MEDIUM_HEIGHT_RATIO.getFloatValue();
                config.showDragBar = true;
                config.maskAlpha = ModalPanelConstants.MEDIUM_MASK_ALPHA.getFloatValue();
                config.enableDamping = true;
                break;
                
            case SETTINGS:
                config.direction = YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP;
                config.heightRatio = ModalPanelConstants.LARGE_HEIGHT_RATIO.getFloatValue();
                config.showDragBar = false;
                config.maskAlpha = ModalPanelConstants.HIGH_MASK_ALPHA.getFloatValue();
                config.cornerRadius = ModalPanelConstants.LARGE_CORNER_RADIUS_DP.getFloatValue();
                config.enableDamping = false;
                break;
                
            case PREVIEW:
                config.direction = YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP;
                config.heightRatio = ModalPanelConstants.SMALL_HEIGHT_RATIO.getFloatValue();
                config.showDragBar = true;
                config.maskAlpha = ModalPanelConstants.LOW_MASK_ALPHA.getFloatValue();
                config.cornerRadius = ModalPanelConstants.SMALL_CORNER_RADIUS_DP.getFloatValue();
                config.animationDuration = ModalPanelConstants.FAST_ANIMATION_DURATION.getLongValue();
                break;
                
            case STANDARD:
            case CUSTOM:
            default:
                // 使用默认配置
                break;
        }
    }

    /**
     * 根据面板样式应用默认配置
     */
    private void applyStyleDefaults(YoModalPanelInterface.PanelStyle style) {
        switch (style) {
            case COMPACT:
                config.contentPadding = ModalPanelConstants.CONTENT_PADDING_SMALL_DP.getFloatValue();
                config.titleMessageGap = ModalPanelConstants.TITLE_CONTENT_GAP_SMALL_DP.getFloatValue();
                config.titleTextSize = ModalPanelConstants.TITLE_TEXT_SIZE_SMALL_SP.getFloatValue();
                config.messageTextSize = ModalPanelConstants.CONTENT_TEXT_SIZE_SMALL_SP.getFloatValue();
                break;
                
            case EXPANDED:
                config.contentPadding = ModalPanelConstants.CONTENT_PADDING_LARGE_DP.getFloatValue();
                config.titleMessageGap = ModalPanelConstants.TITLE_CONTENT_GAP_DP.getFloatValue();
                config.titleTextSize = ModalPanelConstants.TITLE_TEXT_SIZE_LARGE_SP.getFloatValue();
                config.messageTextSize = ModalPanelConstants.CONTENT_TEXT_SIZE_SP.getFloatValue();
                break;
                
            case MINIMAL:
                config.contentPadding = ModalPanelConstants.CONTENT_PADDING_SMALL_DP.getFloatValue();
                config.showDragBar = false;
                config.maskAlpha = ModalPanelConstants.MIN_MASK_ALPHA.getFloatValue();
                config.cornerRadius = ModalPanelConstants.SMALL_CORNER_RADIUS_DP.getFloatValue();
                break;
                
            case CARD:
                config.cornerRadius = ModalPanelConstants.DEFAULT_CORNER_RADIUS_DP.getFloatValue();
                config.contentPadding = ModalPanelConstants.CONTENT_PADDING_DP.getFloatValue();
                break;
                
            case OVERLAY:
                config.maskAlpha = ModalPanelConstants.HIGH_MASK_ALPHA.getFloatValue();
                config.cornerRadius = 0f;
                break;
                
            case DEFAULT:
            default:
                // 使用默认配置
                break;
        }
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建标准构建器
     */
    public static YoModalPanelBuilder createStandard(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.STANDARD)
                .setStyle(YoModalPanelInterface.PanelStyle.DEFAULT);
    }

    /**
     * 创建通知构建器
     */
    public static YoModalPanelBuilder createNotification(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.NOTIFICATION)
                .setStyle(YoModalPanelInterface.PanelStyle.COMPACT);
    }

    /**
     * 创建警告构建器
     */
    public static YoModalPanelBuilder createAlert(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.ALERT)
                .setStyle(YoModalPanelInterface.PanelStyle.DEFAULT);
    }

    /**
     * 创建选择构建器
     */
    public static YoModalPanelBuilder createSelection(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.SELECTION)
                .setStyle(YoModalPanelInterface.PanelStyle.DEFAULT);
    }

    /**
     * 创建设置构建器
     */
    public static YoModalPanelBuilder createSettings(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.SETTINGS)
                .setStyle(YoModalPanelInterface.PanelStyle.EXPANDED);
    }

    /**
     * 创建预览构建器
     */
    public static YoModalPanelBuilder createPreview(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.PREVIEW)
                .setStyle(YoModalPanelInterface.PanelStyle.CARD);
    }

    /**
     * 创建自定义构建器 
     */
    public static YoModalPanelBuilder createCustom(Context context) {
        return new YoModalPanelBuilder(context)
                .setType(YoModalPanelInterface.PanelType.CUSTOM)
                .setStyle(YoModalPanelInterface.PanelStyle.DEFAULT);
    }
}

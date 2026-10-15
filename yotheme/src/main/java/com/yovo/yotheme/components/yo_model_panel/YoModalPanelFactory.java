package com.yovo.yotheme.components.yo_model_panel;

import android.content.Context;
import android.view.View;

import com.yovo.yotheme.constants.ModalPanelConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 模态面板工厂类
 * @note 提供便捷的创建方法和预设配置，简化模态面板的使用
 */
public class YoModalPanelFactory {

    // ==================== 快速创建方法 ====================

    /**
     * 创建标准底部面板
     */
    public static YoModalPanelInterface.YoModalPanel createBottomPanel(Context context, String title, String message) {
        return YoModalPanelBuilder.createStandard(context)
                .setDirection(YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP)
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建标准顶部面板
     */
    public static YoModalPanelInterface.YoModalPanel createTopPanel(Context context, String title, String message) {
        return YoModalPanelBuilder.createStandard(context)
                .setDirection(YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM)
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建通知面板
     */
    public static YoModalPanelInterface.YoModalPanel createNotification(Context context, String title, String message) {
        return YoModalPanelBuilder.createNotification(context)
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建警告面板
     */
    public static YoModalPanelInterface.YoModalPanel createAlert(Context context, String title, String message) {
        return YoModalPanelBuilder.createAlert(context)
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建选择面板
     */
    public static YoModalPanelInterface.YoModalPanel createSelection(Context context, String title) {
        return YoModalPanelBuilder.createSelection(context)
                .setTitle(title)
                .build();
    }

    /**
     * 创建设置面板
     */
    public static YoModalPanelInterface.YoModalPanel createSettings(Context context, String title) {
        return YoModalPanelBuilder.createSettings(context)
                .setTitle(title)
                .build();
    }

    /**
     * 创建预览面板
     */
    public static YoModalPanelInterface.YoModalPanel createPreview(Context context, String title) {
        return YoModalPanelBuilder.createPreview(context)
                .setTitle(title)
                .build();
    }

    // ==================== 自定义内容面板 ====================

    /**
     * 创建自定义内容的底部面板
     */
    public static YoModalPanelInterface.YoModalPanel createBottomPanelWithContent(Context context, View content) {
        return YoModalPanelBuilder.createStandard(context)
                .setDirection(YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP)
                .addContentView(content)
                .build();
    }

    /**
     * 创建自定义内容的顶部面板
     */
    public static YoModalPanelInterface.YoModalPanel createTopPanelWithContent(Context context, View content) {
        return YoModalPanelBuilder.createStandard(context)
                .setDirection(YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM)
                .addContentView(content)
                .build();
    }

    /**
     * 创建全自定义面板
     */
    public static YoModalPanelInterface.YoModalPanel createCustomPanel(Context context, YoModalPanelInterface.PanelConfig config) {
        return YoModalPanelBuilder.createCustom(context)
                .setConfig(config)
                .build();
    }

    // ==================== 特定高度面板 ====================

    /**
     * 创建小高度面板
     */
    public static YoModalPanelInterface.YoModalPanel createSmallPanel(Context context, String title, String message) {
        return YoModalPanelBuilder.createStandard(context)
                .setHeightRatio(ModalPanelConstants.SMALL_HEIGHT_RATIO.getFloatValue())
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建中等高度面板
     */
    public static YoModalPanelInterface.YoModalPanel createMediumPanel(Context context, String title, String message) {
        return YoModalPanelBuilder.createStandard(context)
                .setHeightRatio(ModalPanelConstants.MEDIUM_HEIGHT_RATIO.getFloatValue())
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建大高度面板
     */
    public static YoModalPanelInterface.YoModalPanel createLargePanel(Context context, String title, String message) {
        return YoModalPanelBuilder.createStandard(context)
                .setHeightRatio(ModalPanelConstants.LARGE_HEIGHT_RATIO.getFloatValue())
                .setTitle(title)
                .setMessage(message)
                .build();
    }

    /**
     * 创建全屏面板
     */
    public static YoModalPanelInterface.YoModalPanel createFullScreenPanel(Context context, String title) {
        return YoModalPanelBuilder.createStandard(context)
                .setHeightRatio(ModalPanelConstants.FULL_SCREEN_HEIGHT_RATIO.getFloatValue())
                .setShowDragBar(false)
                .setTitle(title)
                .build();
    }

    // ==================== 快速显示方法 ====================

    /**
     * 显示简单的底部信息面板
     */
    public static void showBottomInfo(Context context, String title, String message) {
        createBottomPanel(context, title, message).showPanel();
    }

    /**
     * 显示简单的顶部通知面板
     */
    public static void showTopNotification(Context context, String title, String message) {
        createNotification(context, title, message).showPanel();
    }

    /**
     * 显示警告面板
     */
    public static void showAlert(Context context, String title, String message) {
        createAlert(context, title, message).showPanel();
    }

    /**
     * 显示自定义内容面板
     */
    public static void showCustomContent(Context context, View content) {
        createBottomPanelWithContent(context, content).showPanel();
    }

    // ==================== 带回调的快速显示方法 ====================

    /**
     * 显示带生命周期回调的通知面板
     */
    public static YoModalPanelInterface.YoModalPanel showNotificationWithCallback(
            Context context, 
            String title, 
            String message,
            YoModalPanelInterface.OnPanelLifecycleListener callback) {
        
        YoModalPanelInterface.YoModalPanel panel = YoModalPanelBuilder.createNotification(context)
                .setTitle(title)
                .setMessage(message)
                .setOnLifecycleListener(callback)
                .build();
        
        panel.showPanel();
        return panel;
    }

    /**
     * 显示带交互回调的选择面板
     */
    public static YoModalPanelInterface.YoModalPanel showSelectionWithCallback(
            Context context,
            String title,
            View contentView,
            YoModalPanelInterface.OnPanelInteractionListener callback) {
        
        YoModalPanelInterface.YoModalPanel panel = YoModalPanelBuilder.createSelection(context)
                .setTitle(title)
                .addContentView(contentView)
                .setOnInteractionListener(callback)
                .build();
        
        panel.showPanel();
        return panel;
    }

    // ==================== 预设配置获取方法 ====================

    /**
     * 获取通知面板预设配置
     */
    public static YoModalPanelInterface.PanelConfig getNotificationConfig() {
        return new YoModalPanelInterface.PanelConfig()
                .setType(YoModalPanelInterface.PanelType.NOTIFICATION)
                .setDirection(YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM)
                .setHeightRatio(ModalPanelConstants.NOTIFICATION_HEIGHT_RATIO.getFloatValue())
                .setShowDragBar(false)
                .setMaskAlpha(ModalPanelConstants.LOW_MASK_ALPHA.getFloatValue())
                .setAnimationDuration(ModalPanelConstants.FAST_ANIMATION_DURATION.getLongValue());
    }

    /**
     * 获取警告面板预设配置
     */
    public static YoModalPanelInterface.PanelConfig getAlertConfig() {
        return new YoModalPanelInterface.PanelConfig()
                .setType(YoModalPanelInterface.PanelType.ALERT)
                .setDirection(YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM)
                .setHeightRatio(ModalPanelConstants.SMALL_HEIGHT_RATIO.getFloatValue())
                .setShowDragBar(false)
                .setMaskAlpha(ModalPanelConstants.HIGH_MASK_ALPHA.getFloatValue())
                .setAnimationDuration(ModalPanelConstants.SLOW_ANIMATION_DURATION.getLongValue());
    }

    /**
     * 获取选择面板预设配置
     */
    public static YoModalPanelInterface.PanelConfig getSelectionConfig() {
        return new YoModalPanelInterface.PanelConfig()
                .setType(YoModalPanelInterface.PanelType.SELECTION)
                .setDirection(YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP)
                .setHeightRatio(ModalPanelConstants.MEDIUM_HEIGHT_RATIO.getFloatValue())
                .setShowDragBar(true)
                .setMaskAlpha(ModalPanelConstants.MEDIUM_MASK_ALPHA.getFloatValue())
                .setEnableDamping(true);
    }

    /**
     * 获取设置面板预设配置
     */
    public static YoModalPanelInterface.PanelConfig getSettingsConfig() {
        return new YoModalPanelInterface.PanelConfig()
                .setType(YoModalPanelInterface.PanelType.SETTINGS)
                .setDirection(YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP)
                .setHeightRatio(ModalPanelConstants.LARGE_HEIGHT_RATIO.getFloatValue())
                .setShowDragBar(false)
                .setMaskAlpha(ModalPanelConstants.HIGH_MASK_ALPHA.getFloatValue())
                .setCornerRadius(ModalPanelConstants.LARGE_CORNER_RADIUS_DP.getFloatValue())
                .setEnableDamping(false);
    }

    /**
     * 获取预览面板预设配置
     */
    public static YoModalPanelInterface.PanelConfig getPreviewConfig() {
        return new YoModalPanelInterface.PanelConfig()
                .setType(YoModalPanelInterface.PanelType.PREVIEW)
                .setDirection(YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP)
                .setHeightRatio(ModalPanelConstants.SMALL_HEIGHT_RATIO.getFloatValue())
                .setShowDragBar(true)
                .setMaskAlpha(ModalPanelConstants.LOW_MASK_ALPHA.getFloatValue())
                .setCornerRadius(ModalPanelConstants.SMALL_CORNER_RADIUS_DP.getFloatValue())
                .setAnimationDuration(ModalPanelConstants.FAST_ANIMATION_DURATION.getLongValue());
    }

    // ==================== 构建器获取方法 ====================

    /**
     * 获取标准构建器
     */
    public static YoModalPanelBuilder getStandardBuilder(Context context) {
        return YoModalPanelBuilder.createStandard(context);
    }

    /**
     * 获取通知构建器
     */
    public static YoModalPanelBuilder getNotificationBuilder(Context context) {
        return YoModalPanelBuilder.createNotification(context);
    }

    /**
     * 获取警告构建器
     */
    public static YoModalPanelBuilder getAlertBuilder(Context context) {
        return YoModalPanelBuilder.createAlert(context);
    }

    /**
     * 获取选择构建器
     */
    public static YoModalPanelBuilder getSelectionBuilder(Context context) {
        return YoModalPanelBuilder.createSelection(context);
    }

    /**
     * 获取设置构建器
     */
    public static YoModalPanelBuilder getSettingsBuilder(Context context) {
        return YoModalPanelBuilder.createSettings(context);
    }

    /**
     * 获取预览构建器
     */
    public static YoModalPanelBuilder getPreviewBuilder(Context context) {
        return YoModalPanelBuilder.createPreview(context);
    }

    /**
     * 获取自定义构建器
     */
    public static YoModalPanelBuilder getCustomBuilder(Context context) {
        return YoModalPanelBuilder.createCustom(context);
    }

    // ==================== 链式调用便利方法 ====================

    /**
     * 创建底部面板的链式调用入口
     */
    public static BottomPanelChain bottom(Context context) {
        return new BottomPanelChain(context);
    }

    /**
     * 创建顶部面板的链式调用入口
     */
    public static TopPanelChain top(Context context) {
        return new TopPanelChain(context);
    }

    /**
     * 底部面板链式调用类
     */
    public static class BottomPanelChain {
        private final YoModalPanelBuilder builder;

        public BottomPanelChain(Context context) {
            this.builder = YoModalPanelBuilder.createStandard(context)
                    .setDirection(YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP);
        }

        public BottomPanelChain title(String title) {
            builder.setTitle(title);
            return this;
        }

        public BottomPanelChain message(String message) {
            builder.setMessage(message);
            return this;
        }

        public BottomPanelChain height(float ratio) {
            builder.setHeightRatio(ratio);
            return this;
        }

        public BottomPanelChain content(View view) {
            builder.addContentView(view);
            return this;
        }

        public BottomPanelChain dragBar(boolean show) {
            builder.setShowDragBar(show);
            return this;
        }

        public BottomPanelChain dragBarTouchScale(boolean enable) {
            builder.setEnableDragBarTouchScale(enable);
            return this;
        }

        public BottomPanelChain onLifecycle(YoModalPanelInterface.OnPanelLifecycleListener listener) {
            builder.setOnLifecycleListener(listener);
            return this;
        }

        public BottomPanelChain onInteraction(YoModalPanelInterface.OnPanelInteractionListener listener) {
            builder.setOnInteractionListener(listener);
            return this;
        }

        public YoModalPanelInterface.YoModalPanel build() {
            return builder.build();
        }

        public YoModalPanelInterface.YoModalPanel show() {
            YoModalPanelInterface.YoModalPanel panel = build();
            panel.showPanel();
            return panel;
        }
    }

    /**
     * 顶部面板链式调用类
     */
    public static class TopPanelChain {
        private final YoModalPanelBuilder builder;

        public TopPanelChain(Context context) {
            this.builder = YoModalPanelBuilder.createStandard(context)
                    .setDirection(YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM);
        }

        public TopPanelChain title(String title) {
            builder.setTitle(title);
            return this;
        }

        public TopPanelChain message(String message) {
            builder.setMessage(message);
            return this;
        }

        public TopPanelChain height(float ratio) {
            builder.setHeightRatio(ratio);
            return this;
        }

        public TopPanelChain content(View view) {
            builder.addContentView(view);
            return this;
        }

        public TopPanelChain dragBar(boolean show) {
            builder.setShowDragBar(show);
            return this;
        }

        public TopPanelChain offset(float offsetDp) {
            builder.setTopOffset(offsetDp);
            return this;
        }

        public TopPanelChain dragBarTouchScale(boolean enable) {
            builder.setEnableDragBarTouchScale(enable);
            return this;
        }

        public TopPanelChain onLifecycle(YoModalPanelInterface.OnPanelLifecycleListener listener) {
            builder.setOnLifecycleListener(listener);
            return this;
        }

        public TopPanelChain onInteraction(YoModalPanelInterface.OnPanelInteractionListener listener) {
            builder.setOnInteractionListener(listener);
            return this;
        }

        public YoModalPanelInterface.YoModalPanel build() {
            return builder.build();
        }

        public YoModalPanelInterface.YoModalPanel show() {
            YoModalPanelInterface.YoModalPanel panel = build();
            panel.showPanel();
            return panel;
        }
    }
}

package com.yovo.yotheme.components.yo_model_panel;

import android.view.View;
import android.graphics.drawable.Drawable;

import com.yovo.yotheme.constants.ModalPanelConstants;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 模态面板组件对外接口定义
 * @note 参考 YoListInterface 架构设计，提供统一的模态面板管理体系
 */
public interface YoModalPanelInterface {

    /**
     * 面板管理器 - 负责面板的显示、隐藏和状态管理
     */
    interface PanelManager {
        boolean showPanel();
        boolean hidePanel();
        boolean isShowing();
        void dismiss();
        PanelState getCurrentState();
        void setState(PanelState state);
    }

    /**
     * 面板外观管理器 - 负责面板的样式和布局配置
     */
    interface PanelAppearanceManager {
        void setDirection(PanelDirection direction);
        void setHeightRatio(float ratio);
        void setCornerRadius(float radiusDp);
        void setMaskAlpha(float alpha);
        void setShowDragBar(boolean show);
        void setTopOffset(float offsetDp);
        void setAnimationDuration(long duration);
        void setEnableDamping(boolean enable);
    }

    /**
     * 面板内容管理器 - 负责面板内容的添加和管理
     */
    interface PanelContentManager {
        void setTitle(String title);
        void setMessage(String message);
        void addContentView(View view);
        void removeContentView(View view);
        void clearContent();
        void setHeaderView(View headerView);
        void setFooterView(View footerView);
        List<View> getAllContentViews();
    }

    /**
     * 面板事件监听器 - 处理用户交互和生命周期事件
     */
    interface PanelEventListener {
        void setOnPanelStateChangeListener(OnPanelStateChangeListener listener);
        void setOnPanelInteractionListener(OnPanelInteractionListener listener);
        void setOnPanelLifecycleListener(OnPanelLifecycleListener listener);
    }

    /**
     * 面板状态变化监听器
     */
    interface OnPanelStateChangeListener {
        void onStateChanged(PanelState oldState, PanelState newState);
    }

    /**
     * 面板交互监听器
     */
    interface OnPanelInteractionListener {
        /**
         * 面板拖拽时调用
         * @param progress 拖拽进度 (0.0 - 1.0)
         * @param velocity 拖拽速度 (px/ms)
         */
        void onPanelDragging(float progress, float velocity);
        
        /**
         * 用户尝试关闭面板时调用
         * @param reason 关闭原因
         * @return true表示允许关闭，false表示阻止关闭
         */
        boolean onPanelShouldDismiss(DismissReason reason);
        
        /**
         * 面板被点击时调用
         * @param view 被点击的视图
         */
        void onPanelClicked(View view);
        
        /**
         * 面板外部区域被点击时调用
         */
        void onOutsideClicked();
    }

    /**
     * 面板生命周期监听器
     */
    interface OnPanelLifecycleListener {
        void onPanelWillShow();
        void onPanelDidShow();
        void onPanelWillHide();
        void onPanelDidHide();
        void onPanelCreated();
        void onPanelDestroyed();
    }

    /**
     * 面板配置类 - 统一的配置管理
     */
    class PanelConfig {
        public PanelDirection direction = PanelDirection.BOTTOM_TO_TOP;
        public float heightRatio = ModalPanelConstants.DEFAULT_HEIGHT_RATIO.getFloatValue();
        public float cornerRadius = ModalPanelConstants.DEFAULT_CORNER_RADIUS_DP.getFloatValue();
        public float maskAlpha = ModalPanelConstants.DEFAULT_MASK_ALPHA.getFloatValue();
        public boolean showDragBar = true;
        public boolean cancelableOnTouchOutside = true;
        public boolean cancelable = true;
        public boolean enableDamping = true;
        /** 是否启用拖拽条触碰放缩动画 */
        public boolean enableDragBarTouchScale = true;
        public float topOffset = 0f;
        public long animationDuration = ModalPanelConstants.DEFAULT_ANIMATION_DURATION.getLongValue();
        public PanelType type = PanelType.STANDARD;
        public PanelStyle style = PanelStyle.DEFAULT;
        public View customContentView;
        
        // 内容配置
        public String title;
        public String message;
        public View headerView;
        public View footerView;
        
        // 样式配置
        public float titleTextSize = 16f;
        public float messageTextSize = 14f;
        public float contentPadding = 20f;
        public float titleMessageGap = 12f;
        
        public PanelConfig() {}
        
        // Builder模式方法
        public PanelConfig setDirection(PanelDirection direction) {
            this.direction = direction;
            return this;
        }
        
        public PanelConfig setHeightRatio(float ratio) {
            this.heightRatio = Math.max(0.1f, Math.min(1.0f, ratio));
            return this;
        }
        
        public PanelConfig setCornerRadius(float radius) {
            this.cornerRadius = Math.max(0f, radius);
            return this;
        }
        
        public PanelConfig setMaskAlpha(float alpha) {
            this.maskAlpha = Math.max(0f, Math.min(1f, alpha));
            return this;
        }
        
        public PanelConfig setShowDragBar(boolean show) {
            this.showDragBar = show;
            return this;
        }
        
        public PanelConfig setCancelableOnTouchOutside(boolean cancelable) {
            this.cancelableOnTouchOutside = cancelable;
            return this;
        }
        
        public PanelConfig setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }
        
        public PanelConfig setEnableDamping(boolean enable) {
            this.enableDamping = enable;
            return this;
        }
        
        /**
         * 设置是否启用拖拽条触碰放缩动画
         */
        public PanelConfig setEnableDragBarTouchScale(boolean enable) {
            this.enableDragBarTouchScale = enable;
            return this;
        }
        
        public PanelConfig setTopOffset(float offset) {
            this.topOffset = Math.max(0f, offset);
            return this;
        }
        
        public PanelConfig setAnimationDuration(long duration) {
            this.animationDuration = Math.max(100L, duration);
            return this;
        }
        
        public PanelConfig setType(PanelType type) {
            this.type = type;
            return this;
        }
        
        public PanelConfig setStyle(PanelStyle style) {
            this.style = style;
            return this;
        }
        
        public PanelConfig setTitle(String title) {
            this.title = title;
            return this;
        }
        
        public PanelConfig setMessage(String message) {
            this.message = message;
            return this;
        }
        
        public PanelConfig setCustomContentView(View view) {
            this.customContentView = view;
            return this;
        }
        
        public PanelConfig setHeaderView(View view) {
            this.headerView = view;
            return this;
        }
        
        public PanelConfig setFooterView(View view) {
            this.footerView = view;
            return this;
        }
    }

    /**
     * 面板状态枚举
     */
    enum PanelState {
        HIDDEN,     // 隐藏状态
        SHOWING,    // 正在显示
        SHOWN,      // 已显示
        HIDING,     // 正在隐藏
        DRAGGING,   // 正在拖拽
        ERROR       // 错误状态
    }

    /**
     * 面板方向枚举
     */
    enum PanelDirection {
        BOTTOM_TO_TOP,    // 从底部向上滑入
        TOP_TO_BOTTOM,    // 从顶部向下滑入
        LEFT_TO_RIGHT,    // 从左侧向右滑入（预留）
        RIGHT_TO_LEFT     // 从右侧向左滑入（预留）
    }

    /**
     * 面板类型枚举
     */
    enum PanelType {
        STANDARD,       // 标准面板
        NOTIFICATION,   // 通知面板
        ALERT,          // 警告面板
        SELECTION,      // 选择面板
        SETTINGS,       // 设置面板
        PREVIEW,        // 预览面板
        CUSTOM          // 自定义面板
    }

    /**
     * 面板样式枚举
     */
    enum PanelStyle {
        DEFAULT,        // 默认样式
        COMPACT,        // 紧凑样式
        EXPANDED,       // 扩展样式
        MINIMAL,        // 最小样式
        CARD,           // 卡片样式
        OVERLAY         // 覆盖样式
    }

    /**
     * 关闭原因枚举
     */
    enum DismissReason {
        BACK_PRESSED,           // 返回键
        OUTSIDE_TOUCH,          // 点击外部
        DRAG_DISMISS,           // 拖拽关闭
        PROGRAMMATIC,           // 程序调用
        TIMEOUT,                // 超时
        USER_ACTION             // 用户操作
    }

    /**
     * 面板构建器接口
     */
    interface YoModalPanelBuilder {
        YoModalPanelBuilder setConfig(PanelConfig config);
        YoModalPanelBuilder setDirection(PanelDirection direction);
        YoModalPanelBuilder setHeightRatio(float ratio);
        YoModalPanelBuilder setType(PanelType type);
        YoModalPanelBuilder setStyle(PanelStyle style);
        YoModalPanelBuilder setTitle(String title);
        YoModalPanelBuilder setMessage(String message);
        YoModalPanelBuilder addContentView(View view);
        YoModalPanelBuilder setOnStateChangeListener(OnPanelStateChangeListener listener);
        YoModalPanelBuilder setOnInteractionListener(OnPanelInteractionListener listener);
        YoModalPanelBuilder setOnLifecycleListener(OnPanelLifecycleListener listener);
        YoModalPanel build();
    }

    /**
     * 主要的模态面板接口
     */
    interface YoModalPanel extends PanelManager, PanelAppearanceManager, 
                                  PanelContentManager, PanelEventListener {
        PanelConfig getConfig();
        void updateConfig(PanelConfig config);
        View getRootView();
        View getContentContainer();
    }
}

package com.yovo.yotheme.components.yo_navigation.nav_structure;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.yovo.yotheme.constants.NavigationConstants;
import com.yovo.yotheme.small_widget.animation.SmoothScaleAnimator;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航项容器组件
 * @note 专门负责导航项的布局容器，包含图标、标题、徽章等子组件
 * @note 单一职责：只处理子组件的布局和容器相关逻辑
 */
public class NavigationItemContainer extends LinearLayout {

    /**
     * 容器配置类
     */
    public static class ContainerConfig {
        public int paddingHorizontal = NavigationConstants.ITEM_PADDING_HORIZONTAL_DP.getIntValue();
        public int paddingVertical = NavigationConstants.ITEM_PADDING_VERTICAL_DP.getIntValue();
        public int iconMarginBottom = NavigationConstants.ICON_MARGIN_BOTTOM_DP.getIntValue();
        public int gravity = Gravity.CENTER;
        public boolean enabled = true;
        public boolean clickable = true;

        public ContainerConfig setPaddingHorizontal(int padding) {
            this.paddingHorizontal = padding;
            return this;
        }

        public ContainerConfig setPaddingVertical(int padding) {
            this.paddingVertical = padding;
            return this;
        }

        public ContainerConfig setIconMarginBottom(int margin) {
            this.iconMarginBottom = margin;
            return this;
        }

        public ContainerConfig setGravity(int gravity) {
            this.gravity = gravity;
            return this;
        }

        public ContainerConfig setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ContainerConfig setClickable(boolean clickable) {
            this.clickable = clickable;
            return this;
        }
    }

    private Context context;
    private ContainerConfig config;
    private int itemId = -1;
    private boolean isSelected = false;

    // 子组件
    private NavigationIcon iconComponent;
    private NavigationTitle titleComponent;
    private NavigationBadge badgeComponent;

    // 状态和动画管理器
    private NavigationStateManager stateManager;
    private NavigationAnimationManager animationManager;
    private SmoothScaleAnimator smoothScaleAnimator;

    // 监听器
    private OnContainerClickListener clickListener;
    private OnContainerStateChangeListener stateChangeListener;

    public NavigationItemContainer(Context context) {
        this(context, null);
    }

    public NavigationItemContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NavigationItemContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    private void init() {
        // 设置默认配置
        config = new ContainerConfig();

        // 设置布局方向为垂直
        setOrientation(VERTICAL);
        setGravity(config.gravity);

        // 设置内边距
        updatePadding();

        // 初始化管理器（必须在设置交互状态之前）
        initManagers();

        // 设置交互状态
        setClickable(config.clickable);
        setFocusable(config.clickable);
        setEnabled(config.enabled);

        // 初始化子组件
        initChildComponents();
    }

    private void initChildComponents() {

        FrameLayout iconContainer = new FrameLayout(context);
        LayoutParams iconContainerParams = new LayoutParams(
                dpToPx(45),
                LayoutParams.WRAP_CONTENT
        );

        iconContainer.setLayoutParams(iconContainerParams);

        LinearLayout contentContainer = new LinearLayout(context);
        // 修改：使用FrameLayout.LayoutParams并设置居中gravity
        FrameLayout.LayoutParams contentContainerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        contentContainerParams.gravity = Gravity.CENTER;
        contentContainer.setGravity(config.gravity);
        contentContainer.setOrientation(VERTICAL);
        contentContainer.setLayoutParams(contentContainerParams);

        iconContainer.addView(contentContainer);

        // 创建图标组件
        iconComponent = new NavigationIcon(context);
        LayoutParams iconParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        iconComponent.setLayoutParams(iconParams);
        contentContainer.addView(iconComponent);

        // 在添加到父容器后，确保图标尺寸正确设置
        iconComponent.updateSize();

        // 创建标题组件
        titleComponent = new NavigationTitle(context);
        LayoutParams titleParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        // 缩小图标与文本之间的间距：在默认配置基础上减去2dp，但不小于0
        int baseMargin = dpToPx(config.iconMarginBottom);
        int reducePx = dpToPx(2);
        int compactMargin = Math.max(0, baseMargin - reducePx);
        titleParams.topMargin = compactMargin;
        titleComponent.setLayoutParams(titleParams);
        contentContainer.addView(titleComponent);

        // 创建徽章组件
        badgeComponent = new NavigationBadge(context);
        // 设置徽章位置在FrameLayout右上角（使用FrameLayout.LayoutParams确保gravity生效）
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        badgeParams.gravity = Gravity.TOP | Gravity.END;
        badgeComponent.setLayoutParams(badgeParams);

        iconContainer.addView(badgeComponent);

        addView(iconContainer);
    }

    private void initManagers() {
        // 初始化状态管理器
        stateManager = new NavigationStateManager();
        stateManager.setOnStateChangeListener(state -> {
            updateComponentStates(state);
            if (stateChangeListener != null) {
                stateChangeListener.onStateChanged(this, state);
            }
        });

        // 初始化动画管理器
        animationManager = new NavigationAnimationManager(this);
        // 初始化平滑缩放动画器
        smoothScaleAnimator = new SmoothScaleAnimator(this).setDuration(180);
    }

    /**
     * 设置容器配置
     *
     * @param config 容器配置
     */
    public void setContainerConfig(ContainerConfig config) {
        this.config = config;
        updatePadding();
        setEnabled(config.enabled);
        setClickable(config.clickable);
        setGravity(config.gravity);

        // 更新图标下边距
        if (titleComponent != null) {
            LayoutParams titleParams = (LayoutParams) titleComponent.getLayoutParams();
            titleParams.topMargin = dpToPx(config.iconMarginBottom);
            titleComponent.setLayoutParams(titleParams);
        }
    }

    /**
     * 设置导航项数据
     *
     * @param itemId  项目ID
     * @param iconRes 图标资源
     * @param title   标题文本
     */
    public void setNavigationData(int itemId, Object iconRes, String title) {
        this.itemId = itemId;

        if (iconComponent != null) {
            iconComponent.setIcon(iconRes);
        }

        if (titleComponent != null) {
            titleComponent.setTitle(title);
        }

        if (stateManager != null) {
            stateManager.setItemId(itemId);
        }
    }

    /**
     * 设置选中状态
     *
     * @param selected 是否选中
     * @param animated 是否显示动画
     */
    public void setSelected(boolean selected, boolean animated) {
        if (this.isSelected == selected) return;

        this.isSelected = selected;

        if (stateManager != null) {
            NavigationStateManager.NavigationState newState = stateManager.getCurrentState();
            newState.isSelected = selected;
            newState.scale = selected ? 1.08f : 1.0f; // 设置缩放比例

            if (animated && animationManager != null) {
                animationManager.animateToState(newState);
            } else {
                stateManager.setState(newState);
            }
        }
    }

    /**
     * 设置启用状态
     *
     * @param enabled 是否启用
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        config.enabled = enabled;

        if (stateManager != null) {
            NavigationStateManager.NavigationState newState = stateManager.getCurrentState();
            newState.isEnabled = enabled;
            stateManager.setState(newState);
        }
    }

    /**
     * 显示徽章
     *
     * @param text 徽章文本，为null时显示圆点
     */
    public void showBadge(String text) {
        if (badgeComponent != null) {
            badgeComponent.showBadge(text);
        }
    }

    /**
     * 隐藏徽章
     */
    public void hideBadge() {
        if (badgeComponent != null) {
            badgeComponent.hideBadge();
        }
    }

    /**
     * 获取图标组件
     *
     * @return 图标组件
     */
    public NavigationIcon getIconComponent() {
        return iconComponent;
    }

    /**
     * 获取标题组件
     *
     * @return 标题组件
     */
    public NavigationTitle getTitleComponent() {
        return titleComponent;
    }

    /**
     * 获取徽章组件
     *
     * @return 徽章组件
     */
    public NavigationBadge getBadgeComponent() {
        return badgeComponent;
    }

    /**
     * 获取状态管理器
     *
     * @return 状态管理器
     */
    public NavigationStateManager getStateManager() {
        return stateManager;
    }

    /**
     * 获取动画管理器
     *
     * @return 动画管理器
     */
    public NavigationAnimationManager getAnimationManager() {
        return animationManager;
    }

    /**
     * 获取项目ID
     *
     * @return 项目ID
     */
    public int getItemId() {
        return itemId;
    }

    /**
     * 是否选中
     *
     * @return 是否选中
     */
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * 设置点击监听器
     *
     * @param listener 点击监听器
     */
    public void setOnContainerClickListener(OnContainerClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * 设置状态变化监听器
     *
     * @param listener 状态变化监听器
     */
    public void setOnContainerStateChangeListener(OnContainerStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !config.clickable) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 去除按压时的回弹效果
                return true;

            case MotionEvent.ACTION_UP:
                // 去除按压结束动画，直接处理点击事件
                if (clickListener != null) {
                    clickListener.onContainerClick(this, itemId);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                // 去除取消动画
                return true;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 更新所有子组件状态
     */
    private void updateComponentStates(NavigationStateManager.NavigationState state) {
        if (iconComponent != null) {
            iconComponent.updateState(state);
        }

        if (titleComponent != null) {
            titleComponent.updateState(state);
        }

        // 根据选中状态设置缩放比例（丝滑动画）
        float targetScale = state.isSelected ? 1.08f : 1.0f; // 选中时放大到1.08倍（稍微放大）
        if (smoothScaleAnimator != null) {
            smoothScaleAnimator.animateTo(targetScale);
        } else {
            setScaleX(targetScale);
            setScaleY(targetScale);
        }

        // 去除容器级透明度衰减，统一保持不透明，避免整体"蒙灰"效果
        setAlpha(1.0f);
    }

    /**
     * 更新内边距
     */
    private void updatePadding() {
        int paddingH = dpToPx(config.paddingHorizontal);
        int paddingV = dpToPx(config.paddingVertical);
        setPadding(paddingH, paddingV, paddingH, paddingV);
    }

    /**
     * 更新徽章位置
     */
    private void updateBadgePosition() {
        if (iconComponent != null && badgeComponent != null) {
            int offsetX = dpToPx(NavigationConstants.BADGE_OFFSET_X_DP.getIntValue());
            int offsetY = dpToPx(NavigationConstants.BADGE_OFFSET_Y_DP.getIntValue());

            badgeComponent.setTranslationX(iconComponent.getRight() + offsetX);
            badgeComponent.setTranslationY(iconComponent.getTop() + offsetY);
        }
    }

    /**
     * dp转px工具方法
     */
    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 容器点击监听器
     */
    public interface OnContainerClickListener {
        void onContainerClick(NavigationItemContainer container, int itemId);
    }

    /**
     * 容器状态变化监听器
     */
    public interface OnContainerStateChangeListener {
        void onStateChanged(NavigationItemContainer container, NavigationStateManager.NavigationState state);
    }

    /**
     * 预设容器配置
     */
    public static class Presets {

        /**
         * 标准导航项容器配置
         */
        public static ContainerConfig standard() {
            return new ContainerConfig();
        }

        /**
         * 紧凑型导航项容器配置
         */
        public static ContainerConfig compact() {
            return new ContainerConfig()
                    .setPaddingHorizontal(NavigationConstants.ITEM_PADDING_HORIZONTAL_COMPACT_DP.getIntValue())
                    .setPaddingVertical(NavigationConstants.ITEM_PADDING_VERTICAL_COMPACT_DP.getIntValue())
                    .setIconMarginBottom(NavigationConstants.ICON_MARGIN_BOTTOM_COMPACT_DP.getIntValue());
        }

        /**
         * 大型导航项容器配置
         */
        public static ContainerConfig large() {
            return new ContainerConfig()
                    .setPaddingHorizontal(NavigationConstants.ITEM_PADDING_HORIZONTAL_LARGE_DP.getIntValue())
                    .setPaddingVertical(NavigationConstants.ITEM_PADDING_VERTICAL_LARGE_DP.getIntValue())
                    .setIconMarginBottom(NavigationConstants.ICON_MARGIN_BOTTOM_LARGE_DP.getIntValue());
        }
    }
}

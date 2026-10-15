package com.yovo.yotheme.components.yo_menu;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.yovo.yotheme.constants.MenuConstants;
import com.yovo.yotheme.components.yo_menu.menu_structure.MenuBackgroundManager;
import com.yovo.yotheme.components.yo_menu.menu_structure.MenuDividerRenderer;
import com.yovo.yotheme.components.yo_menu.menu_structure.MenuItemContainer;
import com.yovo.yotheme.components.yo_menu.menu_structure.MenuLayoutManager;
import com.yovo.yotheme.components.yo_menu.menu_structure.MenuPopupPositioner;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.BarState;
import com.yovo.yotheme.components.yo_scrollbar.YoScrollBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description YoMenu 菜单主容器组件
 * @note 提供菜单管理、外观设置、事件监听等功能，支持滑动
 */
public class YoMenu extends FrameLayout implements YoMenuInterface.MenuManager, YoMenuInterface.MenuAppearanceManager, YoMenuInterface.MenuEventListener {

    // 数据和状态
    private final List<MenuItemContainer> menuItemViews = new ArrayList<>();
    private boolean showDivider = true;
    private Integer itemPaddingVerticalDp = null; // null表示使用默认值
    private boolean showScrollBar = true; // 是否显示滚动条

    // 事件监听
    private YoMenuInterface.OnMenuItemClickListener onMenuItemClickListener;

    // 内部组件
    private FrameLayout contentContainer; // 内容容器（包含ScrollView和滚动条）
    private ScrollView scrollView;
    private MenuContentLayout menuContentLayout;
    private YoScrollBar scrollBar;

    // 管理器
    private MenuBackgroundManager backgroundManager;
    private MenuLayoutManager layoutManager;
    private MenuPopupPositioner popupPositioner;
    private MenuDividerRenderer dividerRenderer;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建一个新的 YoMenu 实例
     *
     * @param context 上下文对象
     * @return YoMenu 实例
     */
    public static YoMenu create(Context context) {
        return new YoMenu(context);
    }

    /**
     * 使用YoMenuBuilder创建标准菜单（推荐方式）
     *
     * @param context 上下文对象
     * @return YoMenuBuilder 菜单构建器
     */
    public static YoMenuBuilder builder(Context context) {
        return YoMenuBuilder.createStandard(context);
    }

    // ==================== 构造函数 ====================

    public YoMenu(Context context) {
        this(context, null);
    }

    public YoMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (scrollBar != null && scrollView != null) {
            scrollBar.bind(scrollView);
            scrollBar.post(this::invalidate);
        }
    }

    private void init(Context context) {
        // 创建内容容器（FrameLayout），用于包含ScrollView和滚动条
        contentContainer = new FrameLayout(context);
        
        // 初始化背景管理器
        backgroundManager = new MenuBackgroundManager(context, this, contentContainer);
        backgroundManager.initContentContainer();
        
        // 创建内容布局（LinearLayout）
        menuContentLayout = new MenuContentLayout(context);
        menuContentLayout.setOrientation(LinearLayout.VERTICAL);
        
        int paddingH = dpToPx(MenuConstants.MENU_PADDING_HORIZONTAL_DP.getIntValue());
        int paddingV = dpToPx(MenuConstants.MENU_PADDING_VERTICAL_DP.getIntValue());
        menuContentLayout.setPadding(paddingH, paddingV, paddingH, paddingV);
        
        // 创建滚动视图
        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false); // 隐藏默认滚动条
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER); // 禁用过度滚动效果
        scrollView.setClipToPadding(false); // 滚动视图也不裁剪padding
        scrollView.setClipChildren(false); // 允许子视图的圆角正常显示
        scrollView.addView(menuContentLayout, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));
        
        // 将滚动视图添加到内容容器
        contentContainer.addView(scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));
        
        // 创建并添加滚动条到内容容器
        scrollBar = new YoScrollBar(context);
        scrollBar.setBarState(BarState.AUTO);
        scrollBar.setContainerCornerRadiusDp(MenuConstants.MENU_CARD_CORNER_RADIUS_DP.getIntValue());
        LayoutParams scrollBarParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        contentContainer.addView(scrollBar, scrollBarParams);
        
        // 绑定滚动条到ScrollView
        scrollBar.bind(scrollView);
        
        // 将内容容器添加到外层容器
        addView(contentContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));
        
        // 初始化布局管理器
        layoutManager = new MenuLayoutManager(context, scrollView, contentContainer);
        
        // 初始化Popup定位器
        popupPositioner = new MenuPopupPositioner(context, backgroundManager.getShadowPadding());
        
        // 初始化分隔线渲染器
        dividerRenderer = new MenuDividerRenderer(context);
        
        // 应用背景和裁剪
        backgroundManager.applyBackground();
    }
    
    /**
     * 内部菜单内容布局类，用于绘制分隔线
     */
    private class MenuContentLayout extends LinearLayout {
        public MenuContentLayout(Context context) {
            super(context);
            setWillNotDraw(false);
        }
        
        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!showDivider || getChildCount() <= 1) return;
            dividerRenderer.drawDividers(this, canvas);
        }
    }

    // ---- MenuManager ----
    @Override
    public boolean addMenuItem(YoMenuInterface.MenuItemConfig itemConfig) {
        YoMenuInterface.MenuItemConfig config = Objects.requireNonNull(itemConfig, "Menu item config must not be null");
        
        MenuItemContainer container = new MenuItemContainer(getContext());
        
        // 设置水波纹层的margin（相对于菜单边界4dp）
        int rippleMargin = dpToPx(MenuConstants.MENU_ITEM_RIPPLE_MARGIN_DP.getIntValue());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(rippleMargin, 0, rippleMargin, 0);
        container.setLayoutParams(lp);
        
        // 设置自定义垂直内边距
        if (itemPaddingVerticalDp != null) {
            container.setCustomPaddingVertical(itemPaddingVerticalDp);
        }
        
        container.bindConfig(config);
        container.setOnClickListener(v -> {
            if (onMenuItemClickListener != null) {
                onMenuItemClickListener.onMenuItemClick(config.itemId);
            }
        });
        
        menuContentLayout.addView(container);
        menuItemViews.add(container);
        layoutManager.updateScrollViewHeight(menuItemViews, this::invalidate);
        return true;
    }

    @Override
    public boolean removeMenuItem(int itemId) {
        for (int i = 0; i < menuItemViews.size(); i++) {
            MenuItemContainer container = menuItemViews.get(i);
            if (container.getItemId() == itemId) {
                menuContentLayout.removeView(container);
                menuItemViews.remove(i);
                layoutManager.updateScrollViewHeight(menuItemViews, this::invalidate);
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearMenuItems() {
        menuContentLayout.removeAllViews();
        menuItemViews.clear();
        layoutManager.updateScrollViewHeight(menuItemViews, this::invalidate);
    }

    @Override
    public int getMenuItemCount() {
        return menuItemViews.size();
    }

    // ---- MenuAppearanceManager ----
    @Override
    public void setShowDivider(boolean show) {
        this.showDivider = show;
        invalidate();
    }

    /**
     * 设置菜单项垂直内边距（用于减小菜单行高）
     * @param paddingDp 垂直内边距（dp）
     */
    public void setItemPaddingVertical(int paddingDp) {
        this.itemPaddingVerticalDp = Math.max(0, paddingDp);
        // 如果已经有菜单项，更新它们的内边距
        for (MenuItemContainer container : menuItemViews) {
            container.setCustomPaddingVertical(paddingDp);
        }
        layoutManager.updateScrollViewHeight(menuItemViews, null);
    }

    /**
     * 设置最多可见的菜单项数量（超过时启用滑动）
     * @param maxItems 最大可见项数，默认为4
     */
    public void setMaxVisibleItems(int maxItems) {
        layoutManager.setMaxVisibleItems(maxItems);
        layoutManager.updateScrollViewHeight(menuItemViews, null);
    }

    /**
     * 设置是否显示滚动条
     * @param show 是否显示
     */
    public void setShowScrollBar(boolean show) {
        this.showScrollBar = show;
        if (scrollBar != null) {
            scrollBar.setBarState(show ? BarState.AUTO : BarState.OFF);
        }
    }

    /**
     * 获取是否显示滚动条
     */
    public boolean isShowScrollBar() {
        return showScrollBar;
    }

    // ---- MenuEventListener ----
    @Override
    public void setOnMenuItemClickListener(YoMenuInterface.OnMenuItemClickListener listener) {
        this.onMenuItemClickListener = listener;
    }

    /**
     * 开关卡片背景
     */
    public void setCardBackgroundEnabled(boolean enabled) {
        backgroundManager.setCardBackgroundEnabled(enabled);
    }

    /**
     * 设置菜单内容最大高度（px），用于弹窗空间适配。
     * @param maxHeightPx 内容最大高度，传入null表示清除限制
     */
    public void setMaxHeightPx(Integer maxHeightPx) {
        layoutManager.setMaxHeightPx(maxHeightPx);
        if (!menuItemViews.isEmpty()) {
            layoutManager.updateScrollViewHeightImmediate(menuItemViews);
        }
        requestLayout();
    }

    /**
     * 清除高度限制。
     */
    public void clearMaxHeightLimit() {
        setMaxHeightPx(null);
    }

    /**
     * 获取Popup宽度（dp）
     */
    public int getPopupWidthDp() {
        return popupPositioner.getPopupWidthDp();
    }

    /**
     * 设置Popup宽度（dp）
     */
    public void setPopupWidthDp(int widthDp) {
        popupPositioner.setPopupWidthDp(widthDp);
    }

    /**
     * 获取Popup Y轴偏移（dp）
     */
    public int getPopupYOffsetDp() {
        return popupPositioner.getPopupYOffsetDp();
    }

    /**
     * 设置Popup Y轴偏移（dp）
     */
    public void setPopupYOffsetDp(int yOffsetDp) {
        popupPositioner.setPopupYOffsetDp(yOffsetDp);
    }

    /**
     * 显示为右对齐的下拉菜单
     * @param popupWindow PopupWindow实例
     * @param anchorView 锚点视图
     */
    public void showAsDropDownRightAligned(android.widget.PopupWindow popupWindow, View anchorView) {
        popupPositioner.showAsDropDownRightAligned(popupWindow, anchorView);
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}



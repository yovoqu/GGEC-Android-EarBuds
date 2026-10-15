package com.yovo.yotheme.components.yo_navigation;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.background.shapeblurbiew.YoBlurBackgroundView;
import com.yovo.yotheme.components.yo_navigation.nav_structure.NavigationItemContainer;
import com.yovo.yotheme.constants.NavigationConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-12-02
 * @description 导航栏核心组件
 * @note 这是一个导航栏控件。
 */
public class YoNavigationBar extends FrameLayout {
    
    // ==================== 内部接口定义 ====================
    
    public interface OnItemSelectedListener {
        /**
         * 当导航项被选中时回调
         * @param itemId 选中的 Item ID
         * @return true 表示消费了事件
         */
        boolean onItemSelected(int itemId);
    }

    public interface OnItemReselectedListener {
        /**
         * 当导航项被重复选中时回调
         * @param itemId 重复选中的 Item ID
         */
        void onItemReselected(int itemId);
    }

    // ==================== 成员变量 ====================

    private FrameLayout backgroundContainer;
    private LinearLayout contentContainer;
    private View dividerView;
    
    private final List<NavigationItemContainer> itemContainers = new ArrayList<>();
    private int selectedItemId = -1;
    private int maxItems = NavigationConstants.MAX_NAVIGATION_ITEMS.getIntValue();
    private int targetHeight = 0;
    
    private OnItemSelectedListener onItemSelectedListener;
    private OnItemReselectedListener onItemReselectedListener;

    // ==================== 构造函数 ====================

    public YoNavigationBar(@NonNull Context context) {
        this(context, null);
    }

    public YoNavigationBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoNavigationBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 1. Background Layer
        backgroundContainer = new FrameLayout(context);
        addView(backgroundContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 2. Content Layer
        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(contentContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 3. Divider Layer
        dividerView = new View(context);
        dividerView.setBackgroundColor(Color.TRANSPARENT);
        dividerView.setVisibility(GONE);
        LayoutParams dividerParams = new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(0.5f));
        dividerParams.gravity = Gravity.TOP;
        addView(dividerView, dividerParams);

        // 默认高度设置
        int defaultHeight = dpToPx(NavigationConstants.DEFAULT_HEIGHT_DP.getIntValue());
        setNavigationHeightPx(defaultHeight);
    }

    // ==================== 核心布局逻辑 ====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        // 强制高度约束：如果父容器允许 wrap_content (AT_MOST) 或 UNSPECIFIED，
        // 且我们设定了 targetHeight，则强制使用 targetHeight。
        if (heightMode != MeasureSpec.EXACTLY && targetHeight > 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // ==================== 配置 API (Configuration) ====================

    /**
     * 设置导航栏高度
     * @param heightDp 高度 (dp)
     */
    public void setNavigationHeight(int heightDp) {
        setNavigationHeightPx(dpToPx(heightDp));
    }

    /**
     * 设置导航栏高度 (px)
     */
    public void setNavigationHeightPx(int heightPx) {
        this.targetHeight = heightPx;
        setMinimumHeight(heightPx);
        requestLayout();
    }

    /**
     * 设置最大 Item 数量限制
     */
    public void setMaxItemCount(int max) {
        this.maxItems = max;
    }

    /**
     * 配置分割线
     */
    public void setDivider(boolean show, @ColorInt int color) {
        dividerView.setVisibility(show ? VISIBLE : GONE);
        dividerView.setBackgroundColor(color);
    }

    // ==================== 背景 API (Background) ====================

    /**
     * 启用高斯模糊背景
     * @param radiusDp 模糊半径 (dp)
     * @param overlayColor 叠加颜色 (通常带透明度)
     */
    public void setBlurBackground(float radiusDp, @ColorInt int overlayColor) {
        backgroundContainer.removeAllViews();
        backgroundContainer.setBackground(null); // 清除普通背景
        
        YoBlurBackgroundView blurView = new YoBlurBackgroundView(getContext(), null);
        blurView.setBlurRadius(radiusDp);
        blurView.setOverlayColor(overlayColor);
        
        backgroundContainer.addView(blurView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /**
     * 设置普通纯色背景
     */
    public void setBackgroundColor(@ColorInt int color) {
        backgroundContainer.removeAllViews();
        backgroundContainer.setBackgroundColor(color);
    }

    /**
     * 设置背景 Drawable
     */
    public void setBackground(Drawable background) {
        backgroundContainer.removeAllViews();
        backgroundContainer.setBackground(background);
    }

    // ==================== Item 管理 API ====================

    /**
     * 添加一个导航项
     * @param itemId 唯一 ID
     * @param icon 图标 Drawable
     * @param title 标题
     * @return 是否添加成功
     */
    public boolean addItem(int itemId, Drawable icon, String title) {
        if (itemContainers.size() >= maxItems) return false;
        if (findContainerById(itemId) != null) return false;

        NavigationItemContainer container = new NavigationItemContainer(getContext());
        container.setNavigationData(itemId, icon, title);
        container.setOnContainerClickListener((c, id) -> setSelectedItem(id));

        // Layout Params: weight=1, height=match_parent
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
        params.weight = 1.0f; // 默认平分
        container.setLayoutParams(params);

        contentContainer.addView(container);
        itemContainers.add(container);
        return true;
    }

    /**
     * 移除导航项
     */
    public boolean removeItem(int itemId) {
        NavigationItemContainer container = findContainerById(itemId);
        if (container == null) return false;

        if (selectedItemId == itemId) selectedItemId = -1;
        contentContainer.removeView(container);
        itemContainers.remove(container);
        return true;
    }
    
    public void clearItems() {
        contentContainer.removeAllViews();
        itemContainers.clear();
        selectedItemId = -1;
    }

    // ==================== Item 状态 API ====================

    /**
     * 选中指定 Item
     */
    public void setSelectedItem(int itemId) {
        if (findContainerById(itemId) == null) return;

        if (selectedItemId == itemId) {
            if (onItemReselectedListener != null) {
                onItemReselectedListener.onItemReselected(itemId);
            }
            return;
        }

        // 更新选中状态
        for (NavigationItemContainer container : itemContainers) {
            container.setSelected(container.getItemId() == itemId, false);
        }
        selectedItemId = itemId;

        if (onItemSelectedListener != null) {
            onItemSelectedListener.onItemSelected(itemId);
        }
    }
    
    public int getSelectedItemId() {
        return selectedItemId;
    }

    // ==================== Item 样式 API ====================

    public void setItemBadge(int itemId, String badgeText) {
        NavigationItemContainer container = findContainerById(itemId);
        if (container != null) container.showBadge(badgeText);
    }

    public void hideItemBadge(int itemId) {
        NavigationItemContainer container = findContainerById(itemId);
        if (container != null) container.hideBadge();
    }

    public void setItemIconType(int itemId, int type) {
        NavigationItemContainer container = findContainerById(itemId);
        if (container != null) container.getIconComponent().setIconType(type);
    }
    
    public void setItemMultiColorIcon(int itemId, Drawable normal, Drawable selected) {
        NavigationItemContainer container = findContainerById(itemId);
        if (container != null) container.getIconComponent().setMultiColorIcon(normal, selected);
    }

    public NavigationItemContainer findContainerById(int itemId) {
        for (NavigationItemContainer c : itemContainers) {
            if (c.getItemId() == itemId) return c;
        }
        return null;
    }
    
    public int getItemCount() {
        return itemContainers.size();
    }

    // ==================== 监听器设置 ====================

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.onItemSelectedListener = listener;
    }

    public void setOnItemReselectedListener(OnItemReselectedListener listener) {
        this.onItemReselectedListener = listener;
    }

    // ==================== 工具方法 ====================

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getContext().getResources().getDisplayMetrics()
        );
    }
}

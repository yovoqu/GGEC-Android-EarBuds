package com.yovo.yotheme.components.yo_menu.menu_structure;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.yovo.yotheme.constants.MenuConstants;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description 菜单布局管理器
 * @note 负责管理滚动视图的高度限制和布局参数
 */
public class MenuLayoutManager {
    
    private final Context context;
    private final ScrollView scrollView;
    private final FrameLayout contentContainer;
    
    private int maxVisibleItems = 4; // 默认最多显示4行
    private Integer maxHeightPx = null; // 像素级高度限制，null表示不限制
    
    public MenuLayoutManager(Context context, ScrollView scrollView, FrameLayout contentContainer) {
        this.context = context;
        this.scrollView = scrollView;
        this.contentContainer = contentContainer;
    }
    
    /**
     * 设置最多可见的菜单项数量
     * @param maxItems 最大可见项数
     */
    public void setMaxVisibleItems(int maxItems) {
        this.maxVisibleItems = Math.max(1, maxItems);
    }
    
    /**
     * 获取最多可见的菜单项数量
     */
    public int getMaxVisibleItems() {
        return maxVisibleItems;
    }
    
    /**
     * 更新滚动视图的高度限制
     * @param menuItemViews 菜单项列表
     * @param postAction 延迟执行的操作
     */
    public void updateScrollViewHeight(List<MenuItemContainer> menuItemViews, Runnable postAction) {
        updateScrollViewHeightInternal(menuItemViews, postAction, true);
    }

    /**
     * 立即更新滚动视图高度（不使用post）。
     * @param menuItemViews 菜单项列表
     */
    public void updateScrollViewHeightImmediate(List<MenuItemContainer> menuItemViews) {
        updateScrollViewHeightInternal(menuItemViews, null, false);
    }

    private void updateScrollViewHeightInternal(List<MenuItemContainer> menuItemViews, Runnable postAction, boolean postToNextFrame) {
        if (menuItemViews.isEmpty()) {
            return;
        }
        
        // 延迟计算，等待菜单项完成测量
        Runnable updateTask = () -> {
            int itemCount = menuItemViews.size();
            if (itemCount == 0) return;
            
            // 测量单个菜单项的高度
            MenuItemContainer firstItem = menuItemViews.get(0);
            firstItem.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int itemHeight = firstItem.getMeasuredHeight();
            
            // 计算实际需要的高度（根据实际菜单项数量或最大可见数）
            int visibleItems = Math.min(itemCount, maxVisibleItems);
            int actualHeight = calculateHeight(itemHeight, visibleItems);
            
            // 设置固定高度
            setLimitedHeight(actualHeight);
        };
        
        if (postToNextFrame) {
            if (postAction != null) {
                scrollView.post(() -> {
                    updateTask.run();
                    postAction.run();
                });
            } else {
                scrollView.post(updateTask);
            }
        } else {
            updateTask.run();
            if (postAction != null) {
                postAction.run();
            }
        }
    }
    
    /**
     * 计算菜单高度
     * @param itemHeight 单个菜单项的高度
     * @param visibleItems 可见项数量
     * @return 菜单高度（px）
     */
    private int calculateHeight(int itemHeight, int visibleItems) {
        int paddingV = dpToPx(MenuConstants.MENU_PADDING_VERTICAL_DP.getIntValue());
        return itemHeight * visibleItems + paddingV * 2;
    }
    
    /**
     * 设置固定高度
     * @param height 高度（px）
     */
    private void setLimitedHeight(int height) {
        int targetHeight = height;
        if (maxHeightPx != null) {
            targetHeight = Math.min(height, maxHeightPx);
        }

        FrameLayout.LayoutParams scrollViewLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            targetHeight
        );
        scrollView.setLayoutParams(scrollViewLp);
        
        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            targetHeight
        );
        contentContainer.setLayoutParams(containerLp);
    }
    
    /**
     * 设置像素高度限制
     */
    public void setMaxHeightPx(Integer maxHeightPx) {
        if (maxHeightPx == null) {
            this.maxHeightPx = null;
        } else {
            this.maxHeightPx = Math.max(0, maxHeightPx);
        }
    }

    /**
     * dp转px
     */
    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
        );
    }
}


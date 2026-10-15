package com.yovo.yotheme.components.yo_menu;

import android.content.Context;

import com.yovo.yotheme.constants.MenuConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description YoMenuBuilder 菜单构建器
 * @note 提供便捷的菜单创建方式
 */
public class YoMenuBuilder {

    private final Context context;
    private boolean showDivider = true;
    private Integer itemPaddingVerticalDp = null; // null表示使用默认值
    private Integer maxVisibleItems = null; // null表示使用默认值（4行）
    private final List<YoMenuInterface.MenuItemConfig> menuItems = new ArrayList<>();

    public YoMenuBuilder(Context context) {
        this.context = context;
    }

    /**
     * 设置是否显示分隔线
     */
    public YoMenuBuilder setShowDivider(boolean show) {
        this.showDivider = show;
        return this;
    }

    /**
     * 设置菜单项垂直内边距（用于减小菜单行高）
     * @param paddingDp 垂直内边距（dp），默认值为12dp
     */
    public YoMenuBuilder setItemPaddingVertical(int paddingDp) {
        this.itemPaddingVerticalDp = Math.max(0, paddingDp);
        return this;
    }

    /**
     * 设置最多可见的菜单项数量（超过时启用滑动）
     * @param maxItems 最大可见项数，默认为4
     */
    public YoMenuBuilder setMaxVisibleItems(int maxItems) {
        this.maxVisibleItems = Math.max(1, maxItems);
        return this;
    }

    /**
     * 添加菜单项
     */
    public YoMenuBuilder addMenuItem(YoMenuInterface.MenuItemConfig config) {
        menuItems.add(Objects.requireNonNull(config, "Menu item config must not be null"));
        return this;
    }

    /**
     * 添加菜单项（便捷方法）
     */
    public YoMenuBuilder addMenuItem(int itemId, String text) {
        return addMenuItem(new YoMenuInterface.MenuItemConfig(itemId, text));
    }

    /**
     * 构建菜单
     */
    public YoMenu build() {
        YoMenu menu = new YoMenu(context);
        menu.setShowDivider(showDivider);
        if (itemPaddingVerticalDp != null) {
            menu.setItemPaddingVertical(itemPaddingVerticalDp);
        }
        if (maxVisibleItems != null) {
            menu.setMaxVisibleItems(maxVisibleItems);
        }
        for (YoMenuInterface.MenuItemConfig item : menuItems) {
            menu.addMenuItem(item);
        }
        return menu;
    }

    /**
     * 创建标准菜单构建器
     */
    public static YoMenuBuilder createStandard(Context context) {
        return new YoMenuBuilder(context).setShowDivider(true);
    }
}


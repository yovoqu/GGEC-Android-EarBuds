package com.yovo.yotheme.components.yo_menu;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description YoMenuInterface 菜单组件接口定义
 * @note 提供菜单管理、外观设置、事件监听等功能
 */
public interface YoMenuInterface {
    
    /**
     * 菜单项配置
     */
    class MenuItemConfig {
        public int itemId;
        public String text;
        public boolean enabled = true;
        
        public MenuItemConfig(int itemId, String text) {
            this.itemId = itemId;
            this.text = text;
        }
        
        public MenuItemConfig setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }
    
    /**
     * 菜单管理器
     */
    interface MenuManager {
        /**
         * 添加菜单项
         */
        boolean addMenuItem(MenuItemConfig itemConfig);
        
        /**
         * 移除菜单项
         */
        boolean removeMenuItem(int itemId);
        
        /**
         * 清空所有菜单项
         */
        void clearMenuItems();
        
        /**
         * 获取菜单项数量
         */
        int getMenuItemCount();
    }
    
    /**
     * 菜单外观管理器
     */
    interface MenuAppearanceManager {
        /**
         * 设置是否显示分隔线
         */
        void setShowDivider(boolean show);
    }
    
    /**
     * 菜单事件监听器
     */
    interface MenuEventListener {
        /**
         * 设置菜单项点击监听器
         */
        void setOnMenuItemClickListener(OnMenuItemClickListener listener);
    }
    
    /**
     * 菜单项点击监听器
     */
    interface OnMenuItemClickListener {
        void onMenuItemClick(int itemId);
    }
}


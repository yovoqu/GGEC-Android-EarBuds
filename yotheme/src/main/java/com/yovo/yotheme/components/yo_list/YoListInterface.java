package com.yovo.yotheme.components.yo_list;

import android.view.View;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description YoListInterface 列表组件对外接口定义
 * @note 提供列表管理、外观设置、事件监听等功能
 */
public interface YoListInterface {

    interface ListManager {
        boolean addItem(ListItemConfig itemConfig);

        boolean removeItem(int itemId);

        void clearItems();

        int getItemCount();

        List<View> getAllItemViews();
    }

    interface ListAppearanceManager {
        void setShowDivider(boolean show);

        void setDividerInsets(int startInsetDp, int endInsetDp);

        void setItemPadding(int horizontalDp, int verticalDp);
    }

    interface ListEventListener {
        void setOnItemClickListener(OnItemClickListener listener);

        void setOnItemLongClickListener(OnItemLongClickListener listener);
    }

    interface OnItemClickListener {
        void onItemClick(View itemView, int itemId);
    }

    interface OnItemLongClickListener {
        boolean onItemLongClick(View itemView, int itemId);
    }

    /**
     * 列表项配置
     */
    class ListItemConfig {
        public int itemId;
        public LeadingType leadingType = LeadingType.NONE;
        public Object leadingSource; // Drawable / Bitmap / ResId
        public int leadingSizeDp = com.yovo.yotheme.constants.ListConstants.LEADING_DEFAULT_SIZE_DP.getIntValue();
        public String title;
        public Integer titleColor; // 主标题颜色（null 时使用默认颜色）
        public String subtitle;
        public Integer subtitleColor; // 副标题颜色（null 时使用默认颜色）
        public List<String> subtitles; // 可选：多行辅助文本
        public TrailingType trailingType = TrailingType.NONE;
        public String trailingText;
        public Object trailingIcon; // Drawable / ResId
        public boolean trailingSwitchChecked = false;
        public View trailingCustomView; // 自定义View
        public View arrowLeftView; // ARROW_RIGHT 类型时，箭头左侧的自定义View
        public String arrowLeftText; // ARROW_RIGHT 类型时，箭头左侧的文本
        public boolean enabled = true;
        public boolean clickable = true; // 是否可点击（控制水波纹效果）

        public ListItemConfig(int itemId, String title) {
            this.itemId = itemId;
            this.title = title;
        }
    }

    enum LeadingType {
        NONE,
        ICON,
        IMAGE,
        AVATAR
    }

    enum TrailingType {
        NONE,
        ICON,
        BUTTON,
        SWITCH,
        TEXT,
        ICON_TEXT,
        BUTTON_TEXT,
        SWITCH_TEXT,
        ICON_BUTTON_TEXT,
        ARROW_RIGHT,  // 右箭头图标（表示可点击进入详情）
        CUSTOM  // 自定义View类型
    }

    interface YoListBuilder {
        YoListBuilder setShowDivider(boolean show);

        YoListBuilder setDividerInsets(int startInsetDp, int endInsetDp);

        YoListBuilder setItemPadding(int horizontalDp, int verticalDp);

        YoListBuilder addItem(ListItemConfig config);

        YoList build();
    }
}



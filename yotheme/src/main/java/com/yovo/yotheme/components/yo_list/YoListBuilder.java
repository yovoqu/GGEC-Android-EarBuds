package com.yovo.yotheme.components.yo_list;

import android.content.Context;

import com.yovo.yotheme.constants.ListConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description YoListBuilder 列表构建器
 * @note 提供便捷的列表创建方式
 */
public class YoListBuilder implements YoListInterface.YoListBuilder {

    private final Context context;
    private boolean showDivider = true;
    private int dividerInsetStartDp = ListConstants.DIVIDER_INSET_START_DP.getIntValue();
    private int dividerInsetEndDp = ListConstants.DIVIDER_INSET_END_DP.getIntValue();
    private int itemContentPaddingHdp = ListConstants.ITEM_CONTENT_PADDING_HORIZONTAL_DP.getIntValue();
    private int itemContentPaddingVdp = ListConstants.ITEM_CONTENT_PADDING_VERTICAL_SINGLE_DP.getIntValue();
    private final List<YoList.ItemConfig> items = new ArrayList<>();

    public YoListBuilder(Context context) {
        this.context = context;
    }

    @Override
    public YoListBuilder setShowDivider(boolean show) {
        this.showDivider = show;
        return this;
    }

    @Override
    public YoListBuilder setDividerInsets(int startInsetDp, int endInsetDp) {
        this.dividerInsetStartDp = startInsetDp;
        this.dividerInsetEndDp = endInsetDp;
        return this;
    }

    @Override
    public YoListBuilder setItemPadding(int horizontalDp, int verticalDp) {
        this.itemContentPaddingHdp = horizontalDp;
        this.itemContentPaddingVdp = verticalDp;
        return this;
    }

    @Override
    public YoListBuilder addItem(YoListInterface.ListItemConfig config) {
        // 为了兼容接口，这里需要转换，或者建议不再使用此接口方法
        // 由于 YoListInterface.ListItemConfig 字段是 public 的，我们可以手动转换
        if (config != null) {
            YoList.ItemConfig itemConfig = new YoList.ItemConfig(config.itemId, config.title);
            // 基础属性映射
            itemConfig.subtitle = config.subtitle;
            itemConfig.titleColor = config.titleColor;
            itemConfig.subtitleColor = config.subtitleColor;
            itemConfig.subtitles = config.subtitles;
            itemConfig.enabled = config.enabled;
            itemConfig.clickable = config.clickable;
            
            // Leading 映射
            if (config.leadingType != null) {
                switch (config.leadingType) {
                    case ICON: itemConfig.leadingType = YoList.LeadingType.ICON; break;
                    case IMAGE: itemConfig.leadingType = YoList.LeadingType.IMAGE; break;
                    case AVATAR: itemConfig.leadingType = YoList.LeadingType.AVATAR; break;
                    default: itemConfig.leadingType = YoList.LeadingType.NONE; break;
                }
            }
            itemConfig.leadingSource = config.leadingSource;
            itemConfig.leadingSizeDp = config.leadingSizeDp;

            // Trailing 映射
            if (config.trailingType != null) {
                switch (config.trailingType) {
                    case ICON: itemConfig.trailingType = YoList.TrailingType.ICON; break;
                    case BUTTON: itemConfig.trailingType = YoList.TrailingType.BUTTON; break;
                    case SWITCH: itemConfig.trailingType = YoList.TrailingType.SWITCH; break;
                    case TEXT: itemConfig.trailingType = YoList.TrailingType.TEXT; break;
                    case ICON_TEXT: itemConfig.trailingType = YoList.TrailingType.ICON_TEXT; break;
                    case BUTTON_TEXT: itemConfig.trailingType = YoList.TrailingType.BUTTON_TEXT; break;
                    case SWITCH_TEXT: itemConfig.trailingType = YoList.TrailingType.SWITCH_TEXT; break;
                    case ICON_BUTTON_TEXT: itemConfig.trailingType = YoList.TrailingType.ICON_BUTTON_TEXT; break;
                    case ARROW_RIGHT: itemConfig.trailingType = YoList.TrailingType.ARROW_RIGHT; break;
                    case CUSTOM: itemConfig.trailingType = YoList.TrailingType.CUSTOM; break;
                    default: itemConfig.trailingType = YoList.TrailingType.NONE; break;
                }
            }
            itemConfig.trailingText = config.trailingText;
            itemConfig.trailingIcon = config.trailingIcon;
            itemConfig.trailingSwitchChecked = config.trailingSwitchChecked;
            itemConfig.trailingCustomView = config.trailingCustomView;
            itemConfig.arrowLeftView = config.arrowLeftView;
            itemConfig.arrowLeftText = config.arrowLeftText;

            items.add(itemConfig);
        }
        return this;
    }

    /**
     * 添加新的 ItemConfig (推荐使用)
     */
    public YoListBuilder addItem(YoList.ItemConfig config) {
        if (config != null) {
            items.add(config);
        }
        return this;
    }

    @Override
    public YoList build() {
        YoList list = new YoList(context);
        list.setShowDivider(showDivider);
        list.setDividerInsets(dividerInsetStartDp, dividerInsetEndDp);
        list.setItemPadding(itemContentPaddingHdp, itemContentPaddingVdp);
        for (YoList.ItemConfig item : items) {
            list.addItem(item);
        }
        return list;
    }

    /**
     * 创建标准列表构建器（使用默认值）
     * 默认值来自ListConstants，单点控制，避免重复设置
     */
    public static YoListBuilder createStandard(Context context) {
        return new YoListBuilder(context);
    }
}



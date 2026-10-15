package com.yovo.yotheme.components.yo_list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.ListConstants;
import com.yovo.yotheme.components.yo_list.list_structure.ListDividerRenderer;
import com.yovo.yotheme.components.yo_list.list_structure.ListItemContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description YoList 列表组件
 * @note 统一对外接口，直接通过 YoList 进行调用，不依赖 YoUI
 */
public class YoList extends LinearLayout {

    // ==================== 枚举类型定义 ====================

    /**
     * 左侧图标类型
     */
    public enum LeadingType {
        NONE,       // 无图标
        ICON,       // 图标
        IMAGE,      // 图片
        AVATAR      // 头像
    }

    /**
     * 右侧内容类型
     */
    public enum TrailingType {
        NONE,               // 无内容
        ICON,               // 图标
        BUTTON,             // 按钮
        SWITCH,             // 开关
        TEXT,               // 文本
        ICON_TEXT,          // 图标+文本
        BUTTON_TEXT,        // 按钮+文本
        SWITCH_TEXT,        // 开关+文本
        ICON_BUTTON_TEXT,   // 图标+按钮+文本
        ARROW_RIGHT,        // 右箭头（表示可点击进入详情）
        CUSTOM              // 自定义View
    }

    // ==================== 列表项配置类 ====================

    /**
     * 列表项配置（链式调用风格）
     */
    public static class ItemConfig {
        public int itemId;
        public LeadingType leadingType = LeadingType.NONE;
        public Object leadingSource;  // Drawable / Bitmap / ResId
        public int leadingSizeDp = ListConstants.LEADING_DEFAULT_SIZE_DP.getIntValue();
        public String title;
        public Integer titleColor;    // 主标题颜色（null 使用默认）
        public String subtitle;
        public Integer subtitleColor; // 副标题颜色（null 使用默认）
        public List<String> subtitles; // 多行辅助文本
        public TrailingType trailingType = TrailingType.NONE;
        public String trailingText;
        public Object trailingIcon;   // Drawable / ResId
        public boolean trailingSwitchChecked = false;
        public View trailingCustomView;
        public View arrowLeftView;    // ARROW_RIGHT 类型时，箭头左侧的自定义View
        public String arrowLeftText;  // ARROW_RIGHT 类型时，箭头左侧的文本
        public boolean enabled = true;
        public boolean clickable = true; // 是否可点击（控制水波纹效果）

        public ItemConfig(int itemId, String title) {
            this.itemId = itemId;
            this.title = title;
        }

        /**
         * 设置左侧内容
         */
        public ItemConfig setLeading(LeadingType type, Object source) {
            this.leadingType = type;
            this.leadingSource = source;
            return this;
        }

        /**
         * 设置左侧图标大小
         */
        public ItemConfig setLeadingSize(int sizeDp) {
            this.leadingSizeDp = sizeDp;
            return this;
        }

        /**
         * 设置副标题
         */
        public ItemConfig setSubtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /**
         * 设置多行副标题
         */
        public ItemConfig setSubtitles(List<String> subtitles) {
            this.subtitles = subtitles;
            return this;
        }

        /**
         * 设置标题颜色
         */
        public ItemConfig setTitleColor(int color) {
            this.titleColor = color;
            return this;
        }

        /**
         * 设置副标题颜色
         */
        public ItemConfig setSubtitleColor(int color) {
            this.subtitleColor = color;
            return this;
        }

        /**
         * 设置右侧类型（无附加数据）
         */
        public ItemConfig setTrailing(TrailingType type) {
            this.trailingType = type;
            return this;
        }

        /**
         * 设置右侧文本
         */
        public ItemConfig setTrailingText(String text) {
            this.trailingText = text;
            return this;
        }

        /**
         * 设置右侧图标
         */
        public ItemConfig setTrailingIcon(Object icon) {
            this.trailingIcon = icon;
            return this;
        }

        /**
         * 设置右侧开关状态
         */
        public ItemConfig setSwitchChecked(boolean checked) {
            this.trailingSwitchChecked = checked;
            return this;
        }

        /**
         * 设置右侧自定义View
         */
        public ItemConfig setTrailingCustomView(View view) {
            this.trailingCustomView = view;
            return this;
        }

        /**
         * 设置箭头左侧的View（仅ARROW_RIGHT类型有效）
         */
        public ItemConfig setArrowLeftView(View view) {
            this.arrowLeftView = view;
            return this;
        }

        /**
         * 设置箭头左侧的文本（仅ARROW_RIGHT类型有效）
         */
        public ItemConfig setArrowLeftText(String text) {
            this.arrowLeftText = text;
            return this;
        }

        /**
         * 设置是否启用
         */
        public ItemConfig setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 设置是否可点击（影响水波纹效果）
         */
        public ItemConfig setClickable(boolean clickable) {
            this.clickable = clickable;
            return this;
        }

        /**
         * 转换为内部接口格式（兼容现有实现）
         */
        YoListInterface.ListItemConfig toInternalConfig() {
            YoListInterface.ListItemConfig config = new YoListInterface.ListItemConfig(itemId, title);
            config.leadingType = convertLeadingType(leadingType);
            config.leadingSource = leadingSource;
            config.leadingSizeDp = leadingSizeDp;
            config.titleColor = titleColor;
            config.subtitle = subtitle;
            config.subtitleColor = subtitleColor;
            config.subtitles = subtitles;
            config.trailingType = convertTrailingType(trailingType);
            config.trailingText = trailingText;
            config.trailingIcon = trailingIcon;
            config.trailingSwitchChecked = trailingSwitchChecked;
            config.trailingCustomView = trailingCustomView;
            config.arrowLeftView = arrowLeftView;
            config.arrowLeftText = arrowLeftText;
            config.enabled = enabled;
            config.clickable = clickable;
            return config;
        }

        private YoListInterface.LeadingType convertLeadingType(LeadingType type) {
            switch (type) {
                case ICON: return YoListInterface.LeadingType.ICON;
                case IMAGE: return YoListInterface.LeadingType.IMAGE;
                case AVATAR: return YoListInterface.LeadingType.AVATAR;
                default: return YoListInterface.LeadingType.NONE;
            }
        }

        private YoListInterface.TrailingType convertTrailingType(TrailingType type) {
            switch (type) {
                case ICON: return YoListInterface.TrailingType.ICON;
                case BUTTON: return YoListInterface.TrailingType.BUTTON;
                case SWITCH: return YoListInterface.TrailingType.SWITCH;
                case TEXT: return YoListInterface.TrailingType.TEXT;
                case ICON_TEXT: return YoListInterface.TrailingType.ICON_TEXT;
                case BUTTON_TEXT: return YoListInterface.TrailingType.BUTTON_TEXT;
                case SWITCH_TEXT: return YoListInterface.TrailingType.SWITCH_TEXT;
                case ICON_BUTTON_TEXT: return YoListInterface.TrailingType.ICON_BUTTON_TEXT;
                case ARROW_RIGHT: return YoListInterface.TrailingType.ARROW_RIGHT;
                case CUSTOM: return YoListInterface.TrailingType.CUSTOM;
                default: return YoListInterface.TrailingType.NONE;
            }
        }
    }

    // ==================== 监听器接口 ====================

    /**
     * 列表项点击监听器
     */
    public interface OnItemClickListener {
        void onItemClick(View itemView, int itemId);
    }

    /**
     * 列表项长按监听器
     */
    public interface OnItemLongClickListener {
        boolean onItemLongClick(View itemView, int itemId);
    }

    // ==================== 成员变量 ====================

    private final List<View> itemViews = new ArrayList<>();
    private boolean showDivider = true;
    private int dividerInsetStartDp = ListConstants.DIVIDER_INSET_START_DP.getIntValue();
    private int dividerInsetEndDp = ListConstants.DIVIDER_INSET_END_DP.getIntValue();
    private int itemContentPaddingHdp = ListConstants.ITEM_CONTENT_PADDING_HORIZONTAL_DP.getIntValue();
    private int itemContentPaddingVdp = ListConstants.ITEM_CONTENT_PADDING_VERTICAL_SINGLE_DP.getIntValue();
    private int itemMinHeightDp = ListConstants.ITEM_MIN_HEIGHT_SINGLE_DP.getIntValue();
    private int itemContentTrailingGapDp = ListConstants.CONTENT_TRAILING_GAP_DP.getIntValue();
    private int cornerRadiusDp = ListConstants.LIST_CARD_CORNER_RADIUS_DP.getIntValue();
    private float cardElevationDp = ListConstants.LIST_CARD_ELEVATION_DP.getFloatValue();
    /** 是否嵌入 ListCard：内边距由 GroupCard 统一提供，避免与列表项 padding 叠加 */
    private boolean embeddedInListCard = false;
    private ListDividerRenderer dividerRenderer;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private boolean cardBackgroundEnabled = true;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建一个新的 YoList 实例
     * 
     * @param context 上下文对象
     * @return YoList 实例
     */
    public static YoList create(Context context) {
        return new YoList(context);
    }

    // ==================== 构造函数 ====================

    public YoList(Context context) {
        this(context, null);
    }

    public YoList(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoList(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        // 第 1 层 · 水波纹缩进（独立模式由 YoList 容器承担；嵌入 ListCard 时由 GroupCard 承担）
        int rippleInset = ListConstants.LIST_CARD_RIPPLE_INSET_DP.getIntValue();
        setPadding(dpToPx(rippleInset), dpToPx(rippleInset), dpToPx(rippleInset), dpToPx(rippleInset));
        dividerRenderer = new ListDividerRenderer(context);

        // 设置卡片圆角背景
        applyCardBackground();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!showDivider || getChildCount() <= 1) return;
        int startInsetPx = dpToPx(dividerInsetStartDp);
        int endInsetPx = dpToPx(dividerInsetEndDp);
        dividerRenderer.drawDividers(this, canvas, startInsetPx, endInsetPx);
    }

    // ==================== 列表项管理 ====================

    /**
     * 添加列表项
     * 
     * @param itemConfig 列表项配置
     * @return 添加是否成功
     */
    public boolean addItem(ItemConfig itemConfig) {
        if (itemConfig == null) return false;
        ListItemContainer container = new ListItemContainer(getContext());
        applyItemLayoutToContainer(container);
        container.bindConfig(itemConfig.toInternalConfig());
        container.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(v, itemConfig.itemId);
            }
        });
        container.setOnLongClickListener(v -> 
            onItemLongClickListener != null && onItemLongClickListener.onItemLongClick(v, itemConfig.itemId)
        );
        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        itemViews.add(container);
        invalidate();
        return true;
    }

    /**
     * 添加简单列表项（便捷方法）
     * 
     * @param itemId 项目ID
     * @param title 标题文本
     * @return 添加是否成功
     */
    public boolean addItem(int itemId, String title) {
        return addItem(new ItemConfig(itemId, title));
    }

    /**
     * 添加带图标的列表项（便捷方法）
     * 
     * @param itemId 项目ID
     * @param icon 图标
     * @param title 标题文本
     * @return 添加是否成功
     */
    public boolean addItem(int itemId, Drawable icon, String title) {
        return addItem(new ItemConfig(itemId, title).setLeading(LeadingType.ICON, icon));
    }

    /**
     * 添加带图标和副标题的列表项（便捷方法）
     * 
     * @param itemId 项目ID
     * @param icon 图标
     * @param title 标题文本
     * @param subtitle 副标题文本
     * @return 添加是否成功
     */
    public boolean addItem(int itemId, Drawable icon, String title, String subtitle) {
        return addItem(new ItemConfig(itemId, title)
            .setLeading(LeadingType.ICON, icon)
            .setSubtitle(subtitle));
    }

    /**
     * 移除指定ID的列表项
     * 
     * @param itemId 项目ID
     * @return 移除是否成功
     */
    public boolean removeItem(int itemId) {
        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.get(i);
            if (v instanceof ListItemContainer) {
                if (((ListItemContainer) v).getItemId() == itemId) {
                    removeView(v);
                    itemViews.remove(i);
                    invalidate();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 清空所有列表项
     */
    public void clearItems() {
        removeAllViews();
        itemViews.clear();
        invalidate();
    }

    /**
     * 获取列表项数量
     * 
     * @return 列表项数量
     */
    public int getItemCount() {
        return itemViews.size();
    }

    /**
     * 获取所有列表项视图
     * 
     * @return 列表项视图列表
     */
    public List<View> getAllItemViews() {
        return new ArrayList<>(itemViews);
    }

    // ==================== 外观设置 ====================

    /**
     * 设置是否显示分隔线
     * 
     * @param show 是否显示
     * @return 当前实例（链式调用）
     */
    public YoList setShowDivider(boolean show) {
        this.showDivider = show;
        invalidate();
        return this;
    }

    /**
     * 设置分隔线缩进
     * 
     * @param startInsetDp 起始缩进（dp）
     * @param endInsetDp 结束缩进（dp）
     * @return 当前实例（链式调用）
     */
    public YoList setDividerInsets(int startInsetDp, int endInsetDp) {
        this.dividerInsetStartDp = startInsetDp;
        this.dividerInsetEndDp = endInsetDp;
        invalidate();
        return this;
    }

    /**
     * 设置列表项内边距
     * 
     * @param horizontalDp 水平内边距（dp）
     * @param verticalDp 垂直内边距（dp）
     * @return 当前实例（链式调用）
     */
    public YoList setItemPadding(int horizontalDp, int verticalDp) {
        this.itemContentPaddingHdp = horizontalDp;
        this.itemContentPaddingVdp = verticalDp;
        refreshExistingItemLayouts();
        invalidate();
        return this;
    }

    /**
     * 嵌入 ListCard 时的布局策略（两层模型，严禁叠加）：
     * <ul>
     *   <li>ripple 层：GroupCard padding（{@link ListConstants#LIST_CARD_RIPPLE_INSET_DP}）</li>
     *   <li>content 层：ListItemContainer.contentWrapper（{@link ListConstants#ITEM_CONTENT_PADDING_HORIZONTAL_DP}）</li>
     * </ul>
     * YoList 容器 padding 清零，避免与 GroupCard 双层 ripple 叠加。
     *
     * @param rippleInsetDp 与 ListCard.setCardPadding 一致，即水波纹相对卡片边缘的内边距
     */
    public void applyListCardEmbedding(int rippleInsetDp) {
        embeddedInListCard = true;
        setPadding(0, 0, 0, 0);
        setCardBackgroundEnabled(false);
        itemContentPaddingHdp = ListConstants.ITEM_CONTENT_PADDING_HORIZONTAL_DP.getIntValue();
        itemContentPaddingVdp = ListConstants.ITEM_CONTENT_PADDING_VERTICAL_SINGLE_DP.getIntValue();
        itemMinHeightDp = ListConstants.LIST_EMBEDDED_ITEM_MIN_HEIGHT_DP.getIntValue();
        refreshExistingItemLayouts();
        invalidate();
    }

    public boolean isEmbeddedInListCard() {
        return embeddedInListCard;
    }

    private void applyItemLayoutToContainer(ListItemContainer container) {
        container.setContentPadding(itemContentPaddingHdp, itemContentPaddingVdp);
        container.setMinimumHeightDp(itemMinHeightDp);
        container.setContentTrailingGapDp(itemContentTrailingGapDp);
        container.setRippleCornerRadius(cornerRadiusDp);
    }

    private void refreshExistingItemLayouts() {
        for (View v : itemViews) {
            if (v instanceof ListItemContainer) {
                applyItemLayoutToContainer((ListItemContainer) v);
            }
        }
    }

    /**
     * 设置列表项最小高度（dp）
     */
    public YoList setItemMinHeight(int minHeightDp) {
        this.itemMinHeightDp = Math.max(0, minHeightDp);
        refreshExistingItemLayouts();
        invalidate();
        return this;
    }

    /**
     * 设置主内容与右侧操作区间距（dp）
     */
    public YoList setItemContentTrailingGap(int gapDp) {
        this.itemContentTrailingGapDp = Math.max(0, gapDp);
        refreshExistingItemLayouts();
        invalidate();
        return this;
    }

    /**
     * 设置卡片圆角半径。同时同步列表项水波纹圆角，保证两者一致。
     * 
     * @param radiusDp 圆角半径（dp）
     * @return 当前实例（链式调用）
     */
    public YoList setCornerRadius(int radiusDp) {
        this.cornerRadiusDp = Math.max(0, radiusDp);
        applyCardBackground();
        refreshExistingItemLayouts();
        return this;
    }

    /**
     * 设置是否显示卡片背景
     * 
     * @param enabled 是否启用
     * @return 当前实例（链式调用）
     */
    public YoList setCardBackgroundEnabled(boolean enabled) {
        this.cardBackgroundEnabled = enabled;
        applyCardBackground();
        return this;
    }

    /**
     * 设置独立列表卡片阴影高度。
     *
     * @param elevationDp 阴影高度（dp），小于 0 时按 0 处理
     * @return 当前实例（链式调用）
     */
    public YoList setCardElevation(float elevationDp) {
        this.cardElevationDp = Math.max(0f, elevationDp);
        applyCardShadow();
        return this;
    }

    // ==================== 事件监听 ====================

    /**
     * 设置列表项点击监听器
     * 
     * @param listener 点击监听器
     * @return 当前实例（链式调用）
     */
    public YoList setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
        return this;
    }

    /**
     * 设置列表项长按监听器
     * 
     * @param listener 长按监听器
     * @return 当前实例（链式调用）
     */
    public YoList setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
        return this;
    }

    // ==================== 私有方法 ====================

    /**
     * 应用卡片圆角背景
     */
    private void applyCardBackground() {
        if (!cardBackgroundEnabled) {
            setBackground(null);
            applyCardShadow();
            return;
        }
        float cornerRadius = dpToPx(cornerRadiusDp);
        float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius, 
                         cornerRadius, cornerRadius, cornerRadius, cornerRadius};
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
        backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
        setBackground(backgroundShape);
        applyCardShadow();
    }

    /**
     * 使用圆角轮廓生成四周环境阴影。嵌入 ListCard 后由外层卡片负责视觉层级，
     * 因此关闭自身阴影，避免叠加。
     */
    private void applyCardShadow() {
        if (!cardBackgroundEnabled || cardElevationDp <= 0f) {
            setElevation(0f);
            setClipToOutline(false);
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            return;
        }

        final float cornerRadiusPx = dpToPx(cornerRadiusDp);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        setClipToOutline(true);
        setElevation(dpToPx(cardElevationDp));
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}

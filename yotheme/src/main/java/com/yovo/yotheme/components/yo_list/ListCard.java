package com.yovo.yotheme.components.yo_list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.ListConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description ListCard 列表卡片容器组件
 * @note 支持单个列表和多个列表的组合
 */
public class ListCard extends LinearLayout {

    private final List<YoList> listViews = new ArrayList<>();
    private boolean isMultiCard = false;
    private int cardCornerRadiusDp = ListConstants.LIST_CARD_CORNER_RADIUS_DP.getIntValue();
    private int cardMarginDp = ListConstants.LIST_CARD_MARGIN_DP.getIntValue();
    /** 水波纹缩进，仅作用于 GroupCard，外层 ListCard 不叠加 padding */
    private int rippleInsetDp = ListConstants.LIST_CARD_RIPPLE_INSET_DP.getIntValue();
    private float cardElevationDp = 0f;
    private boolean enableCardBackground = false;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int dividerHeightPx = dpToPx(ListConstants.DIVIDER_HEIGHT_DP.getIntValue());
    private final Set<YoList> cardManagedDividerLists = new HashSet<>();

    public ListCard(Context context) {
        this(context, null);
    }

    public ListCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        if (enableCardBackground) {
            setCardBackground();
        }
        // 水波纹缩进由 GroupCard 单点承担，外层 ListCard 禁止 setPadding 叠加

        dividerPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setColor(ColorMapper.map(YoThemeColor.COMP_DIVIDER));
    }

    /**
     * 添加列表到卡片中
     */
    public void addList(View listView) {
        addList(listView, false);
    }

    /**
     * 添加列表到卡片中
     * @param listView 列表
     * @param groupWithPrevious 是否与上一个列表合并为同一组（无额外顶部间距）
     */
    public void addList(View listView, boolean groupWithPrevious) {
        if (!(listView instanceof YoList)) return;

        YoList yoList = (YoList) listView;
        // 关闭 YoList 自身分隔线绘制，改由 GroupCard 统一在组容器中绘制
        yoList.setShowDivider(false);
        // 内边距由 GroupCard 单点承担（ripple 层），列表项 contentWrapper 承担 content 层
        yoList.applyListCardEmbedding(rippleInsetDp);

        listViews.add(yoList);

        GroupCard targetGroup;
        if (groupWithPrevious && getChildCount() > 0 && getChildAt(getChildCount() - 1) instanceof GroupCard) {
            targetGroup = (GroupCard) getChildAt(getChildCount() - 1);
        } else {
            targetGroup = new GroupCard(getContext());
            LayoutParams groupParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (getChildCount() > 0) {
                groupParams.topMargin = dpToPx(ListConstants.LIST_GROUP_GAP_DP.getIntValue());
            }
            addView(targetGroup, groupParams);
        }

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        if (targetGroup.getChildCount() > 0) {
            lp.topMargin = dpToPx(ListConstants.LIST_GROUP_INNER_GAP_DP.getIntValue());
        }
        targetGroup.addView(yoList, lp);
        invalidate();
    }

    /**
     * 移除列表
     */
    public void removeList(View listView) {
        if (!(listView instanceof YoList)) return;
        YoList yoList = (YoList) listView;
        if (listViews.remove(yoList)) {
            removeView(yoList);
            updateCardStyle();
        }
    }

    /**
     * 清空所有列表
     */
    public void clearLists() {
        listViews.clear();
        removeAllViews();
        updateCardStyle();
    }

    /**
     * 设置卡片圆角背景
     */
    private void setCardBackground() {
        float cornerRadius = dpToPx(cardCornerRadiusDp);
        float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius};
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        
        ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
        backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
        
        setBackground(backgroundShape);
    }

    /**
     * 设置水波纹缩进（dp），仅更新 GroupCard，外层 ListCard 不叠加 padding。
     */
    public void setCardPadding(int paddingDp) {
        this.rippleInsetDp = paddingDp;
        refreshGroupCardPadding();
        for (YoList yoList : listViews) {
            yoList.applyListCardEmbedding(rippleInsetDp);
        }
    }

    private void refreshGroupCardPadding() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof GroupCard) {
                ((GroupCard) child).applyGroupPadding();
            }
        }
    }

    /**
     * 更新卡片样式
     */
    private void updateCardStyle() {
        isMultiCard = listViews.size() > 1;
    }

    /**
     * 设置卡片外边距
     */
    public void setCardMargin(int marginDp) {
        this.cardMarginDp = marginDp;
        updateCardMargin();
    }

    /**
     * 更新卡片外边距
     */
    private void updateCardMargin() {
        LayoutParams params = (LayoutParams) getLayoutParams();
        if (params != null) {
            int margin = dpToPx(cardMarginDp);
            params.setMargins(margin, margin, margin, margin);
            setLayoutParams(params);
        }
    }

    /**
     * 设置卡片圆角
     */
    public void setCardCornerRadius(int radiusDp) {
        this.cardCornerRadiusDp = radiusDp;
        if (enableCardBackground) {
            setCardBackground();
        }
        applyOuterShadow();
        refreshGroupCardShadow();
    }

    /**
     * 设置卡片阴影（elevation，单位 dp）。大于 0 时在外层 ListCard 启用圆角轮廓阴影。
     */
    public void setCardElevation(float elevationDp) {
        this.cardElevationDp = Math.max(0f, elevationDp);
        applyOuterShadow();
    }

    private void applyOuterShadow() {
        if (cardElevationDp <= 0f) {
            setElevation(0f);
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            return;
        }
        setClipToPadding(false);
        setClipChildren(false);
        final float cornerRadiusPx = dpToPx(cardCornerRadiusDp);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        setClipToOutline(true);
        setElevation(dpToPx(cardElevationDp));
    }

    private void refreshGroupCardShadow() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof GroupCard) {
                ((GroupCard) child).applyShadow();
            }
        }
    }

    /**
     * 控制是否显示外层卡片背景
     */
    public void setEnableCardBackground(boolean enable) {
        this.enableCardBackground = enable;
        if (enable) {
            setCardBackground();
        } else {
            setBackground(null);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // 分隔线由组容器 GroupCard 自行绘制；此处不再统一绘制
    }

    /**
     * 组容器：每个组拥有完整的卡片背景与独立分隔线，组内可包含一个或多个 YoList
     */
    private class GroupCard extends LinearLayout {
        private final Paint groupDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int groupDividerHeightPx = ListConstants.DIVIDER_HEIGHT_PX.getIntValue();

        public GroupCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            applyGroupBackground();
            applyGroupPadding();
            applyShadow();
            groupDividerPaint.setStyle(Paint.Style.FILL);
            groupDividerPaint.setColor(ColorMapper.map(YoThemeColor.COMP_DIVIDER));
        }

        private void applyShadow() {
            setElevation(0f);
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }

        private void applyGroupBackground() {
            float cornerRadius = dpToPx(cardCornerRadiusDp);
            float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius};
            RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
            ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
            backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
            setBackground(backgroundShape);
        }

        private void applyGroupPadding() {
            int padding = dpToPx(rippleInsetDp);
            setPadding(padding, padding, padding, padding);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            // 右边界：与当前列表项容器右边保持一致
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof YoList)) continue;
                YoList yoList = (YoList) child;
                List<View> itemViews = yoList.getAllItemViews();
                int yoListLeft = yoList.getLeft();
                int yoListTop = yoList.getTop();

                // 行内分隔线
                for (int j = 0; j < itemViews.size() - 1; j++) {
                    View item = itemViews.get(j);
                    int top = yoListTop + item.getBottom();
                    int left = resolveTextStartInGroup(yoListLeft, item);
                    int right = yoListLeft + item.getRight();
                    if (right > left) {
                        canvas.drawRect(left, top, right, top + groupDividerHeightPx, groupDividerPaint);
                    }
                }

                // 跨列表分隔线：在组内列表之间补分隔线（当前 YoList 不是组内最后一个，且自身有至少 1 项）
                boolean hasNextList = (i < getChildCount() - 1) && (getChildAt(i + 1) instanceof YoList);
                if (hasNextList && !itemViews.isEmpty()) {
                    View lastItem = itemViews.get(itemViews.size() - 1);
                    int top = yoListTop + lastItem.getBottom();
                    int left = resolveTextStartInGroup(yoListLeft, lastItem);
                    int right = yoListLeft + lastItem.getRight();
                    if (right > left) {
                        canvas.drawRect(left, top, right, top + groupDividerHeightPx, groupDividerPaint);
                    }
                }
            }
        }

        private int resolveTextStartInGroup(int yoListLeft, View item) {
            if (item instanceof com.yovo.yotheme.components.yo_list.list_structure.ListItemContainer) {
                return yoListLeft + ((com.yovo.yotheme.components.yo_list.list_structure.ListItemContainer) item).getTextStartXInParent();
            }
            return yoListLeft + item.getLeft() + dpToPx(ListConstants.DIVIDER_INSET_START_DP.getIntValue());
        }
    }

    /**
     * 是否为多列表卡片
     */
    public boolean isMultiCard() {
        return isMultiCard;
    }

    /**
     * 获取列表数量
     */
    public int getListCount() {
        return listViews.size();
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}

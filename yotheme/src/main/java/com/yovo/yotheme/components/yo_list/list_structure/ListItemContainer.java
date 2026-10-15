package com.yovo.yotheme.components.yo_list.list_structure;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.components.yo_list.YoListInterface;
import com.yovo.yotheme.constants.ListConstants;
import com.yovo.yotheme.small_widget.YoArrowRightIcon;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 列表项容器
 * @note 包含Leading（左）、Content（中：Title/Subtitle）、Trailing（右）
 * @note 支持水波纹效果
 * @note 支持列表项配置绑定
 * @note 支持列表项高度自适应
 * @note 支持列表项点击事件
 * @note 支持列表项选中状态
 * @note 支持列表项禁用状态
 * @note 支持列表项图标类型
 * @note 支持列表项文本类型
 * @note 支持列表项按钮类型
 */
public class ListItemContainer extends LinearLayout {

    private int itemId = -1;

    private LinearLayout contentWrapper;  // 内容包装器，包含padding
    private LinearLayout contentColumn;
    private ListLeading leadingView;
    private View leadingGapSpacer;
    private ListTitle titleView;
    private ListSubtitle subtitleView;
    private ListTrailing trailingView;
    private int leadingContentGapDp = ListConstants.LEADING_CONTENT_GAP_DP.getIntValue();
    private int contentTrailingGapDp = ListConstants.CONTENT_TRAILING_GAP_DP.getIntValue();
    private int contentPaddingHdp = ListConstants.ITEM_CONTENT_PADDING_HORIZONTAL_DP.getIntValue();
    private int contentPaddingVdp = ListConstants.ITEM_CONTENT_PADDING_VERTICAL_SINGLE_DP.getIntValue();
    /** 水波纹/背景圆角，需与 YoList 卡片背景圆角保持一致 */
    private int rippleCornerRadiusDp = ListConstants.LIST_ITEM_RIPPLE_CORNER_RADIUS_DP.getIntValue();
    private boolean rippleEnabled = true;

    public ListItemContainer(Context context) {
        this(context, null);
    }

    public ListItemContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListItemContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);  // 容器为垂直方向
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dpToPx(ListConstants.ITEM_MIN_HEIGHT_SINGLE_DP.getIntValue()));
        initChildren(context);
    }

    private void initChildren(Context context) {
        // 创建内容包装器（水平布局，包含所有内容）
        contentWrapper = new LinearLayout(context);
        contentWrapper.setOrientation(HORIZONTAL);
        contentWrapper.setGravity(Gravity.CENTER_VERTICAL);
        
        // 设置内容包装器的padding（文本内容的内边距）
        int paddingH = dpToPx(contentPaddingHdp);
        int paddingV = dpToPx(contentPaddingVdp);
        contentWrapper.setPadding(paddingH, paddingV, paddingH, paddingV);
        
        LayoutParams lpWrapper = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        contentWrapper.setLayoutParams(lpWrapper);
        
        // Leading
        leadingView = new ListLeading(context);
        LayoutParams lpLeading = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        leadingView.setLayoutParams(lpLeading);

        // Content Column
        contentColumn = new LinearLayout(context);
        contentColumn.setOrientation(VERTICAL);
        LayoutParams lpContent = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        contentColumn.setLayoutParams(lpContent);

        titleView = new ListTitle(context);
        subtitleView = new ListSubtitle(context);
        contentColumn.addView(titleView);

        // Trailing
        trailingView = new ListTrailing(context);
        LayoutParams lpTrailing = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        trailingView.setLayoutParams(lpTrailing);

        // 将Leading、Content、Trailing添加到内容包装器中
        contentWrapper.addView(leadingView);
        contentWrapper.addView(contentColumn);
        contentWrapper.addView(createGap(contentTrailingGapDp, false));
        contentWrapper.addView(trailingView);
        
        // 将内容包装器添加到容器中
        addView(contentWrapper, lpWrapper);
        
        // 设置水波纹效果（在容器层面）
        setupRippleEffect();
    }

    public void bindConfig(YoListInterface.ListItemConfig config) {
        this.itemId = config.itemId;

        resetToSingleLineLayout();

        // Leading
        boolean hasLeading = !(config.leadingType == YoListInterface.LeadingType.NONE || config.leadingSource == null);
        if (hasLeading) {
            leadingView.setVisibility(VISIBLE);
            if (config.leadingSource instanceof Drawable) {
                leadingView.setLeadingDrawable((Drawable) config.leadingSource);
            } else if (config.leadingSource instanceof Integer) {
                leadingView.setImageResource((Integer) config.leadingSource);
            }
            leadingView.setLeadingSizeDp(config.leadingSizeDp);
            ensureLeadingGap();
        } else {
            leadingView.setVisibility(GONE);
            removeLeadingGap();
        }

        // Content（支持多行辅助文本）
        titleView.setText(config.title != null ? config.title : "");
        if (config.titleColor != null) {
            titleView.setTextColor(config.titleColor);
        } else {
            titleView.setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        }
        contentColumn.removeAllViews();
        contentColumn.addView(titleView);

        List<String> lines = new ArrayList<>();
        if (config.subtitles != null && !config.subtitles.isEmpty()) {
            for (String s : config.subtitles) {
                if (!TextUtils.isEmpty(s)) {
                    lines.add(s);
                }
            }
        } else if (!TextUtils.isEmpty(config.subtitle)) {
            lines.add(config.subtitle);
        }

        if (!lines.isEmpty()) {
            contentColumn.addView(createGap(ListConstants.TITLE_SUBTITLE_GAP_DP.getIntValue(), true));
            for (int i = 0; i < lines.size(); i++) {
                ListSubtitle sub = new ListSubtitle(getContext());
                sub.setText(lines.get(i));
                if (config.subtitleColor != null) {
                    sub.setTextColor(config.subtitleColor);
                }
                contentColumn.addView(sub);
                // 行间距：在每条辅助文本之间插入极小占位，保持紧凑（可复用 TITLE_SUBTITLE_GAP_DP 或 SPACER_THIN_PX）
                if (i < lines.size() - 1) {
                    contentColumn.addView(createGap(ListConstants.TITLE_SUBTITLE_GAP_DP.getIntValue(), true));
                }
            }
            // 更新内容包装器的垂直padding和最小高度
            updatePaddingForDoubleLines();
        }

        // Trailing
        setupTrailing(config);

        // Enabled
        setEnabled(config.enabled);
        setAlpha(config.enabled ? 1f : 0.5f);
        
        // Clickable（控制水波纹效果）
        setClickableState(config.clickable);
    }

    private void setupTrailing(YoListInterface.ListItemConfig config) {
        trailingView.removeAllViews();

        switch (config.trailingType) {
            case NONE:
                trailingView.setVisibility(GONE);
                break;
            case ICON:
                trailingView.setVisibility(VISIBLE);
                trailingView.setIcon(config.trailingIcon);
                break;
            case BUTTON:
                trailingView.setVisibility(VISIBLE);
                trailingView.setButtonText(config.trailingText != null ? config.trailingText : "");
                break;
            case SWITCH:
                trailingView.setVisibility(VISIBLE);
                trailingView.setSwitchChecked(config.trailingSwitchChecked);
                break;
            case TEXT:
                trailingView.setVisibility(VISIBLE);
                trailingView.setText(config.trailingText != null ? config.trailingText : "");
                break;
            case ICON_TEXT:
                trailingView.setVisibility(VISIBLE);
                trailingView.setIcon(config.trailingIcon);
                trailingView.setText(config.trailingText != null ? config.trailingText : "");
                break;
            case BUTTON_TEXT:
                trailingView.setVisibility(VISIBLE);
                trailingView.setButtonText(config.trailingText != null ? config.trailingText : "");
                trailingView.setText(config.trailingText != null ? config.trailingText : "");
                break;
            case SWITCH_TEXT:
                trailingView.setVisibility(VISIBLE);
                trailingView.setSwitchChecked(config.trailingSwitchChecked);
                trailingView.setText(config.trailingText != null ? config.trailingText : "");
                break;
            case ICON_BUTTON_TEXT:
                trailingView.setVisibility(VISIBLE);
                trailingView.setIcon(config.trailingIcon);
                trailingView.setButtonText(config.trailingText != null ? config.trailingText : "");
                trailingView.setText(config.trailingText != null ? config.trailingText : "");
                break;
            case ARROW_RIGHT:
                trailingView.setVisibility(VISIBLE);
                // 先添加箭头左侧的内容（自定义View或文本）
                if (config.arrowLeftView != null) {
                    trailingView.setCustomView(config.arrowLeftView);
                } else if (config.arrowLeftText != null && !config.arrowLeftText.isEmpty()) {
                    trailingView.setText(config.arrowLeftText);
                }
                // 再添加右箭头图标
                YoArrowRightIcon arrowIcon = new YoArrowRightIcon(getContext());
                arrowIcon.setIconSize(24);  // 默认24dp大小
                trailingView.setCustomView(arrowIcon);
                break;
            case CUSTOM:
                trailingView.setVisibility(VISIBLE);
                if (config.trailingCustomView != null) {
                    trailingView.setCustomView(config.trailingCustomView);
                }
                break;
        }
    }

    private void addGap(int gapDp) {
        addView(createGap(gapDp, false));
    }

    private View createGap(int gapDp, boolean vertical) {
        View spacer = new View(getContext());
        LayoutParams lp = vertical
                ? new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(gapDp))
                : new LayoutParams(dpToPx(gapDp), ListConstants.SPACER_THIN_PX.getIntValue());
        spacer.setLayoutParams(lp);
        return spacer;
    }

    /**
     * 重置为单行布局的默认高度和内边距
     */
    private void resetToSingleLineLayout() {
        setMinimumHeight(dpToPx(ListConstants.ITEM_MIN_HEIGHT_SINGLE_DP.getIntValue()));
        int paddingH = dpToPx(contentPaddingHdp);
        int paddingV = dpToPx(contentPaddingVdp);
        contentWrapper.setPadding(paddingH, paddingV, paddingH, paddingV);
    }

    /**
     * 更新双行内容的padding和最小高度
     */
    private void updatePaddingForDoubleLines() {
        setMinimumHeight(dpToPx(ListConstants.ITEM_MIN_HEIGHT_DOUBLE_DP.getIntValue()));
        // 更新内容包装器的垂直padding
        int paddingH = dpToPx(contentPaddingHdp);
        int paddingV = dpToPx(ListConstants.ITEM_CONTENT_PADDING_VERTICAL_DOUBLE_DP.getIntValue());
        contentWrapper.setPadding(paddingH, paddingV, paddingH, paddingV);
    }

    public int getItemId() { return itemId; }

    /**
     * 设置内容区域内边距（dp）
     */
    public void setContentPadding(int horizontalDp, int verticalDp) {
        contentPaddingHdp = horizontalDp;
        contentPaddingVdp = verticalDp;
        int paddingH = dpToPx(contentPaddingHdp);
        int paddingV = dpToPx(contentPaddingVdp);
        contentWrapper.setPadding(paddingH, paddingV, paddingH, paddingV);
    }

    /** 设置列表项最小高度（dp），0 表示仅由内容撑开 */
    public void setMinimumHeightDp(int minHeightDp) {
        setMinimumHeight(minHeightDp > 0 ? dpToPx(minHeightDp) : 0);
    }

    /** 设置主内容与右侧操作区间距（dp） */
    public void setContentTrailingGapDp(int gapDp) {
        contentTrailingGapDp = Math.max(0, gapDp);
        int trailingIndex = contentWrapper.indexOfChild(trailingView);
        if (trailingIndex <= 0) return;
        View gap = contentWrapper.getChildAt(trailingIndex - 1);
        if (gap == contentColumn || gap == leadingView) return;
        LayoutParams lp = (LayoutParams) gap.getLayoutParams();
        lp.width = dpToPx(contentTrailingGapDp);
        gap.setLayoutParams(lp);
    }

    /**
     * 设置列表项水波纹/背景圆角（dp），应与 YoList 卡片背景圆角一致。
     */
    public void setRippleCornerRadius(int radiusDp) {
        this.rippleCornerRadiusDp = Math.max(0, radiusDp);
        setClickableState(rippleEnabled);
    }
    
    /**
     * 检查是否有左侧图标
     */
    public boolean hasLeadingIcon() {
        return leadingView != null && leadingView.getVisibility() == View.VISIBLE;
    }
    
    /**
     * 获取左侧图标宽度
     */
    public int getLeadingWidth() {
        if (leadingView != null && leadingView.getVisibility() == View.VISIBLE) {
            return leadingView.getWidth();
        }
        return 0;
    }
    
    /**
     * 获取图标与文本间距
     */
    public int getLeadingContentGap() {
        return dpToPx(leadingContentGapDp);
    }

    /**
     * 获取文本起点相对于父容器的 X 坐标
     * 用于分隔线对齐
     */
    public int getTextStartXInParent() {
        // 返回内容包装器的左padding位置（文本区域的左边界）
        if (contentWrapper != null) {
            return getLeft() + contentWrapper.getPaddingLeft();
        }
        return getLeft();
    }
    
    /**
     * 获取文本终点相对于父容器的 X 坐标
     * 用于分隔线对齐
     */
    public int getTextEndXInParent() {
        // 返回内容包装器的右padding位置（文本区域的右边界）
        if (contentWrapper != null) {
            return getRight() - contentWrapper.getPaddingRight();
        }
        return getRight();
    }

    private void ensureLeadingGap() {
        if (leadingGapSpacer == null) {
            leadingGapSpacer = createGap(leadingContentGapDp, false);
        }
        // 若当前没有插入到 content 之前，则插入到contentWrapper中
        int contentIndex = contentWrapper.indexOfChild(contentColumn);
        if (contentIndex != -1) {
            int spacerIndex = contentWrapper.indexOfChild(leadingGapSpacer);
            if (spacerIndex == -1 || spacerIndex > contentIndex) {
                contentWrapper.removeView(leadingGapSpacer);
                contentWrapper.addView(leadingGapSpacer, contentIndex);
            }
        }
    }

    private void removeLeadingGap() {
        if (leadingGapSpacer != null) {
            contentWrapper.removeView(leadingGapSpacer);
        }
    }
    
    /**
     * 设置水波纹效果（默认启用）
     */
    private void setupRippleEffect() {
        float cornerRadius = dpToPx(rippleCornerRadiusDp);
        float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius};
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        
        // 创建背景形状
        ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
        backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
        
        // 创建水波纹形状
        ShapeDrawable rippleShape = new ShapeDrawable(roundRectShape);
        rippleShape.getPaint().setColor(ColorMapper.map(YoThemeColor.BACKGROUND_FOURTH));
        
        // 创建水波纹效果
        RippleDrawable rippleDrawable = new RippleDrawable(
            ColorStateList.valueOf(ColorMapper.map(YoThemeColor.BACKGROUND_FOURTH)),
            backgroundShape,
            rippleShape
        );
        
        setBackground(rippleDrawable);
        setClickable(true);
        setFocusable(true);
    }
    
    /**
     * 禁用水波纹效果（仅显示纯色背景）
     */
    private void disableRippleEffect() {
        float cornerRadius = dpToPx(rippleCornerRadiusDp);
        float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius};
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        
        // 仅设置背景，无水波纹效果
        ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
        backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
        
        setBackground(backgroundShape);
        setClickable(false);
        setFocusable(false);
    }
    
    /**
     * 设置点击状态（控制是否显示水波纹效果）
     * @param clickable true：启用水波纹；false：禁用水波纹
     */
    private void setClickableState(boolean clickable) {
        rippleEnabled = clickable;
        if (clickable) {
            setupRippleEffect();
        } else {
            disableRippleEffect();
        }
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}



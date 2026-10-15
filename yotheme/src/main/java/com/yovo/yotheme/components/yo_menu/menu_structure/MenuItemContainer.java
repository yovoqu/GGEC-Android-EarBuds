package com.yovo.yotheme.components.yo_menu.menu_structure;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.components.yo_menu.YoMenuInterface;
import com.yovo.yotheme.constants.MenuConstants;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description MenuItemContainer 菜单项容器
 * @note 包含水波纹效果和文本显示
 */
public class MenuItemContainer extends LinearLayout {

    private int itemId = -1;
    private TextView textView;

    public MenuItemContainer(Context context) {
        this(context, null);
    }

    public MenuItemContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MenuItemContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dpToPx(MenuConstants.MENU_ITEM_MIN_HEIGHT_DP.getIntValue()));
        initChildren(context);
        setupRippleEffect();
    }

    private void initChildren(Context context) {
        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MenuConstants.MENU_ITEM_TEXT_SIZE_SP.getIntValue());
        textView.setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        
        int paddingH = dpToPx(MenuConstants.MENU_ITEM_PADDING_HORIZONTAL_DP.getIntValue());
        int paddingV = dpToPx(MenuConstants.MENU_ITEM_PADDING_VERTICAL_DP.getIntValue());
        textView.setPadding(paddingH, paddingV, paddingH, paddingV);
        
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textView.setLayoutParams(lp);
        
        addView(textView);
    }

    public void bindConfig(YoMenuInterface.MenuItemConfig config) {
        YoMenuInterface.MenuItemConfig safeConfig = Objects.requireNonNull(config, "Menu item config must not be null");
        this.itemId = safeConfig.itemId;
        textView.setText(Objects.requireNonNull(safeConfig.text, "Menu item text must not be null"));
        setEnabled(safeConfig.enabled);
        setAlpha(safeConfig.enabled ? 1f : 0.5f);
    }

    public int getItemId() {
        return itemId;
    }

    /**
     * 获取文本起点相对于父容器的 X 坐标
     * 用于分隔线对齐
     */
    public int getTextStartXInParent() {
        return getLeft() + textView.getPaddingLeft();
    }
    
    /**
     * 获取文本终点相对于父容器的 X 坐标
     * 用于分隔线对齐
     */
    public int getTextEndXInParent() {
        return getRight() - textView.getPaddingRight();
    }

    /**
     * 设置自定义垂直内边距（用于减小菜单行高）
     * @param paddingDp 垂直内边距（dp）
     */
    public void setCustomPaddingVertical(int paddingDp) {
        int paddingH = dpToPx(MenuConstants.MENU_ITEM_PADDING_HORIZONTAL_DP.getIntValue());
        int paddingV = dpToPx(paddingDp);
        textView.setPadding(paddingH, paddingV, paddingH, paddingV);
    }

    /**
     * 设置水波纹效果
     */
    private void setupRippleEffect() {
        float cornerRadius = dpToPx(MenuConstants.MENU_ITEM_RIPPLE_CORNER_RADIUS_DP.getFloatValue());
        float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius};
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        
        ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
        backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
        
        ShapeDrawable rippleShape = new ShapeDrawable(roundRectShape);
        rippleShape.getPaint().setColor(ColorMapper.map(YoThemeColor.BACKGROUND_FOURTH));
        
        RippleDrawable rippleDrawable = new RippleDrawable(
            ColorStateList.valueOf(ColorMapper.map(YoThemeColor.BACKGROUND_FOURTH)),
            backgroundShape,
            rippleShape
        );
        
        setBackground(rippleDrawable);
        setClickable(true);
        setFocusable(true);
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}


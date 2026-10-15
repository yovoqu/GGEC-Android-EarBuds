package com.yovo.yotheme.components.yo_button;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.small_widget.YoArrowDownIcon;
import com.yovo.yotheme.small_widget.animation.SmoothWidthAnimator;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description 下拉按钮组件
 */
public class YoDropdownButton extends LinearLayout {
    private static final String TAG = "YoDropdownButton";

    // 默认配置
    private static final float DEFAULT_CORNER_RADIUS_DP = 32f;
    private static final float DEFAULT_PADDING_HORIZONTAL_DP = 16f;
    private static final float DEFAULT_PADDING_VERTICAL_DP = 12f;
    private static final float DEFAULT_TEXT_SIZE_SP = 16f;
    private static final float DEFAULT_ICON_SIZE_DP = 16f;
    private static final float DEFAULT_TEXT_ICON_GAP_DP = 8f;
    private static final float DEFAULT_MIN_WIDTH_DP = 64f;  // 最小宽度

    private TextView textView;
    private YoArrowDownIcon arrowIcon;
    private SmoothWidthAnimator widthAnimator;
    
    private int normalColor;
    private int pressedColor;
    private int textColor;
    private int iconColor;
    private boolean selectedTextBold;
    
    private float cornerRadiusPx;
    private boolean isExpanded = false;  // 展开状态
    private boolean enableWidthAnimation = true;  // 是否启用宽度变化动画
    private int maxTextWidthPx = 0;  // 文本最大宽度，0 表示不限制

    public YoDropdownButton(Context context) {
        this(context, null);
    }

    public YoDropdownButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoDropdownButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 设置为横向布局
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        
        // 初始化颜色
        normalColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_LIGHT_GRAY);
        pressedColor = ColorMapper.map(YoThemeColor.BACKGROUND_FOURTH);
        textColor = ColorMapper.map(YoThemeColor.FONT_PRIMARY);
        iconColor = ColorMapper.map(YoThemeColor.BLACK);
        
        // 初始化圆角
        cornerRadiusPx = dpToPx(DEFAULT_CORNER_RADIUS_DP);
        
        // 创建文本视图
        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE_SP);
        textView.setTextColor(textColor);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setSingleLine(true);
        textView.setMaxLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);  // 超出部分使用省略号，避免占满整行
        
        LayoutParams textParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT,  // 响应式宽度
            LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(textParams);
        
        // 创建箭头图标
        arrowIcon = new YoArrowDownIcon(context);
        arrowIcon.setArrowColor(iconColor);
        arrowIcon.setIconSize((int) DEFAULT_ICON_SIZE_DP);
        
        LayoutParams iconParams = new LayoutParams(
            dpToPx(DEFAULT_ICON_SIZE_DP),
            dpToPx(DEFAULT_ICON_SIZE_DP)
        );
        iconParams.leftMargin = dpToPx(DEFAULT_TEXT_ICON_GAP_DP);
        arrowIcon.setLayoutParams(iconParams);
        
        // 添加子视图
        addView(textView);
        addView(arrowIcon);
        
        // 设置内边距
        int paddingH = dpToPx(DEFAULT_PADDING_HORIZONTAL_DP);
        int paddingV = dpToPx(DEFAULT_PADDING_VERTICAL_DP);
        setPadding(paddingH, paddingV, paddingH, paddingV);
        
        // 设置最小宽度
        setMinimumWidth(dpToPx(DEFAULT_MIN_WIDTH_DP));
        
        // 初始化宽度动画器
        widthAnimator = new SmoothWidthAnimator(this);
        widthAnimator.setDuration(200L)
                     .setAnimationEnabled(enableWidthAnimation);
        
        // 设置背景
        setBackground(createButtonBackground());
        
        // 设置可点击和可聚焦
        setClickable(true);
        setFocusable(true);
        
        // 取消默认阴影
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
            setStateListAnimator(null);
            setElevation(0f);
        }
    }

    /**
     * 创建按钮背景
     */
    private Drawable createButtonBackground() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(this.normalColor);
        content.setCornerRadius(this.cornerRadiusPx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int rippleColor = ColorUtils.setAlphaComponent(this.pressedColor, 80);
            ColorStateList ripple = ColorStateList.valueOf(rippleColor);
            return new RippleDrawable(ripple, content, null);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            GradientDrawable pressedDrawable = new GradientDrawable();
            pressedDrawable.setColor(this.pressedColor);
            pressedDrawable.setCornerRadius(this.cornerRadiusPx);
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
            stateListDrawable.addState(new int[]{}, content);
            return stateListDrawable;
        }
    }

    /**
     * 设置按钮文本（带动画）
     */
    public void setText(String text) {
        setText(text, enableWidthAnimation);
    }

    /**
     * 设置按钮文本（可控制是否使用动画）
     * @param text 文本内容
     * @param animate 是否使用宽度变化动画
     */
    public void setText(String text, boolean animate) {
        String safeText = text != null ? text : "";
        boolean shouldAnimate = animate && enableWidthAnimation;

        textView.setText(safeText);
        if (maxTextWidthPx > 0) {
            textView.setMaxWidth(maxTextWidthPx);
        }

        if (shouldAnimate) {
            // 使用 post 确保布局完成后再执行动画，无论是否是第一次
            post(() -> {
                if (widthAnimator != null) {
                    measure(
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    );
                    int targetWidth = getMeasuredWidth();
                    widthAnimator.animateToWidth(targetWidth);
                }
            });
        } else {
            // 不使用动画时，直接请求重新布局
            requestLayout();
        }
    }

    /**
     * 设置按钮文本（带动画）
     */
    public void setText(int resId) {
        setText(resId, enableWidthAnimation);
    }

    /**
     * 设置按钮文本（可控制是否使用动画）
     * @param resId 字符串资源ID
     * @param animate 是否使用宽度变化动画
     */
    public void setText(int resId, boolean animate) {
        setText(getContext().getString(resId), animate);
    }

    /**
     * 获取按钮文本
     */
    public String getText() {
        return textView.getText().toString();
    }

    /**
     * 设置选中项展示文本颜色（按钮上当前选中值的文字颜色）
     */
    public void setSelectedTextColor(int color) {
        setTextColor(color);
    }

    /**
     * 设置文本颜色
     */
    public void setTextColor(int color) {
        this.textColor = color;
        textView.setTextColor(color);
    }

    /**
     * 设置选中项展示文本是否加粗（按钮上当前选中值的文字样式）
     */
    public void setSelectedTextBold(boolean bold) {
        this.selectedTextBold = bold;
        textView.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    /**
     * 选中项展示文本是否加粗
     */
    public boolean isSelectedTextBold() {
        return selectedTextBold;
    }

    /**
     * 设置文本大小
     */
    public void setTextSize(float sizeSp) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /**
     * 设置箭头图标颜色
     */
    public void setIconColor(int color) {
        this.iconColor = color;
        arrowIcon.setArrowColor(color);
    }

    /**
     * 设置箭头图标大小
     */
    public void setIconSize(int sizeDp) {
        arrowIcon.setIconSize(sizeDp);
        LayoutParams params = (LayoutParams) arrowIcon.getLayoutParams();
        params.width = dpToPx(sizeDp);
        params.height = dpToPx(sizeDp);
        arrowIcon.setLayoutParams(params);
    }

    /**
     * 设置按钮背景颜色
     */
    public void setButtonColor(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;
        setBackground(createButtonBackground());
    }

    /**
     * 设置圆角半径
     */
    public void setCornerRadius(float radiusDp) {
        this.cornerRadiusPx = dpToPx(radiusDp);
        setBackground(createButtonBackground());
    }

    /**
     * 设置文本与图标间距
     */
    public void setTextIconGap(int gapDp) {
        LayoutParams params = (LayoutParams) arrowIcon.getLayoutParams();
        params.leftMargin = dpToPx(gapDp);
        arrowIcon.setLayoutParams(params);
    }

    /**
     * 设置按钮内边距
     */
    public void setButtonPadding(int horizontalDp, int verticalDp) {
        int paddingH = dpToPx(horizontalDp);
        int paddingV = dpToPx(verticalDp);
        setPadding(paddingH, paddingV, paddingH, paddingV);
    }

    /**
     * 设置是否启用
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.5f);
    }

    /**
     * 设置展开状态
     * @param expanded true为展开（箭头向上），false为收起（箭头向下）
     * @param animate 是否播放动画
     */
    public void setExpanded(boolean expanded, boolean animate) {
        if (this.isExpanded == expanded) {
            return;
        }
        
        this.isExpanded = expanded;
        
        float targetRotation = expanded ? 180f : 0f;
        
        if (animate) {
            arrowIcon.animate()
                    .rotation(targetRotation)
                    .setDuration(200)
                    .start();
        } else {
            arrowIcon.setRotation(targetRotation);
        }
    }

    /**
     * 切换展开状态
     * @param animate 是否播放动画
     */
    public void toggleExpanded(boolean animate) {
        setExpanded(!isExpanded, animate);
    }

    /**
     * 获取当前展开状态
     * @return true为展开，false为收起
     */
    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * 设置是否启用宽度变化动画
     * @param enabled true为启用，false为禁用
     */
    public void setWidthAnimationEnabled(boolean enabled) {
        this.enableWidthAnimation = enabled;
        if (widthAnimator != null) {
            widthAnimator.setAnimationEnabled(enabled);
        }
    }

    /**
     * 设置宽度变化动画时长
     * @param durationMs 动画时长（毫秒）
     */
    public void setWidthAnimationDuration(long durationMs) {
        if (widthAnimator != null) {
            widthAnimator.setDuration(durationMs);
        }
    }

    /**
     * 获取是否启用了宽度变化动画
     * @return true为启用，false为禁用
     */
    public boolean isWidthAnimationEnabled() {
        return enableWidthAnimation;
    }

    /**
     * 限制文本最大宽度（dp），避免文本过长挤压布局
     */
    public void setMaxTextWidthDp(int maxWidthDp) {
        if (maxWidthDp <= 0) {
            this.maxTextWidthPx = 0;
            textView.setMaxWidth(Integer.MAX_VALUE);
            return;
        }
        this.maxTextWidthPx = dpToPx(maxWidthDp);
        textView.setMaxWidth(maxTextWidthPx);
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getContext().getResources().getDisplayMetrics()
        );
    }

    /**
     * 保存状态（处理配置更改，如屏幕旋转）
     * 注意：这不能处理应用重启或缓存清除，需要配合外部持久化方案
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.text = getText();
        ss.isExpanded = this.isExpanded;
        ss.normalColor = this.normalColor;
        ss.pressedColor = this.pressedColor;
        ss.textColor = this.textColor;
        ss.iconColor = this.iconColor;
        ss.selectedTextBold = this.selectedTextBold;
        return ss;
    }

    /**
     * 恢复状态（处理配置更改，如屏幕旋转）
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        setText(ss.text, false);  // 恢复时不使用动画
        setExpanded(ss.isExpanded, false);
        setButtonColor(ss.normalColor, ss.pressedColor);
        setTextColor(ss.textColor);
        setIconColor(ss.iconColor);
        setSelectedTextBold(ss.selectedTextBold);
    }

    /**
     * 状态保存类
     */
    static class SavedState extends BaseSavedState {
        String text;
        boolean isExpanded;
        int normalColor;
        int pressedColor;
        int textColor;
        int iconColor;
        boolean selectedTextBold;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            text = in.readString();
            isExpanded = in.readByte() != 0;
            normalColor = in.readInt();
            pressedColor = in.readInt();
            textColor = in.readInt();
            iconColor = in.readInt();
            selectedTextBold = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(text);
            out.writeByte((byte) (isExpanded ? 1 : 0));
            out.writeInt(normalColor);
            out.writeInt(pressedColor);
            out.writeInt(textColor);
            out.writeInt(iconColor);
            out.writeByte((byte) (selectedTextBold ? 1 : 0));
        }

        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}


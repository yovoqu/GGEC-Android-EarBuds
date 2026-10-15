package com.yovo.yotheme.components.yo_button;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.TextViewCompat;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.ButtonConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description 自定义Yo按钮
 * 
 * @note 包含样式和背景定义，便于移植到其他项目
 * @note 不依赖任何第三方库，纯原生实现
 * 
 */
public class YoButton extends AppCompatTextView {
    private static final String TAG = "YoButton";

    // 使用颜色系统中的颜色
    private int normalColor;
    private int pressedColor;
    private int textColor;

    // 实例状态，用于动态修改按钮样式
    private float cornerRadiusPx;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建一个新的 YoButton 实例
     *
     * @param context 上下文对象
     * @return YoButton 实例
     */
    public static YoButton create(Context context) {
        return new YoButton(context);
    }

    /**
     * 创建一个带文本的 YoButton 实例
     *
     * @param context 上下文对象
     * @param text    按钮显示的文本
     * @return YoButton 实例
     */
    public static YoButton create(Context context, String text) {
        YoButton button = new YoButton(context);
        button.setText(text);
        return button;
    }

    // ==================== 构造函数 ====================

    public YoButton(Context context) {
        super(context);
        init(context, null);
    }

    public YoButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public YoButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        this.cornerRadiusPx = dpToPx(context, ButtonConstants.CORNER_RADIUS.getValue());
        
        // 初始化颜色
        normalColor = ColorMapper.map(YoThemeColor.BRAND); // 宇宙蓝（默认）
        pressedColor = ColorMapper.map(YoThemeColor.COLOR_PRIMARY_DARK); // 深蓝（按下）
        textColor = ColorMapper.map(YoThemeColor.WHITE); // 文本颜色（白色）

        // 创建背景
        setBackground(createButtonBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
        }
        
        // 设置文本颜色
        setTextColor(textColor);
        
        // 设置文本样式
        setAllCaps(false);
        setTypeface(getTypeface(), Typeface.BOLD);
        
        // 设置布局参数
        setMinWidth(dpToPx(context, ButtonConstants.MIN_WIDTH.getValue()));
        setMinHeight(dpToPx(context, ButtonConstants.MIN_HEIGHT.getValue()));
        setPadding(
                dpToPx(context, ButtonConstants.PADDING_HORIZONTAL.getValue()),
                dpToPx(context, ButtonConstants.PADDING_VERTICAL.getValue()),
                dpToPx(context, ButtonConstants.PADDING_HORIZONTAL.getValue()),
                dpToPx(context, ButtonConstants.PADDING_VERTICAL.getValue())
        );
        
        // 设置居中
        setGravity(Gravity.CENTER);
        
        // 设置可点击和可聚焦
        setClickable(true);
        setFocusable(true);
        
        // 取消默认的阴影（Android 5.0+特性）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setStateListAnimator(null);
            setElevation(ButtonConstants.ELEVATION_NONE.getFloatValue());
        }
        
        // 设置文本自动调整大小功能
        setupAutoSizeText();

        // 基础交互能力
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /**
     * 设置文本自动调整大小。
     * 为了在 compileSdkVersion 25 下兼容，我们使用反射来调用 API 26 (Android O) 的新特性。
     */
    private void setupAutoSizeText() {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                ButtonConstants.MIN_TEXT_SIZE.getValue(),
                ButtonConstants.MAX_TEXT_SIZE.getValue(),
                ButtonConstants.AUTO_SIZE_STEP_GRANULARITY.getValue(),
                TypedValue.COMPLEX_UNIT_SP
        );
    }

    /**
     * 创建按钮背景
     * 实现与bg_hm_button.xml相同的效果
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
     * dp转px工具方法
     */
    private int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
    
    /**
     * 设置按钮主色调
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
        this.cornerRadiusPx = dpToPx(getContext(), radiusDp);
        setBackground(createButtonBackground());
    }
    
    /**
     * 设置按钮尺寸
     * @param widthDp 宽度(dp)
     * @param heightDp 高度(dp)
     * @param horizontalPaddingDp 水平内边距(dp)
     * @param verticalPaddingDp 垂直内边距(dp)
     */
    public void setButtonSize(int widthDp, int heightDp, int horizontalPaddingDp, int verticalPaddingDp) {
        Context context = getContext();
        
        if (widthDp > 0) {
            setMinWidth(dpToPx(context, widthDp));
        }
        
        if (heightDp > 0) {
            setMinHeight(dpToPx(context, heightDp));
        }
        
        setPadding(
            dpToPx(context, horizontalPaddingDp),
            dpToPx(context, verticalPaddingDp),
            dpToPx(context, horizontalPaddingDp),
            dpToPx(context, verticalPaddingDp)
        );
    }
    
    /**
     * 设置按钮宽度（通过LayoutParams）
     * @param widthDp 宽度(dp)，-1表示MATCH_PARENT，-2表示WRAP_CONTENT
     */
    public void setButtonWidth(int widthDp) {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        if (widthDp == -1) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (widthDp == -2) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            params.width = dpToPx(getContext(), widthDp);
        }
        
        setLayoutParams(params);
    }
    
    /**
     * 设置按钮高度（通过LayoutParams）
     * @param heightDp 高度(dp)，-1表示MATCH_PARENT，-2表示WRAP_CONTENT
     */
    public void setButtonHeight(int heightDp) {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        if (heightDp == -1) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (heightDp == -2) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            params.height = dpToPx(getContext(), heightDp);
        }
        
        setLayoutParams(params);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
        info.setClickable(isClickable());
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.normalColor = this.normalColor;
        ss.pressedColor = this.pressedColor;
        ss.cornerRadiusPx = this.cornerRadiusPx;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.normalColor = ss.normalColor;
        this.pressedColor = ss.pressedColor;
        this.cornerRadiusPx = ss.cornerRadiusPx;
        setBackground(createButtonBackground());
    }

    static class SavedState extends BaseSavedState {
        int normalColor;
        int pressedColor;
        float cornerRadiusPx;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            normalColor = in.readInt();
            pressedColor = in.readInt();
            cornerRadiusPx = in.readFloat();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(normalColor);
            out.writeInt(pressedColor);
            out.writeFloat(cornerRadiusPx);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
} 

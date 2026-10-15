package com.yovo.yotheme.components.yo_list.list_structure;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.components.yo_button.YoButton;
import com.yovo.yotheme.components.yo_switch.YoSwitch;
import com.yovo.yotheme.constants.ListConstants;

/**
 * 右侧操作区容器，支持 Icon/Button/Switch/Text 及组合
 */
public class ListTrailing extends LinearLayout {

    private int gapDp = ListConstants.TRAILING_GAP_DP.getIntValue();
    private TextView textView;
    private ImageView iconView;
    private YoButton buttonView;
    private YoSwitch switchView;
    private View customView;

    public ListTrailing(Context context) {
        this(context, null);
    }

    public ListTrailing(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListTrailing(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    public void setGapDp(int gapDp) {
        this.gapDp = Math.max(0, gapDp);
        requestLayout();
    }

    public void setText(String text) {
        ensureTextView();
        textView.setText(text);
        textView.setTextColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));
        textView.setTextSize(ListConstants.TRAILING_TEXT_SIZE_SP.getFloatValue());
    }

    public void setIcon(Object icon) {
        ensureIconView();
        if (icon instanceof Drawable) {
            iconView.setImageDrawable((Drawable) icon);
        } else if (icon instanceof Integer) {
            iconView.setImageResource((Integer) icon);
        }
        int sizePx = dpToPx(ListConstants.TRAILING_ICON_SIZE_DP.getIntValue());
        LayoutParams lp = new LayoutParams(sizePx, sizePx);
        iconView.setLayoutParams(lp);
    }

    public void setButtonText(String text) {
        ensureButtonView();
        buttonView.setText(text);
    }

    public void setSwitchChecked(boolean checked) {
        ensureSwitchView();
        switchView.setChecked(checked);
    }

    public YoSwitch getSwitchView() { ensureSwitchView(); return switchView; }
    public YoButton getButtonView() { ensureButtonView(); return buttonView; }
    public ImageView getIconView() { ensureIconView(); return iconView; }
    public TextView getTextView() { ensureTextView(); return textView; }
    public View getCustomView() { return customView; }
    
    /**
     * 设置自定义View
     * @param view 自定义View实例
     */
    public void setCustomView(View view) {
        if (view == null) return;
        
        // 移除旧的自定义View
        if (customView != null) {
            removeView(customView);
        }
        
        this.customView = view;
        addWithGap(view);
    }

    private void ensureTextView() {
        if (textView == null) {
            textView = new TextView(getContext());
            addWithGap(textView);
        }
    }

    private void ensureIconView() {
        if (iconView == null) {
            iconView = new ImageView(getContext());
            addWithGap(iconView);
        }
    }

    private void ensureButtonView() {
        if (buttonView == null) {
            buttonView = new YoButton(getContext());
            addWithGap(buttonView);
        }
    }

    private void ensureSwitchView() {
        if (switchView == null) {
            switchView = new YoSwitch(getContext());
            addWithGap(switchView);
        }
    }

    private void addWithGap(View child) {
        if (getChildCount() > 0) {
            LayoutParams gap = new LayoutParams(dpToPx(gapDp), 1);
            View spacer = new View(getContext());
            spacer.setLayoutParams(gap);
            addView(spacer);
        }
        addView(child);
    }

    private int dpToPx(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}



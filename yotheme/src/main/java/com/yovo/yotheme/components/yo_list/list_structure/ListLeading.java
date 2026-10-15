package com.yovo.yotheme.components.yo_list.list_structure;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import androidx.appcompat.widget.AppCompatImageView;

import com.yovo.yotheme.constants.ListConstants;

/**
 * 左侧元素（图标/图片/头像）
 */
public class ListLeading extends AppCompatImageView {

    private int leadingSizeDp = ListConstants.LEADING_DEFAULT_SIZE_DP.getIntValue();

    public ListLeading(Context context) {
        this(context, null);
    }

    public ListLeading(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListLeading(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.CENTER_CROP);
        applySize();
    }

    public void setLeadingDrawable(Drawable drawable) {
        setImageDrawable(drawable);
    }

    public void setLeadingSizeDp(int sizeDp) {
        this.leadingSizeDp = sizeDp;
        applySize();
    }

    private void applySize() {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(dpToPx(leadingSizeDp), dpToPx(leadingSizeDp));
        } else {
            lp.width = dpToPx(leadingSizeDp);
            lp.height = dpToPx(leadingSizeDp);
        }
        setLayoutParams(lp);
    }

    private int dpToPx(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}



package com.yovo.yotheme.components.yo_scrollbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ScrollView variant that applies a maximum height during the normal measure pass.
 */
public class YoMaxHeightScrollView extends ScrollView {

    private int maxHeightPx;

    public YoMaxHeightScrollView(@NonNull Context context) {
        super(context);
    }

    public YoMaxHeightScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public YoMaxHeightScrollView(@NonNull Context context,
                                 @Nullable AttributeSet attrs,
                                 int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxHeightDp(int maxHeightDp) {
        setMaxHeightPx(dpToPx(maxHeightDp));
    }

    public void setMaxHeightPx(int maxHeightPx) {
        int safeMaxHeightPx = Math.max(0, maxHeightPx);
        if (this.maxHeightPx == safeMaxHeightPx) {
            return;
        }
        this.maxHeightPx = safeMaxHeightPx;
        requestLayout();
    }

    public int getMaxHeightPx() {
        return maxHeightPx;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            if (heightMode == MeasureSpec.UNSPECIFIED || heightSize > maxHeightPx) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

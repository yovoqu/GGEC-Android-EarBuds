package com.yovo.yotheme.components.yo_scrollbar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.BarState;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarConfig;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarMetricsUtil;

/**
 * 滚动容器：ScrollView + YoScrollBar + 可选限高。
 * <p>
 * 对齐 HarmonyOS Scroll + ScrollBar 组合用法，是对话框/菜单等场景的首选集成方式。
 */
public class YoScrollContainer extends FrameLayout {

    private final YoMaxHeightScrollView scrollView;
    @Nullable
    private YoScrollBar scrollBar;
    @NonNull
    private ScrollBarConfig scrollBarConfig = ScrollBarConfig.defaults();
    private int maxHeightPx;
    private boolean scrollBarBound;

    public YoScrollContainer(@NonNull Context context) {
        this(context, ScrollBarConfig.defaults());
    }

    public YoScrollContainer(@NonNull Context context, @NonNull ScrollBarConfig scrollBarConfig) {
        super(context);
        this.scrollBarConfig = scrollBarConfig;
        setClipChildren(false);
        setClipToPadding(false);

        scrollView = new YoMaxHeightScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setClipToPadding(false);
        scrollView.setClipChildren(false);
        addView(scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        applyScrollBarPolicy();
    }

    @NonNull
    public static Builder builder(@NonNull Context context) {
        return new Builder(context);
    }

    @NonNull
    public ScrollView getScrollView() {
        return scrollView;
    }

    @Nullable
    public YoScrollBar getScrollBar() {
        return scrollBar;
    }

    public void setScrollBarConfig(@NonNull ScrollBarConfig config) {
        scrollBarConfig = config;
        if (scrollBar != null) {
            scrollBar.setConfig(config);
        }
        applyScrollBarPolicy();
    }

    public void setMaxHeightDp(int maxHeightDp) {
        scrollView.setMaxHeightDp(maxHeightDp);
        maxHeightPx = maxHeightDp > 0
                ? ScrollBarMetricsUtil.dpToPx(getContext(), maxHeightDp)
                : 0;
        requestLayout();
    }

    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = Math.max(0, maxHeightPx);
        scrollView.setMaxHeightPx(this.maxHeightPx);
        requestLayout();
    }

    public int getMaxHeightPxLimit() {
        return maxHeightPx;
    }

    public void setContainerCornerRadiusDp(float cornerRadiusDp) {
        setScrollBarConfig(scrollBarConfig.toBuilder()
                .containerCornerRadiusDp(cornerRadiusDp)
                .build());
    }

    public void setContentView(@NonNull View content) {
        scrollView.removeAllViews();
        scrollView.addView(content, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        requestLayout();
    }

    public void release() {
        if (scrollBar != null) {
            scrollBar.unbind();
            scrollBarBound = false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int innerWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int widthSpec = MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY);

        View content = scrollView.getChildCount() > 0 ? scrollView.getChildAt(0) : null;
        int contentHeight = 0;
        if (content != null) {
            content.measure(
                    widthSpec,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            contentHeight = content.getMeasuredHeight();
        }

        int scrollHeight = contentHeight;
        if (maxHeightPx > 0) {
            scrollHeight = Math.min(contentHeight, maxHeightPx);
        } else if (scrollView.getMaxHeightPx() > 0) {
            scrollHeight = Math.min(contentHeight, scrollView.getMaxHeightPx());
        }

        int scrollHeightSpec = MeasureSpec.makeMeasureSpec(scrollHeight, MeasureSpec.EXACTLY);
        scrollView.measure(widthSpec, scrollHeightSpec);

        if (scrollBar != null && scrollBar.getVisibility() != GONE) {
            scrollBar.measure(widthSpec, scrollHeightSpec);
        }

        setMeasuredDimension(width, scrollHeight + getPaddingTop() + getPaddingBottom());
    }

    private void applyScrollBarPolicy() {
        if (scrollBarConfig.getBarState() == BarState.OFF) {
            if (scrollBar != null) {
                scrollBar.unbind();
                scrollBarBound = false;
                scrollBar.setVisibility(GONE);
            }
            return;
        }

        if (scrollBar == null) {
            scrollBar = new YoScrollBar(getContext());
            addView(scrollBar, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
        }
        scrollBar.setConfig(scrollBarConfig);
        scrollBar.setVisibility(VISIBLE);
        if (!scrollBarBound) {
            scrollBar.bind(scrollView);
            scrollBarBound = true;
        }
    }

    public static final class Builder {
        private final Context context;
        private ScrollBarConfig scrollBarConfig = ScrollBarConfig.defaults();
        private int maxHeightDp;

        private Builder(@NonNull Context context) {
            this.context = context;
        }

        public Builder scrollBarConfig(@NonNull ScrollBarConfig config) {
            scrollBarConfig = config;
            return this;
        }

        public Builder barState(@NonNull BarState barState) {
            scrollBarConfig = scrollBarConfig.toBuilder().barState(barState).build();
            return this;
        }

        public Builder maxHeightDp(int maxHeightDp) {
            this.maxHeightDp = maxHeightDp;
            return this;
        }

        public Builder containerCornerRadiusDp(float cornerRadiusDp) {
            scrollBarConfig = scrollBarConfig.toBuilder()
                    .containerCornerRadiusDp(cornerRadiusDp)
                    .build();
            return this;
        }

        @NonNull
        public YoScrollContainer build() {
            YoScrollContainer container = new YoScrollContainer(context, scrollBarConfig);
            if (maxHeightDp > 0) {
                container.setMaxHeightDp(maxHeightDp);
            }
            return container;
        }
    }
}

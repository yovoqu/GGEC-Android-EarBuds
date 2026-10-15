package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.ScrollBarConstants;

/**
 * 滚动条样式与行为配置，映射 HarmonyOS scrollBar / scrollBarWidth / scrollBarColor / scrollBarMargin。
 */
public final class ScrollBarConfig {

    private final BarState barState;
    private final ScrollBarDirection direction;
    private final float thumbWidthDp;
    private final float thumbWidthActiveDp;
    @ColorInt
    private final int thumbColor;
    private final float marginEndDp;
    private final float marginVerticalDp;
    private final float containerCornerRadiusDp;
    private final long autoHideDelayMs;
    private final float minThumbHeightDp;

    private ScrollBarConfig(Builder builder) {
        barState = builder.barState;
        direction = builder.direction;
        thumbWidthDp = builder.thumbWidthDp;
        thumbWidthActiveDp = builder.thumbWidthActiveDp;
        thumbColor = builder.thumbColor;
        marginEndDp = builder.marginEndDp;
        marginVerticalDp = builder.marginVerticalDp;
        containerCornerRadiusDp = builder.containerCornerRadiusDp;
        autoHideDelayMs = builder.autoHideDelayMs;
        minThumbHeightDp = builder.minThumbHeightDp;
    }

    public static ScrollBarConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public BarState getBarState() {
        return barState;
    }

    public ScrollBarDirection getDirection() {
        return direction;
    }

    public float getThumbWidthDp() {
        return thumbWidthDp;
    }

    public float getThumbWidthActiveDp() {
        return thumbWidthActiveDp;
    }

    @ColorInt
    public int getThumbColor() {
        return thumbColor;
    }

    public float getMarginEndDp() {
        return marginEndDp;
    }

    public float getMarginVerticalDp() {
        return marginVerticalDp;
    }

    public float getContainerCornerRadiusDp() {
        return containerCornerRadiusDp;
    }

    public long getAutoHideDelayMs() {
        return autoHideDelayMs;
    }

    public float getMinThumbHeightDp() {
        return minThumbHeightDp;
    }

    public boolean isAutoHide() {
        return barState == BarState.AUTO;
    }

    public Builder toBuilder() {
        return builder()
                .barState(barState)
                .direction(direction)
                .thumbWidthDp(thumbWidthDp)
                .thumbWidthActiveDp(thumbWidthActiveDp)
                .thumbColor(thumbColor)
                .marginEndDp(marginEndDp)
                .marginVerticalDp(marginVerticalDp)
                .containerCornerRadiusDp(containerCornerRadiusDp)
                .autoHideDelayMs(autoHideDelayMs)
                .minThumbHeightDp(minThumbHeightDp);
    }

    public static final class Builder {
        private BarState barState = BarState.AUTO;
        private ScrollBarDirection direction = ScrollBarDirection.VERTICAL;
        private float thumbWidthDp = ScrollBarConstants.SCROLLBAR_WIDTH_DP.getFloatValue();
        private float thumbWidthActiveDp = ScrollBarConstants.SCROLLBAR_WIDTH_HOVER_DP.getFloatValue();
        @ColorInt
        private int thumbColor = ColorMapper.map(YoThemeColor.ICON_TERTIARY);
        private float marginEndDp = ScrollBarConstants.SCROLLBAR_MARGIN_END_DP.getFloatValue();
        private float marginVerticalDp = ScrollBarConstants.SCROLLBAR_MARGIN_VERTICAL_DP.getFloatValue();
        private float containerCornerRadiusDp;
        private long autoHideDelayMs = ScrollBarConstants.AUTO_HIDE_DELAY_MS.getIntValue();
        private float minThumbHeightDp = ScrollBarConstants.SCROLLBAR_MIN_HEIGHT_DP.getFloatValue();

        public Builder barState(@Nullable BarState barState) {
            if (barState != null) {
                this.barState = barState;
            }
            return this;
        }

        public Builder direction(@Nullable ScrollBarDirection direction) {
            if (direction != null) {
                this.direction = direction;
            }
            return this;
        }

        public Builder thumbWidthDp(float thumbWidthDp) {
            this.thumbWidthDp = Math.max(0f, thumbWidthDp);
            return this;
        }

        public Builder thumbWidthActiveDp(float thumbWidthActiveDp) {
            this.thumbWidthActiveDp = Math.max(0f, thumbWidthActiveDp);
            return this;
        }

        public Builder thumbColor(@ColorInt int thumbColor) {
            this.thumbColor = thumbColor;
            return this;
        }

        public Builder marginEndDp(float marginEndDp) {
            this.marginEndDp = Math.max(0f, marginEndDp);
            return this;
        }

        public Builder marginVerticalDp(float marginVerticalDp) {
            this.marginVerticalDp = Math.max(0f, marginVerticalDp);
            return this;
        }

        /** 宿主容器圆角（dp），用于计算滚动条距顶/底避让距离（HarmonyOS API 10+）。 */
        public Builder containerCornerRadiusDp(float containerCornerRadiusDp) {
            this.containerCornerRadiusDp = Math.max(0f, containerCornerRadiusDp);
            return this;
        }

        public Builder autoHideDelayMs(long autoHideDelayMs) {
            this.autoHideDelayMs = Math.max(0L, autoHideDelayMs);
            return this;
        }

        public Builder minThumbHeightDp(float minThumbHeightDp) {
            this.minThumbHeightDp = Math.max(0f, minThumbHeightDp);
            return this;
        }

        public ScrollBarConfig build() {
            return new ScrollBarConfig(this);
        }
    }
}

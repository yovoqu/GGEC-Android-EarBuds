package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import androidx.annotation.NonNull;

import android.content.Context;

/**
 * 滚动条几何计算：滑块尺寸/位置、圆角避让边距。
 * 圆角避让逻辑参考 HarmonyOS API 10+ 滚动条距顶/底动态计算。
 */
public final class ScrollBarMetrics {

    public static final class ThumbGeometry {
        public float left;
        public float top;
        public float right;
        public float bottom;
        public float cornerRadius;
        public float trackTravelPx;
        public boolean visible;

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }
    }

    private ScrollBarMetrics() {}

    /**
     * 根据容器圆角计算滚动条轨道上下边距（dp）。
     * 圆角为 0 时不额外避让；有圆角时至少保留固定边距。
     */
    public static float resolveVerticalTrackMarginDp(@NonNull ScrollBarConfig config) {
        float fixed = config.getMarginVerticalDp();
        float corner = config.getContainerCornerRadiusDp();
        if (corner <= 0f) {
            return 0f;
        }
        return Math.max(fixed, corner * 0.35f);
    }

    @NonNull
    public static ThumbGeometry computeVerticalThumb(@NonNull Context context,
                                                     @NonNull ScrollBarConfig config,
                                                     @NonNull ScrollTarget target,
                                                     float trackWidthPx,
                                                     float trackHeightPx,
                                                     boolean active) {
        ThumbGeometry geometry = new ThumbGeometry();
        geometry.visible = false;
        geometry.trackTravelPx = 0f;

        if (!target.isScrollable() || trackWidthPx <= 0f || trackHeightPx <= 0f) {
            return geometry;
        }

        float thumbWidthPx = ScrollBarMetricsUtil.dpToPxF(
                context,
                active ? config.getThumbWidthActiveDp() : config.getThumbWidthDp());
        if (thumbWidthPx <= 0f) {
            return geometry;
        }

        float marginEndPx = ScrollBarMetricsUtil.dpToPxF(context, config.getMarginEndDp());
        float marginVerticalPx = ScrollBarMetricsUtil.dpToPxF(context, resolveVerticalTrackMarginDp(config));
        float minThumbHeightPx = ScrollBarMetricsUtil.dpToPxF(context, config.getMinThumbHeightDp());
        float cornerRadiusPx = ScrollBarMetricsUtil.dpToPxF(context,
                active
                        ? com.yovo.yotheme.constants.ScrollBarConstants.SCROLLBAR_CORNER_RADIUS_HOVER_DP.getFloatValue()
                        : com.yovo.yotheme.constants.ScrollBarConstants.SCROLLBAR_CORNER_RADIUS_DP.getFloatValue());

        float availableTrackHeight = Math.max(0f, trackHeightPx - marginVerticalPx * 2f);
        if (availableTrackHeight <= 0f) {
            return geometry;
        }

        float viewportRatio = (float) target.getViewportSize() / target.getContentSize();
        float thumbHeight = Math.max(minThumbHeightPx, availableTrackHeight * viewportRatio);
        thumbHeight = Math.min(thumbHeight, availableTrackHeight);

        float scrollRatio = target.getScrollRange() > 0
                ? (float) target.getScrollOffset() / target.getScrollRange()
                : 0f;
        scrollRatio = Math.max(0f, Math.min(1f, scrollRatio));

        float thumbTop = marginVerticalPx + scrollRatio * (availableTrackHeight - thumbHeight);
        float thumbLeft = trackWidthPx - marginEndPx - thumbWidthPx;

        geometry.left = Math.max(0f, thumbLeft);
        geometry.top = thumbTop;
        geometry.right = geometry.left + thumbWidthPx;
        geometry.bottom = thumbTop + thumbHeight;
        geometry.cornerRadius = cornerRadiusPx;
        geometry.trackTravelPx = Math.max(1f, availableTrackHeight - thumbHeight);
        geometry.visible = true;
        return geometry;
    }
}

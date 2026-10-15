package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import androidx.annotation.NonNull;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

import com.yovo.yotheme.constants.ScrollBarConstants;

/**
 * 滚动条滑块拖拽/点击交互。
 */
public final class ScrollBarTouchController {

    public interface Host {
        @NonNull
        ScrollTarget getTarget();

        @NonNull
        ScrollBarMetrics.ThumbGeometry getThumbGeometry();

        float getTrackTravelPx();

        void onDragStarted();

        void onDragEnded();

        void onInteractionStateChanged(@NonNull ScrollBarStateManager.InteractionState state);
    }

    private final View hostView;
    private final Host host;
    private final float touchExpansionPx;

    private boolean dragging;
    private float dragStartY;
    private int scrollStartOffset;
    private float trackTravelPx;

    public ScrollBarTouchController(@NonNull View hostView, @NonNull Host host) {
        this.hostView = hostView;
        this.host = host;
        touchExpansionPx = ScrollBarMetricsUtil.dpToPxF(
                hostView.getContext(),
                ScrollBarConstants.THUMB_TOUCH_EXPANSION_DP.getFloatValue());
    }

    public boolean onTouchEvent(@NonNull MotionEvent event) {
        ScrollBarMetrics.ThumbGeometry geometry = host.getThumbGeometry();
        if (!geometry.visible) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (hitTest(event.getX(), event.getY(), geometry)) {
                    dragging = true;
                    dragStartY = event.getY();
                    scrollStartOffset = host.getTarget().getScrollOffset();
                    trackTravelPx = Math.max(1f, host.getTrackTravelPx());
                    host.onInteractionStateChanged(ScrollBarStateManager.InteractionState.PRESS);
                    host.onDragStarted();
                    disallowParentIntercept(true);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return false;
                }
                host.onInteractionStateChanged(ScrollBarStateManager.InteractionState.DRAGGING);
                applyDrag(event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) {
                    return false;
                }
                dragging = false;
                host.onInteractionStateChanged(ScrollBarStateManager.InteractionState.NORMAL);
                host.onDragEnded();
                disallowParentIntercept(false);
                return true;

            default:
                return false;
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    private void applyDrag(float currentY) {
        ScrollTarget target = host.getTarget();
        float deltaY = currentY - dragStartY;
        float scrollRatio = deltaY / trackTravelPx;
        int newOffset = (int) (scrollStartOffset + scrollRatio * target.getScrollRange());
        target.scrollTo(newOffset);
    }

    private boolean hitTest(float x, float y, @NonNull ScrollBarMetrics.ThumbGeometry geometry) {
        float left = geometry.left - touchExpansionPx;
        float right = geometry.right + touchExpansionPx;
        float top = geometry.top - touchExpansionPx;
        float bottom = geometry.bottom + touchExpansionPx;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private void disallowParentIntercept(boolean disallow) {
        ViewParent parent = hostView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }
}

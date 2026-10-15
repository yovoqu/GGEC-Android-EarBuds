package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.ScrollBarConstants;

/**
 * 按 {@link BarState} 管理滚动条可见性（Auto / On / Off）。
 */
public final class ScrollBarVisibilityController {

    public interface Host {
        @NonNull
        ScrollBarConfig getConfig();

        @NonNull
        ScrollTarget getTarget();

        boolean isDragging();

        float resolveNormalAlpha();

        void showImmediate(float alpha);

        void fadeIn(float alpha);

        void fadeOut();

        void hideImmediate();
    }

    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable;
    private boolean userVisible = true;

    public ScrollBarVisibilityController(@NonNull Host host) {
        this.host = host;
        hideRunnable = this::scheduleFadeOutIfNeeded;
    }

    public void setUserVisible(boolean visible) {
        userVisible = visible;
        refresh();
    }

    public void onScrollChanged() {
        ScrollBarConfig config = host.getConfig();
        if (config.getBarState() == BarState.OFF || !userVisible) {
            return;
        }
        if (!host.getTarget().isScrollable()) {
            host.hideImmediate();
            return;
        }

        float alpha = host.resolveNormalAlpha();
        if (config.getBarState() == BarState.ON) {
            host.showImmediate(alpha);
            return;
        }

        host.fadeIn(alpha);
        resetAutoHideTimer();
    }

    public void onDragStarted() {
        cancelAutoHideTimer();
        host.showImmediate(host.resolveNormalAlpha());
    }

    public void onDragEnded() {
        refreshAfterInteraction();
    }

    public void refresh() {
        ScrollBarConfig config = host.getConfig();
        if (!userVisible || config.getBarState() == BarState.OFF) {
            host.hideImmediate();
            cancelAutoHideTimer();
            return;
        }
        if (!host.getTarget().isScrollable()) {
            host.hideImmediate();
            return;
        }
        if (config.getBarState() == BarState.ON) {
            host.showImmediate(host.resolveNormalAlpha());
            cancelAutoHideTimer();
            return;
        }
        host.hideImmediate();
    }

    public void refreshAfterInteraction() {
        if (host.getConfig().getBarState() == BarState.AUTO && userVisible) {
            resetAutoHideTimer();
        } else {
            refresh();
        }
    }

    public void cleanup() {
        cancelAutoHideTimer();
    }

    private void scheduleFadeOutIfNeeded() {
        if (host.isDragging()) {
            return;
        }
        ScrollBarConfig config = host.getConfig();
        if (config.getBarState() == BarState.AUTO && userVisible && host.getTarget().isScrollable()) {
            host.fadeOut();
        }
    }

    private void resetAutoHideTimer() {
        cancelAutoHideTimer();
        if (host.getConfig().getBarState() == BarState.AUTO) {
            handler.postDelayed(hideRunnable, host.getConfig().getAutoHideDelayMs());
        }
    }

    private void cancelAutoHideTimer() {
        handler.removeCallbacks(hideRunnable);
    }
}

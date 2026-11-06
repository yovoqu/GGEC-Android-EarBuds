package com.ggec.earbuds.ui.ai.call.subtitle;

import androidx.annotation.NonNull;

/**
 * 字幕防弹回智能滚动引擎 (Smart Scroll Controller & State Machine)。
 *
 * <p>高内聚封装：
 * <ul>
 *   <li>触控手势检测 (Touch interception)；</li>
 *   <li>滚动视口容差计算 (Viewport tolerance evaluation)；</li>
 *   <li>自动跟随状态机 (Follow mode vs. History reading lock mode)；</li>
 *   <li>悬浮胶囊状态协调 (Floating pill coordination)。</li>
 * </ul>
 * 与具体 Android View 视图彻底解耦，依赖轻量接口 {@link ScrollTarget} 与 {@link PillListener}。
 * </p>
 */
public final class AiSubtitleScrollEngine {

    public interface ScrollTarget {
        int getContentHeight();
        int getViewportScrollY();
        int getViewportHeight();
        void scrollToBottomImmediate();
        void smoothScrollToBottom();
    }

    public interface PillListener {
        void onPillVisibilityChanged(boolean visible);
    }

    private final ScrollTarget target;
    private final PillListener pillListener;
    private final int bottomTolerancePx;

    private boolean userIsScrolledUp = false;
    private boolean isUserTouching = false;
    private boolean isProgrammaticScrolling = false;
    private boolean hasContent = false;

    public AiSubtitleScrollEngine(
            @NonNull ScrollTarget target,
            @NonNull PillListener pillListener,
            int bottomTolerancePx
    ) {
        this.target = target;
        this.pillListener = pillListener;
        this.bottomTolerancePx = bottomTolerancePx;
    }

    public void onTouchDownOrMove() {
        isUserTouching = true;
    }

    public void onTouchUpOrCancel() {
        isUserTouching = false;
        evaluateScrollPosition();
    }

    public void onScrollChanged(int scrollY) {
        if (isProgrammaticScrolling) {
            return;
        }
        evaluateScrollPosition();
    }

    public void onContentUpdated(boolean hasItems) {
        this.hasContent = hasItems;
        if (!hasItems) {
            userIsScrolledUp = false;
            updatePillState();
            return;
        }

        if (userIsScrolledUp || isUserTouching) {
            updatePillState();
            return;
        }

        updatePillState();
        isProgrammaticScrolling = true;
        target.scrollToBottomImmediate();
    }

    public void onProgrammaticScrollFinished() {
        isProgrammaticScrolling = false;
    }

    public void onScrollToLatestClicked() {
        userIsScrolledUp = false;
        isUserTouching = false;
        updatePillState();
        isProgrammaticScrolling = true;
        target.smoothScrollToBottom();
    }

    public void evaluateScrollPosition() {
        int contentBottom = target.getContentHeight();
        int viewportBottom = target.getViewportScrollY() + target.getViewportHeight();

        if (contentBottom - viewportBottom <= bottomTolerancePx) {
            userIsScrolledUp = false;
        } else {
            userIsScrolledUp = true;
        }
        updatePillState();
    }

    private void updatePillState() {
        boolean shouldShow = userIsScrolledUp && hasContent;
        pillListener.onPillVisibilityChanged(shouldShow);
    }

    public boolean isScrolledUp() {
        return userIsScrolledUp;
    }

    public boolean isUserTouching() {
        return isUserTouching;
    }

    public boolean isProgrammaticScrolling() {
        return isProgrammaticScrolling;
    }
}

package com.ggec.earbuds.ui.ai.call.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class AiSubtitleScrollEngineTest {

    private FakeScrollTarget target;
    private FakePillListener pillListener;
    private AiSubtitleScrollEngine engine;

    private static final int TOLERANCE_PX = 50;

    @Before
    public void setUp() {
        target = new FakeScrollTarget();
        pillListener = new FakePillListener();
        engine = new AiSubtitleScrollEngine(target, pillListener, TOLERANCE_PX);
    }

    @Test
    public void onContentUpdatedWhenAtBottomTriggersImmediateScroll() {
        target.contentHeight = 500;
        target.viewportScrollY = 200;
        target.viewportHeight = 300; // 500 - (200 + 300) = 0 <= 50

        engine.onContentUpdated(true);

        assertEquals(1, target.immediateScrollCount);
        assertFalse(engine.isScrolledUp());
        assertFalse(pillListener.lastPillVisible);
    }

    @Test
    public void onUserTouchDownLocksAutomaticScroll() {
        target.contentHeight = 500;
        target.viewportScrollY = 200;
        target.viewportHeight = 300;

        engine.onTouchDownOrMove();
        assertTrue(engine.isUserTouching());

        // 用户按住屏幕时，新流式文本到达严禁触发滚动
        engine.onContentUpdated(true);
        assertEquals(0, target.immediateScrollCount);

        engine.onTouchUpOrCancel();
        assertFalse(engine.isUserTouching());
    }

    @Test
    public void evaluateScrollPositionDetectsScrolledUpAndShowsPill() {
        target.contentHeight = 1000;
        target.viewportScrollY = 200;
        target.viewportHeight = 300; // 1000 - 500 = 500 > 50 (上滑查看历史)

        // 先上滑判定
        engine.evaluateScrollPosition();

        assertTrue(engine.isScrolledUp());
        // 首次还没有内容更新，设置有内容
        engine.onContentUpdated(true);
        assertTrue(pillListener.lastPillVisible);

        // 新流式内容到达时，保持阅读锁定，绝不弹回
        engine.onContentUpdated(true);
        assertEquals(0, target.immediateScrollCount);
        assertTrue(pillListener.lastPillVisible);
    }

    @Test
    public void scrollToLatestClickedSmoothlyReturnsToBottomAndHidesPill() {
        target.contentHeight = 1000;
        target.viewportScrollY = 200;
        target.viewportHeight = 300;
        engine.onContentUpdated(true);
        engine.evaluateScrollPosition();
        assertTrue(engine.isScrolledUp());

        // 点击胶囊
        engine.onScrollToLatestClicked();

        assertFalse(engine.isScrolledUp());
        assertFalse(pillListener.lastPillVisible);
        assertEquals(1, target.smoothScrollCount);
    }

    private static final class FakeScrollTarget implements AiSubtitleScrollEngine.ScrollTarget {
        int contentHeight = 0;
        int viewportScrollY = 0;
        int viewportHeight = 0;
        int immediateScrollCount = 0;
        int smoothScrollCount = 0;

        @Override
        public int getContentHeight() {
            return contentHeight;
        }

        @Override
        public int getViewportScrollY() {
            return viewportScrollY;
        }

        @Override
        public int getViewportHeight() {
            return viewportHeight;
        }

        @Override
        public void scrollToBottomImmediate() {
            immediateScrollCount++;
        }

        @Override
        public void smoothScrollToBottom() {
            smoothScrollCount++;
        }
    }

    private static final class FakePillListener implements AiSubtitleScrollEngine.PillListener {
        boolean lastPillVisible = false;

        @Override
        public void onPillVisibilityChanged(boolean visible) {
            this.lastPillVisible = visible;
        }
    }
}

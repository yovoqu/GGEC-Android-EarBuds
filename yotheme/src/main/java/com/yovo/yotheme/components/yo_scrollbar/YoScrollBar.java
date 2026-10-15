package com.yovo.yotheme.components.yo_scrollbar;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.BarState;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarAnimationManager;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarConfig;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarMetrics;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarRenderer;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarStateManager;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarTouchController;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollBarVisibilityController;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.RecyclerViewScrollTarget;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollTarget;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.ScrollViewScrollTarget;
import com.yovo.yotheme.constants.ScrollBarConstants;

/**
 * YoTheme 滚动条滑块视图（薄入口）。
 * <p>
 * 显示策略 {@link BarState}、几何计算、动画与触摸交互均委托 scrollbar_structure 层。
 * 推荐通过 {@link YoScrollContainer} 与 ScrollView 组合使用。
 */
public class YoScrollBar extends View implements
        ScrollBarVisibilityController.Host,
        ScrollBarTouchController.Host,
        ScrollBarAnimationManager.Callback {

    private ScrollBarConfig config = ScrollBarConfig.defaults();
    private final ScrollBarStateManager interactionState = new ScrollBarStateManager();
    private final ScrollBarRenderer renderer;
    private final ScrollBarAnimationManager animationManager;
    private final ScrollBarVisibilityController visibilityController;
    private final ScrollBarTouchController touchController;

    @Nullable
    private ScrollTarget scrollTarget;
    private final ScrollBarMetrics.ThumbGeometry thumbGeometry = new ScrollBarMetrics.ThumbGeometry();
    private final Runnable scrollChangedRunnable = this::onScrollChanged;

    private float drawAlphaMultiplier = ScrollBarConstants.ZERO_ALPHA.getFloatValue();
    private boolean drawVisible;

    public YoScrollBar(@NonNull Context context) {
        this(context, null);
    }

    public YoScrollBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoScrollBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        renderer = new ScrollBarRenderer(config.getThumbColor());
        animationManager = new ScrollBarAnimationManager(this);
        visibilityController = new ScrollBarVisibilityController(this);
        touchController = new ScrollBarTouchController(this, this);

        interactionState.setListener(state -> {
            updateThumbGeometry();
            invalidate();
        });

        setClickable(true);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /**
     * 绑定 {@link ScrollView}（Dialog / Menu 等场景）。
     */
    public void bind(@NonNull ScrollView scrollView) {
        ScrollViewScrollTarget target = new ScrollViewScrollTarget(scrollView);
        target.attach();
        bindTarget(target);
    }

    /**
     * 绑定通用 {@link ScrollTarget}（RecyclerView 等场景需先 attach）。
     */
    public void bindTarget(@NonNull ScrollTarget target) {
        unbind();
        scrollTarget = target;
        scrollTarget.addOnScrollChangedListener(scrollChangedRunnable);
        scrollTarget.addOnContentSizeChangedListener(scrollChangedRunnable);
        post(this::refreshGeometryAndVisibility);
    }

    public void unbind() {
        if (scrollTarget != null) {
            scrollTarget.removeOnScrollChangedListener(scrollChangedRunnable);
            scrollTarget.removeOnContentSizeChangedListener(scrollChangedRunnable);
            if (scrollTarget instanceof ScrollViewScrollTarget) {
                ((ScrollViewScrollTarget) scrollTarget).detach();
            } else if (scrollTarget instanceof RecyclerViewScrollTarget) {
                ((RecyclerViewScrollTarget) scrollTarget).detach();
            }
            scrollTarget = null;
        }
    }

    public void setConfig(@NonNull ScrollBarConfig config) {
        this.config = config;
        renderer.setThumbColor(config.getThumbColor());
        visibilityController.refresh();
        updateThumbGeometry();
        invalidate();
    }

    @NonNull
    @Override
    public ScrollBarConfig getConfig() {
        return config;
    }

    public void setBarState(@NonNull BarState barState) {
        setConfig(config.toBuilder().barState(barState).build());
    }

    /** 宿主容器圆角（dp），用于轨道避让计算。 */
    public void setContainerCornerRadiusDp(float cornerRadiusDp) {
        setConfig(config.toBuilder().containerCornerRadiusDp(cornerRadiusDp).build());
    }

    public void setThumbColor(@ColorInt int color) {
        setConfig(config.toBuilder().thumbColor(color).build());
    }

    /**
     * 为已有 ScrollView 宿主附加滚动条（XML 布局等场景）。
     */
    @NonNull
    public static YoScrollBar attach(@NonNull ViewGroup scrollHost,
                                     @NonNull ScrollView scrollView,
                                     @NonNull ScrollBarConfig config) {
        scrollView.setVerticalScrollBarEnabled(false);
        YoScrollBar existing = findExistingScrollBar(scrollHost);
        YoScrollBar scrollBar = existing != null ? existing : new YoScrollBar(scrollHost.getContext());
        scrollBar.setConfig(config);
        if (existing == null) {
            scrollHost.setClipChildren(false);
            scrollHost.setClipToPadding(false);
            scrollHost.addView(scrollBar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        scrollBar.setVisibility(VISIBLE);
        scrollBar.bind(scrollView);
        return scrollBar;
    }

    @Nullable
    private static YoScrollBar findExistingScrollBar(@NonNull ViewGroup host) {
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child instanceof YoScrollBar) {
                return (YoScrollBar) child;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public ScrollTarget getTarget() {
        return scrollTarget != null ? scrollTarget : EmptyScrollTarget.INSTANCE;
    }

    @Override
    public boolean isDragging() {
        return touchController.isDragging();
    }

    @Override
    public float resolveNormalAlpha() {
        return ScrollBarConstants.SCROLLBAR_ALPHA_NORMAL.getFloatValue();
    }

    @Override
    public void showImmediate(float alpha) {
        animationManager.showImmediate(alpha);
    }

    @Override
    public void fadeIn(float alpha) {
        animationManager.fadeIn(alpha);
    }

    @Override
    public void fadeOut() {
        animationManager.fadeOut();
    }

    @Override
    public void hideImmediate() {
        animationManager.showImmediate(ScrollBarConstants.ZERO_ALPHA.getFloatValue());
    }

    @NonNull
    @Override
    public ScrollBarMetrics.ThumbGeometry getThumbGeometry() {
        return thumbGeometry;
    }

    @Override
    public float getTrackTravelPx() {
        return thumbGeometry.trackTravelPx;
    }

    @Override
    public void onDragStarted() {
        visibilityController.onDragStarted();
    }

    @Override
    public void onDragEnded() {
        visibilityController.onDragEnded();
    }

    @Override
    public void onInteractionStateChanged(@NonNull ScrollBarStateManager.InteractionState state) {
        interactionState.setState(state);
    }

    @Override
    public void onAlphaChanged(float alpha) {
        drawAlphaMultiplier = alpha;
        invalidate();
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
        drawVisible = visible;
        if (!visible) {
            drawAlphaMultiplier = ScrollBarConstants.ZERO_ALPHA.getFloatValue();
        }
        invalidate();
    }

    private void onScrollChanged() {
        updateThumbGeometry();
        visibilityController.onScrollChanged();
        invalidate();
    }

    private void refreshGeometryAndVisibility() {
        updateThumbGeometry();
        visibilityController.refresh();
        invalidate();
    }

    private void updateThumbGeometry() {
        ScrollBarMetrics.ThumbGeometry computed = ScrollBarMetrics.computeVerticalThumb(
                getContext(),
                config,
                getTarget(),
                getWidth(),
                getHeight(),
                interactionState.isActive());
        thumbGeometry.left = computed.left;
        thumbGeometry.top = computed.top;
        thumbGeometry.right = computed.right;
        thumbGeometry.bottom = computed.bottom;
        thumbGeometry.cornerRadius = computed.cornerRadius;
        thumbGeometry.trackTravelPx = computed.trackTravelPx;
        thumbGeometry.visible = computed.visible;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumbGeometry();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!drawVisible && drawAlphaMultiplier <= 0f) {
            return;
        }
        if (!thumbGeometry.visible) {
            return;
        }
        float alpha = drawAlphaMultiplier
                * resolveNormalAlpha()
                * interactionState.resolveAlphaMultiplier();
        renderer.drawThumb(canvas, thumbGeometry, alpha);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (config.getBarState() == BarState.OFF) {
            return false;
        }
        return touchController.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        unbind();
        visibilityController.cleanup();
        animationManager.cleanup();
        super.onDetachedFromWindow();
    }

    /** 未绑定 ScrollView 时的占位 Target。 */
    private enum EmptyScrollTarget implements ScrollTarget {
        INSTANCE;

        @Override
        public int getScrollOffset() {
            return 0;
        }

        @Override
        public int getScrollRange() {
            return 0;
        }

        @Override
        public int getViewportSize() {
            return 0;
        }

        @Override
        public int getContentSize() {
            return 0;
        }

        @Override
        public void scrollTo(int offset) { /* no-op */ }

        @Override
        public void addOnScrollChangedListener(@NonNull Runnable listener) { /* no-op */ }

        @Override
        public void removeOnScrollChangedListener(@NonNull Runnable listener) { /* no-op */ }

        @Override
        public void addOnContentSizeChangedListener(@NonNull Runnable listener) { /* no-op */ }

        @Override
        public void removeOnContentSizeChangedListener(@NonNull Runnable listener) { /* no-op */ }
    }
}

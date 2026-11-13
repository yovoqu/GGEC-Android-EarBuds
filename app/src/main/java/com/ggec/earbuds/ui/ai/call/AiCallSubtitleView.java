package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItemView;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleScrollEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 通话实时流式字幕卡片复合容器 (AiCallSubtitleView)。
 *
 * <p>严格遵循高内聚低耦合与 MVVM 架构：
 * <ul>
 *   <li><b>数据层解耦</b>：仅依赖不可变 {@link AiSubtitleItem} 列表，单一真实数据源；</li>
 *   <li><b>行为层解耦</b>：滚动判定与防弹回状态机交由 {@link AiSubtitleScrollEngine} 独立驱动；</li>
 *   <li><b>视图层解耦</b>：单条消息卡片由独立组件 {@link AiSubtitleItemView} 负责排版折行；</li>
 *   <li><b>弹性测量</b>：内容少时紧凑包裹，内容多时最大限制 300dp，永不挤压底部操作舱。</li>
 * </ul>
 * </p>
 */
public final class AiCallSubtitleView extends FrameLayout {

    public static final float MAX_HEIGHT_DP = 300f;
    private static final float CARD_RADIUS_DP = 20f;

    private final AiTheme theme;
    private final ScrollView scrollView;
    private final LinearLayout textContainer;
    private final TextView scrollPill;
    private final AiSubtitleScrollEngine scrollEngine;
    private final List<AiSubtitleItemView> itemViews = new ArrayList<>();

    private List<AiSubtitleItem> lastRenderedItems = List.of();
    private boolean lastCaptionsEnabled = false;

    public AiCallSubtitleView(@NonNull Context context, @NonNull AiTheme theme) {
        super(context);
        this.theme = theme;

        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padV = theme.dp(12f);

        // 1. 自适应高质感磨砂质感卡片底座
        int cardBgColor = theme.isDark()
                ? ColorUtils.setAlphaComponent(theme.card, 0xD8)
                : ColorUtils.setAlphaComponent(AiTheme.MEDIA_WHITE, 0xF4);
        GradientDrawable bg = theme.roundedStroke(cardBgColor, CARD_RADIUS_DP, theme.cardStroke, 1f);
        setBackground(bg);
        setPadding(padH, padV, padH, padV);

        // 2. 柔和微光浮层阴影与刚性轮廓裁剪
        setClipChildren(true);
        setClipToPadding(true);
        setElevation(theme.dp(4f));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), theme.dp(CARD_RADIUS_DP));
                outline.setAlpha(1.0f);
            }
        });
        setClipToOutline(true);

        int subShadowColor = theme.isDark() ? AiTheme.MEDIA_BLACK : theme.text1;
        setOutlineSpotShadowColor(ColorUtils.setAlphaComponent(subShadowColor, theme.isDark() ? 0x50 : 0x16));
        setOutlineAmbientShadowColor(ColorUtils.setAlphaComponent(subShadowColor, theme.isDark() ? 0x28 : 0x0B));

        // 3. 滚动视图容器
        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setClipChildren(true);
        scrollView.setClipToPadding(true);
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.setFadingEdgeLength(theme.dp(16f));

        // 4. 消息回合垂直列表容器
        textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(textContainer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(scrollView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 5. 【↓ 最新】智能悬浮胶囊：在上滑阅读历史时轻巧呼出，支持一键平滑归位
        scrollPill = new TextView(context);
        scrollPill.setText("↓ 最新");
        scrollPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        scrollPill.setTypeface(Typeface.DEFAULT_BOLD);
        scrollPill.setTextColor(theme.brand);
        scrollPill.setGravity(Gravity.CENTER);
        int pillPadH = theme.dp(10f);
        int pillPadV = theme.dp(4f);
        scrollPill.setPadding(pillPadH, pillPadV, pillPadH, pillPadV);

        GradientDrawable pillBg = theme.roundedStroke(
                theme.isDark() ? 0xEE1E293B : 0xEEFFFFFF,
                13f,
                theme.brandSoft,
                1f
        );
        scrollPill.setBackground(pillBg);
        scrollPill.setElevation(theme.dp(4f));
        scrollPill.setVisibility(GONE);

        LayoutParams pillParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.gravity = Gravity.BOTTOM | Gravity.END;
        pillParams.bottomMargin = theme.dp(4f);
        pillParams.rightMargin = theme.dp(4f);
        addView(scrollPill, pillParams);

        // 6. 构造独立防弹回智能滚动引擎
        scrollEngine = new AiSubtitleScrollEngine(
                new AiSubtitleScrollEngine.ScrollTarget() {
                    @Override
                    public int getContentHeight() {
                        View content = scrollView.getChildAt(0);
                        return content != null ? content.getBottom() : 0;
                    }

                    @Override
                    public int getViewportScrollY() {
                        return scrollView.getScrollY();
                    }

                    @Override
                    public int getViewportHeight() {
                        return scrollView.getHeight();
                    }

                    @Override
                    public void scrollToBottomImmediate() {
                        scrollView.post(() -> {
                            if (!scrollEngine.isScrolledUp() && !scrollEngine.isUserTouching()) {
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                            scrollView.post(scrollEngine::onProgrammaticScrollFinished);
                        });
                    }

                    @Override
                    public void smoothScrollToBottom() {
                        View content = scrollView.getChildAt(0);
                        if (content != null) {
                            scrollView.smoothScrollTo(0, content.getBottom());
                        }
                        scrollView.postDelayed(scrollEngine::onProgrammaticScrollFinished,
                                AiTheme.DURATION_SCROLL_SETTLE);
                    }
                },
                this::animatePillVisibility,
                theme.dp(24f)
        );

        scrollPill.setOnClickListener(v -> scrollEngine.onScrollToLatestClicked());

        scrollView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                scrollEngine.onTouchDownOrMove();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                scrollEngine.onTouchUpOrCancel();
            }
            return false;
        });

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            scrollEngine.onScrollChanged(scrollY);
        });

        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxH = theme.dp(MAX_HEIGHT_DP);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);

        if (mode == MeasureSpec.EXACTLY) {
            size = Math.min(size, maxH);
        } else if (mode == MeasureSpec.AT_MOST) {
            size = Math.min(size, maxH);
        } else {
            size = maxH;
            mode = MeasureSpec.AT_MOST;
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, mode));
        if (getMeasuredHeight() > maxH) {
            setMeasuredDimension(getMeasuredWidth(), maxH);
        }
    }

    /**
     * 渲染结构化字幕项序列。
     */
    public void renderItems(@NonNull List<AiSubtitleItem> items, boolean visible) {
        if (!visible || items.isEmpty()) {
            setVisibility(GONE);
            lastRenderedItems = List.of();
            lastCaptionsEnabled = visible;
            scrollEngine.onContentUpdated(false);
            return;
        }

        setVisibility(VISIBLE);

        // 幂等防御：若文本数据与显示状态未变化（如定时器每秒滴答刷新），直接返回，杜绝闪烁与误触滚动
        if (visible == lastCaptionsEnabled && items.equals(lastRenderedItems)) {
            return;
        }
        lastRenderedItems = List.copyOf(items);
        lastCaptionsEnabled = visible;

        int count = items.size();

        // 保持复用现存 ItemView，增量多退少补
        while (itemViews.size() > count) {
            AiSubtitleItemView extra = itemViews.remove(itemViews.size() - 1);
            textContainer.removeView(extra);
        }

        for (int i = 0; i < count; i++) {
            AiSubtitleItem item = items.get(i);
            AiSubtitleItemView itemView;
            if (i < itemViews.size()) {
                itemView = itemViews.get(i);
            } else {
                itemView = new AiSubtitleItemView(getContext(), theme);
                itemViews.add(itemView);
                textContainer.addView(itemView);
            }
            itemView.bind(item, i == count - 1);
        }

        scrollEngine.onContentUpdated(true);
    }

    private void animatePillVisibility(boolean visible) {
        if (scrollPill == null) {
            return;
        }
        if (visible && scrollPill.getVisibility() != VISIBLE) {
            scrollPill.setAlpha(0f);
            scrollPill.setVisibility(VISIBLE);
            scrollPill.animate().cancel();
            scrollPill.animate().alpha(1f).setDuration(AiTheme.DURATION_M).start();
        } else if (!visible && scrollPill.getVisibility() == VISIBLE) {
            scrollPill.animate().cancel();
            scrollPill.animate().alpha(0f).setDuration(AiTheme.DURATION_XS).withEndAction(() -> {
                scrollPill.setVisibility(GONE);
            }).start();
        }
    }
}

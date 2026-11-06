package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 通话实时流式字幕卡片 (AiCallSubtitleView)：
 * 全面基于 {@link AiTheme} 语义化调色系统构建，严禁硬编码，自适应深浅色模式。
 *
 * <p>架构规格：
 * <ul>
 *   <li><b>弹性高度限制</b>：内容少时紧凑包裹，多轮对话最大限制 180dp 并内置平滑滚动，绝不挤压操作栏；</li>
 *   <li><b>多角色富文本排版</b>：区分【● AI】极光蓝与【● 你】次级灰角色徽标，字距与行距舒适易读；</li>
 *   <li><b>悬浮磨砂玻璃底座</b>：20dp 圆角半透明卡片，配超细发光描边与柔和环境微光。</li>
 * </ul>
 * </p>
 */
public final class AiCallSubtitleView extends FrameLayout {

    private static final float MAX_HEIGHT_DP = 180f;
    private static final float CARD_RADIUS_DP = 20f;

    private final AiTheme theme;
    private final ScrollView scrollView;
    private final LinearLayout textContainer;
    private final TextView transcriptText;

    public AiCallSubtitleView(@NonNull Context context, @NonNull AiTheme theme) {
        super(context);
        this.theme = theme;

        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padV = theme.dp(14f);

        // 自适应磨砂质感卡片底座
        GradientDrawable bg = theme.roundedStroke(theme.card, CARD_RADIUS_DP, theme.cardStroke, 1f);
        setBackground(bg);
        setPadding(padH, padV, padH, padV);

        // 柔和微光浮层阴影
        setClipChildren(false);
        setClipToPadding(false);
        setElevation(theme.dp(4f));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), theme.dp(CARD_RADIUS_DP));
                outline.setAlpha(1.0f);
            }
        });
        setClipToOutline(false);

        int subShadowColor = theme.isDark() ? 0xFF000000 : theme.text1;
        setOutlineSpotShadowColor(ColorUtils.setAlphaComponent(subShadowColor, theme.isDark() ? 0x50 : 0x16));
        setOutlineAmbientShadowColor(ColorUtils.setAlphaComponent(subShadowColor, theme.isDark() ? 0x28 : 0x0B));

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);

        textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        transcriptText = theme.text(AiTheme.TEXT_BODY, theme.text1);
        transcriptText.setLineSpacing(theme.dp(4f), 1.25f);
        transcriptText.setGravity(Gravity.START);

        textContainer.addView(transcriptText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(textContainer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(scrollView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setVisibility(GONE);
    }

    /**
     * 刚性约束最大高度为 180dp，确保无论转写文字多长，
     * 都绝对不会在垂直维度过度膨胀而挤压或重叠底部控制栏。
     */
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
    }

    /** 更新实时流字幕与可见性，采用富文本双角色排版 */
    public void render(@Nullable String rawTranscript, boolean enabled) {
        String text = rawTranscript == null ? "" : rawTranscript.trim();

        if (!enabled || TextUtils.isEmpty(text)) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
        CharSequence styled = formatRichTranscript(text);
        transcriptText.setText(styled);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /**
     * 解析转写流中的角色标记，构建高质感富文本气泡排版：
     * - AI 发言：【● AI】科技青蓝高亮徽标；
     * - 用户发言：【● 你】低饱和次级色徽标；
     * - 实时动态流转末尾保留呼吸省略号。
     */
    @NonNull
    private CharSequence formatRichTranscript(@NonNull String text) {
        String[] lines = text.split("\n");
        SpannableStringBuilder ssb = new SpannableStringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (ssb.length() > 0) {
                ssb.append("\n\n");
            }

            int lineStart = ssb.length();
            if (line.startsWith("AI：") || line.startsWith("AI:")) {
                String content = line.substring(3).trim();
                String prefix = "● AI  ";
                ssb.append(prefix);
                int prefixEnd = ssb.length();
                ssb.append(content);

                // AI 角色徽标：品牌青蓝 + 加粗
                ssb.setSpan(new ForegroundColorSpan(theme.brand), lineStart, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), lineStart, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                // AI 正文文字：主色
                ssb.setSpan(new ForegroundColorSpan(theme.text1), prefixEnd, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("你：") || line.startsWith("你:")) {
                String content = line.substring(2).trim();
                String prefix = "● 你  ";
                ssb.append(prefix);
                int prefixEnd = ssb.length();
                ssb.append(content);

                // 用户角色徽标：次级灰 + 加粗
                ssb.setSpan(new ForegroundColorSpan(theme.text2), lineStart, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), lineStart, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                // 用户正文文字：略带柔和的主色
                ssb.setSpan(new ForegroundColorSpan(theme.text1), prefixEnd, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.append(line);
                ssb.setSpan(new ForegroundColorSpan(theme.text1), lineStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return ssb;
    }
}

package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;

/**
 * 通话页顶部导航栏 (AiCallHeaderView)：
 * 全面基于 {@link AiTheme} 语义化调色系统与 {@link AiHeaderActionButton} 构建，严禁硬编码，自适应深浅色模式。
 */
public final class AiCallHeaderView extends RelativeLayout {

    public interface Listener {
        void onClose();
        void onToggleSubtitles();
    }

    private static final float STATUS_DOT_SIZE = 7f;

    private final AiTheme theme;
    private final Listener listener;

    private final AiHeaderActionButton closeButton;
    private final AiHeaderActionButton subtitlesButton;

    private final View statusDot;
    private final TextView statusText;
    private final TextView durationText;

    public AiCallHeaderView(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;

        int pad = theme.dp(AiTheme.PAGE_INSET);
        setPadding(pad, theme.dp(AiTheme.SPACE_S), pad, theme.dp(AiTheme.SPACE_S));

        // 1. 左侧：42dp 统一圆形返回/关闭按键 (朝向左侧的返回箭头)
        closeButton = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.ai_call_close)
                .onClick(v -> listener.onClose());
        LayoutParams closeParams = AiHeaderActionButton.createRelativeLayoutParams(theme);
        closeParams.addRule(ALIGN_PARENT_START);
        closeParams.addRule(CENTER_VERTICAL);
        addView(closeButton, closeParams);

        // 2. 中央：灵动状态胶囊 (Status Capsule Pill)
        LinearLayout centerPill = new LinearLayout(context);
        centerPill.setOrientation(LinearLayout.HORIZONTAL);
        centerPill.setGravity(Gravity.CENTER);

        // 胶囊背景：采用主题卡片底色与微描边，完美自适应深浅模式
        GradientDrawable pillBg = theme.roundedStroke(theme.card, 20f, theme.cardStroke, 1f);
        centerPill.setBackground(pillBg);
        centerPill.setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S));

        // 呼吸状态指示灯
        statusDot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(theme.brand);
        statusDot.setBackground(dotBg);
        int dotPx = theme.dp(STATUS_DOT_SIZE);
        centerPill.addView(statusDot, new LinearLayout.LayoutParams(dotPx, dotPx));

        // 状态文字
        statusText = theme.text(AiTheme.TEXT_SUBHEAD, theme.text1, AiTheme.Weight.MEDIUM);
        statusText.setText(R.string.ai_call_phase_connecting);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = theme.dp(AiTheme.SPACE_S);
        statusParams.rightMargin = theme.dp(AiTheme.SPACE_XS);
        centerPill.addView(statusText, statusParams);

        // 分隔圆点
        TextView dotSeparator = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        dotSeparator.setText("·");
        centerPill.addView(dotSeparator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 实时通话时长
        durationText = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        durationText.setText("00:00");
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durParams.leftMargin = theme.dp(AiTheme.SPACE_XS);
        centerPill.addView(durationText, durParams);

        LayoutParams centerParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerParams.addRule(CENTER_IN_PARENT);
        addView(centerPill, centerParams);

        // 3. 右侧：42dp 统一圆形双态字幕开关按键
        subtitlesButton = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.TOGGLE)
                .icon(R.drawable.ic_ai_call_subtitles)
                .contentDesc(R.string.ai_call_subtitles_toggle)
                .onClick(v -> listener.onToggleSubtitles());
        LayoutParams subParams = AiHeaderActionButton.createRelativeLayoutParams(theme);
        subParams.addRule(ALIGN_PARENT_END);
        subParams.addRule(CENTER_VERTICAL);
        addView(subtitlesButton, subParams);
    }

    public void render(@NonNull AiCallUiState state) {
        statusText.setText(state.title());
        durationText.setText(state.formattedDuration());

        // 呼吸指示灯随通话相位切换主题语义色
        int dotColor = switch (state.phase()) {
            case LISTENING -> theme.isDark() ? 0xFF00E676 : 0xFF10B981;
            case SPEAKING -> theme.brand;
            case THINKING -> theme.isDark() ? 0xFFA855F7 : 0xFF7C3AED;
            case MUTED -> theme.warning;
            case CONNECTING -> theme.brand;
            case OFFLINE -> theme.text3;
        };
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(dotColor);
        statusDot.setBackground(dotDrawable);

        subtitlesButton.setActive(state.captionsVisible());
    }
}

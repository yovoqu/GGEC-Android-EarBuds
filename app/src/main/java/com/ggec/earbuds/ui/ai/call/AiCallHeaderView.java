package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;

/**
 * 通话页顶部导航栏 (AiCallHeaderView)：
 * 基于 {@link AiTheme} 语义化调色系统构建，复用指示灯 Drawable 并按字段差异刷新，
 * 避免每秒时长跳变触发重复 Drawable 分配。
 */
public final class AiCallHeaderView extends RelativeLayout {

    public interface Listener {
        void onClose();
        void onToggleSubtitles();
        void onToggleDuplex();
    }

    private static final float STATUS_DOT_SIZE = 7f;
    private static final float DUPLEX_ICON_SIZE = 15f;
    private static final float BUTTON_SIZE_DP = 40f;

    private final AiTheme theme;

    private final AiHeaderActionButton closeButton;
    private final AiHeaderActionButton subtitlesButton;

    private final GradientDrawable statusDotDrawable;
    private final TextView statusText;
    private final TextView durationText;

    private final LinearLayout duplexButton;
    private final ImageView duplexIcon;
    private final TextView duplexText;

    private int lastTitle;
    private int lastDurationSeconds = -1;
    private int lastDotColor = Integer.MIN_VALUE;
    @Nullable
    private VoiceDuplex lastDuplex;
    @Nullable
    private Boolean lastCaptionsVisible;

    public AiCallHeaderView(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        super(context);
        this.theme = theme;

        int pad = theme.dp(AiTheme.PAGE_INSET);
        setPadding(pad, theme.dp(AiTheme.SPACE_S), pad, theme.dp(AiTheme.SPACE_S));

        int btnSize = theme.dp(BUTTON_SIZE_DP);

        closeButton = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.ai_call_close)
                .onClick(v -> listener.onClose());
        closeButton.setId(View.generateViewId());
        LayoutParams closeParams = new LayoutParams(btnSize, btnSize);
        closeParams.addRule(ALIGN_PARENT_START);
        closeParams.addRule(CENTER_VERTICAL);
        addView(closeButton, closeParams);

        subtitlesButton = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.TOGGLE)
                .icon(R.drawable.ic_ai_call_subtitles)
                .contentDesc(R.string.ai_call_subtitles_toggle)
                .onClick(v -> listener.onToggleSubtitles());
        subtitlesButton.setId(View.generateViewId());
        LayoutParams subParams = new LayoutParams(btnSize, btnSize);
        subParams.addRule(ALIGN_PARENT_END);
        subParams.addRule(CENTER_VERTICAL);
        addView(subtitlesButton, subParams);

        LinearLayout centerPill = new LinearLayout(context);
        centerPill.setId(View.generateViewId());
        centerPill.setOrientation(LinearLayout.HORIZONTAL);
        centerPill.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable pillBg = theme.roundedStroke(theme.card, BUTTON_SIZE_DP / 2f, theme.cardStroke, 1f);
        centerPill.setBackground(pillBg);
        centerPill.setPadding(theme.dp(12f), 0, theme.dp(10f), 0);

        View statusDot = new View(context);
        statusDotDrawable = new GradientDrawable();
        statusDotDrawable.setShape(GradientDrawable.OVAL);
        statusDotDrawable.setColor(theme.brand);
        statusDot.setBackground(statusDotDrawable);
        int dotPx = theme.dp(STATUS_DOT_SIZE);
        centerPill.addView(statusDot, new LinearLayout.LayoutParams(dotPx, dotPx));

        statusText = theme.text(AiTheme.TEXT_SUBHEAD, theme.text1, AiTheme.Weight.MEDIUM);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setText(R.string.ai_call_phase_connecting);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = theme.dp(6f);
        statusParams.rightMargin = theme.dp(4f);
        centerPill.addView(statusText, statusParams);

        TextView dotSeparator = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        dotSeparator.setText("·");
        centerPill.addView(dotSeparator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        durationText = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        durationText.setSingleLine(true);
        durationText.setText("00:00");
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durParams.leftMargin = theme.dp(4f);
        centerPill.addView(durationText, durParams);

        View divider = new View(context);
        divider.setBackgroundColor(theme.cardStroke);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                theme.dp(1f), theme.dp(16f));
        divParams.leftMargin = theme.dp(10f);
        divParams.rightMargin = theme.dp(8f);
        centerPill.addView(divider, divParams);

        duplexButton = new LinearLayout(context);
        duplexButton.setOrientation(LinearLayout.HORIZONTAL);
        duplexButton.setGravity(Gravity.CENTER);
        duplexButton.setClickable(true);
        duplexButton.setFocusable(true);
        duplexButton.setPadding(theme.dp(6f), theme.dp(4f), theme.dp(6f), theme.dp(4f));

        GradientDrawable duplexRippleMask = new GradientDrawable();
        duplexRippleMask.setCornerRadius(theme.dp(12f));
        duplexRippleMask.setColor(0xFFFFFFFF);
        duplexButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(theme.brandSoft), null, duplexRippleMask));
        duplexButton.setOnClickListener(v -> listener.onToggleDuplex());

        duplexIcon = new ImageView(context);
        duplexIcon.setImageResource(R.drawable.ic_ai_swap);
        int iconPx = theme.dp(DUPLEX_ICON_SIZE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconPx, iconPx);
        duplexButton.addView(duplexIcon, iconParams);

        duplexText = theme.text(12.5f, theme.brand, AiTheme.Weight.MEDIUM);
        duplexText.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = theme.dp(4f);
        duplexButton.addView(duplexText, textParams);

        centerPill.addView(duplexButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams centerParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, btnSize);
        centerParams.addRule(CENTER_IN_PARENT);
        addView(centerPill, centerParams);
    }

    public void render(@NonNull AiCallUiState state) {
        if (state.title() != lastTitle) {
            lastTitle = state.title();
            statusText.setText(state.title());
        }
        if (state.durationSeconds() != lastDurationSeconds) {
            lastDurationSeconds = state.durationSeconds();
            durationText.setText(state.formattedDuration());
        }

        int dotColor = switch (state.phase()) {
            case LISTENING -> theme.isDark() ? 0xFF00E676 : 0xFF10B981;
            case SPEAKING, CONNECTING -> theme.brand;
            case THINKING -> theme.isDark() ? 0xFFA855F7 : 0xFF7C3AED;
            case OFFLINE -> theme.text3;
        };
        if (dotColor != lastDotColor) {
            lastDotColor = dotColor;
            statusDotDrawable.setColor(dotColor);
        }

        VoiceDuplex currentDuplex = state.duplex();
        if (currentDuplex != lastDuplex) {
            lastDuplex = currentDuplex;
            boolean isFull = currentDuplex == VoiceDuplex.FULL_DUPLEX;
            int targetColor = isFull ? theme.brand : theme.text2;
            duplexText.setText(isFull ? R.string.ai_call_duplex_full : R.string.ai_call_duplex_half);
            duplexText.setTextColor(targetColor);
            duplexIcon.setColorFilter(targetColor);
            duplexButton.setContentDescription(getContext().getString(
                    R.string.ai_call_duplex_toggle_desc,
                    getContext().getString(isFull ? R.string.ai_call_duplex_full : R.string.ai_call_duplex_half)));
        }

        boolean captionsVisible = state.captionsVisible();
        if (lastCaptionsVisible == null || lastCaptionsVisible != captionsVisible) {
            lastCaptionsVisible = captionsVisible;
            subtitlesButton.setActive(captionsVisible);
        }
    }
}

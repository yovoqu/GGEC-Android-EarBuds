package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 通话页底部控制面板 (AiCallControlsView)：
 * 复用按钮椭圆 Drawable/Ripple 并按字段差分更新，避免定时器每秒刷新时重复重建样式对象。
 */
public final class AiCallControlsView extends LinearLayout {

    public interface Listener {
        void onMicMuted(boolean micMuted);
        void onSpeaker(boolean speaker);
        void onHangUp();
    }

    private static final float SIDE_BUTTON_SIZE = 52f;
    private static final float HERO_BUTTON_SIZE = 64f;
    private static final float TOP_RADIUS_DP = 24f;
    @ColorInt private static final int HANGUP_RED = 0xFFEF4444;
    @ColorInt private static final int HANGUP_RED_END = 0xFFDC2626;

    private final AiTheme theme;

    private final ImageView micButton;
    private final ImageView hangUpButton;
    private final ImageView speakerButton;
    private final GradientDrawable micBg;
    private final GradientDrawable speakerBg;

    private boolean micMuted;
    private boolean speakerOn = true;

    @Nullable
    private Boolean renderedMicMuted;
    @Nullable
    private Boolean renderedSpeakerOn;
    @Nullable
    private Boolean renderedEnded;

    public AiCallControlsView(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        super(context);
        this.theme = theme;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);

        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padTop = theme.dp(16f);
        int padBottom = theme.dp(16f);
        setPadding(padH, padTop, padH, padBottom);

        int topRadiusPx = theme.dp(TOP_RADIUS_DP);
        int panelColor = theme.isDark()
                ? ColorUtils.setAlphaComponent(theme.card, 0xF6)
                : 0xFFFFFFFF;

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(panelColor);
        panelBg.setCornerRadii(new float[]{
                topRadiusPx, topRadiusPx,
                topRadiusPx, topRadiusPx,
                0f, 0f,
                0f, 0f
        });
        panelBg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
        setBackground(panelBg);

        setElevation(theme.dp(8f));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + topRadiusPx, topRadiusPx);
                outline.setAlpha(1.0f);
            }
        });
        setClipToOutline(false);

        int shadowColor = theme.isDark() ? 0xFF000000 : theme.text1;
        setOutlineSpotShadowColor(ColorUtils.setAlphaComponent(shadowColor, theme.isDark() ? 0x60 : 0x14));
        setOutlineAmbientShadowColor(ColorUtils.setAlphaComponent(shadowColor, theme.isDark() ? 0x30 : 0x0A));

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER_VERTICAL);
        buttonsRow.setClipChildren(false);
        buttonsRow.setClipToPadding(false);

        micBg = new GradientDrawable();
        micBg.setShape(GradientDrawable.OVAL);
        micButton = createCircleButton(SIDE_BUTTON_SIZE, 12f);
        micButton.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.brandSoft), micBg, null));
        FrameLayout micSlot = createControlSlot(micButton, v -> listener.onMicMuted(!micMuted));

        hangUpButton = createHeroButton(HERO_BUTTON_SIZE, 15f);
        FrameLayout hangUpSlot = createControlSlot(hangUpButton, v -> listener.onHangUp());

        speakerBg = new GradientDrawable();
        speakerBg.setShape(GradientDrawable.OVAL);
        speakerButton = createCircleButton(SIDE_BUTTON_SIZE, 12f);
        speakerButton.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.brandSoft), speakerBg, null));
        FrameLayout speakerSlot = createControlSlot(speakerButton, v -> listener.onSpeaker(!speakerOn));

        addDockItem(buttonsRow, micSlot);
        addDockItem(buttonsRow, hangUpSlot);
        addDockItem(buttonsRow, speakerSlot);

        addView(buttonsRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        applyMicLook(false);
        applySpeakerLook(true);
        applyHangUpLook(false);
    }

    public void render(@NonNull AiCallUiState state) {
        this.micMuted = state.micMuted();
        this.speakerOn = state.speakerOn();

        if (renderedMicMuted == null || renderedMicMuted != state.micMuted()) {
            applyMicLook(state.micMuted());
        }
        if (renderedSpeakerOn == null || renderedSpeakerOn != state.speakerOn()) {
            applySpeakerLook(state.speakerOn());
        }
        if (renderedEnded == null || renderedEnded != state.ended()) {
            applyHangUpLook(state.ended());
        }
    }

    /** 响应系统导航栏与全面屏手势条安全区。 */
    public void setBottomInset(int navBottomPx) {
        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padTop = theme.dp(16f);
        int padBottom = Math.max(navBottomPx, 0) + theme.dp(12f);
        setPadding(padH, padTop, padH, padBottom);
    }

    private void applyMicLook(boolean muted) {
        renderedMicMuted = muted;
        if (muted) {
            micBg.setColor(theme.warningSoft);
            micBg.setStroke(Math.max(1, theme.dp(1.2f)), theme.warning);
            micButton.setImageResource(R.drawable.ic_ai_call_mic_off);
            micButton.setColorFilter(theme.warning);
            micButton.setContentDescription(getContext().getString(R.string.ai_call_mic_muted_label));
        } else {
            micBg.setColor(theme.fill);
            micBg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
            micButton.setImageResource(R.drawable.ic_ai_call_mic);
            micButton.setColorFilter(theme.text1);
            micButton.setContentDescription(getContext().getString(R.string.ai_call_mic_label));
        }
    }

    private void applySpeakerLook(boolean speaker) {
        renderedSpeakerOn = speaker;
        if (speaker) {
            speakerBg.setColor(theme.brandSoft);
            speakerBg.setStroke(Math.max(1, theme.dp(1.2f)), theme.brand);
            speakerButton.setImageResource(R.drawable.ic_ai_call_speaker);
            speakerButton.setColorFilter(theme.brand);
            speakerButton.setContentDescription(getContext().getString(R.string.ai_call_speaker_label));
        } else {
            speakerBg.setColor(theme.fill);
            speakerBg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
            speakerButton.setImageResource(R.drawable.ic_ai_call_earpiece);
            speakerButton.setColorFilter(theme.text1);
            speakerButton.setContentDescription(getContext().getString(R.string.ai_call_earpiece_label));
        }
    }

    private void applyHangUpLook(boolean ended) {
        renderedEnded = ended;
        hangUpButton.setContentDescription(getContext().getString(
                ended ? R.string.ai_call_close : R.string.ai_call_hang_up));
    }

    private FrameLayout createControlSlot(View button, OnClickListener onClick) {
        FrameLayout buttonSlot = new FrameLayout(getContext());
        FrameLayout.LayoutParams slotButtonLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        buttonSlot.addView(button, slotButtonLp);
        buttonSlot.setOnClickListener(onClick);
        return buttonSlot;
    }

    private void addDockItem(LinearLayout dock, View item) {
        LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dock.addView(item, lp);
    }

    private ImageView createCircleButton(float sizeDp, float paddingDp) {
        ImageView iv = new ImageView(getContext());
        int sizePx = theme.dp(sizeDp);
        int padPx = theme.dp(paddingDp);
        iv.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        iv.setPadding(padPx, padPx, padPx, padPx);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return iv;
    }

    private ImageView createHeroButton(float sizeDp, float paddingDp) {
        ImageView iv = new ImageView(getContext());
        int sizePx = theme.dp(sizeDp);
        int padPx = theme.dp(paddingDp);
        iv.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        iv.setPadding(padPx, padPx, padPx, padPx);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setImageResource(R.drawable.ic_ai_call_hangup);
        iv.setColorFilter(Color.WHITE);

        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{HANGUP_RED, HANGUP_RED_END}
        );
        heroBg.setShape(GradientDrawable.OVAL);
        iv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), heroBg, null));
        return iv;
    }
}

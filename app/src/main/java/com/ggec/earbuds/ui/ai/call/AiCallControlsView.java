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
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 通话页底部控制面板 (AiCallControlsView)：
 * 全面基于 {@link AiTheme} 语义化调色系统构建，直接贯通全面屏底部导航区，无缝贴底沉浸式操作底座。
 *
 * <p>布局架构与双对齐规范：
 * <ul>
 *   <li><b>底部全量打通沉浸</b>：彻底移除悬浮胶囊底座与周边留白，面板直接向左右两侧与屏幕底部完全贯通贴底；</li>
 *   <li><b>安全区自适应内衬</b>：背景自然流淌铺满手势条/导航栏安全区，按钮通过内部动态 Inset 上浮，保证防误触；</li>
 *   <li><b>极简无文本设计</b>：全面移除冗余文本描述标签，释放纵向宝贵空间，聚焦核心操作手势；</li>
 *   <li><b>按键水平轴心对齐</b>：按键槽位高度固定为 64dp，52dp 侧边键垂直居中，
 *       使左【麦克风】、中【挂断】、右【扬声器/听筒】圆心绝对统一于同一水平轴线；</li>
 *   <li><b>独立专业听筒图标</b>：听筒模式配备独立专属的 {@code ic_ai_call_earpiece} 图标，区别于静音符号；</li>
 *   <li><b>顶部大圆角与微光阴影</b>：顶部双圆角 (24dp) 搭配向上柔和微光环境阴影，视觉分层清晰。</li>
 * </ul>
 * </p>
 */
public final class AiCallControlsView extends LinearLayout {

    public interface Listener {
        void onMuted(boolean muted);
        void onSpeaker(boolean speaker);
        void onHangUp();
    }

    private static final float SIDE_BUTTON_SIZE = 52f;
    private static final float HERO_BUTTON_SIZE = 64f;
    private static final float TOP_RADIUS_DP = 24f;
    @ColorInt private static final int HANGUP_RED = 0xFFEF4444;
    @ColorInt private static final int HANGUP_RED_END = 0xFFDC2626;

    private final AiTheme theme;
    private final Listener listener;

    private final ImageView micButton;
    private final ImageView hangUpButton;
    private final ImageView speakerButton;

    private boolean muted = false;
    private boolean speakerOn = true;
    private boolean ended = false;

    public AiCallControlsView(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);

        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padTop = theme.dp(16f);
        int padBottom = theme.dp(16f);
        setPadding(padH, padTop, padH, padBottom);

        // 1. 底部贴底打通面板底座：仅顶部双圆角 (24dp)，底部完全直通
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

        // 2. 向上柔和微光投影（下方打通，阴影仅在上方扩散）
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

        // 3. 核心控制按钮水平排布行
        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER_VERTICAL);
        buttonsRow.setClipChildren(false);
        buttonsRow.setClipToPadding(false);

        // 左侧：麦克风静音按键
        micButton = createCircleButton(SIDE_BUTTON_SIZE, 12f);
        FrameLayout micSlot = createControlSlot(micButton, v -> listener.onMuted(!muted));

        // 正中央英雄位：64dp 核心挂断按键（醒目电话正红）
        hangUpButton = createHeroButton(HERO_BUTTON_SIZE, 15f);
        FrameLayout hangUpSlot = createControlSlot(hangUpButton, v -> listener.onHangUp());

        // 右侧：扬声器/听筒路由按键
        speakerButton = createCircleButton(SIDE_BUTTON_SIZE, 12f);
        FrameLayout speakerSlot = createControlSlot(speakerButton, v -> listener.onSpeaker(!speakerOn));

        // 黄金比例均匀排布，挂断居中
        addDockItem(buttonsRow, micSlot);
        addDockItem(buttonsRow, hangUpSlot);
        addDockItem(buttonsRow, speakerSlot);

        addView(buttonsRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        applyMicLook();
        applySpeakerLook();
        applyHangUpLook();
    }

    public void render(@NonNull AiCallUiState state) {
        this.muted = state.muted();
        this.speakerOn = state.speakerOn();
        this.ended = state.ended();

        applyMicLook();
        applySpeakerLook();
        applyHangUpLook();
    }

    /**
     * 响应系统导航栏与全面屏手势条安全区：
     * 底座背景直接贯穿至物理屏幕最底，通过增加内边距将按钮提升至手势区上方。
     */
    public void setBottomInset(int navBottomPx) {
        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padTop = theme.dp(16f);
        int padBottom = Math.max(navBottomPx, 0) + theme.dp(12f);
        setPadding(padH, padTop, padH, padBottom);
    }

    private void applyMicLook() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);

        if (muted) {
            // 静音报警暖琥珀态
            bg.setColor(theme.warningSoft);
            bg.setStroke(Math.max(1, theme.dp(1.2f)), theme.warning);
            micButton.setImageResource(R.drawable.ic_ai_call_mic_off);
            micButton.setColorFilter(theme.warning);
            micButton.setContentDescription(getContext().getString(R.string.ai_call_mic_muted_label));
        } else {
            // 正常采音通透态
            bg.setColor(theme.fill);
            bg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
            micButton.setImageResource(R.drawable.ic_ai_call_mic);
            micButton.setColorFilter(theme.text1);
            micButton.setContentDescription(getContext().getString(R.string.ai_call_mic_label));
        }
        micButton.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.brandSoft), bg, null));
    }

    private void applySpeakerLook() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);

        if (speakerOn) {
            // 扬声器外放通透高亮
            bg.setColor(theme.brandSoft);
            bg.setStroke(Math.max(1, theme.dp(1.2f)), theme.brand);
            speakerButton.setImageResource(R.drawable.ic_ai_call_speaker);
            speakerButton.setColorFilter(theme.brand);
            speakerButton.setContentDescription(getContext().getString(R.string.ai_call_speaker_label));
        } else {
            // 听筒模式：使用独立专属听筒图标 (ic_ai_call_earpiece)
            bg.setColor(theme.fill);
            bg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
            speakerButton.setImageResource(R.drawable.ic_ai_call_earpiece);
            speakerButton.setColorFilter(theme.text1);
            speakerButton.setContentDescription(getContext().getString(R.string.ai_call_earpiece_label));
        }
        speakerButton.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.brandSoft), bg, null));
    }

    private void applyHangUpLook() {
        hangUpButton.setContentDescription(getContext().getString(
                ended ? R.string.ai_call_close : R.string.ai_call_hang_up));
    }

    /**
     * 统一 64dp 按钮舱槽位，确保 52dp 侧键与 64dp 主键圆心绝对水平对齐
     */
    private FrameLayout createControlSlot(View button, OnClickListener onClick) {
        FrameLayout buttonSlot = new FrameLayout(getContext());
        int slotPx = theme.dp(HERO_BUTTON_SIZE);
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

        // 经典电话挂断正红渐变
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{HANGUP_RED, HANGUP_RED_END}
        );
        heroBg.setShape(GradientDrawable.OVAL);
        iv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), heroBg, null));
        return iv;
    }
}

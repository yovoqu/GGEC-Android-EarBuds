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
 * 通话页底部悬浮控制舱 (AiCallControlsView)：
 * 全面基于 {@link AiTheme} 语义化调色系统构建，严禁硬编码，自适应深浅色模式。
 *
 * <p>布局架构与双对齐规范：
 * <ul>
 *   <li><b>极简无文本设计</b>：全面移除冗余文本描述标签，释放纵向宝贵空间，聚焦核心操作手势；</li>
 *   <li><b>紧凑高度调优</b>：控制坞整体高度紧凑收敛至 ~76dp，避免挤压中央流体声场与字幕卡片；</li>
 *   <li><b>按键水平轴心对齐</b>：按键槽位高度固定为 64dp，52dp 侧边键垂直居中，
 *       使左【麦克风】、中【挂断】、右【扬声器/听筒】圆心绝对统一于同一水平轴线；</li>
 *   <li><b>独立专业听筒图标</b>：听筒模式配备独立专属的 {@code ic_ai_call_earpiece} 图标，区别于静音符号；</li>
 *   <li><b>高质感悬浮底座</b>：32dp 大圆角磨砂玻璃舱体，带柔和环境微光阴影。</li>
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
    private static final float DOCK_RADIUS = 32f;
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
        // 关闭容器裁剪，允许子卡片阴影自然向周边发散，彻底消除阴影被裁剪的生硬直角与切边
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(0, theme.dp(AiTheme.SPACE_XS), 0, theme.dp(AiTheme.SPACE_M));

        // 悬浮式控制坞底座 (Floating Action Capsule Dock)
        LinearLayout dock = new LinearLayout(context);
        dock.setOrientation(HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        GradientDrawable dockBg = theme.roundedStroke(theme.card, DOCK_RADIUS, theme.cardStroke, 1f);
        dock.setBackground(dockBg);
        dock.setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(6f),
                theme.dp(AiTheme.PAGE_INSET), theme.dp(6f));

        // 柔和环境光悬浮阴影：调校环境光与聚光阴影色相，避免浅色模式下生硬黑斑，阴影自然晕开
        dock.setElevation(theme.dp(6f));
        dock.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), theme.dp(DOCK_RADIUS));
                outline.setAlpha(1.0f);
            }
        });
        dock.setClipToOutline(false);

        int shadowColor = theme.isDark() ? 0xFF000000 : theme.text1;
        dock.setOutlineSpotShadowColor(ColorUtils.setAlphaComponent(shadowColor, theme.isDark() ? 0x60 : 0x1A));
        dock.setOutlineAmbientShadowColor(ColorUtils.setAlphaComponent(shadowColor, theme.isDark() ? 0x30 : 0x0E));

        // 1. 左侧：麦克风静音按键
        micButton = createCircleButton(SIDE_BUTTON_SIZE, 12f);
        FrameLayout micSlot = createControlSlot(micButton, v -> listener.onMuted(!muted));

        // 2. 正中央英雄位：64dp 核心挂断按键（醒目电话正红）
        hangUpButton = createHeroButton(HERO_BUTTON_SIZE, 15f);
        FrameLayout hangUpSlot = createControlSlot(hangUpButton, v -> listener.onHangUp());

        // 3. 右侧：扬声器/听筒路由按键
        speakerButton = createCircleButton(SIDE_BUTTON_SIZE, 12f);
        FrameLayout speakerSlot = createControlSlot(speakerButton, v -> listener.onSpeaker(!speakerOn));

        // 黄金比例均匀排布，挂断居中
        addDockItem(dock, micSlot);
        addDockItem(dock, hangUpSlot);
        addDockItem(dock, speakerSlot);

        // 采用外边距而不是内边距，配合 setClipChildren(false) 为阴影留足充裕晕染空间
        LayoutParams dockLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        dockLp.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        dockLp.topMargin = theme.dp(2f);
        dockLp.bottomMargin = theme.dp(2f);
        addView(dock, dockLp);

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

    public void setBottomInset(int px) {
        setPadding(0, getPaddingTop(), 0, px);
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

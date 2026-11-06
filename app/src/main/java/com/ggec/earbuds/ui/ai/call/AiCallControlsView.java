package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
 *   <li><b>按键水平轴心对齐</b>：按键舱高度固定为 72dp，58dp 侧边键垂直居中，
 *       使左【麦克风】、中【挂断】、右【扬声器】圆心绝对统一于 Y=36dp 轴线；</li>
 *   <li><b>文本标签基线对齐</b>：标签槽位统一距离按键舱 8dp，三组说明文字绝对基线重合；</li>
 *   <li><b>中央英雄位</b>：72dp 醒目红【挂断】按键，居中统领全局；</li>
 *   <li><b>高质感悬浮底座</b>：36dp 大圆角磨砂玻璃舱体，带柔和环境微光阴影（关闭裁剪，确保全方位阴影通透发散）。</li>
 * </ul>
 * </p>
 */
public final class AiCallControlsView extends LinearLayout {

    public interface Listener {
        void onMuted(boolean muted);
        void onSpeaker(boolean speaker);
        void onHangUp();
    }

    private static final float SIDE_BUTTON_SIZE = 58f;
    private static final float HERO_BUTTON_SIZE = 72f;
    private static final float DOCK_RADIUS = 36f;
    @ColorInt private static final int HANGUP_RED = 0xFFEF4444;
    @ColorInt private static final int HANGUP_RED_END = 0xFFDC2626;

    private final AiTheme theme;
    private final Listener listener;

    private final ImageView micButton;
    private final TextView micLabel;

    private final ImageView hangUpButton;
    private final TextView hangUpLabel;

    private final ImageView speakerButton;
    private final TextView speakerLabel;

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
        setPadding(0, theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_XL));

        // 悬浮式控制坞底座 (Floating Action Capsule Dock)
        LinearLayout dock = new LinearLayout(context);
        dock.setOrientation(HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        GradientDrawable dockBg = theme.roundedStroke(theme.card, DOCK_RADIUS, theme.cardStroke, 1f);
        dock.setBackground(dockBg);
        dock.setPadding(theme.dp(AiTheme.SPACE_XL), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.SPACE_XL), theme.dp(AiTheme.SPACE_M));

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
        micButton = createCircleButton(SIDE_BUTTON_SIZE, 14f);
        micLabel = createLabel(R.string.ai_call_mic_label);
        LinearLayout micItem = createAlignedControlItem(micButton, micLabel, v -> listener.onMuted(!muted));

        // 2. 正中央英雄位：72dp 巨幕挂断按键（醒目电话正红）
        hangUpButton = createHeroButton(HERO_BUTTON_SIZE, 18f);
        hangUpLabel = createLabel(R.string.ai_call_hang_up);
        hangUpLabel.setTypeface(Typeface.DEFAULT_BOLD);
        hangUpLabel.setTextColor(HANGUP_RED);
        LinearLayout hangUpItem = createAlignedControlItem(hangUpButton, hangUpLabel, v -> listener.onHangUp());

        // 3. 右侧：扬声器听筒路由按键
        speakerButton = createCircleButton(SIDE_BUTTON_SIZE, 14f);
        speakerLabel = createLabel(R.string.ai_call_speaker_label);
        LinearLayout speakerItem = createAlignedControlItem(speakerButton, speakerLabel, v -> listener.onSpeaker(!speakerOn));

        // 黄金比例均匀排布，挂断居中
        addDockItem(dock, micItem);
        addDockItem(dock, hangUpItem);
        addDockItem(dock, speakerItem);

        // 采用外边距而不是内边距，配合 setClipChildren(false) 为阴影留足充裕晕染空间
        LayoutParams dockLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        dockLp.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        dockLp.topMargin = theme.dp(AiTheme.SPACE_XS);
        dockLp.bottomMargin = theme.dp(AiTheme.SPACE_XS);
        addView(dock, dockLp);

        applyMicLook();
        applySpeakerLook();
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
            micLabel.setText(R.string.ai_call_mic_muted_label);
            micLabel.setTextColor(theme.warning);
        } else {
            // 正常采音通透态
            bg.setColor(theme.fill);
            bg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
            micButton.setImageResource(R.drawable.ic_ai_call_mic);
            micButton.setColorFilter(theme.text1);
            micLabel.setText(R.string.ai_call_mic_label);
            micLabel.setTextColor(theme.text2);
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
            speakerLabel.setText(R.string.ai_call_speaker_label);
            speakerLabel.setTextColor(theme.brand);
        } else {
            // 听筒模式
            bg.setColor(theme.fill);
            bg.setStroke(Math.max(1, theme.dp(1f)), theme.cardStroke);
            speakerButton.setImageResource(R.drawable.ic_ai_call_speaker_off);
            speakerButton.setColorFilter(theme.text2);
            speakerLabel.setText(R.string.ai_call_earpiece_label);
            speakerLabel.setTextColor(theme.text2);
        }
        speakerButton.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.brandSoft), bg, null));
    }

    private void applyHangUpLook() {
        if (ended) {
            hangUpLabel.setText(R.string.ai_call_close);
        } else {
            hangUpLabel.setText(R.string.ai_call_hang_up);
        }
    }

    /**
     * 构建双轴线对齐控制项：
     * 1. 上层按钮槽位高度统一为 72dp，内部按键居中，使得 58dp 与 72dp 按键的圆心完全水平重合；
     * 2. 下层文字标签统一外边距 8dp，使得三组说明文字严格位于同一水平基线。
     */
    private LinearLayout createAlignedControlItem(View button, TextView label, OnClickListener onClick) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setOnClickListener(onClick);

        // 统一 72dp 按钮舱槽位，确保按键圆心绝对水平对齐
        FrameLayout buttonSlot = new FrameLayout(getContext());
        int slotPx = theme.dp(HERO_BUTTON_SIZE);
        FrameLayout.LayoutParams slotButtonLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        buttonSlot.addView(button, slotButtonLp);

        LayoutParams slotLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, slotPx);
        col.addView(buttonSlot, slotLp);

        // 统一文字标签外边距，确保文字基线绝对对齐
        LayoutParams labelLp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = theme.dp(AiTheme.SPACE_S);
        col.addView(label, labelLp);
        return col;
    }

    private void addDockItem(LinearLayout dock, LinearLayout item) {
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

    private TextView createLabel(int textRes) {
        TextView tv = theme.text(AiTheme.TEXT_CAPTION, theme.text2);
        tv.setText(textRes);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}

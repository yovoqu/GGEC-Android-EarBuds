package com.ggec.earbuds.ui.ai.call;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 通话页唯一的主角：一颗跟着真实输入电平变化的圆。
 *
 * <p>这一页要回答的只有一句"它现在听不听得见我"，所以不画波形：波形把注意力拉到音频细节上，
 * 而用户需要的信号只有三个——圆动不动（链路在不在跑）、呼吸快慢（它有多忙）、
 * 圆多大（我这一句它收到了多少）。</p>
 *
 * <p>六个相位里**只有三个会动**：聆听、思考、说话。接通中是一圈静止的中性浅环（声音还没来），
 * 静音是静止的中性盘（麦克风已关），失败与结束直接落成一块灰盘、连光晕都不留
 * （链路已经断了，再动就是在骗人）。这条规则比"看起来丰富"重要。</p>
 *
 * <p>呼吸只改两个量：光晕与环的 alpha、圆的半径。没有位移、没有形变、没有随机抖动——
 * 唯一的输入是会话给的 {@code inputLevel}，页面不自己造数据。</p>
 */
public final class VoiceOrbView extends View {

    /**
     * 页面能画出来的相位，对应 {@code AiVoiceSnapshot.State}：
     * IDLE/CONNECTING → {@link #CONNECTING}，FAILED/ENDED → {@link #OFFLINE}。
     */
    public enum Phase {
        /** 接通中：静止的中性浅环，没有实心圆。 */
        CONNECTING,
        /** 聆听：品牌色呼吸，半径跟真实电平。 */
        LISTENING,
        /** 思考：慢呼吸，圆收紧。 */
        THINKING,
        /** 说话：快呼吸，圆外扩。 */
        SPEAKING,
        /** 静音：静止的中性盘，麦克风已关。 */
        MUTED,
        /** 失败 / 结束：静止的中性灰盘，没有环、没有光晕。 */
        OFFLINE
    }

    /** 静息状态下的圆半径占短边的比例。 */
    private static final float DISC_RATIO = 0.18f;
    /** 电平能把圆再推出去多少：说话时明显长一圈，但不至于顶到环上。 */
    private static final float LEVEL_RATIO = 0.10f;
    /** 环与圆之间的留白。 */
    private static final float RING_GAP_RATIO = 0.10f;
    /** 光晕扩散半径。 */
    private static final float HALO_RATIO = 0.46f;

    /** 呼吸一圈的时长：聆听是常态，思考更慢（在等），说话更快（在输出）。 */
    private static final long BREATH_LISTENING = 2400L;
    private static final long BREATH_THINKING = 3800L;
    private static final long BREATH_SPEAKING = 1100L;

    /** 静止相位取呼吸曲线的中点，这样从动态切到静止不会突然变大或变亮。 */
    private static final float STILL = 0.5f;
    /** 电平平滑：会话给的是采样的瞬时值，直接画会抖；这只做低通，不造数据。 */
    private static final float LEVEL_SMOOTHING = 0.45f;
    /** 光晕最亮时的 alpha。 */
    private static final int HALO_ALPHA = 0x88;

    private final AiTheme theme;
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);

    @NonNull
    private Phase phase = Phase.CONNECTING;
    @Nullable
    private ValueAnimator breath;
    private float level;
    private float breathing = STILL;
    private float haloRadius;
    @ColorInt
    private int tone;

    public VoiceOrbView(@NonNull Context context, @NonNull AiTheme theme) {
        super(context);
        this.theme = theme;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(theme.dpf(1.2f));
        core.setStyle(Paint.Style.FILL);
        halo.setStyle(Paint.Style.FILL);
        // 先定色再等第一次 onSizeChanged 建渐变：相位没变时 setPhase 会直接返回，
        // 不在这里落一次初值，第一帧就是黑的。
        applyTone();
    }

    /** 切换相位：换色、换呼吸节奏；静止相位在这里把动画停掉。 */
    public void setPhase(@NonNull Phase next) {
        if (phase == next) {
            return;
        }
        phase = next;
        applyTone();
        restartBreath();
        invalidate();
    }

    /**
     * 输入电平，0..1。只有聆听相位真的拿它改半径；其余相位连看一眼都不看，
     * 否则静音时残留的旧电平会让圆停在"刚才那一句"的大小上。
     */
    public void setLevel(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        float next = level + (target - level) * LEVEL_SMOOTHING;
        if (Math.abs(next - level) < 0.003f) {
            next = target;
        }
        if (Math.abs(next - level) < 0.001f) {
            return;
        }
        level = next;
        if (phase == Phase.LISTENING) {
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        haloRadius = Math.min(width, height) * HALO_RATIO;
        rebuildHalo();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restartBreath();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreath();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        float shortSide = Math.min(width, height);
        float centerX = width / 2f;
        float centerY = height / 2f;
        boolean live = isLive();
        float breathValue = live ? breathing : STILL;
        float disc = discRadius(shortSide, breathValue);

        if (live && halo.getShader() != null) {
            halo.setAlpha((int) (HALO_ALPHA * (0.45f + 0.55f * breathValue)));
            canvas.drawCircle(centerX, centerY, haloRadius, halo);
        }

        // 失败与结束没有环：灰盘是一个句号，不是"还在转圈等你回来"。
        if (phase != Phase.OFFLINE) {
            ring.setColor(tone);
            ring.setAlpha(ringAlpha(breathValue));
            float ringRadius = phase == Phase.CONNECTING
                    ? shortSide * (DISC_RATIO + RING_GAP_RATIO)
                    : disc + shortSide * RING_GAP_RATIO;
            canvas.drawCircle(centerX, centerY, ringRadius, ring);
        }

        // 接通中只有环：还没有声音，画实心圆会显得"已经通了"。
        if (phase != Phase.CONNECTING) {
            core.setColor(tone);
            core.setAlpha(discAlpha());
            canvas.drawCircle(centerX, centerY, disc, core);
        }
    }

    /** 圆的半径：聆听吃电平，思考收紧，说话外扩；静音与失败固定。 */
    private float discRadius(float shortSide, float breathValue) {
        float base = shortSide * DISC_RATIO;
        return switch (phase) {
            case LISTENING -> (base + shortSide * LEVEL_RATIO * level) * (0.98f + 0.04f * breathValue);
            case THINKING -> base * 0.86f * (0.98f + 0.04f * breathValue);
            case SPEAKING -> base * 1.12f * (0.95f + 0.09f * breathValue);
            default -> base;
        };
    }

    private int ringAlpha(float breathValue) {
        return switch (phase) {
            // "浅环"：中性色再压一档，静止，不参与呼吸。
            case CONNECTING -> 0x40;
            case MUTED -> 0x33;
            default -> (int) (0x44 * (0.55f + 0.45f * breathValue));
        };
    }

    private int discAlpha() {
        return switch (phase) {
            case OFFLINE -> 0x66;
            case MUTED -> 0xCC;
            default -> 0xFF;
        };
    }

    /** 只有聆听、思考、说话会呼吸；这三个相位是"声音正在流动"的全部情况。 */
    private boolean isLive() {
        return phase == Phase.LISTENING || phase == Phase.THINKING || phase == Phase.SPEAKING;
    }

    private void applyTone() {
        tone = isLive() ? theme.brand : theme.text3;
        rebuildHalo();
    }

    /** 光晕是同一个圆心的径向渐变；换色或尺寸变化时重建一次，每帧只改 paint 的 alpha。 */
    private void rebuildHalo() {
        if (haloRadius <= 0f) {
            return;
        }
        halo.setShader(new RadialGradient(getWidth() / 2f, getHeight() / 2f, haloRadius,
                new int[]{ColorUtils.setAlphaComponent(tone, 0xFF), ColorUtils.setAlphaComponent(tone, 0x00)},
                null, Shader.TileMode.CLAMP));
    }

    private void restartBreath() {
        stopBreath();
        if (!isAttachedToWindow() || !isLive()) {
            breathing = STILL;
            invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0.1f, 1f);
        animator.setDuration(breathDuration());
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            breathing = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
        breath = animator;
    }

    private long breathDuration() {
        return switch (phase) {
            case THINKING -> BREATH_THINKING;
            case SPEAKING -> BREATH_SPEAKING;
            default -> BREATH_LISTENING;
        };
    }

    private void stopBreath() {
        ValueAnimator current = breath;
        breath = null;
        if (current != null) {
            current.cancel();
        }
    }
}

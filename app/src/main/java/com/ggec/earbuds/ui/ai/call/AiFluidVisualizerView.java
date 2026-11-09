package com.ggec.earbuds.ui.ai.call;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 次世代全息极光流体声场视效引擎 (Next-Gen Organic Fluid Soundfield Engine)。
 *
 * <p>绘制路径零分配优化：所有画笔、渐变着色器与矩阵变换全部缓存复用，60fps 高帧率渲染期间
 * 不在 {@link #onDraw(Canvas)} 内分配任何 {@link Paint}、{@link Shader} 或数组。</p>
 */
public final class AiFluidVisualizerView extends View {

    public enum VisualPhase {
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING,
        OFFLINE
    }

    public interface OnBargeInListener {
        void onBargeIn();
    }

    private static final int VERTEX_COUNT = 64;
    private static final float BASE_RADIUS_RATIO = 0.27f;
    private static final float BLOOM_RADIUS_RATIO = 0.48f;
    private static final float LEVEL_DAMPING = 0.25f;

    private final AiTheme theme;

    // 画笔体系（构造期一次性分配）
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fluidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fluidRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 几何路径与样条控制点
    private final Path fluidPath = new Path();
    private final PointF[] vertices = new PointF[VERTEX_COUNT];
    private final PointF[] midPoints = new PointF[VERTEX_COUNT];
    private final Matrix shaderMatrix = new Matrix();
    private final Matrix auraMatrix = new Matrix();
    private final Matrix radialMatrix = new Matrix();

    // 预分配渐变参数数组，避免每帧分配 int[]/float[]
    private final int[] auraColors = new int[4];
    private final float[] auraStops = {0.0f, 0.42f, 0.75f, 1.0f};
    private final int[] sweepColors = new int[4];
    private final float[] sweepStops = {0.0f, 0.42f, 0.80f, 1.0f};
    private final int[] centerAuraColors = new int[3];
    private final float[] centerAuraStops = {0.0f, 0.35f, 1.0f};
    private final int[] centerCoreColors = new int[3];
    private final float[] centerCoreStops = {0.0f, 0.45f, 1.0f};
    private final int[] innerGlowColors = new int[3];
    private final float[] innerGlowStops = {0.0f, 0.35f, 1.0f};
    private final int[] offlineColors = new int[2];
    private final float[] offlineStops = {0.0f, 1.0f};

    // 缓存 Shader（仅在尺寸或相位变化时重建）
    @Nullable private RadialGradient auraShader;
    @Nullable private SweepGradient sweepShader;
    @Nullable private RadialGradient centerAuraShader;
    @Nullable private RadialGradient centerCoreShader;
    @Nullable private RadialGradient innerGlowShader;
    @Nullable private RadialGradient offlineShader;
    @Nullable private VisualPhase cachedShaderPhase;
    private float cachedCx = -1f;
    private float cachedCy = -1f;
    private float cachedShortSide = -1f;
    private boolean cachedDark;

    @NonNull
    private VisualPhase phase = VisualPhase.CONNECTING;
    @Nullable
    private OnBargeInListener bargeInListener;
    @Nullable
    private ValueAnimator breathAnimator;

    private float rawLevel;
    private float smoothLevel;
    private float breathProgress = 0.5f;

    private float timePhase;
    private float vortexRotation;
    private long lastFrameTime;

    private float touchScale = 1.0f;

    @ColorInt private int colorA;
    @ColorInt private int colorB;
    @ColorInt private int colorC;
    @ColorInt private int auraGlow;

    public AiFluidVisualizerView(@NonNull Context context, @NonNull AiTheme theme) {
        super(context);
        this.theme = theme;
        init();
    }

    private void init() {
        for (int i = 0; i < VERTEX_COUNT; i++) {
            vertices[i] = new PointF();
            midPoints[i] = new PointF();
        }

        auraPaint.setStyle(Paint.Style.FILL);
        fluidPaint.setStyle(Paint.Style.FILL);
        centerAuraPaint.setStyle(Paint.Style.FILL);
        centerCorePaint.setStyle(Paint.Style.FILL);
        innerGlowPaint.setStyle(Paint.Style.FILL);

        fluidRimPaint.setStyle(Paint.Style.STROKE);
        fluidRimPaint.setStrokeWidth(theme.dpf(1.6f));

        updatePalette();
    }

    public void setPhase(@NonNull VisualPhase next) {
        if (phase == next) {
            return;
        }
        phase = next;
        updatePalette();
        restartBreath();
        invalidate();
    }

    public void setAudioLevel(float level) {
        rawLevel = Math.max(0f, Math.min(1f, level));
        if (phase == VisualPhase.LISTENING || phase == VisualPhase.SPEAKING) {
            postInvalidateOnAnimation();
        }
    }

    public void setOnBargeInListener(@Nullable OnBargeInListener listener) {
        this.bargeInListener = listener;
        setClickable(listener != null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bargeInListener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                touchScale = 0.90f;
                bargeInListener.onBargeIn();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchScale = 1.0f;
                invalidate();
                return true;
            }
            default -> {
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameTime = SystemClock.uptimeMillis();
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
        if (width <= 0 || height <= 0) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = lastFrameTime > 0 ? (now - lastFrameTime) : 16;
        lastFrameTime = now;

        smoothLevel += (rawLevel - smoothLevel) * LEVEL_DAMPING;
        if (Math.abs(smoothLevel - rawLevel) < 0.001f) {
            smoothLevel = rawLevel;
        }

        float speedMultiplier = switch (phase) {
            case THINKING -> 2.2f;
            case SPEAKING -> 1.5f;
            case LISTENING -> 1.1f + smoothLevel * 1.6f;
            case CONNECTING -> 0.9f;
            default -> 0.5f;
        };
        timePhase = (timePhase + (delta / 4200f) * speedMultiplier * (float) (2 * Math.PI))
                % (float) (2 * Math.PI);
        vortexRotation = (vortexRotation + (delta * 0.035f) * speedMultiplier) % 360f;

        float shortSide = Math.min(width, height);
        float cx = width / 2f;
        float cy = height / 2f;
        ensureShaders(cx, cy, shortSide);

        canvas.save();
        if (touchScale != 1.0f) {
            canvas.scale(touchScale, touchScale, cx, cy);
        }

        float breathVal = isLive() ? breathProgress : 0.5f;

        if (phase != VisualPhase.OFFLINE) {
            drawAtmosphericAura(canvas, cx, cy, shortSide, breathVal);
        }

        drawHarmonicFluidCore(canvas, cx, cy, shortSide, breathVal);

        canvas.restore();

        if (isLive() || phase == VisualPhase.CONNECTING) {
            postInvalidateOnAnimation();
        }
    }

    private void ensureShaders(float cx, float cy, float shortSide) {
        boolean dark = theme.isDark();
        if (cachedShaderPhase == phase
                && cachedCx == cx
                && cachedCy == cy
                && cachedShortSide == shortSide
                && cachedDark == dark
                && sweepShader != null) {
            return;
        }
        cachedShaderPhase = phase;
        cachedCx = cx;
        cachedCy = cy;
        cachedShortSide = shortSide;
        cachedDark = dark;

        float baseR = Math.max(1f, shortSide * BASE_RADIUS_RATIO);
        float bloomR = Math.max(1f, shortSide * BLOOM_RADIUS_RATIO);

        offlineColors[0] = theme.cardStroke;
        offlineColors[1] = theme.card;
        offlineShader = new RadialGradient(cx, cy, baseR, offlineColors, offlineStops, Shader.TileMode.CLAMP);

        auraColors[0] = ColorUtils.setAlphaComponent(auraGlow, 255);
        auraColors[1] = ColorUtils.setAlphaComponent(colorA, 115);
        auraColors[2] = ColorUtils.setAlphaComponent(colorB, 38);
        auraColors[3] = Color.TRANSPARENT;
        auraShader = new RadialGradient(cx, cy, bloomR, auraColors, auraStops, Shader.TileMode.CLAMP);

        sweepColors[0] = colorA;
        sweepColors[1] = colorB;
        sweepColors[2] = colorC;
        sweepColors[3] = colorA;
        sweepShader = new SweepGradient(cx, cy, sweepColors, sweepStops);

        centerAuraColors[0] = ColorUtils.setAlphaComponent(Color.WHITE, dark ? 140 : 110);
        centerAuraColors[1] = ColorUtils.setAlphaComponent(colorA, dark ? 150 : 100);
        centerAuraColors[2] = Color.TRANSPARENT;
        centerAuraShader = new RadialGradient(cx, cy, baseR * 0.95f,
                centerAuraColors, centerAuraStops, Shader.TileMode.CLAMP);

        centerCoreColors[0] = ColorUtils.setAlphaComponent(Color.WHITE, dark ? 160 : 120);
        centerCoreColors[1] = ColorUtils.setAlphaComponent(colorA, dark ? 120 : 80);
        centerCoreColors[2] = Color.TRANSPARENT;
        centerCoreShader = new RadialGradient(cx, cy, baseR * 0.35f,
                centerCoreColors, centerCoreStops, Shader.TileMode.CLAMP);

        innerGlowColors[0] = dark ? 0x99FFFFFF : 0x66FFFFFF;
        innerGlowColors[1] = dark ? 0x33FFFFFF : 0x1AFFFFFF;
        innerGlowColors[2] = Color.TRANSPARENT;
        innerGlowShader = new RadialGradient(cx - baseR * 0.28f, cy - baseR * 0.28f,
                baseR * 1.25f, innerGlowColors, innerGlowStops, Shader.TileMode.CLAMP);
    }

    /** 绘制弥散体光气场（全量复用 Cached RadialGradient + Matrix 缩放） */
    private void drawAtmosphericAura(@NonNull Canvas canvas, float cx, float cy, float shortSide, float breathVal) {
        if (auraShader == null) {
            return;
        }
        float scale = 0.86f + 0.14f * breathVal + 0.32f * smoothLevel;
        float auraRadius = shortSide * BLOOM_RADIUS_RATIO * scale;

        boolean dark = theme.isDark();
        int maxAlpha = switch (phase) {
            case LISTENING -> dark ? (int) (60 + 150 * smoothLevel) : (int) (40 + 90 * smoothLevel);
            case SPEAKING -> dark ? (int) (105 + 70 * breathVal) : (int) (70 + 45 * breathVal);
            case THINKING -> dark ? (int) (80 + 55 * breathVal) : (int) (55 + 35 * breathVal);
            case CONNECTING -> dark ? 42 : 28;
            default -> 0;
        };

        auraMatrix.setScale(scale, scale, cx, cy);
        auraShader.setLocalMatrix(auraMatrix);
        auraPaint.setShader(auraShader);
        auraPaint.setAlpha(Math.max(0, Math.min(255, maxAlpha)));
        canvas.drawCircle(cx, cy, auraRadius, auraPaint);
    }

    /** 绘制 64 阶复合正余弦闭合样条流体核 */
    private void drawHarmonicFluidCore(@NonNull Canvas canvas, float cx, float cy, float shortSide, float breathVal) {
        float baseR = Math.max(1f, shortSide * BASE_RADIUS_RATIO);

        if (phase == VisualPhase.OFFLINE) {
            fluidPaint.setShader(offlineShader);
            canvas.drawCircle(cx, cy, baseR * 0.88f, fluidPaint);
            return;
        }

        float fluidExpansion = (phase == VisualPhase.LISTENING) ? (shortSide * 0.12f * smoothLevel) : 0f;
        float r0 = (baseR + fluidExpansion) * (0.95f + 0.08f * breathVal);

        float amp1 = (phase == VisualPhase.LISTENING) ? (0.05f + 0.09f * smoothLevel) : 0.04f;
        float amp2 = (phase == VisualPhase.THINKING) ? 0.075f : 0.03f;
        float amp3 = (phase == VisualPhase.SPEAKING) ? 0.06f : 0.025f;
        float amp4 = (phase == VisualPhase.LISTENING) ? (0.035f * smoothLevel) : 0.012f;

        for (int i = 0; i < VERTEX_COUNT; i++) {
            double angle = (2 * Math.PI * i) / VERTEX_COUNT;
            double deformation = 1.0
                    + amp1 * Math.sin(2 * angle + timePhase * 1.7)
                    + amp2 * Math.cos(3 * angle - timePhase * 2.1)
                    + amp3 * Math.sin(5 * angle + timePhase * 1.3)
                    + amp4 * Math.cos(4 * angle - timePhase * 1.9);

            float r = (float) (r0 * deformation);
            vertices[i].set(
                    cx + (float) (r * Math.cos(angle)),
                    cy + (float) (r * Math.sin(angle))
            );
        }

        fluidPath.reset();
        for (int i = 0; i < VERTEX_COUNT; i++) {
            PointF p1 = vertices[i];
            PointF p2 = vertices[(i + 1) % VERTEX_COUNT];
            midPoints[i].set((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f);
        }

        fluidPath.moveTo(midPoints[VERTEX_COUNT - 1].x, midPoints[VERTEX_COUNT - 1].y);
        for (int i = 0; i < VERTEX_COUNT; i++) {
            fluidPath.quadTo(vertices[i].x, vertices[i].y, midPoints[i].x, midPoints[i].y);
        }
        fluidPath.close();

        if (sweepShader != null) {
            shaderMatrix.setRotate(vortexRotation, cx, cy);
            sweepShader.setLocalMatrix(shaderMatrix);
            fluidPaint.setShader(sweepShader);
            canvas.drawPath(fluidPath, fluidPaint);
        }

        float radialScale = r0 / baseR;
        radialMatrix.setScale(radialScale, radialScale, cx, cy);

        if (centerAuraShader != null) {
            centerAuraShader.setLocalMatrix(radialMatrix);
            centerAuraPaint.setShader(centerAuraShader);
            canvas.drawPath(fluidPath, centerAuraPaint);
        }

        if (centerCoreShader != null) {
            centerCoreShader.setLocalMatrix(radialMatrix);
            centerCorePaint.setShader(centerCoreShader);
            canvas.drawCircle(cx, cy, r0 * 0.35f, centerCorePaint);
        }

        if (innerGlowShader != null) {
            innerGlowShader.setLocalMatrix(radialMatrix);
            innerGlowPaint.setShader(innerGlowShader);
            canvas.drawPath(fluidPath, innerGlowPaint);
        }

        boolean dark = theme.isDark();
        fluidRimPaint.setColor(colorA);
        fluidRimPaint.setAlpha(dark ? (int) (85 + 65 * breathVal) : (int) (60 + 45 * breathVal));
        canvas.drawPath(fluidPath, fluidRimPaint);
    }

    private void updatePalette() {
        boolean dark = theme.isDark();
        switch (phase) {
            case LISTENING -> {
                colorA = dark ? 0xFF00F2FE : 0xFF00B4D8;
                colorB = dark ? 0xFF4FACFE : 0xFF0077B6;
                colorC = dark ? 0xFF00E676 : 0xFF10B981;
                auraGlow = dark ? 0xFF00F2FE : 0xFF00B4D8;
            }
            case SPEAKING -> {
                colorA = dark ? 0xFF3B82F6 : 0xFF2563EB;
                colorB = dark ? 0xFF8B5CF6 : 0xFF7C3AED;
                colorC = dark ? 0xFFEC4899 : 0xFFDB2777;
                auraGlow = dark ? 0xFF6366F1 : 0xFF4F46E5;
            }
            case THINKING -> {
                colorA = dark ? 0xFF7C3AED : 0xFF6366F1;
                colorB = dark ? 0xFFC026D3 : 0xFFA855F7;
                colorC = dark ? 0xFF38BDF8 : 0xFF0284C7;
                auraGlow = dark ? 0xFF9333EA : 0xFF7C3AED;
            }
            case CONNECTING -> {
                colorA = dark ? 0xFF06B6D4 : 0xFF0891B2;
                colorB = dark ? 0xFF0284C7 : 0xFF0369A1;
                colorC = dark ? 0xFF3B82F6 : 0xFF1D4ED8;
                auraGlow = dark ? 0xFF0284C7 : 0xFF0891B2;
            }
            case OFFLINE -> {
                colorA = theme.text3;
                colorB = theme.cardStroke;
                colorC = theme.card;
                auraGlow = Color.TRANSPARENT;
            }
        }
    }

    private void restartBreath() {
        stopBreath();
        if (!isLive() && phase != VisualPhase.CONNECTING) {
            breathProgress = 0.5f;
            return;
        }

        long duration = switch (phase) {
            case SPEAKING -> 1200L;
            case LISTENING -> 2200L;
            case THINKING -> 3000L;
            default -> 2600L;
        };

        breathAnimator = ValueAnimator.ofFloat(0.1f, 0.9f);
        breathAnimator.setDuration(duration);
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breathAnimator.addUpdateListener(anim -> {
            breathProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        breathAnimator.start();
    }

    private void stopBreath() {
        if (breathAnimator != null) {
            breathAnimator.cancel();
            breathAnimator = null;
        }
    }

    private boolean isLive() {
        return phase == VisualPhase.LISTENING
                || phase == VisualPhase.SPEAKING
                || phase == VisualPhase.THINKING;
    }
}

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
 * <p>彻底清理多余杂乱的硬质同心圆环与浮动微粒，遵循极简呼吸有机生命体设计美学：
 * <ul>
 *   <li><b>深浅色双模自适应架构</b>：基于 {@link AiTheme} 色彩系统，自适应暗黑深邃夜空与明朗纯白通透画卷；</li>
 *   <li><b>64阶多相傅里叶谐波流体核 (64-Vertex Fourier Harmonic Spline)</b>：
 *       4 重复合角频率平滑叠加，呈现无死板圆环痕迹的有机水滴呼吸与声音振荡；</li>
 *   <li><b>极光色彩涡旋与中心消缝弥散光 (Seamless Aurora Chromatic Vortex)</b>：
 *       双层渐变融合，彻底消除硬边缘与接缝，营造半透明全息液态质感；</li>
 *   <li><b>触感弹性果冻打断 (Haptic Jelly Bounce)</b>：
 *       触碰即产生流体阻尼果冻形变，直觉打断 AI 发言。</li>
 * </ul>
 * </p>
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

    // 画笔体系
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fluidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fluidRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 几何路径与样条控制点
    private final Path fluidPath = new Path();
    private final PointF[] vertices = new PointF[VERTEX_COUNT];
    private final PointF[] midPoints = new PointF[VERTEX_COUNT];
    private final Matrix shaderMatrix = new Matrix();

    @NonNull
    private VisualPhase phase = VisualPhase.CONNECTING;
    @Nullable
    private OnBargeInListener bargeInListener;
    @Nullable
    private ValueAnimator breathAnimator;

    private float rawLevel = 0f;
    private float smoothLevel = 0f;
    private float breathProgress = 0.5f;

    // 旋转与流动时间相位
    private float timePhase = 0f;
    private float vortexRotation = 0f;
    private long lastFrameTime = 0L;

    // 触控打断果冻回弹
    private float touchScale = 1.0f;

    // 配色方案（支持深浅色模式语义映射）
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
                if (bargeInListener != null) {
                    bargeInListener.onBargeIn();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchScale = 1.0f;
                invalidate();
                return true;
            }
            default -> {}
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

        // 低通平滑电平滤波
        smoothLevel += (rawLevel - smoothLevel) * LEVEL_DAMPING;
        if (Math.abs(smoothLevel - rawLevel) < 0.001f) {
            smoothLevel = rawLevel;
        }

        // 推进时间相位与极光涡流转角
        float speedMultiplier = switch (phase) {
            case THINKING -> 2.2f;
            case SPEAKING -> 1.5f;
            case LISTENING -> 1.1f + smoothLevel * 1.6f;
            case CONNECTING -> 0.9f;
            default -> 0.5f;
        };
        timePhase = (timePhase + (delta / 4200f) * speedMultiplier * (float) (2 * Math.PI)) % (float) (2 * Math.PI);
        vortexRotation = (vortexRotation + (delta * 0.035f) * speedMultiplier) % 360f;

        float shortSide = Math.min(width, height);
        float cx = width / 2f;
        float cy = height / 2f;

        canvas.save();

        // 触控果冻微缩
        if (touchScale != 1.0f) {
            canvas.scale(touchScale, touchScale, cx, cy);
        }

        float breathVal = isLive() ? breathProgress : 0.5f;

        // 1. 绘制极光背景体辉光（纯净无硬边缘，自然融入画布）
        if (phase != VisualPhase.OFFLINE) {
            drawAtmosphericAura(canvas, cx, cy, shortSide, breathVal);
        }

        // 2. 绘制 64 阶高阶谐波平滑流体核（纯净无多余圆环和杂点）
        drawHarmonicFluidCore(canvas, cx, cy, shortSide, breathVal);

        canvas.restore();

        // 高帧率常态驱动
        if (isLive() || phase == VisualPhase.CONNECTING) {
            postInvalidateOnAnimation();
        }
    }

    /** 绘制弥散体光气场 */
    private void drawAtmosphericAura(@NonNull Canvas canvas, float cx, float cy, float shortSide, float breathVal) {
        float auraRadius = shortSide * BLOOM_RADIUS_RATIO * (0.86f + 0.14f * breathVal + 0.32f * smoothLevel);

        boolean dark = theme.isDark();
        int maxAlpha = switch (phase) {
            case LISTENING -> dark ? (int) (60 + 150 * smoothLevel) : (int) (40 + 90 * smoothLevel);
            case SPEAKING -> dark ? (int) (105 + 70 * breathVal) : (int) (70 + 45 * breathVal);
            case THINKING -> dark ? (int) (80 + 55 * breathVal) : (int) (55 + 35 * breathVal);
            case CONNECTING -> dark ? 42 : 28;
            default -> 0;
        };

        auraPaint.setShader(new RadialGradient(cx, cy, auraRadius,
                new int[]{
                        ColorUtils.setAlphaComponent(auraGlow, Math.min(255, maxAlpha)),
                        ColorUtils.setAlphaComponent(colorA, (int) (maxAlpha * 0.45f)),
                        ColorUtils.setAlphaComponent(colorB, (int) (maxAlpha * 0.15f)),
                        Color.TRANSPARENT
                },
                new float[]{0.0f, 0.42f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP));

        canvas.drawCircle(cx, cy, auraRadius, auraPaint);
    }

    /** 绘制 64 阶复合正余弦闭合样条流体核 */
    private void drawHarmonicFluidCore(@NonNull Canvas canvas, float cx, float cy, float shortSide, float breathVal) {
        float baseR = shortSide * BASE_RADIUS_RATIO;

        if (phase == VisualPhase.OFFLINE) {
            // 离线静息态：依主题选用柔和灰卡
            fluidPaint.setShader(new RadialGradient(cx, cy, baseR,
                    new int[]{theme.cardStroke, theme.card}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, baseR * 0.88f, fluidPaint);
            return;
        }

        // 声音电平与呼吸复合形变振幅
        float fluidExpansion = (phase == VisualPhase.LISTENING) ? (shortSide * 0.12f * smoothLevel) : 0f;
        float r0 = (baseR + fluidExpansion) * (0.95f + 0.08f * breathVal);

        float amp1 = (phase == VisualPhase.LISTENING) ? (0.05f + 0.09f * smoothLevel) : 0.04f;
        float amp2 = (phase == VisualPhase.THINKING) ? 0.075f : 0.03f;
        float amp3 = (phase == VisualPhase.SPEAKING) ? 0.06f : 0.025f;
        float amp4 = (phase == VisualPhase.LISTENING) ? (0.035f * smoothLevel) : 0.012f;

        // 计算 64 控制点坐标
        for (int i = 0; i < VERTEX_COUNT; i++) {
            double angle = (2 * Math.PI * i) / VERTEX_COUNT;

            // 4 重空间角频率复合叠加波形
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

        // 构建闭合平滑贝塞尔样条
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

        // 极光涡流色彩渐变着色器（SweepGradient + Matrix 旋转）
        SweepGradient sweepShader = new SweepGradient(cx, cy,
                new int[]{colorA, colorB, colorC, colorA},
                new float[]{0.0f, 0.42f, 0.80f, 1.0f});
        shaderMatrix.reset();
        shaderMatrix.postRotate(vortexRotation, cx, cy);
        sweepShader.setLocalMatrix(shaderMatrix);

        fluidPaint.setShader(sweepShader);
        canvas.drawPath(fluidPath, fluidPaint);

        // 柔和中心弥散极光核，彻底消除扫掠渐变角边接缝，呈现通透纵深
        boolean dark = theme.isDark();
        int whiteCenterAlpha = dark ? 140 : 110;
        int colorBlendAlpha = dark ? 150 : 100;
        centerAuraPaint.setShader(new RadialGradient(cx, cy, r0 * 0.95f,
                new int[]{
                        ColorUtils.setAlphaComponent(Color.WHITE, whiteCenterAlpha),
                        ColorUtils.setAlphaComponent(colorA, colorBlendAlpha),
                        Color.TRANSPARENT
                },
                new float[]{0.0f, 0.35f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawPath(fluidPath, centerAuraPaint);

        // 柔和流体核芯消除原点射线汇聚尖点
        Paint centerCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerCorePaint.setStyle(Paint.Style.FILL);
        centerCorePaint.setShader(new RadialGradient(cx, cy, r0 * 0.35f,
                new int[]{
                        ColorUtils.setAlphaComponent(Color.WHITE, dark ? 160 : 120),
                        ColorUtils.setAlphaComponent(colorA, dark ? 120 : 80),
                        Color.TRANSPARENT
                },
                new float[]{0.0f, 0.45f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r0 * 0.35f, centerCorePaint);

        // 内部通透高光反射弧
        innerGlowPaint.setShader(new RadialGradient(cx - r0 * 0.28f, cy - r0 * 0.28f, r0 * 1.25f,
                new int[]{dark ? 0x99FFFFFF : 0x66FFFFFF, dark ? 0x33FFFFFF : 0x1AFFFFFF, Color.TRANSPARENT},
                new float[]{0.0f, 0.35f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawPath(fluidPath, innerGlowPaint);

        // 晶莹流体高光边缘线
        fluidRimPaint.setColor(colorA);
        fluidRimPaint.setAlpha(dark ? (int) (85 + 65 * breathVal) : (int) (60 + 45 * breathVal));
        canvas.drawPath(fluidPath, fluidRimPaint);
    }

    private void updatePalette() {
        boolean dark = theme.isDark();
        switch (phase) {
            case LISTENING -> {
                // 极光青翠：自适应高灵敏拾音
                colorA = dark ? 0xFF00F2FE : 0xFF00B4D8;
                colorB = dark ? 0xFF4FACFE : 0xFF0077B6;
                colorC = dark ? 0xFF00E676 : 0xFF10B981;
                auraGlow = dark ? 0xFF00F2FE : 0xFF00B4D8;
            }
            case SPEAKING -> {
                // 电光紫蓝：饱满声浪发音
                colorA = dark ? 0xFF3B82F6 : 0xFF2563EB;
                colorB = dark ? 0xFF8B5CF6 : 0xFF7C3AED;
                colorC = dark ? 0xFFEC4899 : 0xFFDB2777;
                auraGlow = dark ? 0xFF6366F1 : 0xFF4F46E5;
            }
            case THINKING -> {
                // 深度霓紫：沉浸推理星云
                colorA = dark ? 0xFF7C3AED : 0xFF6366F1;
                colorB = dark ? 0xFFC026D3 : 0xFFA855F7;
                colorC = dark ? 0xFF38BDF8 : 0xFF0284C7;
                auraGlow = dark ? 0xFF9333EA : 0xFF7C3AED;
            }
            case CONNECTING -> {
                // 静谧蔚蓝：平稳寻呼待命
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

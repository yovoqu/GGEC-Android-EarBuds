package com.ggec.earbuds.ui.ai;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.yohu.youi.api.theme.YoColors;
import com.yohu.youi.api.theme.YoFonts;

/**
 * AI 界面的视觉令牌与视图工厂。
 *
 * <p>颜色全部来自 YoUI 主题快照（{@link YoColors#of(Context)}），深浅色切换时随页面重建；
 * 字号/间距按 ui-design.md 的 AI 专用阶梯，与 {@code YoDimens} 数值重合的保持一致，避免两套口径互相打架。</p>
 */
public final class AiTheme {

    public enum Weight {
        REGULAR,
        MEDIUM,
        BOLD
    }

    // ---- 间距 ----
    public static final float SPACE_XS = 4f;
    public static final float SPACE_S = 8f;
    public static final float SPACE_M = 12f;
    public static final float SPACE_L = 16f;
    public static final float SPACE_XL = 24f;

    // ---- 圆角 ----
    public static final float RADIUS_FIELD = 24f;
    public static final float RADIUS_SHEET = 16f;
    public static final float RADIUS_ROW = 12f;

    // ---- 字号 ----
    public static final float TEXT_CAPTION = 12f;
    public static final float TEXT_SUBHEAD = 13f;
    public static final float TEXT_BODY = 15f;
    public static final float TEXT_TITLE = 17f;
    public static final float TEXT_HEADLINE = 20f;

    // ---- 尺寸 ----
    public static final float PAGE_INSET = 20f;
    /**
     * 自有 {@code YoList} 卡片离父级的距离。边距写在库里（16dp），页面不要再叠一层页边距。
     * 分区标题、搜索和分段跟卡片外沿对齐。
     */
    public static final float LIST_EDGE = 16f;
    /** 页内标题行高度；比库的标题栏矮，把高度让给消息。*/
    public static final float HEADER_HEIGHT = 56f;
    /** 建议行的固定行高，与系统列表的两行高度一致。*/
    public static final float ROW_HEIGHT = 56f;
    public static final float TOUCH_TARGET = 40f;
    /** 空状态的标题距内容区顶部的高度；三种空状态共用一个起点。*/
    public static final float EMPTY_TOP = 88f;

    // ---- 动效时长 ----
    public static final long DURATION_XS = 120L;
    public static final long DURATION_S = 140L;
    public static final long DURATION_M = 160L;
    /** 空状态入场。 */
    public static final long DURATION_L = 240L;
    /** 程序滚动到末尾后等待滚动收尾的时间。 */
    public static final long DURATION_SCROLL_SETTLE = 300L;

    // ---- 字号补充 ----
    /** 12 与 13 之间的紧凑标签（存储横幅、双工标签）。 */
    public static final float TEXT_CAPTION_LARGE = 12.5f;
    /** 二级分区标题。 */
    public static final float TEXT_SECTION = 16f;

    // ---- 媒体层（半透明卡片、遮罩） ----
    public static final int MEDIA_WHITE = 0xFFFFFFFF;
    public static final int MEDIA_BLACK = 0xFF000000;

    // ---- 输入区 ----
    public static final float COMPOSER_PAD = 10f;
    public static final float INPUT_PAD = 11f;
    public static final float TEXT_INPUT = 16f;
    public static final float SEND_SIZE = 36f;
    public static final float SEND_GLYPH_PAD = 9f;
    public static final float SEND_MARGIN = 6f;
    public static final float SEND_RADIUS = 18f;

    // ---- 通话页 ----
    public static final int CALL_PAGE_DARK_TOP = 0xFF080C14;
    public static final int CALL_PAGE_DARK_MID = 0xFF0F172A;
    public static final int HANGUP_START = 0xFFEF4444;
    public static final int HANGUP_END = 0xFFDC2626;

    /** 声场配色按语义阶段取，不按调用点各写一份色值。 */
    public enum CallPhase {
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING
    }

    public record CallPalette(@ColorInt int colorA, @ColorInt int colorB,
                              @ColorInt int colorC, @ColorInt int auraGlow) {
    }

    public final Context context;
    public final float density;
    public final boolean dark;
    public boolean isDark() { return dark; }

    @ColorInt public final int page;
    @ColorInt public final int card;
    @ColorInt public final int cardStroke;
    @ColorInt public final int fill;
    @ColorInt public final int text1;
    @ColorInt public final int text2;
    @ColorInt public final int text3;
    @ColorInt public final int hint;
    @ColorInt public final int brand;
    @ColorInt public final int brandSoft;
    @ColorInt public final int onBrand;
    @ColorInt public final int divider;
    @ColorInt public final int alert;
    @ColorInt public final int alertSoft;
    @ColorInt public final int warning;
    @ColorInt public final int warningSoft;
    @ColorInt public final int code;
    private final int ripple;

    private AiTheme(@NonNull Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        YoColors colors = YoColors.of(context);
        this.dark = colors.isDark();
        this.page = colors.palette().snowGray(1);
        this.card = colors.system().cardBg();
        this.text1 = colors.textPrimary();
        this.text2 = colors.textSecondary();
        this.text3 = colors.textTertiary();
        this.hint = colors.textQuaternary();
        this.brand = colors.brand();
        this.onBrand = colors.textOnPrimary();
        this.divider = colors.divider();
        this.alert = colors.alert();
        this.warning = colors.warning();
        this.ripple = colors.ripple();
        this.cardStroke = ColorUtils.setAlphaComponent(text1, dark ? 0x1F : 0x14);
        this.fill = ColorUtils.setAlphaComponent(text1, dark ? 0x1A : 0x0F);
        this.brandSoft = ColorUtils.setAlphaComponent(brand, dark ? 0x2E : 0x1A);
        this.alertSoft = ColorUtils.setAlphaComponent(alert, dark ? 0x2E : 0x14);
        this.warningSoft = ColorUtils.setAlphaComponent(warning, dark ? 0x2E : 0x1A);
        this.code = ColorUtils.setAlphaComponent(text1, dark ? 0x24 : 0x0D);
    }

    @NonNull
    public static AiTheme of(@NonNull Context context) {
        return new AiTheme(context);
    }

    public int dp(float value) {
        return Math.round(value * density);
    }

    public float dpf(float value) {
        return value * density;
    }

    /** 通话页三段渐变：深色是黑曜石夜空，浅色是纯白雪夜。 */
    @NonNull
    public int[] callPageStops() {
        return dark
                ? new int[]{CALL_PAGE_DARK_TOP, CALL_PAGE_DARK_MID, CALL_PAGE_DARK_TOP}
                : new int[]{page, ColorUtils.blendARGB(page, MEDIA_WHITE, 0.4f), page};
    }

    /** 语音声场四个阶段的配色（含光晕）。 */
    @NonNull
    public CallPalette callPalette(@NonNull CallPhase phase) {
        return switch (phase) {
            case LISTENING -> new CallPalette(dark ? 0xFF00F2FE : 0xFF00B4D8,
                    dark ? 0xFF4FACFE : 0xFF0077B6, dark ? 0xFF00E676 : 0xFF10B981,
                    dark ? 0xFF00F2FE : 0xFF00B4D8);
            case SPEAKING -> new CallPalette(dark ? 0xFF3B82F6 : 0xFF2563EB,
                    dark ? 0xFF8B5CF6 : 0xFF7C3AED, dark ? 0xFFEC4899 : 0xFFDB2777,
                    dark ? 0xFF6366F1 : 0xFF4F46E5);
            case THINKING -> new CallPalette(dark ? 0xFF7C3AED : 0xFF6366F1,
                    dark ? 0xFFC026D3 : 0xFFA855F7, dark ? 0xFF38BDF8 : 0xFF0284C7,
                    dark ? 0xFF9333EA : 0xFF7C3AED);
            case CONNECTING -> new CallPalette(dark ? 0xFF06B6D4 : 0xFF0891B2,
                    dark ? 0xFF0284C7 : 0xFF0369A1, dark ? 0xFF3B82F6 : 0xFF1D4ED8,
                    dark ? 0xFF0284C7 : 0xFF0891B2);
        };
    }

    // ---- 文本与图标 ----

    @NonNull
    public TextView text(float sp, @ColorInt int color) {
        return text(sp, color, Weight.REGULAR);
    }

    @NonNull
    public TextView text(float sp, @ColorInt int color, @NonNull Weight weight) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        switch (weight) {
            case MEDIUM -> YoFonts.applyMedium(view);
            case BOLD -> YoFonts.applyBold(view);
            default -> YoFonts.applyRegular(view);
        }
        return view;
    }

    @NonNull
    public ImageView icon(@DrawableRes int resource, @ColorInt int tint) {
        ImageView view = new ImageView(context);
        view.setImageResource(resource);
        view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    /** 消息操作行上的小图标按钮：固定触控区 + 圆形水波纹。*/
    @NonNull
    public ImageView iconButton(@DrawableRes int resource, @ColorInt int tint,
                                float sizeDp, float iconPaddingDp, @NonNull CharSequence description) {
        ImageView button = icon(resource, tint);
        int pad = dp(iconPaddingDp);
        button.setPadding(pad, pad, pad, pad);
        button.setBackground(pressableCircle());
        button.setContentDescription(description);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return button;
    }

    // ---- 背景 ----

    @NonNull
    public GradientDrawable rounded(@ColorInt int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpf(radiusDp));
        return drawable;
    }

    @NonNull
    public GradientDrawable roundedStroke(@ColorInt int color, float radiusDp, @ColorInt int stroke, float strokeDp) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return drawable;
    }

    /**
     * 对角渐变底：只用于语音状态卡"身份"面，制造一点光的来源感，不做装饰滥用。
     */
    @NonNull
    public GradientDrawable gradient(@ColorInt int start, @ColorInt int end, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dpf(radiusDp));
        return drawable;
    }

    @NonNull
    public GradientDrawable gradientStroke(@ColorInt int start, @ColorInt int end, float radiusDp,
                                          @ColorInt int stroke, float strokeDp) {
        GradientDrawable drawable = gradient(start, end, radiusDp);
        drawable.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return drawable;
    }

    /** 四个角分别指定半径（dp）：左上、右上、右下、左下。*/
    @NonNull
    public GradientDrawable corners(@ColorInt int color, float topLeft, float topRight, float bottomRight,
                                    float bottomLeft) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[]{
                dpf(topLeft), dpf(topLeft), dpf(topRight), dpf(topRight),
                dpf(bottomRight), dpf(bottomRight), dpf(bottomLeft), dpf(bottomLeft)});
        return drawable;
    }

    @NonNull
    public GradientDrawable circle(@ColorInt int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** 空心圆环，用来画次级项目符号。*/
    @NonNull
    public GradientDrawable ring(@ColorInt int color, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(Math.max(1, dp(strokeDp)), color);
        return drawable;
    }

    /** 在内容背景上叠一层按下的水波纹，水波纹被限制。{@code content} 的形状内。*/
    @NonNull
    public Drawable pressable(@NonNull Drawable content, float radiusDp) {
        Drawable mask = rounded(MEDIA_WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, mask);
    }

    /** 无底色的可点击背景，只有按下的水波纹。*/
    @NonNull
    public Drawable pressable(float radiusDp) {
        Drawable mask = rounded(MEDIA_WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, mask);
    }

    @NonNull
    private Drawable pressableCircle() {
        GradientDrawable mask = circle(MEDIA_WHITE);
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, mask);
    }
}

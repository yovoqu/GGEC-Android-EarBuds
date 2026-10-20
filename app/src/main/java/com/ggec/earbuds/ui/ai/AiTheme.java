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
    public static final float RADIUS_FIELD = 22f;

    // ---- 字号 ----
    public static final float TEXT_CAPTION = 12f;
    public static final float TEXT_SUBHEAD = 13f;
    public static final float TEXT_BODY = 15f;
    public static final float TEXT_HEADLINE = 20f;

    // ---- 尺寸 ----
    public static final float PAGE_INSET = 20f;

    public final Context context;
    public final float density;
    private final boolean dark;

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

    /** 消息操作行上的小图标按钮：固定触控区 + 圆形水波纹。 */
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

    /** 四个角分别指定半径（dp）：左上、右上、右下、左下。 */
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

    /** 空心圆环，用来画次级项目符号。 */
    @NonNull
    public GradientDrawable ring(@ColorInt int color, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(Math.max(1, dp(strokeDp)), color);
        return drawable;
    }

    /** 在内容背景上叠一层按下的水波纹，水波纹被限制在 {@code content} 的形状内。 */
    @NonNull
    public Drawable pressable(@NonNull Drawable content, float radiusDp) {
        Drawable mask = rounded(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, mask);
    }

    /** 无底色的可点击背景，只有按下的水波纹。 */
    @NonNull
    public Drawable pressable(float radiusDp) {
        Drawable mask = rounded(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, mask);
    }

    @NonNull
    private Drawable pressableCircle() {
        GradientDrawable mask = circle(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, mask);
    }
}

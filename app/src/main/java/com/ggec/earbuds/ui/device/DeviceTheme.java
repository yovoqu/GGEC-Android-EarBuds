package com.ggec.earbuds.ui.device;

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
 * 设备模块视觉令牌：与 AI 页同一套阶梯（白底单列、字号 28/20/17/15/14/13/12、
 * 蓝色只给主操作与进行中）。颜色全部来自 YoUI 主题快照，跟随深浅色切换。
 */
public final class DeviceTheme {

    public enum Weight {
        REGULAR,
        MEDIUM,
        BOLD
    }

    public static final float SPACE_XS = 4f;
    public static final float SPACE_S = 8f;
    public static final float SPACE_M = 12f;
    public static final float SPACE_L = 16f;
    public static final float SPACE_XL = 24f;

    /** 多行说明文字的额外行距（dp）。 */
    public static final float LINE_SPACING_EXTRA = 5f;
    /** 状态圆点直径（dp）。 */
    public static final float STATUS_DOT = 8f;

    public static final float RADIUS_FIELD = 24f;
    public static final float RADIUS_SHEET = 16f;
    public static final float RADIUS_ROW = 12f;

    public static final float TEXT_CAPTION = 12f;
    public static final float TEXT_SUBHEAD = 13f;
    public static final float TEXT_CALLOUT = 14f;
    public static final float TEXT_BODY = 15f;
    public static final float TEXT_TITLE = 17f;
    public static final float TEXT_HEADLINE = 20f;

    public static final float PAGE_INSET = 20f;
    public static final float LIST_EDGE = 16f;
    public static final float HEADER_HEIGHT = 56f;
    /** 已连接状态点：与业务状态色一致，不随深浅色变化。 */
    public static final int CONNECTED_GREEN = 0xFF22C55E;
    public static final float ROW_HEIGHT = 56f;
    public static final float TOUCH_TARGET = 40f;
    public static final float EMPTY_TOP = 88f;

    public static final float SCAN_CARD_MIN_HEIGHT = 76f;
    public static final float SCAN_ICON_BOX = 40f;
    public static final float SCAN_ICON = 20f;
    public static final float SCAN_CHEVRON = 16f;
    public static final float SCAN_SUBTITLE_GAP = 2f;
    public static final float PRIMARY_ACTION_HEIGHT = 48f;

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
    @ColorInt public final int confirm;
    private final int ripple;

    private DeviceTheme(@NonNull Context context) {
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
        this.confirm = colors.confirm();
        this.ripple = colors.ripple();
        this.cardStroke = ColorUtils.setAlphaComponent(text1, dark ? 0x1F : 0x14);
        this.fill = ColorUtils.setAlphaComponent(text1, dark ? 0x1A : 0x0F);
        this.brandSoft = ColorUtils.setAlphaComponent(brand, dark ? 0x2E : 0x1A);
        this.alertSoft = ColorUtils.setAlphaComponent(alert, dark ? 0x2E : 0x14);
    }

    @NonNull
    public static DeviceTheme of(@NonNull Context context) {
        return new DeviceTheme(context);
    }

    public int dp(float value) {
        return Math.round(value * density);
    }

    public float dpf(float value) {
        return value * density;
    }

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

    @NonNull
    public GradientDrawable rounded(@ColorInt int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpf(radiusDp));
        return drawable;
    }

    @NonNull
    public GradientDrawable roundedStroke(@ColorInt int color, float radiusDp,
                                          @ColorInt int stroke, float strokeDp) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return drawable;
    }

    @NonNull
    public GradientDrawable circle(@ColorInt int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

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

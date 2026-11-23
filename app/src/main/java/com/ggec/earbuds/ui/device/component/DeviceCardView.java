package com.ggec.earbuds.ui.device.component;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.common.YoRipple;
import com.yohu.youi.api.controls.YoSwitch;

/**
 * 公共卡片（一行一列 / 一行两列通用的单元格）：圆角卡面 + 标题 + 尾槽。
 *
 * <p>尾槽两种：</p>
 * <ul>
 *   <li>入口卡：右侧只放「彩色分区图标」，<b>不再叠右箭头</b>（有图标即不再加箭头）；按压用
 *       YoUI 的 {@link YoRipple}（默认 MASK 灰遮罩）作卡面背景；</li>
 *   <li>开关卡：右侧用 YoUI 的 {@link YoSwitch}。</li>
 * </ul>
 * 卡片宽度由 {@link DeviceCardRow} 决定；本类只渲染单张卡。
 */
public final class DeviceCardView {

    /** 开关变化回调。 */
    public interface OnToggle {
        void onToggle(boolean checked);
    }

    private DeviceCardView() {
    }

    /** 入口卡。 */
    @NonNull
    public static View entry(@NonNull Context context, @NonNull DeviceTheme theme,
                             @NonNull CharSequence label, @DrawableRes int iconRes,
                             @NonNull Runnable onClick) {
        LinearLayout card = baseCard(context, theme);
        card.setBackground(rippleBackground(context, theme));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onClick.run());
        card.addView(label(context, theme, label), labelParams());
        card.addView(icon(context, theme, iconRes), iconParams(theme));
        return card;
    }

    /** 开关卡。{@code switch} 注册到 {@code components}，由调用方统一释放。 */
    @NonNull
    public static View toggle(@NonNull Context context, @NonNull DeviceTheme theme,
                              @NonNull YoComponents components, @NonNull CharSequence label,
                              @DrawableRes int iconRes, boolean checked, boolean enabled,
                              @NonNull OnToggle onToggle) {
        LinearLayout card = baseCard(context, theme);
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.CARD_RADIUS,
                theme.cardStroke, 1f));
        card.addView(label(context, theme, label), labelParams());
        card.addView(icon(context, theme, iconRes), iconParams(theme));

        YoSwitch toggle = YoSwitch.create(context)
                .setChecked(checked)
                .setEnabled(enabled)
                .setOnCheckedChangeListener(onToggle::onToggle)
                .build();
        components.own(toggle::destroy);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.leftMargin = DeviceInsets.gap(theme);
        card.addView(toggle.getView(), toggleParams);
        return card;
    }

    /**
     * 可点击卡面（统一按压反馈）：白色圆角卡 + YoUI MASK 水波纹 + 描边 + 卡片内边距。
     * 任何自绘卡都必须用这一层做底，避免出现「能点却没水波纹」的卡片。调用方按需 addView 内容。
     */
    @NonNull
    public static LinearLayout pressable(@NonNull Context context, @NonNull DeviceTheme theme) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(theme.dp(DeviceTheme.CARD_MIN_HEIGHT));
        int padH = DeviceInsets.cardInner(theme);
        int padV = theme.dp(DeviceTheme.CARD_PADDING_V);
        card.setPadding(padH, padV, padH, padV);
        card.setBackground(rippleBackground(context, theme));
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    /** 图标按矢量自带的多色渲染，不做运行时单色着色。 */
    @NonNull
    private static ImageView icon(@NonNull Context context, @NonNull DeviceTheme theme,
                                  @DrawableRes int iconRes) {        ImageView view = new ImageView(context);
        view.setImageResource(iconRes);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    @NonNull
    private static LinearLayout.LayoutParams iconParams(@NonNull DeviceTheme theme) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.CARD_ICON), theme.dp(DeviceTheme.CARD_ICON));
        params.leftMargin = DeviceInsets.gap(theme);
        return params;
    }

    /** 卡面：白色圆角卡 + 卡片内边距；不含按压/开关（由具体卡决定）。 */
    @NonNull
    private static LinearLayout baseCard(@NonNull Context context, @NonNull DeviceTheme theme) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(theme.dp(DeviceTheme.CARD_MIN_HEIGHT));
        int padH = DeviceInsets.cardInner(theme);
        int padV = theme.dp(DeviceTheme.CARD_PADDING_V);
        card.setPadding(padH, padV, padH, padV);
        return card;
    }

    /** YoUI MASK 按压背景：圆角 + 卡面底色 + 描边，全部由一次构建给出。 */
    @NonNull
    private static Drawable rippleBackground(@NonNull Context context, @NonNull DeviceTheme theme) {
        return YoRipple.create(context)
                .setCornerRadius(DeviceTheme.CARD_RADIUS)
                .setBackgroundColor(theme.card)
                .setBorder(1f, theme.cardStroke)
                .build();
    }

    @NonNull
    private static TextView label(@NonNull Context context, @NonNull DeviceTheme theme,
                                  @NonNull CharSequence text) {
        TextView label = theme.text(DeviceTheme.TEXT_TITLE, theme.text1, DeviceTheme.Weight.MEDIUM);
        label.setText(text);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return label;
    }

    @NonNull
    private static LinearLayout.LayoutParams labelParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}

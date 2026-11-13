package com.ggec.earbuds.ui.ai.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 统一标题栏拟物微质感圆形操作按键 (AiHeaderActionButton)：
 * <p>
 * 遵循次世代设计系统规范：
 * 1. 统一 42dp × 42dp 黄金触控胶囊，10dp 内边距，居中对齐。
 * 2. 100% 依托 {@link AiTheme} 语义调色系统，自适应深浅双色模式，严禁硬编码。
 * 3. 支持三种经典交互风格：
 *    - {@link Style#NEUTRAL}：标准返回/关闭/更多等常规按键（卡片底色 + 1dp 轮廓描边 + 主文字色图标）。
 *    - {@link Style#BRAND}：品牌入口按键（如通话入口按键，图标使用品牌色高亮）。
 *    - {@link Style#TOGGLE}：双态开关按键（如字幕 CC 开关，激活态动态转换为品牌浅底与品牌描边）。
 * </p>
 */
public class AiHeaderActionButton extends AppCompatImageView {

    public enum Style {
        /** 标准无状态按键 (例如：返回、关闭、历史、更多) */
        NEUTRAL,
        /** 品牌高亮无状态按键 (例如：对话页电话呼叫入口) */
        BRAND,
        /** 双态开关按键 (例如：通话页字幕开关) */
        TOGGLE
    }

    public static final float DEFAULT_SIZE_DP = 42f;
    public static final float DEFAULT_PADDING_DP = 10f;
    public static final float BORDER_NORMAL_DP = 1.0f;
    public static final float BORDER_ACTIVE_DP = 1.2f;

    private AiTheme theme;
    private Style style = Style.NEUTRAL;
    private boolean active = false;
    @Nullable
    private Integer customIconTint = null;

    public AiHeaderActionButton(@NonNull Context context) {
        this(context, AiTheme.of(context), Style.NEUTRAL);
    }

    public AiHeaderActionButton(@NonNull Context context, @NonNull AiTheme theme) {
        this(context, theme, Style.NEUTRAL);
    }

    public AiHeaderActionButton(@NonNull Context context, @NonNull AiTheme theme, @NonNull Style style) {
        super(context);
        this.theme = theme;
        this.style = style;
        init();
    }

    private void init() {
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = theme.dp(DEFAULT_PADDING_DP);
        setPadding(pad, pad, pad, pad);
        updateVisualState();
    }

    /** 设置图标资源 */
    @NonNull
    public AiHeaderActionButton icon(@DrawableRes int iconRes) {
        setImageResource(iconRes);
        return this;
    }

    /** 设置辅助说明文本 */
    @NonNull
    public AiHeaderActionButton contentDesc(@NonNull CharSequence desc) {
        setContentDescription(desc);
        return this;
    }

    /** 设置辅助说明文本资源 */
    @NonNull
    public AiHeaderActionButton contentDesc(@StringRes int descRes) {
        setContentDescription(getContext().getString(descRes));
        return this;
    }

    /** 链式绑定点击监听 */
    @NonNull
    public AiHeaderActionButton onClick(@Nullable OnClickListener listener) {
        setOnClickListener(listener);
        return this;
    }

    /** 设置风格模式 */
    @NonNull
    public AiHeaderActionButton style(@NonNull Style style) {
        if (this.style != style) {
            this.style = style;
            updateVisualState();
        }
        return this;
    }

    /** 设置并刷新开关激活状态 (仅对 {@link Style#TOGGLE} 生效) */
    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            updateVisualState();
        }
    }

    public boolean isActive() {
        return active;
    }

    /** 自定义图标着色（传 null 恢复默认风格着色） */
    public void setCustomIconTint(@Nullable @ColorInt Integer tint) {
        this.customIconTint = tint;
        updateVisualState();
    }

    /** 重新计算背景、边框、水波纹与图标着色 */
    private void updateVisualState() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);

        int iconTint;

        if (style == Style.TOGGLE && active) {
            // 开关激活态：品牌浅色底 + 品牌主色描边 + 品牌主色图标
            bg.setColor(theme.brandSoft);
            bg.setStroke(Math.max(1, theme.dp(BORDER_ACTIVE_DP)), theme.brand);
            iconTint = theme.brand;
        } else {
            // 普通态或开关关闭态：卡片底色 + 微弱描边
            bg.setColor(theme.card);
            bg.setStroke(Math.max(1, theme.dp(BORDER_NORMAL_DP)), theme.cardStroke);

            if (style == Style.BRAND) {
                iconTint = theme.brand;
            } else if (style == Style.TOGGLE) {
                iconTint = theme.text2;
            } else {
                iconTint = theme.text1;
            }
        }

        if (customIconTint != null) {
            iconTint = customIconTint;
        }

        setColorFilter(iconTint);
        setBackground(new RippleDrawable(ColorStateList.valueOf(theme.brandSoft), bg, null));
    }

    // ==========================================
    // 实例 LayoutParams 快捷方法
    // ==========================================

    @NonNull
    public ViewGroup.LayoutParams createLayoutParams() {
        return createLayoutParams(this.theme);
    }

    @NonNull
    public LinearLayout.LayoutParams createLinearLayoutParams() {
        return createLinearLayoutParams(this.theme);
    }

    @NonNull
    public RelativeLayout.LayoutParams createRelativeLayoutParams() {
        return createRelativeLayoutParams(this.theme);
    }

    // ==========================================
    // 静态 LayoutParams 工厂方法
    // ==========================================

    @NonNull
    public static ViewGroup.LayoutParams createLayoutParams(@NonNull AiTheme theme) {
        int sizePx = theme.dp(DEFAULT_SIZE_DP);
        return new ViewGroup.LayoutParams(sizePx, sizePx);
    }

    @NonNull
    public static LinearLayout.LayoutParams createLinearLayoutParams(@NonNull AiTheme theme) {
        int sizePx = theme.dp(DEFAULT_SIZE_DP);
        return new LinearLayout.LayoutParams(sizePx, sizePx);
    }

    @NonNull
    public static RelativeLayout.LayoutParams createRelativeLayoutParams(@NonNull AiTheme theme) {
        int sizePx = theme.dp(DEFAULT_SIZE_DP);
        return new RelativeLayout.LayoutParams(sizePx, sizePx);
    }
}

package com.yovo.yotheme.components.yo_card;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.yovo.yotheme.R;
import com.yovo.yotheme.background.shapeblurbiew.YoBlurBackgroundView;
import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author tingfeng.yang
 * @date 2025-01-29
 * @description 通用图标+主/副标题卡片组件
 * @note 适用于首页眼镜功能卡、AI功能卡等左图右文场景
 */
public class YoIconTextCard extends FrameLayout {

    private static final float DEFAULT_CORNER_RADIUS_DP = 16f;
    private static final float DEFAULT_ELEVATION_DP = 2f;
    private static final float DEFAULT_ICON_SIZE_DP = 28f;
    private static final float DEFAULT_ICON_MARGIN_END_DP = 12f;
    private static final float DEFAULT_HORIZONTAL_PADDING_DP = 14f;
    private static final float DEFAULT_VERTICAL_PADDING_DP = 12f;
    private static final float DEFAULT_TITLE_TEXT_SIZE_SP = 15f;
    private static final float DEFAULT_SUBTITLE_TEXT_SIZE_SP = 11f;
    private static final float DEFAULT_SUBTITLE_LINE_SPACING_DP = 2f;

    private final int defaultTitleColor = ColorMapper.map(YoThemeColor.FONT_PRIMARY);
    private final int defaultSubtitleColor = ColorMapper.map(YoThemeColor.FONT_SECONDARY);
    private final int defaultBackgroundColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY);
    private final int defaultRippleColor = ColorMapper.map(YoThemeColor.INTERACTIVE_PRESSED);

    private ImageView iconView;
    private TextView titleView;
    private TextView subtitleView;
    private LinearLayout textContainer;
    private LinearLayout contentLayout;

    private GradientDrawable backgroundDrawable;
    private boolean rippleEnabled = true;
    private int cardBackgroundColor = defaultBackgroundColor;
    private int rippleColor = defaultRippleColor;
    private float cornerRadiusDp = DEFAULT_CORNER_RADIUS_DP;

    private int iconSizePx;
    private int iconMarginEndPx;

    private boolean useBlurBackground = false;
    private YoBlurBackgroundView blurBackgroundView;
    private FrameLayout blurContainer;

    public YoIconTextCard(@NonNull Context context) {
        super(context);
        init(context);
    }

    public YoIconTextCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public YoIconTextCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     * 初始化组件
     */
    private void init(Context context) {
        // 在SDK层禁止裁剪边缘，确保阴影和边缘效果能正常显示
        setClipToPadding(false);
        setClipChildren(false);
        
        // 为阴影效果预留边距空间（上下左右各留出阴影扩散空间）
        int shadowMargin = dpToPx(DEFAULT_ELEVATION_DP + 2);
        setPadding(shadowMargin, shadowMargin, shadowMargin, shadowMargin);
        
        iconSizePx = dpToPx(DEFAULT_ICON_SIZE_DP);
        iconMarginEndPx = dpToPx(DEFAULT_ICON_MARGIN_END_DP);

        // 创建内容布局
        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);
        contentLayout.setClipToPadding(false);
        contentLayout.setClipChildren(false);

        int horizontalPadding = dpToPx(DEFAULT_HORIZONTAL_PADDING_DP);
        int verticalPadding = dpToPx(DEFAULT_VERTICAL_PADDING_DP);
        contentLayout.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        createIconView(context);
        createTextContainer(context);

        // 将内容添加到根布局
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        addView(contentLayout, params);

        // 先设置背景（这会应用到contentLayout）
        setupBackground();
        
        // 设置圆角裁剪和阴影
        setupOutlineAndElevation();

        setClickable(true);
        setFocusable(true);
    }
    
    /**
     * 设置轮廓和阴影
     */
    private void setupOutlineAndElevation() {
        // 设置contentLayout的轮廓提供器以支持圆角阴影
        contentLayout.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPxF(cornerRadiusDp));
            }
        });
        contentLayout.setClipToOutline(true);
        contentLayout.setElevation(dpToPxF(DEFAULT_ELEVATION_DP));
    }

    private void setupBackground() {
        backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
        backgroundDrawable.setColor(cardBackgroundColor);
        backgroundDrawable.setCornerRadius(dpToPxF(cornerRadiusDp));
        updateBackgroundDrawable();
    }

    private void updateBackgroundDrawable() {
        backgroundDrawable.setColor(cardBackgroundColor);
        backgroundDrawable.setCornerRadius(dpToPxF(cornerRadiusDp));
        
        if (rippleEnabled) {
            // 创建独立的mask drawable，避免与content drawable共用同一实例
            GradientDrawable maskDrawable = new GradientDrawable();
            maskDrawable.setShape(GradientDrawable.RECTANGLE);
            maskDrawable.setColor(0xFFFFFFFF);  // mask使用纯色
            maskDrawable.setCornerRadius(dpToPxF(cornerRadiusDp));
            
            RippleDrawable rippleDrawable = new RippleDrawable(
                    ColorStateList.valueOf(rippleColor),
                    backgroundDrawable,
                    maskDrawable
            );
            // 将背景应用到contentLayout而不是根视图，确保阴影效果正常显示
            contentLayout.setBackground(rippleDrawable);
            contentLayout.setClickable(true);
            contentLayout.setFocusable(true);
        } else {
            contentLayout.setBackground(backgroundDrawable);
            contentLayout.setClickable(false);
            contentLayout.setFocusable(false);
        }
        // 根视图透明，不设置背景
        setBackground(null);
    }

    private void createIconView(Context context) {
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        iconParams.setMarginEnd(iconMarginEndPx);
        iconView.setLayoutParams(iconParams);
        contentLayout.addView(iconView);
    }

    private void createTextContainer(Context context) {
        textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f
        );
        textContainer.setLayoutParams(containerParams);

        createTitleView(context);
        createSubtitleView(context);

        contentLayout.addView(textContainer);
    }

    private void createTitleView(Context context) {
        titleView = new TextView(context);
        titleView.setTextColor(defaultTitleColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TITLE_TEXT_SIZE_SP);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(4);
        titleView.setLayoutParams(titleParams);
        textContainer.addView(titleView);
    }

    private void createSubtitleView(Context context) {
        subtitleView = new TextView(context);
        subtitleView.setTextColor(defaultSubtitleColor);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_SUBTITLE_TEXT_SIZE_SP);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setLineSpacing(dpToPxF(DEFAULT_SUBTITLE_LINE_SPACING_DP), 1.0f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        subtitleView.setLayoutParams(descParams);
        subtitleView.setVisibility(GONE);
        textContainer.addView(subtitleView);
    }

    // ==================== 图标配置 ====================

    /**
     * 设置图标（从assets路径）
     */
    public void setIcon(String assetPath) {
        setIconAsset(assetPath);
    }

    /**
     * 设置图标（从assets路径）
     */
    public void setIconAsset(@Nullable String assetPath) {
        if (TextUtils.isEmpty(assetPath)) {
            iconView.setImageDrawable(null);
            return;
        }
        try (InputStream is = getContext().getAssets().open(assetPath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            setIconBitmap(bitmap);
        } catch (IOException e) {
            // 不中断调用链，保留空白图标
            e.printStackTrace();
        }
    }

    /**
     * 设置图标（资源ID）
     */
    public void setIconResource(@DrawableRes int resId) {
        iconView.setImageResource(resId);
    }

    /**
     * 设置图标（Drawable）
     */
    public void setIconDrawable(@Nullable Drawable drawable) {
        iconView.setImageDrawable(drawable);
    }

    /**
     * 设置图标（Bitmap）
     */
    public void setIconBitmap(@Nullable Bitmap bitmap) {
        iconView.setImageBitmap(bitmap);
    }

    /**
     * 设置图标着色
     */
    public void setIconTint(@ColorInt int color) {
        iconView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    /**
     * 清除图标着色
     */
    public void clearIconTint() {
        iconView.clearColorFilter();
    }

    /**
     * 设置图标大小
     */
    public void setIconSizeDp(float sizeDp) {
        iconSizePx = dpToPx(sizeDp);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) iconView.getLayoutParams();
        params.width = iconSizePx;
        params.height = iconSizePx;
        iconView.setLayoutParams(params);
    }

    /**
     * 设置图标与文本的间距
     */
    public void setIconMarginEndDp(float marginDp) {
        iconMarginEndPx = dpToPx(marginDp);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) iconView.getLayoutParams();
        params.setMarginEnd(iconMarginEndPx);
        iconView.setLayoutParams(params);
    }

    // ==================== 文本配置 ====================

    /**
     * 设置主标题
     */
    public void setTitle(@Nullable CharSequence title) {
        titleView.setText(title);
    }

    /**
     * 设置主标题（资源ID）
     */
    public void setTitle(@StringRes int resId) {
        titleView.setText(resId);
    }

    /**
     * 设置副标题
     */
    public void setSubtitle(@Nullable CharSequence subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
    }

    /**
     * 设置副标题（资源ID）
     */
    public void setSubtitle(@StringRes int resId) {
        setSubtitle(getContext().getString(resId));
    }

    /**
     * 设置副标题可见性
     */
    public void setSubtitleVisible(boolean visible) {
        subtitleView.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 设置主标题颜色
     */
    public void setTitleTextColor(@ColorInt int color) {
        titleView.setTextColor(color);
    }

    /**
     * 设置副标题颜色
     */
    public void setSubtitleTextColor(@ColorInt int color) {
        subtitleView.setTextColor(color);
    }

    /**
     * 设置主标题字号
     */
    public void setTitleTextSizeSp(float sizeSp) {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /**
     * 设置副标题字号
     */
    public void setSubtitleTextSizeSp(float sizeSp) {
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /**
     * 设置主标题最大行数
     */
    public void setTitleMaxLines(int maxLines) {
        titleView.setMaxLines(maxLines);
    }

    /**
     * 设置副标题最大行数
     */
    public void setSubtitleMaxLines(int maxLines) {
        subtitleView.setMaxLines(maxLines);
    }

    /**
     * 设置副标题行距
     */
    public void setSubtitleLineSpacing(float addDp, float multiplier) {
        subtitleView.setLineSpacing(dpToPxF(addDp), multiplier);
    }

    // ==================== 布局与样式 ====================

    /**
     * 设置内容内边距（dp）
     */
    public void setContentPaddingDp(float horizontalDp, float verticalDp) {
        int horizontal = dpToPx(horizontalDp);
        int vertical = dpToPx(verticalDp);
        contentLayout.setPadding(horizontal, vertical, horizontal, vertical);
    }

    /**
     * 设置圆角半径
     */
    public void setCornerRadiusDp(float radiusDp) {
        cornerRadiusDp = radiusDp;
        updateBackgroundDrawable();
    }

    /**
     * 设置卡片背景色
     */
    public void setCardBackgroundColor(@ColorInt int color) {
        cardBackgroundColor = color;
        updateBackgroundDrawable();
    }

    /**
     * 设置水波纹颜色
     */
    public void setRippleColor(@ColorInt int color) {
        rippleColor = color;
        if (rippleEnabled) {
            updateBackgroundDrawable();
        }
    }

    /**
     * 启用/禁用水波纹
     */
    public void setUseRipple(boolean enabled) {
        rippleEnabled = enabled;
        updateBackgroundDrawable();
    }

    /**
     * 设置海拔高度
     */
    public void setElevationDp(float elevationDp) {
        contentLayout.setElevation(dpToPxF(elevationDp));
    }
    
    /**
     * 重写点击监听器，确保点击事件和水波纹效果都能正确工作
     */
    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        super.setOnClickListener(listener);
        // 同时设置到contentLayout以确保水波纹效果正确显示
        if (contentLayout != null) {
            contentLayout.setOnClickListener(listener);
        }
    }

    // ==================== 便捷配置 ====================

    /**
     * 快速配置卡片（assets图标）
     */
    public void setupCard(@Nullable String iconAssetPath, @StringRes int titleResId, @StringRes int subtitleResId) {
        setIconAsset(iconAssetPath);
        setTitle(titleResId);
        setSubtitle(subtitleResId);
    }

    /**
     * 快速配置卡片（资源图标）
     */
    public void setupCard(@DrawableRes int iconResId, @StringRes int titleResId, @StringRes int subtitleResId) {
        setIconResource(iconResId);
        setTitle(titleResId);
        setSubtitle(subtitleResId);
    }

    /**
     * 快速配置卡片（自定义内容）
     */
    public void setupCard(@Nullable Drawable iconDrawable, @Nullable CharSequence title, @Nullable CharSequence subtitle) {
        setIconDrawable(iconDrawable);
        setTitle(title);
        setSubtitle(subtitle);
    }

    /**
     * 设置辅助图标背景，例如状态色块
     */
    public void setIconBackground(@DrawableRes int backgroundResId) {
        iconView.setBackground(ContextCompat.getDrawable(getContext(), backgroundResId));
    }

    // ==================== 模糊背景配置 ====================

    /**
     * 启用高斯模糊背景
     */
    public void enableBlurBackground(boolean enable) {
        if (useBlurBackground == enable) {
            return;
        }
        useBlurBackground = enable;

        if (enable) {
            setupBlurBackground();
        } else {
            removeBlurBackground();
        }
    }

    /**
     * 启用高斯模糊背景（带自定义参数）
     */
    public void enableBlurBackground(float blurRadiusDp, int overlayColor) {
        useBlurBackground = true;
        setupBlurBackground(blurRadiusDp, overlayColor);
    }

    /**
     * 设置模糊背景的边框
     *
     * @param borderWidthDp 边框宽度（dp）
     * @param borderColor   边框颜色
     */
    public void setBlurBorder(float borderWidthDp, @ColorInt int borderColor) {
        if (blurBackgroundView != null) {
            blurBackgroundView.setBorderWidth(borderWidthDp);
            blurBackgroundView.setBorderColor(borderColor);
        }
    }

    /**
     * 设置模糊背景的边框宽度
     *
     * @param borderWidthDp 边框宽度（dp）
     */
    public void setBlurBorderWidth(float borderWidthDp) {
        if (blurBackgroundView != null) {
            blurBackgroundView.setBorderWidth(borderWidthDp);
        }
    }

    /**
     * 设置模糊背景的边框颜色
     *
     * @param borderColor 边框颜色
     */
    public void setBlurBorderColor(@ColorInt int borderColor) {
        if (blurBackgroundView != null) {
            blurBackgroundView.setBorderColor(borderColor);
        }
    }

    private void setupBlurBackground() {
        // 使用默认参数：模糊半径15，半透明背景色（alpha=120，约47%透明度）
        int overlayBaseColor = cardBackgroundColor;
        int overlayColor = ColorUtils.setAlphaComponent(overlayBaseColor, 120);
        setupBlurBackground(15f, overlayColor);
    }

    private void setupBlurBackground(float blurRadius, int overlayColor) {
        if (blurContainer != null) {
            // 已经设置过，更新配置
            if (blurBackgroundView != null) {
                blurBackgroundView.setBlurRadius(blurRadius);
                blurBackgroundView.setCornerRadius(cornerRadiusDp);
                blurBackgroundView.setOverlayColor(overlayColor);
            }
            return;
        }

        // 移除原有背景
        contentLayout.setBackground(null);
        setBackground(null);

        // 从原父视图中移除contentLayout
        removeView(contentLayout);

        // 创建模糊容器
        blurContainer = YoBlurBackgroundView.createForCard(getContext(), contentLayout, overlayColor);

        // 获取模糊视图的引用以便后续配置
        findBlurBackgroundView();

        // 默认添加边框效果
        if (blurBackgroundView != null) {
            blurBackgroundView.setBorderWidth(1f); // 1dp边框
            blurBackgroundView.setBorderColor(0x33FFFFFF); // 20%透明白色
        }

        // 将容器添加到当前卡片
        LayoutParams containerParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        addView(blurContainer, containerParams);
    }

    /**
     * 从容器中查找YoBlurBackgroundView
     */
    private void findBlurBackgroundView() {
        if (blurContainer == null) {
            return;
        }

        for (int i = 0; i < blurContainer.getChildCount(); i++) {
            View child = blurContainer.getChildAt(i);
            if (child instanceof YoBlurBackgroundView) {
                blurBackgroundView = (YoBlurBackgroundView) child;
                break;
            }
        }
    }

    private void removeBlurBackground() {
        if (blurContainer == null) {
            return;
        }

        // 移除容器中的内容
        blurContainer.removeView(contentLayout);

        // 移除容器
        removeView(blurContainer);
        blurContainer = null;
        blurBackgroundView = null;

        // 恢复内容布局
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        addView(contentLayout, params);

        // 恢复原有背景
        updateBackgroundDrawable();
    }

    // ==================== 工具方法 ====================

    private int dpToPx(float dp) {
        return Math.round(
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp,
                        getResources().getDisplayMetrics()
                )
        );
    }

    private float dpToPxF(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

}

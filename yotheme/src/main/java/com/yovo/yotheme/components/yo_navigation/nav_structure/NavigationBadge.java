package com.yovo.yotheme.components.yo_navigation.nav_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.NavigationConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航栏徽章组件
 * @note 专门负责徽章的显示和管理，支持文本徽章和圆点徽章
 * @note 单一职责：只处理徽章相关的显示逻辑和状态管理
 */
public class NavigationBadge extends ViewGroup {
    
    /**
     * 徽章配置类
     */
    public static class BadgeConfig {
        public String badgeText = "";
        public boolean showDot = false;
        public boolean visible = false;
        public int backgroundColor = ColorMapper.map(YoThemeColor.ERROR_RED);
        public int textColor = ColorMapper.map(YoThemeColor.WHITE);
        public float textSize = NavigationConstants.BADGE_TEXT_SIZE_SMALL_SP.getFloatValue();
        public float dotSize = NavigationConstants.BADGE_SIZE_DP.getFloatValue();
        public float badgeSize = NavigationConstants.BADGE_LARGE_SIZE_DP.getFloatValue();
        public float minWidth = NavigationConstants.BADGE_MIN_WIDTH_DP.getFloatValue();
        public float padding = NavigationConstants.BADGE_PADDING_DEFAULT_DP.getFloatValue();
        public int maxLength = NavigationConstants.BADGE_MAX_LENGTH.getIntValue();
        public String overflowText = "99+";
        
        public BadgeConfig setBadgeText(String text) {
            this.badgeText = text != null ? text : "";
            this.showDot = TextUtils.isEmpty(this.badgeText);
            this.visible = !TextUtils.isEmpty(this.badgeText) || this.showDot;
            return this;
        }
        
        public BadgeConfig setShowDot(boolean showDot) {
            this.showDot = showDot;
            if (showDot) {
                this.badgeText = "";
                this.visible = true;
            }
            return this;
        }
        
        public BadgeConfig setVisible(boolean visible) {
            this.visible = visible;
            return this;
        }
        
        public BadgeConfig setBackgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }
        
        public BadgeConfig setTextColor(int color) {
            this.textColor = color;
            return this;
        }
        
        public BadgeConfig setTextSize(float sizeSp) {
            this.textSize = sizeSp;
            return this;
        }
        
        public BadgeConfig setDotSize(float sizeDp) {
            this.dotSize = sizeDp;
            return this;
        }
        
        public BadgeConfig setBadgeSize(float sizeDp) {
            this.badgeSize = sizeDp;
            return this;
        }
        
        public BadgeConfig setMinWidth(float widthDp) {
            this.minWidth = widthDp;
            return this;
        }
        
        public BadgeConfig setPadding(float paddingDp) {
            this.padding = paddingDp;
            return this;
        }
        
        public BadgeConfig setMaxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }
        
        public BadgeConfig setOverflowText(String overflowText) {
            this.overflowText = overflowText;
            return this;
        }
    }
    
    private Context context;
    private BadgeConfig config;
    private Paint backgroundPaint;
    private Paint textPaint;
    private String displayText = "";
    
    public NavigationBadge(Context context) {
        this(context, null);
    }
    
    public NavigationBadge(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public NavigationBadge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }
    
    private void init() {
        // 设置默认配置
        config = new BadgeConfig();
        
        // 初始化画笔
        initPaints();
        
        // 设置绘制属性
        setWillNotDraw(false);
        setVisibility(config.visible ? VISIBLE : GONE);
    }
    
    private void initPaints() {
        // 背景画笔
        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setColor(config.backgroundColor);
        
        // 文本画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(config.textColor);
        textPaint.setTextSize(spToPx(config.textSize));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }
    
    /**
     * 设置徽章配置
     * @param config 徽章配置
     */
    public void setBadgeConfig(BadgeConfig config) {
        this.config = config;
        initPaints();
        updateDisplayText();
        updateVisibility();
        invalidate();
        requestLayout();
    }
    
    /**
     * 显示徽章
     * @param text 徽章文本，为null或空时显示圆点
     */
    public void showBadge(String text) {
        if (TextUtils.isEmpty(text)) {
            config.setShowDot(true);
        } else {
            config.setBadgeText(text);
        }
        
        updateDisplayText();
        updateVisibility();
        invalidate();
        requestLayout();
    }
    
    /**
     * 隐藏徽章
     */
    public void hideBadge() {
        config.setVisible(false);
        updateVisibility();
    }
    
    /**
     * 设置徽章文本
     * @param text 徽章文本
     */
    public void setBadgeText(String text) {
        config.setBadgeText(text);
        updateDisplayText();
        updateVisibility();
        invalidate();
        requestLayout();
    }
    
    /**
     * 设置为圆点徽章
     */
    public void showDotBadge() {
        config.setShowDot(true);
        updateVisibility();
        invalidate();
        requestLayout();
    }
    
    /**
     * 设置徽章颜色
     * @param backgroundColor 背景色
     * @param textColor 文本颜色
     */
    public void setBadgeColors(int backgroundColor, int textColor) {
        config.setBackgroundColor(backgroundColor);
        config.setTextColor(textColor);
        initPaints();
        invalidate();
    }
    
    /**
     * 更新显示文本
     */
    private void updateDisplayText() {
        if (config.showDot) {
            displayText = "";
            return;
        }
        
        if (TextUtils.isEmpty(config.badgeText)) {
            displayText = "";
            return;
        }
        
        // 处理数字溢出
        try {
            int number = Integer.parseInt(config.badgeText);
            if (number > config.maxLength) {
                displayText = config.overflowText;
            } else {
                displayText = config.badgeText;
            }
        } catch (NumberFormatException e) {
            // 非数字文本，直接使用
            int preview = NavigationConstants.BADGE_MAX_TEXT_PREVIEW_CHARS.getIntValue();
            displayText = config.badgeText.length() > preview 
                ? config.badgeText.substring(0, preview) + "..." 
                : config.badgeText;
        }
    }
    
    /**
     * 更新可见性
     */
    private void updateVisibility() {
        setVisibility(config.visible ? VISIBLE : GONE);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (!config.visible) return;
        
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        
        if (config.showDot) {
            drawDotBadge(canvas, centerX, centerY);
        } else {
            drawTextBadge(canvas, centerX, centerY);
        }
    }
    
    /**
     * 绘制圆点徽章
     */
    private void drawDotBadge(Canvas canvas, int centerX, int centerY) {
        float radius = dpToPx(config.dotSize) / 2f;
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint);
    }
    
    /**
     * 绘制文本徽章
     */
    private void drawTextBadge(Canvas canvas, int centerX, int centerY) {
        if (TextUtils.isEmpty(displayText)) return;
        
        // 计算徽章尺寸
        float textWidth = textPaint.measureText(displayText);
        float paddingPx = dpToPx(config.padding);
        float minWidthPx = dpToPx(config.minWidth);
        
        float badgeWidth = Math.max(textWidth + paddingPx * 2, minWidthPx);
        float badgeHeight = dpToPx(config.badgeSize);
        float radius = badgeHeight / 2f;
        
        // 绘制背景
        float left = centerX - badgeWidth / 2f;
        float top = centerY - badgeHeight / 2f;
        float right = centerX + badgeWidth / 2f;
        float bottom = centerY + badgeHeight / 2f;
        
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, backgroundPaint);
        
        // 绘制文本
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float textY = centerY - (fontMetrics.top + fontMetrics.bottom) / 2f;
        canvas.drawText(displayText, centerX, textY, textPaint);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!config.visible) {
            setMeasuredDimension(0, 0);
            return;
        }
        
        int width, height;
        
        if (config.showDot) {
            int size = (int) dpToPx(config.dotSize);
            width = height = size;
        } else {
            if (TextUtils.isEmpty(displayText)) {
                setMeasuredDimension(0, 0);
                return;
            }
            
            float textWidth = textPaint.measureText(displayText);
            float paddingPx = dpToPx(config.padding);
            float minWidthPx = dpToPx(config.minWidth);
            
            width = (int) Math.max(textWidth + paddingPx * 2, minWidthPx);
            height = (int) dpToPx(config.badgeSize);
        }
        
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // NavigationBadge 不包含子视图，无需布局
    }
    
    /**
     * 获取当前徽章配置
     * @return 徽章配置
     */
    public BadgeConfig getBadgeConfig() {
        return config;
    }
    
    /**
     * 是否显示徽章
     * @return 是否显示
     */
    public boolean isBadgeVisible() {
        return config.visible;
    }
    
    /**
     * 是否为圆点徽章
     * @return 是否为圆点
     */
    public boolean isDotBadge() {
        return config.showDot;
    }
    
    /**
     * 获取徽章文本
     * @return 徽章文本
     */
    public String getBadgeText() {
        return config.badgeText;
    }
    
    /**
     * dp转px工具方法
     */
    private float dpToPx(float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
    
    /**
     * sp转px工具方法
     */
    private float spToPx(float sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.getResources().getDisplayMetrics()
        );
    }
    
    /**
     * 预设徽章配置
     */
    public static class Presets {
        
        /**
         * 标准文本徽章配置
         */
        public static BadgeConfig standardText() {
            return new BadgeConfig();
        }
        
        /**
         * 圆点徽章配置
         */
        public static BadgeConfig dot() {
            return new BadgeConfig()
                    .setShowDot(true);
        }
        
        /**
         * 数字徽章配置
         */
        public static BadgeConfig number() {
            return new BadgeConfig()
                    .setMaxLength(99)
                    .setOverflowText("99+");
        }
        
        /**
         * 小尺寸徽章配置
         */
        public static BadgeConfig small() {
            return new BadgeConfig()
                    .setTextSize(8f)
                    .setDotSize(6f)
                    .setBadgeSize(14f)
                    .setPadding(2f);
        }
        
        /**
         * 大尺寸徽章配置
         */
        public static BadgeConfig large() {
            return new BadgeConfig()
                    .setTextSize(12f)
                    .setDotSize(10f)
                    .setBadgeSize(20f)
                    .setPadding(6f);
        }
        
        /**
         * 警告色徽章配置
         */
        public static BadgeConfig warning() {
            return new BadgeConfig()
                    .setBackgroundColor(ColorMapper.map(YoThemeColor.WARNING));
        }
        
        /**
         * 成功色徽章配置
         */
        public static BadgeConfig success() {
            return new BadgeConfig()
                    .setBackgroundColor(ColorMapper.map(YoThemeColor.SUCCESS_GREEN));
        }
        
        /**
         * 信息色徽章配置
         */
        public static BadgeConfig info() {
            return new BadgeConfig()
                    .setBackgroundColor(ColorMapper.map(YoThemeColor.BRAND));
        }
    }
}

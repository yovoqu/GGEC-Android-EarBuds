package com.yovo.yotheme.components.yo_navigation.nav_structure;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatTextView;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.NavigationConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航栏标题组件
 * @note 专门负责标题文本的显示和状态管理，包括颜色、字体样式等
 * @note 单一职责：只处理标题文本相关的显示逻辑和状态切换
 */
public class NavigationTitle extends AppCompatTextView {
    
    /**
     * 标题配置类
     */
    public static class TitleConfig {
        public String title = "";
        public float textSize = NavigationConstants.TEXT_SIZE_DEFAULT_SP.getFloatValue();
        public int normalColor = ColorMapper.map(YoThemeColor.FONT_SECONDARY);
        public int selectedColor = ColorMapper.map(YoThemeColor.BRAND);
        public int disabledColor = ColorMapper.map(YoThemeColor.FONT_FOURTH);
        public int maxLines = NavigationConstants.TEXT_MAX_LINES.getIntValue();
        public TextUtils.TruncateAt ellipsize = TextUtils.TruncateAt.END;
        public boolean boldWhenSelected = true;
        public int gravity = Gravity.CENTER;
        
        public TitleConfig setTitle(String title) {
            this.title = title;
            return this;
        }
        
        public TitleConfig setTextSize(float sizeSp) {
            this.textSize = sizeSp;
            return this;
        }
        
        public TitleConfig setNormalColor(int color) {
            this.normalColor = color;
            return this;
        }
        
        public TitleConfig setSelectedColor(int color) {
            this.selectedColor = color;
            return this;
        }
        
        public TitleConfig setDisabledColor(int color) {
            this.disabledColor = color;
            return this;
        }
        
        public TitleConfig setMaxLines(int maxLines) {
            this.maxLines = maxLines;
            return this;
        }
        
        public TitleConfig setEllipsize(TextUtils.TruncateAt ellipsize) {
            this.ellipsize = ellipsize;
            return this;
        }
        
        public TitleConfig setBoldWhenSelected(boolean bold) {
            this.boldWhenSelected = bold;
            return this;
        }
        
        public TitleConfig setGravity(int gravity) {
            this.gravity = gravity;
            return this;
        }
        
        /**
         * 设置为小尺寸文本
         */
        public TitleConfig asSmall() {
            this.textSize = 10f;
            return this;
        }
        
        /**
         * 设置为大尺寸文本
         */
        public TitleConfig asLarge() {
            this.textSize = 16f;
            return this;
        }
        
        /**
         * 设置为品牌色主题
         */
        public TitleConfig asBrandTheme() {
            this.selectedColor = ColorMapper.map(YoThemeColor.BRAND);
            this.normalColor = ColorMapper.map(YoThemeColor.FONT_SECONDARY);
            return this;
        }
    }
    
    private Context context;
    private TitleConfig config;
    private boolean isSelected = false;
    private boolean isEnabled = true;
    private Typeface originalTypeface;
    
    public NavigationTitle(Context context) {
        this(context, null);
    }
    
    public NavigationTitle(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public NavigationTitle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }
    
    private void init() {
        // 设置默认配置
        config = new TitleConfig();
        
        // 保存原始字体
        originalTypeface = getTypeface();
        // 去除字体内置上下留白，缩小与图标之间的视觉间距
        setIncludeFontPadding(false);
        
        // 应用默认配置
        applyConfig();
    }
    
    /**
     * 设置标题配置
     * @param config 标题配置
     */
    public void setTitleConfig(TitleConfig config) {
        this.config = config;
        applyConfig();
    }
    
    /**
     * 设置标题文本（简化接口）
     * @param title 标题文本
     */
    public void setTitle(String title) {
        config.title = title != null ? title : "";
        setText(config.title);
    }
    
    /**
     * 设置文本大小
     * @param sizeSp 文本大小（sp）
     */
    public void setTitleTextSize(float sizeSp) {
        config.textSize = sizeSp;
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }
    
    /**
     * 设置文本颜色
     * @param normalColor 普通状态颜色
     * @param selectedColor 选中状态颜色
     */
    public void setTitleColors(int normalColor, int selectedColor) {
        config.normalColor = normalColor;
        config.selectedColor = selectedColor;
        updateTextColor();
    }
    
    /**
     * 根据状态更新标题
     * @param state 导航状态
     */
    public void updateState(NavigationStateManager.NavigationState state) {
        boolean needsUpdate = false;
        
        if (this.isSelected != state.isSelected) {
            this.isSelected = state.isSelected;
            needsUpdate = true;
        }
        
        if (this.isEnabled != state.isEnabled) {
            this.isEnabled = state.isEnabled;
            needsUpdate = true;
        }
        
        if (needsUpdate) {
            updateTextAppearance();
        }
    }
    
    /**
     * 创建文本的颜色动画过渡
     * @param fromColor 起始颜色
     * @param toColor 目标颜色
     * @param progress 动画进度 (0.0 - 1.0)
     */
    public void setColorTransition(int fromColor, int toColor, float progress) {
        int interpolatedColor = interpolateColor(fromColor, toColor, progress);
        setTextColor(interpolatedColor);
		
		// 根据目标状态和进度更新字体样式：只有目标为选中时才可能加粗
		if (config.boldWhenSelected) {
			boolean targetIsSelected = (toColor == config.selectedColor);
			updateTypefaceByProgress(progress, targetIsSelected);
		}
    }
    
    /**
     * 应用配置
     */
    private void applyConfig() {
        setText(config.title);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSize);
        setMaxLines(config.maxLines);
        setEllipsize(config.ellipsize);
        setGravity(config.gravity);
        updateTextAppearance();
    }
    
    /**
     * 更新文本外观
     */
    private void updateTextAppearance() {
        updateTextColor();
        updateTextStyle();
    }
    
    /**
     * 更新文本颜色
     */
    private void updateTextColor() {
        int targetColor;
        
        if (!isEnabled) {
            targetColor = config.disabledColor;
        } else if (isSelected) {
            targetColor = config.selectedColor;
        } else {
            targetColor = config.normalColor;
        }
        
        setTextColor(targetColor);
    }
    
    /**
     * 更新文本样式（字体粗细）
     */
    private void updateTextStyle() {
        if (!config.boldWhenSelected) return;
        
        if (isSelected && isEnabled) {
            setTypeface(originalTypeface, Typeface.BOLD);
        } else {
            setTypeface(originalTypeface, Typeface.NORMAL);
        }
    }
    
    /**
     * 根据动画进度更新字体样式
     * @param progress 动画进度 (0.0 - 1.0)
     */
	private void updateTypefaceByProgress(float progress, boolean targetIsSelected) {
		if (!config.boldWhenSelected) return;
		
		// 禁用状态下始终不加粗
		if (!isEnabled) {
			setTypeface(originalTypeface, Typeface.NORMAL);
			return;
		}
		
		// 只有当目标状态为选中时，才在过渡后半段呈现加粗；
		// 目标为未选中时，动画过程中与结束都保持不加粗
		boolean shouldBeBold = targetIsSelected && progress > 0.5f;
		boolean isCurrentlyBold = getTypeface() != null && getTypeface().isBold();
		
		if (shouldBeBold != isCurrentlyBold) {
			if (shouldBeBold) {
				setTypeface(originalTypeface, Typeface.BOLD);
			} else {
				setTypeface(originalTypeface, Typeface.NORMAL);
			}
		}
	}
    
    /**
     * 颜色插值计算
     */
    private int interpolateColor(int startColor, int endColor, float ratio) {
        int startA = (startColor >> 24) & 0xff;
        int startR = (startColor >> 16) & 0xff;
        int startG = (startColor >> 8) & 0xff;
        int startB = startColor & 0xff;
        
        int endA = (endColor >> 24) & 0xff;
        int endR = (endColor >> 16) & 0xff;
        int endG = (endColor >> 8) & 0xff;
        int endB = endColor & 0xff;
        
        return ((int) (startA + (endA - startA) * ratio) << 24) |
               ((int) (startR + (endR - startR) * ratio) << 16) |
               ((int) (startG + (endG - startG) * ratio) << 8) |
               (int) (startB + (endB - startB) * ratio);
    }
    
    /**
     * 获取当前标题配置
     * @return 标题配置
     */
    public TitleConfig getTitleConfig() {
        return config;
    }
    
    /**
     * 是否为选中状态
     * @return 是否选中
     */
    public boolean isTitleSelected() {
        return isSelected;
    }
    
    /**
     * 是否为启用状态
     * @return 是否启用
     */
    public boolean isTitleEnabled() {
        return isEnabled;
    }
    
    /**
     * 获取标题文本
     * @return 标题文本
     */
    public String getTitleText() {
        return config.title;
    }
    
    /**
     * 预设标题配置
     */
    public static class Presets {
        
        /**
         * 标准标题配置
         */
        public static TitleConfig standard() {
            return new TitleConfig();
        }
        
        /**
         * 小尺寸标题配置
         */
        public static TitleConfig small() {
            return new TitleConfig()
                    .setTextSize(NavigationConstants.TEXT_SIZE_SMALL_SP.getFloatValue())
                    .setMaxLines(NavigationConstants.TEXT_SINGLE_LINE.getIntValue());
        }
        
        /**
         * 大尺寸标题配置
         */
        public static TitleConfig large() {
            return new TitleConfig()
                    .setTextSize(NavigationConstants.TEXT_SIZE_XL_SP.getFloatValue())
                    .setMaxLines(NavigationConstants.TEXT_MAX_LINES.getIntValue());
        }
        
        /**
         * 品牌色主题标题配置
         */
        public static TitleConfig brandTheme() {
            return new TitleConfig()
                    .setSelectedColor(ColorMapper.map(YoThemeColor.BRAND))
                    .setBoldWhenSelected(true);
        }
        
        /**
         * 单行标题配置
         */
        public static TitleConfig singleLine() {
            return new TitleConfig()
                    .setMaxLines(NavigationConstants.TEXT_SINGLE_LINE.getIntValue())
                    .setEllipsize(TextUtils.TruncateAt.END);
        }
        
        /**
         * 多行标题配置
         */
        public static TitleConfig multiLine() {
            return new TitleConfig()
                    .setMaxLines(NavigationConstants.TEXT_MAX_LINES_EXTENDED.getIntValue())
                    .setEllipsize(TextUtils.TruncateAt.END);
        }
        
        /**
         * 无粗体变化标题配置
         */
        public static TitleConfig noBold() {
            return new TitleConfig()
                    .setBoldWhenSelected(false);
        }
    }
}

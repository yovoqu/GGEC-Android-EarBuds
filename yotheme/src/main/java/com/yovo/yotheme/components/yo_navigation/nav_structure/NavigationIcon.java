package com.yovo.yotheme.components.yo_navigation.nav_structure;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;
import android.view.ViewGroup;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.NavigationConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航栏图标组件
 * @note 专门负责图标的显示和状态管理，支持单色、双色、多色图标
 * @note 单一职责：只处理图标相关的显示逻辑和状态切换
 */
public class NavigationIcon extends AppCompatImageView {
    
    /**
     * 图标配置类
     */
    public static class IconConfig {
        public Drawable normalIcon;
        public Drawable selectedIcon;
        public int iconType = NavigationConstants.ICON_TYPE_SINGLE_COLOR.getIntValue();
        public int normalColor = ColorMapper.map(YoThemeColor.ICON_SECONDARY);
        public int selectedColor = ColorMapper.map(YoThemeColor.BRAND);
        public int secondaryColor = ColorMapper.map(YoThemeColor.ICON_TERTIARY);
        public int iconSize = NavigationConstants.ICON_SIZE_DEFAULT_DP.getIntValue();
        
        public IconConfig setNormalIcon(Drawable icon) {
            this.normalIcon = icon;
            return this;
        }
        
        public IconConfig setSelectedIcon(Drawable icon) {
            this.selectedIcon = icon;
            this.iconType = NavigationConstants.ICON_TYPE_MULTI_COLOR.getIntValue();
            return this;
        }
        
        public IconConfig setIconType(int type) {
            this.iconType = type;
            return this;
        }
        
        public IconConfig setNormalColor(int color) {
            this.normalColor = color;
            return this;
        }
        
        public IconConfig setSelectedColor(int color) {
            this.selectedColor = color;
            return this;
        }
        
        public IconConfig setSecondaryColor(int color) {
            this.secondaryColor = color;
            return this;
        }
        
        public IconConfig setIconSize(int sizeDp) {
            this.iconSize = sizeDp;
            return this;
        }
        
        /**
         * 设置为单色图标
         */
        public IconConfig asSingleColor(Drawable icon, int normalColor, int selectedColor) {
            this.normalIcon = icon;
            this.iconType = NavigationConstants.ICON_TYPE_SINGLE_COLOR.getIntValue();
            this.normalColor = normalColor;
            this.selectedColor = selectedColor;
            return this;
        }
        
        /**
         * 设置为多色图标
         */
        public IconConfig asMultiColor(Drawable normalIcon, Drawable selectedIcon) {
            this.normalIcon = normalIcon;
            this.selectedIcon = selectedIcon;
            this.iconType = NavigationConstants.ICON_TYPE_MULTI_COLOR.getIntValue();
            return this;
        }
    }
    
    private Context context;
    private IconConfig config;
    private boolean isSelected = false;
    private boolean isEnabled = true;
    
    public NavigationIcon(Context context) {
        this(context, null);
    }
    
    public NavigationIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public NavigationIcon(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }
    
    private void init() {
        // 设置默认配置
        config = new IconConfig();
        
        // 设置默认属性
        setScaleType(ScaleType.CENTER_INSIDE);
        updateIconSize();
    }
    
    /**
     * 设置图标配置
     * @param config 图标配置
     */
    public void setIconConfig(IconConfig config) {
        this.config = config;
        updateIconSize();
        updateIconState();
    }
    
    /**
     * 设置图标（简化接口）
     * @param iconRes 图标资源（可以是Drawable或资源ID）
     */
    public void setIcon(Object iconRes) {
        if (iconRes instanceof Drawable) {
            config.normalIcon = (Drawable) iconRes;
        } else if (iconRes instanceof Integer) {
            config.normalIcon = context.getResources().getDrawable((Integer) iconRes, null);
        }
        updateIconState();
    }
    
    /**
     * 设置多色图标
     * @param normalIcon 普通状态图标
     * @param selectedIcon 选中状态图标
     */
    public void setMultiColorIcon(Drawable normalIcon, Drawable selectedIcon) {
        config.normalIcon = normalIcon;
        config.selectedIcon = selectedIcon;
        config.iconType = NavigationConstants.ICON_TYPE_MULTI_COLOR.getIntValue();
        updateIconState();
    }
    
    /**
     * 设置图标类型
     * @param iconType 图标类型：1-单色，2-双色，3-多色
     */
    public void setIconType(int iconType) {
        config.iconType = iconType;
        updateIconState();
    }
    
    /**
     * 设置图标大小
     * @param sizeDp 图标大小（dp）
     */
    public void setIconSize(int sizeDp) {
        config.iconSize = sizeDp;
        updateIconSize();
    }
    
    /**
     * 设置图标颜色
     * @param normalColor 普通状态颜色
     * @param selectedColor 选中状态颜色
     */
    public void setIconColors(int normalColor, int selectedColor) {
        config.normalColor = normalColor;
        config.selectedColor = selectedColor;
        updateIconState();
    }
    
    /**
     * 根据状态更新图标
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
            updateIconState();
        }
    }
    
    /**
     * 更新图标状态
     */
    private void updateIconState() {
        if (!isEnabled) {
            applyDisabledState();
            return;
        }
        
        switch (config.iconType) {
            case 1: // 单色图标
                applySingleColorIcon();
                break;
            case 2: // 双色图标
                applyDualColorIcon();
                break;
            case 3: // 多色图标
                applyMultiColorIcon();
                break;
            default:
                applySingleColorIcon();
                break;
        }
    }
    
    /**
     * 应用单色图标状态
     */
    private void applySingleColorIcon() {
        if (config.normalIcon != null) {
            setImageDrawable(config.normalIcon);
            int targetColor = isSelected ? config.selectedColor : config.normalColor;
            setColorFilter(new PorterDuffColorFilter(targetColor, PorterDuff.Mode.SRC_IN));
        }
    }
    
    /**
     * 应用双色图标状态
     * TODO: 需要根据具体需求实现双色图标逻辑
     */
    private void applyDualColorIcon() {
        // 暂时使用单色逻辑
        applySingleColorIcon();
    }
    
    /**
     * 应用多色图标状态
     */
    private void applyMultiColorIcon() {
        Drawable targetDrawable = isSelected ? config.selectedIcon : config.normalIcon;
        
        if (targetDrawable != null) {
            setImageDrawable(targetDrawable);
            clearColorFilter(); // 多色图标不应用颜色滤镜
        } else if (config.normalIcon != null) {
            // 如果没有选中状态图标，降级为单色处理
            applySingleColorIcon();
        }
    }
    
    /**
     * 应用禁用状态
     */
    private void applyDisabledState() {
        if (config.normalIcon != null) {
            setImageDrawable(config.normalIcon);
            int disabledColor = ColorMapper.map(YoThemeColor.ICON_FOURTH);
            setColorFilter(new PorterDuffColorFilter(disabledColor, PorterDuff.Mode.SRC_IN));
        }
    }
    
    /**
     * 更新图标尺寸
     */
    private void updateIconSize() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            int sizePx = dpToPx(config.iconSize);
            params.width = sizePx;
            params.height = sizePx;
            requestLayout();
        }
    }
    
    /**
     * 公开的更新尺寸方法，用于在添加到父容器后调用
     */
    public void updateSize() {
        updateIconSize();
    }
    
    /**
     * 创建图标的颜色动画过渡
     * @param fromColor 起始颜色
     * @param toColor 目标颜色
     * @param progress 动画进度 (0.0 - 1.0)
     */
    public void setColorTransition(int fromColor, int toColor, float progress) {
        if (config.iconType == NavigationConstants.ICON_TYPE_MULTI_COLOR.getIntValue()) {
            // 多色图标直接切换，不支持颜色过渡
            updateIconState();
            return;
        }
        
        int interpolatedColor = interpolateColor(fromColor, toColor, progress);
        setColorFilter(new PorterDuffColorFilter(interpolatedColor, PorterDuff.Mode.SRC_IN));
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
     * 获取当前图标配置
     * @return 图标配置
     */
    public IconConfig getIconConfig() {
        return config;
    }
    
    /**
     * 是否为选中状态
     * @return 是否选中
     */
    public boolean isIconSelected() {
        return isSelected;
    }
    
    /**
     * 是否为启用状态
     * @return 是否启用
     */
    public boolean isIconEnabled() {
        return isEnabled;
    }
    
    /**
     * dp转px工具方法
     */
    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
    
    /**
     * 预设图标配置
     */
    public static class Presets {
        
        /**
         * 标准单色图标配置
         */
        public static IconConfig standardSingleColor(Drawable icon) {
            return new IconConfig()
                    .setNormalIcon(icon)
                    .setIconType(NavigationConstants.ICON_TYPE_SINGLE_COLOR.getIntValue());
        }
        
        /**
         * 品牌色单色图标配置
         */
        public static IconConfig brandSingleColor(Drawable icon) {
            return new IconConfig()
                    .setNormalIcon(icon)
                    .setIconType(NavigationConstants.ICON_TYPE_SINGLE_COLOR.getIntValue())
                    .setSelectedColor(ColorMapper.map(YoThemeColor.BRAND));
        }
        
        /**
         * 标准多色图标配置
         */
        public static IconConfig standardMultiColor(Drawable normalIcon, Drawable selectedIcon) {
            return new IconConfig()
                    .setNormalIcon(normalIcon)
                    .setSelectedIcon(selectedIcon)
                    .setIconType(NavigationConstants.ICON_TYPE_MULTI_COLOR.getIntValue());
        }
        
        /**
         * 小尺寸图标配置
         */
        public static IconConfig smallSize(Drawable icon) {
            return new IconConfig()
                    .setNormalIcon(icon)
                    .setIconSize(NavigationConstants.ICON_SIZE_XS_DP.getIntValue());
        }
        
        /**
         * 大尺寸图标配置
         */
        public static IconConfig largeSize(Drawable icon) {
            return new IconConfig()
                    .setNormalIcon(icon)
                    .setIconSize(NavigationConstants.ICON_SIZE_XL_DP.getIntValue());
        }
    }
}

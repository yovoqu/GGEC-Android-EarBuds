package com.yovo.yotheme.components.yo_menu.menu_structure;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.MenuConstants;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description 菜单背景管理器
 * @note 负责管理菜单的背景、圆角、裁剪和阴影效果
 */
public class MenuBackgroundManager {
    
    private final Context context;
    private final FrameLayout outerContainer;      // 外层容器（用于阴影）
    private final FrameLayout contentContainer;    // 内容容器（用于背景和裁剪）
    
    private boolean cardBackgroundEnabled = true;
    private float cornerRadius;
    private int shadowPadding;
    
    public MenuBackgroundManager(Context context, FrameLayout outerContainer, FrameLayout contentContainer) {
        this.context = context;
        this.outerContainer = outerContainer;
        this.contentContainer = contentContainer;
        
        // 初始化尺寸参数
        this.cornerRadius = dpToPx(MenuConstants.MENU_CARD_CORNER_RADIUS_DP.getFloatValue());
        this.shadowPadding = dpToPx(MenuConstants.MENU_SHADOW_PADDING_DP.getIntValue());
        
        // 初始化外层容器
        initOuterContainer();
    }
    
    /**
     * 初始化外层容器（用于阴影）
     */
    private void initOuterContainer() {
        // 为阴影效果设置外层容器的padding（容纳阴影空间）
        outerContainer.setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding);
        
        // 设置阴影效果（elevation）
        outerContainer.setElevation(dpToPx(MenuConstants.MENU_ELEVATION_DP.getFloatValue()));
        
        // 外层容器不裁剪，以显示阴影
        outerContainer.setClipToPadding(false);
        outerContainer.setClipChildren(false);
    }
    
    /**
     * 初始化内容容器
     */
    public void initContentContainer() {
        contentContainer.setClipToPadding(false);
        contentContainer.setClipChildren(false);
    }
    
    /**
     * 应用卡片背景和裁剪效果
     */
    public void applyBackground() {
        if (!cardBackgroundEnabled) {
            clearBackground();
            return;
        }
        
        // 设置圆角背景
        float[] radii = {
            cornerRadius, cornerRadius, 
            cornerRadius, cornerRadius, 
            cornerRadius, cornerRadius, 
            cornerRadius, cornerRadius
        };
        RoundRectShape roundRectShape = new RoundRectShape(radii, null, null);
        ShapeDrawable backgroundShape = new ShapeDrawable(roundRectShape);
        backgroundShape.getPaint().setColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));
        
        // 背景设置在contentContainer上
        contentContainer.setBackground(backgroundShape);
        
        // 重新设置阴影
        outerContainer.setElevation(dpToPx(MenuConstants.MENU_ELEVATION_DP.getFloatValue()));
        
        // 应用裁剪
        applyClipping();
    }
    
    /**
     * 清除背景和阴影
     */
    private void clearBackground() {
        contentContainer.setBackground(null);
        outerContainer.setElevation(0);
        contentContainer.setClipToOutline(false);
        outerContainer.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    }
    
    /**
     * 应用圆角裁剪效果
     */
    private void applyClipping() {
        if (!cardBackgroundEnabled) {
            contentContainer.setClipToOutline(false);
            outerContainer.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            return;
        }
        
        // 为contentContainer设置轮廓提供者用于裁剪内容
        contentContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        
        // contentContainer启用裁剪，确保滚动内容和滚动条都在圆角内
        contentContainer.setClipToOutline(true);
        
        // 为外层容器设置轮廓提供者用于阴影绘制
        outerContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(
                    shadowPadding, 
                    shadowPadding, 
                    view.getWidth() - shadowPadding, 
                    view.getHeight() - shadowPadding, 
                    cornerRadius
                );
            }
        });
        
        // 外层容器不启用裁剪，以显示阴影
        outerContainer.setClipToOutline(false);
    }
    
    /**
     * 设置是否启用卡片背景
     */
    public void setCardBackgroundEnabled(boolean enabled) {
        this.cardBackgroundEnabled = enabled;
        applyBackground();
    }
    
    /**
     * 获取是否启用卡片背景
     */
    public boolean isCardBackgroundEnabled() {
        return cardBackgroundEnabled;
    }
    
    /**
     * 获取阴影padding
     */
    public int getShadowPadding() {
        return shadowPadding;
    }
    
    /**
     * dp转px
     */
    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
        );
    }
}


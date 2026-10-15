package com.yovo.yotheme.components.yo_menu.menu_structure;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.PopupWindow;

import com.yovo.yotheme.constants.MenuConstants;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description 菜单弹窗定位器
 * @note 负责管理PopupWindow的定位和显示
 */
public class MenuPopupPositioner {
    
    private final Context context;
    
    private int popupWidthDp = MenuConstants.POPUP_DEFAULT_WIDTH_DP.getIntValue();
    private int popupYOffsetDp = MenuConstants.POPUP_Y_OFFSET_DP.getIntValue();
    private int shadowPadding;
    
    public MenuPopupPositioner(Context context, int shadowPadding) {
        this.context = context;
        this.shadowPadding = shadowPadding;
    }
    
    /**
     * 显示为右对齐的下拉菜单
     * 菜单上边界紧贴锚点下边界，菜单右边界与锚点右边界对齐
     * 
     * @param popupWindow PopupWindow实例
     * @param anchorView 锚点视图
     */
    public void showAsDropDownRightAligned(PopupWindow popupWindow, View anchorView) {
        int anchorWidth = anchorView.getWidth();
        int menuWidthPx = dpToPx(popupWidthDp);
        
        // showAsDropDown 默认从 anchor 左下角开始定位
        // 考虑阴影padding：菜单实际宽度 = menuWidthPx + shadowPadding * 2
        // 为了使菜单内容（不含阴影）右边与 anchor 右边对齐：
        // xOffset = anchorWidth - menuWidthPx - shadowPadding * 2 + shadowPadding
        //         = anchorWidth - menuWidthPx - shadowPadding
        int xOffset = anchorWidth - menuWidthPx - shadowPadding;
        
        // yOffset：减去shadowPadding，使菜单内容上边与anchor下边紧贴
        int yOffset = dpToPx(popupYOffsetDp) - shadowPadding;
        
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
    }
    
    /**
     * 设置Popup宽度（dp）
     */
    public void setPopupWidthDp(int widthDp) {
        this.popupWidthDp = widthDp;
    }
    
    /**
     * 获取Popup宽度（dp）
     */
    public int getPopupWidthDp() {
        return popupWidthDp;
    }
    
    /**
     * 设置Popup Y轴偏移（dp）
     */
    public void setPopupYOffsetDp(int yOffsetDp) {
        this.popupYOffsetDp = yOffsetDp;
    }
    
    /**
     * 获取Popup Y轴偏移（dp）
     */
    public int getPopupYOffsetDp() {
        return popupYOffsetDp;
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


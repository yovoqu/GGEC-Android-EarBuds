package com.yovo.yotheme.components.yo_menu.menu_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.MenuConstants;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description MenuDividerRenderer 菜单分隔线绘制器
 * @note 参考列表组件的分隔线设计
 */
public class MenuDividerRenderer {
    private final Paint dividerPaint;
    private final int dividerHeightPx;

    public MenuDividerRenderer(Context context) {
        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setColor(ColorMapper.map(YoThemeColor.COMP_DIVIDER));
        dividerHeightPx = MenuConstants.DIVIDER_HEIGHT_PX.getIntValue();
    }

    public void drawDividers(ViewGroup parent, Canvas canvas) {
        int childCount = parent.getChildCount();

        for (int i = 0; i < childCount - 1; i++) { 
            MenuItemContainer child = (MenuItemContainer) parent.getChildAt(i);

            // 直接使用文本区域的边界，getTextStartXInParent 和 getTextEndXInParent 
            // 已经计算了所有的 padding，分隔线应该完全对齐文本
            int left = child.getTextStartXInParent();
            int right = child.getTextEndXInParent();
            int top = child.getBottom();
            
            if (right > left) {
                canvas.drawRect(left, top, right, top + dividerHeightPx, dividerPaint);
            }
        }
    }
}


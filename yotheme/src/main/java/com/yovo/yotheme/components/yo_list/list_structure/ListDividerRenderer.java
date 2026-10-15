package com.yovo.yotheme.components.yo_list.list_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.ListConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description ListDividerRenderer 列表分隔线绘制器
 * @note 提供列表分隔线绘制功能
 */
public class ListDividerRenderer {
    private final Paint dividerPaint;
    private final int dividerHeightPx;

    public ListDividerRenderer(Context context) {
        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setColor(ColorMapper.map(YoThemeColor.COMP_DIVIDER));
        // 使用列表自身常量：像素高度
        dividerHeightPx = ListConstants.DIVIDER_HEIGHT_PX.getIntValue();
    }

    public void drawDividers(ViewGroup parent, Canvas canvas, int startInsetPx, int endInsetPx) {
        int childCount = parent.getChildCount();

        for (int i = 0; i < childCount - 1; i++) { 
            View child = parent.getChildAt(i);

            // 参考Menu组件的实现：直接对齐到文本区域边界
            int left;
            int right;
            if (child instanceof ListItemContainer) {
                ListItemContainer listItem = (ListItemContainer) child;
                // 左边界和右边界：对齐到文本区域边界（contentWrapper的padding边界）
                left = listItem.getTextStartXInParent();
                right = listItem.getTextEndXInParent();
            } else {
                // 兼容非ListItemContainer的情况
                left = child.getLeft() + startInsetPx;
                right = child.getRight() - endInsetPx;
            }

            int top = child.getBottom();
            if (right > left) {
                canvas.drawRect(left, top, right, top + dividerHeightPx, dividerPaint);
            }
        }
    }
}



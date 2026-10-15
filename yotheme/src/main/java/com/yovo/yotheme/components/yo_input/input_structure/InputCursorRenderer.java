package com.yovo.yotheme.components.yo_input.input_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.widget.EditText;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.InputConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 自定义输入光标绘制，替代系统 EditText 光标
 */
public class InputCursorRenderer {

    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cursorWidthPx;

    public InputCursorRenderer(Context context) {
        cursorPaint.setColor(ColorMapper.map(YoThemeColor.BRAND));
        cursorPaint.setStyle(Paint.Style.FILL);
        float density = context.getResources().getDisplayMetrics().density;
        cursorWidthPx = InputConstants.CURSOR_WIDTH.getIntValue() * density;
    }

    public void draw(Canvas canvas, EditText editor, boolean visible) {
        if (!visible || editor == null || !editor.isFocused()) {
            return;
        }
        Layout layout = editor.getLayout();
        if (layout == null) {
            return;
        }

        int offset = editor.getSelectionStart();
        if (offset < 0) {
            return;
        }

        int saveCount = canvas.save();
        canvas.translate(editor.getLeft(), editor.getTop());

        int line = layout.getLineForOffset(offset);
        float x = layout.getPrimaryHorizontal(offset)
                + editor.getTotalPaddingLeft()
                - editor.getScrollX();
        float baseline = layout.getLineBaseline(line)
                + editor.getTotalPaddingTop()
                - editor.getScrollY();
        Paint.FontMetricsInt fontMetrics = editor.getPaint().getFontMetricsInt();
        float top = baseline + fontMetrics.top;
        float bottom = baseline + fontMetrics.bottom;

        canvas.drawRect(x, top, x + cursorWidthPx, bottom, cursorPaint);
        canvas.restoreToCount(saveCount);
    }

    public void setCursorColor(int color) {
        cursorPaint.setColor(color);
    }
}

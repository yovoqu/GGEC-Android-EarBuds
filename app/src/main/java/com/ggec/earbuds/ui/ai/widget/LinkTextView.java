package com.ggec.earbuds.ui.ai.widget;

import android.content.Context;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * 能点链接、却不抢走其他触摸的文本视图。
 * 给 TextView 设 LinkMovementMethod 会让它变成可点击、可长按，父级消息卡片的长按菜单就被吃掉了；
 * 这里自己判断手指落在不落在链接上，只有落在链接上才消费事件，其余触摸原样交给父级。
 */
public class LinkTextView extends AppCompatTextView {

    @Nullable
    private ClickableSpan pressedSpan;

    public LinkTextView(@NonNull Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedSpan = spanAt(event.getX(), event.getY());
            if (pressedSpan != null) {
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            ClickableSpan span = pressedSpan;
            pressedSpan = null;
            if (span != null && span == spanAt(event.getX(), event.getY())) {
                span.onClick(this);
                return true;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            pressedSpan = null;
        }
        return pressedSpan != null || super.onTouchEvent(event);
    }

    @Nullable
    private ClickableSpan spanAt(float touchX, float touchY) {
        CharSequence text = getText();
        Layout layout = getLayout();
        if (!(text instanceof Spanned spanned) || layout == null) {
            return null;
        }
        float x = touchX - getTotalPaddingLeft() + getScrollX();
        float y = touchY - getTotalPaddingTop() + getScrollY();
        if (y < 0 || y > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical((int) y);
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null;
        }
        int offset = layout.getOffsetForHorizontal(line, x);
        ClickableSpan[] spans = spanned.getSpans(offset, offset, ClickableSpan.class);
        return spans.length == 0 ? null : spans[0];
    }
}

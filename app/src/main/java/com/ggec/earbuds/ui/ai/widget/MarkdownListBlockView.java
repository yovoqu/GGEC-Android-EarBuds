package com.ggec.earbuds.ui.ai.widget;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;

import java.util.List;

/** Markdown 列表：项目符号、编号和复选项共用同一行布局。 */
final class MarkdownListBlockView extends LinearLayout {

    private final AiTheme theme;
    private final MarkdownTextFactory text;

    MarkdownListBlockView(@NonNull AiTheme theme, @NonNull MarkdownTextFactory text) {
        super(theme.context);
        this.theme = theme;
        this.text = text;
        setOrientation(VERTICAL);
    }

    void bind(@NonNull MdBlock.ListBlock block) {
        List<MdBlock.ListItem> items = block.items();
        for (int i = 0; i < items.size(); i++) {
            MdBlock.ListItem item = items.get(i);
            ItemRow row = i < getChildCount() ? (ItemRow) getChildAt(i) : null;
            if (row == null) {
                row = new ItemRow();
                addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.bind(item, i == 0);
        }
        while (getChildCount() > items.size()) {
            removeViewAt(getChildCount() - 1);
        }
    }

    private final class ItemRow extends LinearLayout {

        private final TextView marker;
        private final View dot;
        private final TextView content;

        ItemRow() {
            super(theme.context);
            setOrientation(HORIZONTAL);
            content = text.body(text.textSize(), text.textColor());
            android.graphics.Paint.FontMetrics metrics = content.getPaint().getFontMetrics();
            float firstLine = metrics.descent - metrics.ascent;

            FrameLayout slot = new FrameLayout(theme.context);
            marker = theme.text(text.textSize(), text.textColor());
            marker.setGravity(Gravity.END);
            marker.setLineSpacing(theme.dpf(4), 1f);
            slot.addView(marker, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            dot = new View(theme.context);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                    theme.dp(6), theme.dp(6), Gravity.END | Gravity.TOP);
            dotParams.topMargin = Math.round(firstLine / 2f - theme.dp(3));
            dotParams.setMarginEnd(theme.dp(4));
            slot.addView(dot, dotParams);
            addView(slot, new LinearLayout.LayoutParams(
                    theme.dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginStart(theme.dp(6));
            addView(content, params);
        }

        void bind(@NonNull MdBlock.ListItem item, boolean first) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
            params.topMargin = first ? 0 : theme.dp(4);
            params.setMarginStart(theme.dp(18f * item.level()));
            setLayoutParams(params);
            boolean bullet = item.checked() == null && !item.ordered();
            dot.setVisibility(bullet ? VISIBLE : GONE);
            marker.setVisibility(bullet ? GONE : VISIBLE);
            if (bullet) {
                dot.setBackground(item.level() % 2 == 0
                        ? theme.circle(text.textColor())
                        : theme.ring(text.textColor(), 1.5f));
            } else if (item.checked() != null) {
                marker.setText(item.checked() ? "☑" : "☐");
            } else {
                marker.setText(item.number() + ".");
            }
            content.setText(text.spannable(item.spans()));
        }
    }
}

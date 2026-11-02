package com.ggec.earbuds.ui.ai.widget;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;
import com.ggec.earbuds.ui.ai.markdown.MdSpan;
import com.yohu.youi.api.theme.YoFonts;

import java.util.List;

/** Markdown 表格：固定单元格最小宽度，超宽时整体横向滚动。 */
final class MarkdownTableBlockView extends HorizontalScrollView {

    private final AiTheme theme;
    private final MarkdownTextFactory text;
    private final TableLayout table;

    MarkdownTableBlockView(@NonNull AiTheme theme, @NonNull MarkdownTextFactory text) {
        super(theme.context);
        this.theme = theme;
        this.text = text;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        table = new TableLayout(theme.context);
        table.setBackground(theme.roundedStroke(0, 10, theme.cardStroke, 1));
        table.setClipToOutline(true);
        table.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        addView(table, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void bind(@NonNull MdBlock.Table block) {
        table.removeAllViews();
        addRow(block, block.header(), true);
        for (List<List<MdSpan>> row : block.rows()) {
            addRow(block, row, false);
        }
    }

    private void addRow(@NonNull MdBlock.Table block, @NonNull List<List<MdSpan>> cells,
                        boolean header) {
        TableRow row = new TableRow(theme.context);
        if (header) {
            row.setBackgroundColor(theme.fill);
        } else if (table.getChildCount() > 0) {
            View separator = new View(theme.context);
            separator.setBackgroundColor(theme.cardStroke);
            table.addView(separator, new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));
        }
        for (int column = 0; column < block.aligns().size(); column++) {
            TextView cell = text.body(text.textSize() - 1f, text.textColor());
            cell.setMaxWidth(theme.dp(220));
            cell.setMinWidth(theme.dp(64));
            cell.setPadding(theme.dp(10), theme.dp(8), theme.dp(10), theme.dp(8));
            if (header) {
                YoFonts.applyBold(cell);
            }
            MdBlock.Align align = block.aligns().get(column);
            cell.setGravity(align == MdBlock.Align.END ? Gravity.END
                    : align == MdBlock.Align.CENTER ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            List<MdSpan> spans = column < cells.size() ? cells.get(column) : List.of();
            cell.setText(text.spannable(spans));
            row.addView(cell, new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        table.addView(row, new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
